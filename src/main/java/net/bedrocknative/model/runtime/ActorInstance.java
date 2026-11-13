package net.bedrocknative.model.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.BoneSlots;
import net.bedrocknative.model.animation.BoneTimeline;
import net.bedrocknative.model.animation.ControllerDef;
import net.bedrocknative.model.animation.Keyframe;
import net.bedrocknative.model.animation.PoseBuffer;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.OverlayPlan;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.render.MaterialTable;
import net.bedrocknative.model.render.RenderControllerDef;
import net.bedrocknative.molang.EffectSink;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangGeometry;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangResources;
import net.bedrocknative.molang.MolangStruct;
import net.bedrocknative.molang.MolangVersion;
import net.bedrocknative.molang.VariableStore;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class ActorInstance implements MolangGeometry, WearerBones {
   private static final int MAX_CONTROLLER_NESTING = 8;
   private static final float[] WHITE = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
   private static final float[] CLEAR = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
   private static final float[] HURT = new float[]{1.0F, 0.0F, 0.0F, 0.5F};
   private static final boolean HOLD_LAST_POSE_WINS = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.holdLastPose"));
   private final ModelRegistry registry;
   private final ClientEntityDef definition;
   private final VariableStore variables = new VariableStore();
   private final VariableStore context = new VariableStore(8);
   private final BoneSlots slots = new BoneSlots();
   private final Map<String, ActorInstance.GeometryState> geometries = new HashMap<>();
   private final ActorInstance.GeometryState[] geometryList;
   @Nullable
   private final ActorInstance.GeometryState primary;
   @Nullable
   private ActorInstance.GeometryState rest;
   private final PoseBuffer pose;
   private final Map<AnimationDef, int[]> animationSlots = new HashMap<>();
   private final List<ActorInstance.AnimateSlot> animate = new ArrayList<>();
   private final List<RuntimeController> runtimeControllers = new ArrayList<>();
   private final List<RuntimeController> pendingRuntimeControllers = new ArrayList<>();
   private final List<ActorInstance> attachables = new ArrayList<>();
   private final Map<String, MolangProgram> bindings = new HashMap<>();
   @Nullable
   private WearerBones wearer;
   @Nullable
   private MolangGeometry wearerGeometry;
   private final MolangEnv bindingEnv = new MolangEnv();
   private final DrawList drawList = new DrawList();
   private final DrawList.DrawCall[] callPool;
   private MolangActor currentActor = MolangActor.NONE;
   private boolean initialized;
   private long frame;
   private int changeColor = -1;
   private int secondColor = -1;
   private final boolean vanilla;
   @Nullable
   private ActorInstance.GeometryState firstDrawn;
   @Nullable
   private boolean[] drawnControllers;
   private long lastHeldSignature;

   public ActorInstance(ModelRegistry registry, ClientEntityDef definition) {
      this(registry, definition, false);
   }

   public ActorInstance(ModelRegistry registry, ClientEntityDef definition, boolean vanilla) {
      this(registry, definition, vanilla, null);
   }

   public ActorInstance(ModelRegistry registry, ClientEntityDef definition, boolean vanilla, @Nullable Function<String, MeshedGeometry> own) {
      this.registry = registry;
      this.definition = definition;
      this.vanilla = vanilla;
      this.callPool = new DrawList.DrawCall[definition.renderControllers().size()];

      for (int i = 0; i < this.callPool.length; i++) {
         this.callPool[i] = new DrawList.DrawCall();
      }

      Map<String, MeshedGeometry> meshes = new HashMap<>();

      for (String identifier : definition.geometries().values()) {
         MeshedGeometry mesh = own != null ? own.apply(identifier) : null;
         if (mesh == null) {
            mesh = registry.mesh(identifier, definition);
         }

         if (mesh != null) {
            meshes.put(identifier, mesh);
            this.slots.addAll(mesh);
         }
      }

      meshes.forEach((identifier, meshx) -> this.geometries.put(identifier, new ActorInstance.GeometryState(meshx, this.slots.map(meshx))));
      this.geometryList = this.geometries.values().toArray(new ActorInstance.GeometryState[0]);
      this.primary = this.geometries.get(primaryGeometry(definition));
      this.pose = new PoseBuffer(this.slots.size());
      this.rest = this.primary;
      if (this.primary != null) {
         this.pose.setRest(this.primary.mesh, this.primary.boneSlots);
      }

      this.pose.reset();

      for (ClientEntityDef.AnimateEntry entry : definition.scripts().animate()) {
         this.animate.add(new ActorInstance.AnimateSlot(entry, this.create(entry.name())));
      }

      if (vanilla && definition.playsLegacyControllers()) {
         definition.legacyControllers()
            .forEach(
               (name, id) -> this.animate.add(new ActorInstance.AnimateSlot(new ClientEntityDef.AnimateEntry(name, null), this.createById(id, List.of())))
            );
      }
   }

   @Nullable
   private static String primaryGeometry(ClientEntityDef definition) {
      String queryable = definition.queryableGeometry() != null ? definition.geometry(definition.queryableGeometry()) : null;
      if (queryable != null) {
         return queryable;
      } else {
         String fallback = definition.geometry("default");
         return fallback != null ? fallback : definition.geometries().values().stream().findFirst().orElse(null);
      }
   }

   public ClientEntityDef definition() {
      return this.definition;
   }

   public void restrict(OverlayPlan plan) {
      List<ClientEntityDef.RenderControllerRef> references = this.definition.renderControllers();
      this.drawnControllers = new boolean[references.size()];

      for (int i = 0; i < references.size(); i++) {
         this.drawnControllers[i] = plan.draws(references.get(i).name());
      }

      boolean[] kept = plan.animateKept();

      for (int i = 0; i < this.animate.size() && i < kept.length; i++) {
         if (!kept[i]) {
            this.animate.set(i, new ActorInstance.AnimateSlot(this.animate.get(i).entry, null));
         }
      }
   }

   public VariableStore variables() {
      return this.variables;
   }

   public VariableStore context() {
      return this.context;
   }

   public void setChangeColor(int argb) {
      this.changeColor = argb;
   }

   public void setSecondColor(int argb) {
      this.secondColor = argb;
   }

   public PoseBuffer pose() {
      return this.pose;
   }

   public BoneSlots slots() {
      return this.slots;
   }

   @Nullable
   public Skeleton skeleton(String geometryIdentifier) {
      ActorInstance.GeometryState state = this.geometries.get(geometryIdentifier);
      return state != null ? state.skeleton : null;
   }

   @Nullable
   public Skeleton primarySkeleton() {
      return this.primary != null ? this.primary.skeleton : null;
   }

   @Nullable
   public Skeleton drawnSkeleton() {
      return this.firstDrawn != null ? this.firstDrawn.skeleton : null;
   }

   private void updateHeldMasks() {
      long signature = 0L;
      int count = Math.min(this.animate.size(), 62);

      for (int i = 0; i < count; i++) {
         if (this.animate.get(i).held) {
            signature |= 1L << i;
         }
      }

      if (signature != this.lastHeldSignature) {
         this.lastHeldSignature = signature;

         for (int ix = 0; ix < this.animate.size(); ix++) {
            ActorInstance.AnimateSlot slot = this.animate.get(ix);
            slot.heldMask = null;
            if (slot.playable instanceof AnimationNode node && !node.looping()) {
               int[] mask = null;

               for (int bone = 0; bone < node.slots().length; bone++) {
                  int boneSlot = node.slots()[bone];
                  if (boneSlot >= 0 && this.coveredLater(ix, boneSlot)) {
                     if (mask == null) {
                        mask = (int[])node.slots().clone();
                     }

                     mask[bone] = -1;
                  }
               }

               slot.heldMask = mask;
            }
         }
      }
   }

   private boolean coveredLater(int index, int boneSlot) {
      for (int later = index + 1; later < this.animate.size(); later++) {
         ActorInstance.AnimateSlot other = this.animate.get(later);
         if (other.held && other.playable instanceof AnimationNode node) {
            for (int slot : node.slots()) {
               if (slot == boneSlot) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Nullable
   Playable create(String name) {
      return this.create(name, List.of());
   }

   @Nullable
   private Playable create(String name, List<String> ancestors) {
      String id = this.definition.animation(name);
      return this.createById(id != null ? id : name, ancestors);
   }

   @Nullable
   private Playable createById(String id, List<String> ancestors) {
      ControllerDef controller = this.registry.controller(id);
      if (controller != null) {
         if (ancestors.size() < 8 && !ancestors.contains(id)) {
            List<String> nested = new ArrayList<>(ancestors);
            nested.add(id);
            List<String> chain = List.copyOf(nested);
            return new ControllerInstance(controller, child -> this.create(child, chain), this.vanilla);
         } else {
            return null;
         }
      } else {
         AnimationDef animation = this.registry.animation(id);
         return animation != null ? new AnimationNode(animation, this.slotsFor(animation)) : null;
      }
   }

   private int[] slotsFor(AnimationDef animation) {
      return this.animationSlots.computeIfAbsent(animation, this.slots::map);
   }

   public boolean animate(String animation, String nextState, String stopExpression, int stopExpressionVersion, String controller, float blendOutTime) {
      AnimationDef definitionAnimation = this.registry.animation(animation);
      if (definitionAnimation == null && this.definition.animation(animation) != null) {
         definitionAnimation = this.registry.animation(this.definition.animation(animation));
      }

      if (definitionAnimation == null) {
         return false;
      } else {
         String key = controller.isEmpty() ? animation : controller;
         this.runtimeControllers.removeIf(running -> running.key.equals(key));
         this.pendingRuntimeControllers.removeIf(pending -> pending.key.equals(key));
         MolangProgram stop = this.registry
            .molang()
            .compile(stopExpression.isBlank() ? "query.any_animation_finished" : stopExpression, MolangVersion.ofProtocol(stopExpressionVersion));
         this.pendingRuntimeControllers
            .add(new RuntimeController(key, new AnimationNode(definitionAnimation, this.slotsFor(definitionAnimation)), stop, blendOutTime));
         return true;
      }
   }

   public void play(AnimationDef animation, String key, @Nullable MolangProgram stop, float blendOutTime) {
      this.runtimeControllers.removeIf(running -> running.key.equals(key));
      this.pendingRuntimeControllers.removeIf(pending -> pending.key.equals(key));
      MolangProgram until = stop != null ? stop : this.registry.molang().compile("query.any_animation_finished", MolangVersion.LATEST);
      this.pendingRuntimeControllers.add(new RuntimeController(key, new AnimationNode(animation, this.slotsFor(animation)), until, blendOutTime));
   }

   public void stop(String key) {
      this.runtimeControllers.removeIf(running -> running.key.equals(key));
      this.pendingRuntimeControllers.removeIf(pending -> pending.key.equals(key));
   }

   public boolean playing(String key) {
      for (int i = 0; i < this.runtimeControllers.size(); i++) {
         if (this.runtimeControllers.get(i).key.equals(key)) {
            return true;
         }
      }

      for (int ix = 0; ix < this.pendingRuntimeControllers.size(); ix++) {
         if (this.pendingRuntimeControllers.get(ix).key.equals(key)) {
            return true;
         }
      }

      return false;
   }

   public int runtimeControllers() {
      return this.runtimeControllers.size() + this.pendingRuntimeControllers.size();
   }

   public List<String> runtimeAnimations() {
      List<String> names = new ArrayList<>();
      this.pendingRuntimeControllers.forEach(controller -> names.add(controller.animation()));
      this.runtimeControllers.forEach(controller -> names.add(controller.animation()));
      return names;
   }

   public ActorInstance attach(ClientEntityDef attachable) {
      ActorInstance instance = new ActorInstance(this.registry, attachable, this.vanilla);
      instance.wearer = this;
      instance.wearerGeometry = this;
      this.attachables.add(instance);
      return instance;
   }

   public void detach(ActorInstance attachable) {
      this.attachables.remove(attachable);
   }

   public void setWearer(@Nullable WearerBones bones, @Nullable MolangGeometry geometry) {
      this.wearer = bones;
      this.wearerGeometry = geometry;
   }

   public DrawList frame(MolangEnv env, MolangActor actor, float deltaTime, float frameAlpha) {
      EffectSink outer = env.effects;
      if (outer == null) {
         return this.frameScoped(env, actor, deltaTime, frameAlpha);
      } else {
         env.effects = outer.forDefinition(this.definition);

         DrawList var6;
         try {
            var6 = this.frameScoped(env, actor, deltaTime, frameAlpha);
         } finally {
            env.effects = outer;
         }

         return var6;
      }
   }

   private DrawList frameScoped(MolangEnv env, MolangActor actor, float deltaTime, float frameAlpha) {
      this.frame++;
      this.prepare(env, actor, deltaTime, frameAlpha);
      ClientEntityDef.Scripts scripts = this.definition.scripts();
      if (!this.initialized) {
         this.initialized = true;
         run(scripts.initialize(), env);
      }

      run(scripts.preAnimation(), env);

      for (int i = 0; i < this.attachables.size(); i++) {
         run(this.attachables.get(i).definition.scripts().parentSetup(), env);
      }

      ActorInstance.GeometryState drawn = this.restGeometry(env);
      if (drawn != null && drawn != this.rest) {
         this.rest = drawn;
         this.pose.setRest(drawn.mesh, drawn.boneSlots);
      }

      this.pose.reset();
      this.updateHeldMasks();

      for (int i = 0; i < this.animate.size(); i++) {
         ActorInstance.AnimateSlot slot = this.animate.get(i);
         if (slot.playable != null) {
            float weight = slot.entry.weight() != null ? slot.entry.weight().evaluate(env) : 1.0F;
            slot.lastWeight = weight;
            if (weight == 0.0F) {
               slot.active = false;
               slot.held = false;
            } else {
               if (!slot.active) {
                  slot.playable.restart(env);
                  slot.active = true;
               }

               slot.playable.update(env, deltaTime);
               if (slot.heldMask != null && slot.playable instanceof AnimationNode node) {
                  node.applyOnly(this.pose, weight, env, slot.heldMask);
               } else {
                  slot.playable.apply(this.pose, weight, env);
               }

               slot.held = HOLD_LAST_POSE_WINS && !this.vanilla && slot.playable instanceof AnimationNode node && node.heldPose();
            }
         }
      }

      for (int ix = 0; ix < this.pendingRuntimeControllers.size(); ix++) {
         RuntimeController pending = this.pendingRuntimeControllers.get(ix);
         pending.start(env);
         this.runtimeControllers.add(pending);
      }

      this.pendingRuntimeControllers.clear();

      for (int ix = 0; ix < this.runtimeControllers.size(); ix++) {
         RuntimeController controller = this.runtimeControllers.get(ix);
         controller.update(env, deltaTime);
         controller.apply(this.pose, env);
      }

      this.runtimeControllers.removeIf(RuntimeController::done);
      float scale = value(scripts.scale(), env, 1.0F);
      this.drawList.begin(scale * value(scripts.scaleX(), env, 1.0F), scale * value(scripts.scaleY(), env, 1.0F), scale * value(scripts.scaleZ(), env, 1.0F));
      this.firstDrawn = null;
      List<ClientEntityDef.RenderControllerRef> references = this.definition.renderControllers();

      for (int ix = 0; ix < references.size(); ix++) {
         ClientEntityDef.RenderControllerRef reference = references.get(ix);
         if ((this.drawnControllers == null || this.drawnControllers[ix]) && (reference.condition() == null || reference.condition().test(env))) {
            RenderControllerDef controller = this.registry.renderController(reference.name());
            if (controller != null && this.render(controller, env, this.callPool[ix])) {
               this.drawList.add(this.callPool[ix]);
            }
         }
      }

      env.resources = null;
      this.drawList.endOwn();
      if (this.primary != null) {
         this.posed(this.primary);
      }

      for (int ixx = 0; ixx < this.attachables.size(); ixx++) {
         List<DrawList.DrawCall> attached = this.attachables.get(ixx).frame(env, actor, deltaTime, frameAlpha).calls();

         for (int call = 0; call < attached.size(); call++) {
            this.drawList.add(attached.get(call));
         }
      }

      return this.drawList;
   }

   public void adoptVariables(ActorInstance previous) {
      VariableStore old = previous.variables;
      old.forEachId(id -> this.variables.set(id, old.get(id), old.object(id)));
      this.initialized = previous.initialized;
   }

   public String traceScripts() {
      ClientEntityDef.Scripts scripts = this.definition.scripts();
      return this.definition.identifier()
         + ": initialize ["
         + (scripts.initialize() != null ? scripts.initialize().source() : "")
         + "] pre_animation ["
         + (scripts.preAnimation() != null ? scripts.preAnimation().source() : "")
         + "] animations "
         + this.definition.animations();
   }

   public String trace() {
      StringBuilder out = new StringBuilder(this.definition.identifier()).append(": animate");
      StringBuilder details = new StringBuilder();

      for (ActorInstance.AnimateSlot slot : this.animate) {
         out.append(' ').append(slot.entry.name());
         if (slot.entry.weight() != null) {
            details.append(" ")
               .append(slot.entry.name())
               .append("{w=")
               .append(slot.entry.weight().source())
               .append(" now ")
               .append(slot.lastWeight)
               .append('}');
         }

         if (slot.playable instanceof AnimationNode node) {
            AnimationDef animation = node.animation;
            details.append(slot.entry.weight() == null ? " " + slot.entry.name() : "")
               .append('<')
               .append(animation.name())
               .append(' ')
               .append(animation.loop())
               .append(" length ")
               .append(animation.length());
            if (animation.animTimeUpdate() != null) {
               details.append(" time ").append(animation.animTimeUpdate().source());
            }

            details.append(" blend ")
               .append(animation.blendWeight().source())
               .append(" delay ")
               .append(animation.startDelay().source())
               .append(" override ")
               .append(animation.overridePreviousAnimation())
               .append(" bones");

            for (BoneTimeline timeline : animation.bones()) {
               details.append(' ')
                  .append(timeline.bone())
                  .append(timeline.rotation() != null ? "+r" + timeline.rotation().length : "")
                  .append(timeline.position() != null ? "+p" + timeline.position().length : "");
               if (timeline.rotation() != null && timeline.bone().toLowerCase(Locale.ROOT).equals("top")) {
                  details.append(" top.rot[");

                  for (Keyframe key : timeline.rotation()) {
                     details.append(String.format(Locale.ROOT, "%.2f:", key.time()));

                     for (int axis = 0; axis < 3; axis++) {
                        details.append(key.post().isConstant() ? String.valueOf(key.post().constant(axis)) : key.post().program(axis).source())
                           .append(axis < 2 ? "," : " ");
                     }
                  }

                  details.append(']');
               }
            }

            details.append('>');
         }

         if (slot.playable == null) {
            out.append("(missing)");
         } else if (!slot.active) {
            out.append("(off)");
         } else if (slot.held) {
            out.append("(held").append(slot.heldMask != null ? ", replaced on some bones" : "").append(')');
         } else if (slot.playable instanceof ControllerInstance controller) {
            out.append("->").append(controller.state());
         }
      }

      ActorInstance.GeometryState drawn = this.firstDrawn != null ? this.firstDrawn : this.primary;
      if (drawn == null) {
         return out.append("; nothing drawn").append(details.length() > 0 ? " | animations" + details : "").toString();
      } else {
         out.append("; ").append(drawn.mesh.identifier()).append(" moved");

         for (int i = 0; i < drawn.mesh.boneCount(); i++) {
            int slot = drawn.boneSlots[i];
            if (slot >= 0) {
               MeshedGeometry.Bone bone = drawn.mesh.bone(i);
               float[] rest = bone.restRotation();
               boolean moved = false;

               for (int axis = 0; axis < 3; axis++) {
                  moved |= this.pose.position(slot, axis) != 0.0F || this.pose.scale(slot, axis) != 1.0F || this.pose.rotation(slot, axis) != rest[axis];
               }

               if (moved) {
                  out.append(
                     String.format(
                        Locale.ROOT,
                        " %s[pivot %.1f %.1f %.1f, position %.2f %.2f %.2f, rotation %.1f %.1f %.1f, scale %.2f %.2f %.2f]",
                        bone.name(),
                        0.0F - bone.pivot()[0],
                        bone.pivot()[1],
                        bone.pivot()[2],
                        this.pose.position(slot, 0),
                        this.pose.position(slot, 1),
                        this.pose.position(slot, 2),
                        this.pose.rotation(slot, 0),
                        this.pose.rotation(slot, 1),
                        this.pose.rotation(slot, 2),
                        this.pose.scale(slot, 0),
                        this.pose.scale(slot, 1),
                        this.pose.scale(slot, 2)
                     )
                  );
               }
            }
         }

         return details.length() > 0 ? out.append(" | animations").append((CharSequence)details).toString() : out.toString();
      }
   }

   public int openLids() {
      ActorInstance.GeometryState drawn = this.firstDrawn != null ? this.firstDrawn : this.primary;
      if (drawn == null) {
         return 0;
      } else {
         int open = 0;

         for (int i = 0; i < drawn.mesh.boneCount(); i++) {
            int slot = drawn.boneSlots[i];
            String name = drawn.mesh.bone(i).name().toLowerCase(Locale.ROOT);
            if (slot >= 0 && (name.equals("top") || name.contains("lid") || name.contains("cover") || name.contains("door") || name.contains("hatch"))) {
               float[] rest = drawn.mesh.bone(i).restRotation();

               for (int axis = 0; axis < 3; axis++) {
                  if (Math.abs(this.pose.rotation(slot, axis) - rest[axis]) > 60.0F) {
                     open++;
                     break;
                  }
               }
            }
         }

         return open;
      }
   }

   public DrawList lastDrawList() {
      return this.drawList;
   }

   public long frames() {
      return this.frame;
   }

   private void prepare(MolangEnv env, MolangActor actor, float deltaTime, float frameAlpha) {
      this.currentActor = actor;
      env.actor = actor;
      env.variables = this.variables;
      env.context = this.context;
      env.geometry = this;
      env.controller = null;
      env.resources = null;
      env.deltaTime = deltaTime;
      env.frameAlpha = frameAlpha;
      env.animTime = 0.0F;
      env.thisValue = 0.0F;
      env.attachable = this.wearer != null;
   }

   @Nullable
   private ActorInstance.GeometryState restGeometry(MolangEnv env) {
      List<ClientEntityDef.RenderControllerRef> references = this.definition.renderControllers();

      for (int i = 0; i < references.size(); i++) {
         ClientEntityDef.RenderControllerRef reference = references.get(i);
         if ((this.drawnControllers == null || this.drawnControllers[i]) && (reference.condition() == null || reference.condition().test(env))) {
            RenderControllerDef controller = this.registry.renderController(reference.name());
            if (controller != null) {
               env.resources = controller;
               String geometryId = this.resolve(controller.geometry().evaluateObject(env), "geometry");
               env.resources = null;
               ActorInstance.GeometryState state = geometryId != null ? this.geometries.get(geometryId) : null;
               if (state != null) {
                  return state;
               }
            }
         }
      }

      return null;
   }

   private boolean render(RenderControllerDef controller, MolangEnv env, DrawList.DrawCall call) {
      env.resources = controller;
      String geometryId = this.resolve(controller.geometry().evaluateObject(env), "geometry");
      ActorInstance.GeometryState state = geometryId != null ? this.geometries.get(geometryId) : null;
      if (state == null) {
         return false;
      } else {
         this.posed(state);
         if (this.firstDrawn == null) {
            this.firstDrawn = state;
         }

         call.reset(controller.name(), geometryId, state.mesh, state.skeleton, this.wearer != null ? state.bindings : null);
         List<String> textures = call.mutableTextures();
         List<MolangProgram> texturePrograms = controller.textures();

         for (int i = 0; i < texturePrograms.size(); i++) {
            String path = this.resolve(texturePrograms.get(i).evaluateObject(env), "texture");
            if (path != null) {
               textures.add(path);
            }
         }

         int[][] patterns = state.patterns.get(controller);
         if (patterns == null) {
            patterns = patterns(controller, state.mesh);
            state.patterns.put(controller, patterns);
         }

         int bones = state.mesh.boneCount();
         MaterialTable.MaterialInfo[] materials = call.boneMaterials();
         List<RenderControllerDef.PatternValue> materialPatterns = controller.materials();
         if (materialPatterns.isEmpty()) {
            String name = this.definition.material("default");
            Arrays.fill(materials, this.registry.materials().get(name != null ? name : "entity_alphatest"));
         }

         for (int ix = 0; ix < materialPatterns.size(); ix++) {
            String name = this.resolve(materialPatterns.get(ix).value().evaluateObject(env), "material");
            if (name != null) {
               MaterialTable.MaterialInfo material = this.registry.materials().get(name);

               for (int bone : patterns[ix]) {
                  materials[bone] = material;
               }
            }
         }

         boolean[] visible = call.boneVisible();
         List<RenderControllerDef.PatternValue> visibility = controller.partVisibility();

         for (int ixx = 0; ixx < visibility.size(); ixx++) {
            boolean shown = visibility.get(ixx).value().test(env);

            for (int bone : patterns[materialPatterns.size() + ixx]) {
               visible[bone] = shown;
            }
         }

         for (int bone = 0; !this.vanilla && bone < bones; bone++) {
            int parent = state.mesh.bone(bone).parent();
            if (parent >= 0 && !visible[parent]) {
               visible[bone] = false;
            }
         }

         call.changeColor(this.changeColor);
         call.secondColor(this.secondColor);
         if (controller.color() != null) {
            this.color(controller.color(), env, call.colorArray(), WHITE);
         }

         if (controller.overlayColor() != null) {
            this.color(controller.overlayColor(), env, call.overlayColorArray(), CLEAR);
         }

         if (controller.isHurtColor() != null) {
            this.color(controller.isHurtColor(), env, call.hurtColorArray(), HURT);
         }

         if (controller.onFireColor() != null) {
            this.color(controller.onFireColor(), env, call.fireColorArray(), CLEAR);
         }

         if (this.vanilla) {
            env.thisValue = 0.0F;
         }

         RenderControllerDef.UvAnimation uv = controller.uvAnimation();
         if (uv != null) {
            float[] values = call.uvAnimationArray();
            values[0] = uv.offsetU().evaluate(env);
            values[1] = uv.offsetV().evaluate(env);
            values[2] = uv.scaleU().evaluate(env);
            values[3] = uv.scaleV().evaluate(env);
         }

         call.lighting(controller.ignoreLighting() != null && controller.ignoreLighting().test(env), value(controller.lightColorMultiplier(), env, 1.0F));
         return true;
      }
   }

   private static int[][] patterns(RenderControllerDef controller, MeshedGeometry mesh) {
      List<RenderControllerDef.PatternValue> all = new ArrayList<>(controller.materials());
      all.addAll(controller.partVisibility());
      int[][] bones = new int[all.size()][];

      for (int i = 0; i < all.size(); i++) {
         List<Integer> matched = new ArrayList<>();

         for (int bone = 0; bone < mesh.boneCount(); bone++) {
            if (all.get(i).matches(mesh.bone(bone).name())) {
               matched.add(bone);
            }
         }

         bones[i] = matched.stream().mapToInt(Integer::intValue).toArray();
      }

      return bones;
   }

   private void posed(ActorInstance.GeometryState state) {
      if (state.posedFrame != this.frame) {
         state.posedFrame = this.frame;
         if (this.wearer != null) {
            for (int bone = 0; bone < state.mesh.boneCount(); bone++) {
               MeshedGeometry.Bone definitionBone = state.mesh.bone(bone);
               boolean root = definitionBone.parent() < 0;
               if (!root && definitionBone.binding() == null && !this.wearer.has(definitionBone.key())) {
                  state.bindings[bone] = null;
                  state.skeleton.bind(bone, null);
               } else {
                  String target = this.bindingTarget(definitionBone);
                  if (state.bound[bone] == null) {
                     state.bound[bone] = new Matrix4f();
                  }

                  Matrix4f bound = this.wearer.bind(target, definitionBone, state.bound[bone]);
                  state.bindings[bone] = !root && bound == null ? null : target;
                  state.skeleton.bind(bone, bound);
               }
            }
         }

         this.pose.applyTo(state.skeleton, state.boneSlots);
      }
   }

   private String bindingTarget(MeshedGeometry.Bone bone) {
      if (bone.binding() == null) {
         return bone.name();
      } else {
         MolangProgram program = this.bindings.get(bone.binding());
         if (program == null) {
            program = this.registry.molang().compile(bone.binding(), MolangVersion.LATEST);
            this.bindings.put(bone.binding(), program);
         }

         this.bindingEnv.actor = this.currentActor;
         this.bindingEnv.context = this.context;
         this.bindingEnv.variables = this.variables;
         this.bindingEnv.attachable = true;
         return program.evaluateObject(this.bindingEnv) instanceof String name && !name.isEmpty() ? name : bone.name();
      }
   }

   @Nullable
   private String resolve(@Nullable Object value, String kind) {
      if (value instanceof MolangResources.Ref ref) {
         return switch (kind) {
            case "geometry" -> this.definition.geometry(ref.name());
            case "texture" -> this.definition.texture(ref.name());
            default -> this.definition.material(ref.name());
         };
      } else {
         return value instanceof String text && !text.isEmpty() ? text : null;
      }
   }

   private void color(RenderControllerDef.Color color, MolangEnv env, float[] into, float[] defaults) {
      into[0] = this.channel(color.r(), env, defaults[0]);
      into[1] = this.channel(color.g(), env, defaults[1]);
      into[2] = this.channel(color.b(), env, defaults[2]);
      into[3] = this.channel(color.a(), env, defaults[3]);
   }

   private float channel(MolangProgram program, MolangEnv env, float engineValue) {
      if (this.vanilla) {
         env.thisValue = engineValue;
      }

      return program.evaluate(env);
   }

   private static float value(@Nullable MolangProgram program, MolangEnv env, float fallback) {
      return program != null ? program.evaluate(env) : fallback;
   }

   private static void run(@Nullable MolangProgram program, MolangEnv env) {
      if (program != null) {
         program.evaluate(env);
      }
   }

   @Nullable
   @Override
   public Matrix4f bone(String name) {
      ActorInstance.GeometryState drawn = this.firstDrawn;
      if (drawn != null && drawn != this.primary) {
         int index = drawn.mesh.bone(name);
         if (index >= 0) {
            this.posed(drawn);
            return drawn.skeleton.world(index);
         }
      }

      if (this.primary != null) {
         int index = this.primary.mesh.bone(name);
         if (index >= 0) {
            this.posed(this.primary);
            return this.primary.skeleton.world(index);
         }
      }

      for (int i = 0; i < this.geometryList.length; i++) {
         ActorInstance.GeometryState state = this.geometryList[i];
         int index = state.mesh.bone(name);
         if (index >= 0) {
            this.posed(state);
            return state.skeleton.world(index);
         }
      }

      return null;
   }

   @Nullable
   @Override
   public float[] pivot(String name) {
      ActorInstance.GeometryState drawn = this.firstDrawn;
      if (drawn != null && drawn.mesh.bone(name) >= 0) {
         return drawn.mesh.bone(drawn.mesh.bone(name)).pivot();
      } else if (this.primary != null && this.primary.mesh.bone(name) >= 0) {
         return this.primary.mesh.bone(this.primary.mesh.bone(name)).pivot();
      } else {
         for (int i = 0; i < this.geometryList.length; i++) {
            int index = this.geometryList[i].mesh.bone(name);
            if (index >= 0) {
               return this.geometryList[i].mesh.bone(index).pivot();
            }
         }

         return null;
      }
   }

   @Nullable
   @Override
   public MolangStruct bone(String query, String bone) {
      if (this.primary == null) {
         return null;
      } else {
         int index = this.primary.mesh.bone(bone);
         if (index < 0) {
            return null;
         } else {
            MeshedGeometry.Bone definitionBone = this.primary.mesh.bone(index);

            return switch (query) {
               case "origin" -> MolangStruct.xyz(-definitionBone.pivot()[0], definitionBone.pivot()[1], definitionBone.pivot()[2]);
               case "rotation" -> MolangStruct.xyz(definitionBone.restRotation()[0], definitionBone.restRotation()[1], definitionBone.restRotation()[2]);
               default -> aabb(definitionBone);
            };
         }
      }
   }

   private static MolangStruct aabb(MeshedGeometry.Bone bone) {
      float[] quads = bone.quads();
      float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      for (int i = 0; i < quads.length; i += 8) {
         float[] raw = new float[]{-quads[i], quads[i + 1], quads[i + 2]};

         for (int axis = 0; axis < 3; axis++) {
            min[axis] = Math.min(min[axis], raw[axis]);
            max[axis] = Math.max(max[axis], raw[axis]);
         }
      }

      if (quads.length == 0) {
         min = max = new float[3];
      }

      MolangStruct struct = new MolangStruct();
      struct.members().set("min", MolangStruct.xyz(min[0], min[1], min[2]));
      struct.members().set("max", MolangStruct.xyz(max[0], max[1], max[2]));
      return struct;
   }

   @Override
   public float defaultBonePivot(String bone, int axis) {
      if (this.primary != null && axis >= 0 && axis <= 2) {
         int index = this.primary.mesh.bone(bone);
         if (index < 0) {
            return 0.0F;
         } else {
            float value = this.primary.mesh.bone(index).pivot()[axis];
            return axis == 0 ? -value : value;
         }
      } else {
         return 0.0F;
      }
   }

   @Override
   public float locatorOffset(String locator, int axis) {
      if (this.primary != null && axis >= 0 && axis <= 2) {
         for (int i = 0; i < this.primary.mesh.boneCount(); i++) {
            List<MeshedGeometry.Locator> locators = this.primary.mesh.bone(i).locators();

            for (int l = 0; l < locators.size(); l++) {
               MeshedGeometry.Locator found = locators.get(l);
               if (found.name().equalsIgnoreCase(locator)) {
                  return axis == 0 ? -found.offset()[0] : found.offset()[axis];
               }
            }
         }

         return 0.0F;
      } else {
         return 0.0F;
      }
   }

   @Override
   public float rootLocatorOffset(String locator, int axis) {
      if (this.wearerGeometry != null) {
         return this.wearerGeometry.rootLocatorOffset(locator, axis);
      } else if (!this.vanilla) {
         return this.locatorOffset(locator, axis);
      } else if (this.wearer == null && this.primary != null && axis >= 0 && axis <= 2) {
         for (int i = 0; i < this.primary.mesh.boneCount(); i++) {
            MeshedGeometry.Bone bone = this.primary.mesh.bone(i);
            List<MeshedGeometry.Locator> locators = bone.locators();

            for (int l = 0; l < locators.size(); l++) {
               MeshedGeometry.Locator found = locators.get(l);
               if (found.name().equalsIgnoreCase(locator)) {
                  float offset = found.offset()[axis] - bone.pivot()[axis];
                  return axis == 0 ? -offset : offset;
               }
            }
         }

         return 0.0F;
      } else {
         return 0.0F;
      }
   }

   private static final class AnimateSlot {
      final ClientEntityDef.AnimateEntry entry;
      @Nullable
      final Playable playable;
      boolean active;
      float lastWeight;
      boolean held;
      @Nullable
      int[] heldMask;

      AnimateSlot(ClientEntityDef.AnimateEntry entry, @Nullable Playable playable) {
         this.entry = entry;
         this.playable = playable;
      }
   }

   private static final class GeometryState {
      final MeshedGeometry mesh;
      final Skeleton skeleton;
      final int[] boneSlots;
      final String[] bindings;
      final Matrix4f[] bound;
      final Map<RenderControllerDef, int[][]> patterns = new IdentityHashMap<>();
      long posedFrame = -1L;

      GeometryState(MeshedGeometry mesh, int[] boneSlots) {
         this.mesh = mesh;
         this.skeleton = new Skeleton(mesh);
         this.boneSlots = boneSlots;
         this.bindings = new String[mesh.boneCount()];
         this.bound = new Matrix4f[mesh.boneCount()];
      }
   }
}
