package net.bedrocknative.client.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.entity.BodyRotation;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.OverlayPlan;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.render.DyeColors;
import net.bedrocknative.model.runtime.ActorBounds;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.runtime.DrawList;
import net.bedrocknative.model.runtime.RuntimeEquipment;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.VariableStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ActorState {
   private static final int ATTACK_TIME = MolangNames.id("attack_time");
   private static final int IS_FIRST_PERSON = MolangNames.id("is_first_person");
   private static final int IS_PAPERDOLL = MolangNames.id("is_paperdoll");
   private static final int MAP_FACE_ICON = MolangNames.id("map_face_icon");
   private static final int GLIDING_SPEED_VALUE = MolangNames.id("gliding_speed_value");
   private static final int SWIM_AMOUNT = MolangNames.id("swim_amount");
   private static final int IS_HOLDING_RIGHT = MolangNames.id("is_holding_right");
   private static final int IS_HOLDING_LEFT = MolangNames.id("is_holding_left");
   private static final int PLAYER_X_ROTATION = MolangNames.id("player_x_rotation");
   private static final int HAS_TARGET = MolangNames.id("has_target");
   private static final long SAME_FRAME_NANOS = 100000000L;
   @Nullable
   private static final String TRACE = System.getProperty("bedrocknative.traceActor");
   private static final long TRACE_NANOS = 2000000000L;
   private static final Set<String> TRACED_SCRIPTS = ConcurrentHashMap.newKeySet();
   private static final int LEATHER = -6265536;
   private static final String[] EQUIPMENT_SLOTS = new String[]{
      "slot.weapon.mainhand", "slot.weapon.offhand", "slot.armor.head", "slot.armor.chest", "slot.armor.legs", "slot.armor.feet", "slot.armor.body"
   };
   private static final EquipmentSlot[] JAVA_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY
   };
   private final BedrockActors actors;
   private final int javaId;
   private final long runtimeId;
   private final ActorData data;
   private final boolean carrier;
   private final ClientActorView view;
   private final ActorSnapshot[] snapshots = new ActorSnapshot[]{new ActorSnapshot(), new ActorSnapshot()};
   private final long created = System.nanoTime();
   @Nullable
   private final MobEngineState mob;
   @Nullable
   private final PlayerRuntime player;
   @Nullable
   private ActorInstance instance;
   @Nullable
   private ActorEffects effects;
   @Nullable
   private ActorEffects playerEffects;
   @Nullable
   private ActorEffects overlayEffects;
   @Nullable
   private RuntimeEquipment equipment;
   @Nullable
   private ModelRegistry mobRegistry;
   @Nullable
   private ModelRegistry bindAttempt;
   @Nullable
   private float[] bounds;
   private final double[] worldBounds = new double[6];
   @Nullable
   private ActorSnapshot last;
   private int next;
   private long lastFrame = -1L;
   private long lastNanos;
   private long lastTrace;
   private long evaluations;
   private boolean failed;
   private float lastBodyYaw;
   private long uniqueId;
   @Nullable
   private BodyRotation.Tracker bodyTracker;
   private boolean facingLogged;
   @Nullable
   private ActorInstance overlay;
   @Nullable
   private ModelRegistry overlayRegistry;
   private final ActorSnapshot[] overlaySnapshots = new ActorSnapshot[]{new ActorSnapshot(), new ActorSnapshot()};
   @Nullable
   private ActorSnapshot overlayLast;
   private int overlayNext;
   private long overlayFrame = -1L;
   private long overlayNanos;
   private boolean overlayFailed;
   private final boolean[] handsAttached = new boolean[2];

   private static boolean traced(String identifier) {
      for (String part : TRACE.split(",")) {
         if (!part.isBlank() && identifier.contains(part.trim())) {
            return true;
         }
      }

      return false;
   }

   ActorState(BedrockActors actors, int javaId, long runtimeId, ActorData data, boolean carrier, boolean vanillaMob) {
      this(actors, javaId, runtimeId, data, carrier, vanillaMob, false);
   }

   ActorState(BedrockActors actors, int javaId, long runtimeId, ActorData data, boolean carrier, boolean vanillaMob, boolean player) {
      this.actors = actors;
      this.javaId = javaId;
      this.runtimeId = runtimeId;
      this.data = data;
      this.carrier = carrier;
      this.mob = vanillaMob ? new MobEngineState(data.identifier()) : null;
      this.player = player ? new PlayerRuntime(this) : null;
      this.view = new ClientActorView(actors, this);
   }

   long uniqueId() {
      return this.uniqueId;
   }

   BodyRotation.Tracker bodyTracker() {
      if (this.bodyTracker == null) {
         this.bodyTracker = new BodyRotation.Tracker();
      }

      return this.bodyTracker;
   }

   @Nullable
   BodyRotation.Tracker existingBodyTracker() {
      return this.bodyTracker;
   }

   void uniqueId(long id) {
      this.uniqueId = id;
   }

   @Nullable
   PlayerRuntime player() {
      return this.player;
   }

   public boolean isPlayer() {
      return this.player != null;
   }

   float lifeTime() {
      return (float)(System.nanoTime() - this.created) / 1.0E9F;
   }

   MolangEnv env() {
      return this.actors.env();
   }

   public int javaId() {
      return this.javaId;
   }

   public long runtimeId() {
      return this.runtimeId;
   }

   public ActorData data() {
      return this.data;
   }

   public boolean carrier() {
      return this.carrier;
   }

   public boolean vanillaMob() {
      return this.mob != null;
   }

   @Nullable
   public ActorInstance instance() {
      return this.instance;
   }

   @Nullable
   public RuntimeEquipment equipment() {
      return this.equipment;
   }

   public long evaluations() {
      return this.evaluations;
   }

   public boolean failed() {
      return this.failed || this.player != null && this.player.failed();
   }

   void bind(@Nullable ModelRegistry registry, @Nullable ClientEntityDef definition) {
      ActorInstance previous = this.instance;
      this.instance = null;
      this.closeEffects();
      this.equipment = null;
      this.mobRegistry = null;
      this.bounds = null;
      this.last = null;
      this.lastFrame = -1L;
      this.failed = false;
      if (registry != null && definition != null) {
         try {
            ActorInstance created = new ActorInstance(registry, definition, this.mob != null);
            if (previous != null) {
               created.adoptVariables(previous);
            }

            this.instance = created;
            Skeleton primary = created.primarySkeleton();
            this.bounds = primary != null ? ActorBounds.local(primary.geometry()) : null;
            if (this.mob != null) {
               this.mobRegistry = registry;
               this.equipment = new RuntimeEquipment(registry, created);
            }
         } catch (RuntimeException var6) {
            BedrockNative.LOGGER.warn("Couldn't set up the Bedrock model of {}", definition.identifier(), var6);
         }
      }
   }

   void unbindMob() {
      if (this.mob != null) {
         this.bind(null, null);
         this.bindAttempt = null;
      }

      if (this.player != null) {
         this.player.unbind();
         this.closePlayerEffects();
      }
   }

   void release() {
      this.view.detach();
      if (this.player != null) {
         this.player.release();
      }

      this.closeEffects();
      this.closePlayerEffects();
      this.closeOverlayEffects();
   }

   private void closeEffects() {
      ActorEffects running = this.effects;
      this.effects = null;
      if (running != null) {
         running.close();
      }
   }

   private void closePlayerEffects() {
      ActorEffects running = this.playerEffects;
      this.playerEffects = null;
      if (running != null) {
         running.close();
      }
   }

   private void closeOverlayEffects() {
      ActorEffects running = this.overlayEffects;
      this.overlayEffects = null;
      if (running != null) {
         running.close();
      }
   }

   private static ActorEffects effectsFor(@Nullable ActorEffects current, ClientEntityDef definition, Entity entity) {
      if (current != null && current.definition() == definition && current.entity() == entity) {
         return current;
      } else {
         if (current != null) {
            current.close();
         }

         return new ActorEffects(definition, entity);
      }
   }

   ActorEffects playerEffects(ClientEntityDef definition, Entity entity) {
      this.playerEffects = effectsFor(this.playerEffects, definition, entity);
      return this.playerEffects;
   }

   void animate(String animation, String nextState, String stopExpression, int stopExpressionVersion, String controller, float blendOutTime) {
      if (this.player == null) {
         ActorInstance running = this.instance;
         if (running != null && !running.animate(animation, nextState, stopExpression, stopExpressionVersion, controller, blendOutTime)) {
            BedrockNative.LOGGER.debug("AnimateEntity: no animation {} for {}", animation, this.data.identifier());
         }
      } else {
         LocalPlayer local = Minecraft.getInstance().player;
         if (local == null || local.getId() != this.javaId) {
            this.player.animate(animation, nextState, stopExpression, stopExpressionVersion, controller, blendOutTime);
         }
      }
   }

   void onEvent(EntityEventType type, int eventData, long gameTime) {
      if (this.mob != null) {
         this.mob.onEvent(type, eventData, gameTime);
      }
   }

   @Nullable
   ActorSnapshot evaluateMob(ModelRegistry registry, ClientEntityDef definition, Entity entity, EntityRenderState render, float partialTick) {
      if (this.mob == null) {
         return null;
      } else {
         if (this.instance == null || this.mobRegistry != registry) {
            if (this.bindAttempt == registry) {
               return null;
            }

            this.bindAttempt = registry;
            this.bind(registry, definition);
         }

         return this.instance != null ? this.evaluate(entity, render, partialTick) : null;
      }
   }

   @Nullable
   ActorSnapshot evaluate(Entity entity, EntityRenderState render, float partialTick) {
      ActorInstance running = this.instance;
      if (running != null && !this.failed) {
         long frame = BedrockActors.frame();
         long now = System.nanoTime();
         if (frame == this.lastFrame && this.last != null && now - this.lastNanos < 100000000L) {
            return this.last;
         } else {
            float deltaTime = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1.0E9F;
            this.lastNanos = now;
            this.lastFrame = frame;

            try {
               this.view.prepare(entity, render, partialTick, (float)(now - this.created) / 1.0E9F, this.mob);
               this.engineVariables(running.variables(), entity, render, partialTick);
               boolean[] attached = null;
               if (this.mob != null) {
                  this.mob.catchUp(entity.level().getGameTime(), this.data, entity);
                  this.mob.setVariables(running.variables(), this.data, entity, partialTick);
                  running.setChangeColor(DyeColors.argb((int)this.data.number(EntityDataTypes.COLOR)));
                  running.setSecondColor(this.data.value(EntityDataTypes.COLOR_2) != null ? DyeColors.argb((int)this.data.number(EntityDataTypes.COLOR_2)) : -1);
                  attached = entity instanceof LivingEntity living ? this.equipment(living) : null;
               }

               if (this.effects == null) {
                  this.effects = new ActorEffects(running.definition(), entity);
               }

               MolangEnv env = this.actors.env();
               env.effects = this.effects;

               DrawList list;
               try {
                  list = running.frame(env, this.view, deltaTime, partialTick);
               } finally {
                  env.effects = null;
               }

               ActorSnapshot snapshot = this.snapshots[this.next];
               this.next ^= 1;
               snapshot.identifier(this.data.identifier());
               snapshot.fill(list, this.data.scale(), BedrockMobs.composing());
               snapshot.draw().setUvAnimation(BedrockMobs.composing());
               if (this.mob != null) {
                  snapshot.hands(running.drawnSkeleton(), attached);
               }

               snapshot.bodyYaw(this.view.bodyYaw());
               snapshot.deathProgress(Math.min(1.0F, this.view.deathTicks() / 200.0F));
               if (TRACE != null && traced(this.data.identifier()) && now - this.lastTrace >= 2000000000L) {
                  this.lastTrace = now;
                  if (TRACED_SCRIPTS.add(this.data.identifier())) {
                     BedrockNative.LOGGER.info("Actor scripts {}", running.traceScripts());
                  }

                  BedrockNative.LOGGER
                     .info(
                        "Actor trace {} at {} {} {}, {} blocks from the camera: {}",
                        new Object[]{
                           this.javaId,
                           Math.round(entity.getX()),
                           Math.round(entity.getY()),
                           Math.round(entity.getZ()),
                           Math.round(Math.sqrt(render.distanceToCameraSq)),
                           running.trace()
                        }
                     );
               }

               this.last = snapshot;
               this.lastBodyYaw = this.view.bodyYaw();
               this.evaluations++;
               if (!this.facingLogged && now - this.created >= 1000000000L) {
                  this.facingLogged = true;
                  if (this.carrier || running.definition().serverPack()) {
                     this.actors
                        .facing()
                        .frame(
                           this,
                           entity,
                           (float)(now - this.created) / 1.0E9F,
                           this.view.bodyYaw(),
                           this.view.headYRotation(0),
                           BedrockModels.registry(),
                           snapshot.draw()
                        );
                  }
               }

               return snapshot;
            } catch (RuntimeException var17) {
               this.failed = true;
               this.last = null;
               BedrockNative.LOGGER.error("The Bedrock model of {} failed and is no longer drawn", this.data.identifier(), var17);
               return null;
            }
         }
      } else {
         return null;
      }
   }

   @Nullable
   ActorSnapshot evaluateOverlay(ModelRegistry registry, OverlayPlan plan, Entity entity, EntityRenderState render, float partialTick) {
      if (this.overlayRegistry != registry) {
         this.overlayRegistry = registry;
         this.overlayFailed = false;
         this.overlayLast = null;
         this.overlayNanos = 0L;
         ActorInstance previous = this.overlay;
         this.overlay = null;
         this.closeOverlayEffects();

         try {
            ActorInstance created = new ActorInstance(registry, plan.definition());
            created.restrict(plan);
            if (previous != null) {
               created.adoptVariables(previous);
            }

            this.overlay = created;
         } catch (RuntimeException var20) {
            this.overlayFailed = true;
            BedrockNative.LOGGER.warn("Couldn't set up what the server's packs add to {}", this.data.identifier(), var20);
         }
      }

      ActorInstance running = this.overlay;
      if (running != null && !this.overlayFailed) {
         long frame = BedrockActors.frame();
         long now = System.nanoTime();
         if (frame == this.overlayFrame && this.overlayLast != null && now - this.overlayNanos < 100000000L) {
            return this.overlayLast;
         } else {
            float deltaTime = this.overlayNanos == 0L ? 0.0F : (float)(now - this.overlayNanos) / 1.0E9F;
            this.overlayNanos = now;
            this.overlayFrame = frame;

            try {
               this.view.prepare(entity, render, partialTick, (float)(now - this.created) / 1.0E9F, this.mob);
               this.engineVariables(running.variables(), entity, render, partialTick);
               if (this.mob != null) {
                  this.mob.catchUp(entity.level().getGameTime(), this.data, entity);
                  this.mob.setVariables(running.variables(), this.data, entity, partialTick);
                  running.setChangeColor(DyeColors.argb((int)this.data.number(EntityDataTypes.COLOR)));
               }

               MolangEnv env = this.actors.env();
               this.overlayEffects = effectsFor(this.overlayEffects, running.definition(), entity);
               env.effects = this.overlayEffects;

               DrawList list;
               try {
                  list = running.frame(env, this.view, deltaTime, partialTick);
               } finally {
                  env.effects = null;
               }

               ActorSnapshot snapshot = this.overlaySnapshots[this.overlayNext];
               this.overlayNext ^= 1;
               snapshot.identifier(this.data.identifier());
               snapshot.fill(list, this.data.scale(), BedrockMobs.composing());
               snapshot.draw().setUvAnimation(BedrockMobs.composing());
               snapshot.bodyYaw(this.view.bodyYaw());
               this.overlayLast = snapshot;
               return snapshot;
            } catch (RuntimeException var19) {
               this.overlayFailed = true;
               this.overlayLast = null;
               BedrockNative.LOGGER.error("What the server's packs add to {} failed and is no longer drawn", this.data.identifier(), var19);
               return null;
            }
         }
      } else {
         return null;
      }
   }

   @Nullable
   public ActorInstance overlay() {
      return this.overlay;
   }

   private boolean[] equipment(LivingEntity entity) {
      RuntimeEquipment running = this.equipment;
      this.handsAttached[0] = false;
      this.handsAttached[1] = false;
      if (running == null) {
         return this.handsAttached;
      } else {
         for (int slot = 0; slot < EQUIPMENT_SLOTS.length; slot++) {
            ItemStack stack = entity.getItemBySlot(JAVA_SLOTS[slot]);
            DyedItemColor dyed = (DyedItemColor)stack.get(DataComponents.DYED_COLOR);

            boolean drawn;
            try {
               drawn = running.update(
                  slot, this.data.item(EQUIPMENT_SLOTS[slot]), stack.hasFoil(), dyed != null ? 0xFF000000 | dyed.rgb() : -6265536, this.view, this.actors.env()
               );
            } catch (RuntimeException var8) {
               BedrockNative.LOGGER
                  .warn("Couldn't set up the attachable of {} on {}", new Object[]{this.data.item(EQUIPMENT_SLOTS[slot]), this.data.identifier(), var8});
               drawn = false;
            }

            if (slot <= 1) {
               this.handsAttached[slot] = drawn;
            }
         }

         return this.handsAttached;
      }
   }

   private void engineVariables(VariableStore variables, Entity entity, EntityRenderState render, float partialTick) {
      LivingEntity living = entity instanceof LivingEntity livingEntity ? livingEntity : null;
      variables.set(ATTACK_TIME, living != null ? living.getSwingAnimation(partialTick) : 0.0F, null);
      variables.set(IS_FIRST_PERSON, 0.0F, null);
      variables.set(IS_PAPERDOLL, 0.0F, null);
      variables.set(MAP_FACE_ICON, 0.0F, null);
      variables.set(GLIDING_SPEED_VALUE, 1.0F, null);
      variables.set(SWIM_AMOUNT, living != null ? living.getSwimAmount(partialTick) : 0.0F, null);
      variables.set(IS_HOLDING_RIGHT, this.data.item("slot.weapon.mainhand").isEmpty() ? 0.0F : 1.0F, null);
      variables.set(IS_HOLDING_LEFT, this.data.item("slot.weapon.offhand").isEmpty() ? 0.0F : 1.0F, null);
      variables.set(PLAYER_X_ROTATION, render instanceof LivingEntityRenderState livingRender ? livingRender.xRot : entity.getXRot(partialTick), null);
      variables.set(HAS_TARGET, this.view.hasTarget() ? 1.0F : 0.0F, null);
   }

   @Nullable
   float[] bounds() {
      return this.bounds;
   }

   double[] worldBounds() {
      return this.worldBounds;
   }

   @Nullable
   public ActorSnapshot lastSnapshot() {
      return this.last;
   }

   @Nullable
   public Vector3f locatorOffset(String locator) {
      ActorInstance running = this.instance;
      Skeleton skeleton = running != null ? running.primarySkeleton() : null;
      ActorSnapshot snapshot = this.last;
      Vector3f point = skeleton != null ? skeleton.locator(locator) : null;
      if (point != null && snapshot != null) {
         float scale = snapshot.actorScale();
         point.mul(snapshot.draw().scale(0) * scale, snapshot.draw().scale(1) * scale, snapshot.draw().scale(2) * scale);
         return Skeleton.toWorld(point, this.lastBodyYaw, 1.0F);
      } else {
         return null;
      }
   }

   public float lastBodyYaw() {
      return this.lastBodyYaw;
   }

   float actorScale() {
      return this.data.scale();
   }

   public ClientActorView view() {
      return this.view;
   }

   @Override
   public String toString() {
      return this.data.identifier()
         + "#"
         + this.javaId
         + (this.instance != null ? " (drawn)" : (this.carrier ? " (no definition)" : ""))
         + (this.data.value(EntityDataTypes.MARK_VARIANT) != null ? " mark_variant " + this.data.number(EntityDataTypes.MARK_VARIANT) : "");
   }
}
