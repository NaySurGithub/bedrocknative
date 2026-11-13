package net.bedrocknative.model.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.BoneTimeline;
import net.bedrocknative.model.animation.ControllerDef;
import net.bedrocknative.model.animation.Keyframe;
import net.bedrocknative.model.animation.StateVariable;
import net.bedrocknative.model.animation.Vec3Value;
import net.bedrocknative.model.render.RenderControllerDef;
import net.bedrocknative.molang.CameraCalls;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public final class DefinitionNeeds {
   private final ModelRegistry registry;
   private final ClientEntityDef definition;
   private final Set<String> queries = new TreeSet<>();
   private final Set<String> cameraCalls = new TreeSet<>();
   private final Set<String> reads = new HashSet<>();
   private final Set<String> writes = new HashSet<>();
   private final Set<String> visited = new HashSet<>();
   private final List<String> missingRenderControllers = new ArrayList<>();
   private final List<String> missingAnimations = new ArrayList<>();

   private DefinitionNeeds(ModelRegistry registry, ClientEntityDef definition) {
      this.registry = registry;
      this.definition = definition;
   }

   public static DefinitionNeeds.Needs of(ModelRegistry registry, ClientEntityDef definition) {
      DefinitionNeeds walk = new DefinitionNeeds(registry, definition);
      walk.walk();
      Set<String> engine = new TreeSet<>(walk.reads);
      engine.removeAll(walk.writes);
      boolean anyGeometry = definition.geometries().values().stream().anyMatch(id -> registry.geometry(id) != null);
      return new DefinitionNeeds.Needs(
         walk.queries, engine, List.copyOf(walk.missingRenderControllers), List.copyOf(walk.missingAnimations), anyGeometry, walk.cameraCalls
      );
   }

   private void walk() {
      ClientEntityDef.Scripts scripts = this.definition.scripts();
      this.program(scripts.initialize());
      this.program(scripts.preAnimation());
      this.program(scripts.scale());
      this.program(scripts.scaleX());
      this.program(scripts.scaleY());
      this.program(scripts.scaleZ());
      this.program(scripts.shouldUpdateBonesAndEffectsOffscreen());
      this.program(scripts.shouldUpdateEffectsOffscreen());
      this.program(scripts.hideHeldItems());

      for (ClientEntityDef.AnimateEntry entry : scripts.animate()) {
         this.program(entry.weight());
         this.named(entry.name());
      }

      if (this.definition.playsLegacyControllers()) {
         this.definition.legacyControllers().values().forEach(this::playable);
      }

      for (ClientEntityDef.RenderControllerRef reference : this.definition.renderControllers()) {
         this.program(reference.condition());
         RenderControllerDef controller = this.registry.renderController(reference.name());
         if (controller == null) {
            this.missingRenderControllers.add(reference.name());
         } else {
            this.render(controller);
         }
      }
   }

   private void named(String name) {
      String id = this.definition.animation(name);
      this.playable(id != null ? id : name);
   }

   private void playable(String id) {
      if (this.visited.add(id)) {
         ControllerDef controller = this.registry.controller(id);
         if (controller != null) {
            for (ControllerDef.State state : controller.states().values()) {
               state.transitions().forEach(transition -> this.program(transition.condition()));
               state.onEntry().forEach(this::program);
               state.onExit().forEach(this::program);

               for (StateVariable variable : state.variables()) {
                  this.program(variable.input());
                  this.writes.add(variable.name().toLowerCase(Locale.ROOT));
               }

               for (ControllerDef.StateAnimation animation : state.animations()) {
                  this.program(animation.weight());
                  this.named(animation.name());
               }
            }
         } else {
            AnimationDef animation = this.registry.animation(id);
            if (animation == null) {
               this.missingAnimations.add(id);
            } else {
               this.program(animation.animTimeUpdate());
               this.program(animation.blendWeight());
               this.program(animation.startDelay());
               this.program(animation.loopDelay());

               for (BoneTimeline bone : animation.bones()) {
                  this.keys(bone.rotation());
                  this.keys(bone.position());
                  this.keys(bone.scale());
               }

               animation.timeline().forEach(script -> script.programs().forEach(this::program));
            }
         }
      }
   }

   private void keys(Keyframe @Nullable [] keys) {
      if (keys != null) {
         for (Keyframe key : keys) {
            this.value(key.pre());
            this.value(key.post());
         }
      }
   }

   private void value(Vec3Value value) {
      for (int axis = 0; axis < 3; axis++) {
         this.program(value.program(axis));
      }
   }

   private void render(RenderControllerDef controller) {
      this.program(controller.geometry());
      controller.materials().forEach(pattern -> this.program(pattern.value()));
      controller.textures().forEach(this::program);
      controller.partVisibility().forEach(pattern -> this.program(pattern.value()));
      this.color(controller.color());
      this.color(controller.overlayColor());
      this.color(controller.isHurtColor());
      this.color(controller.onFireColor());
      RenderControllerDef.UvAnimation uv = controller.uvAnimation();
      if (uv != null) {
         this.program(uv.offsetU());
         this.program(uv.offsetV());
         this.program(uv.scaleU());
         this.program(uv.scaleV());
      }

      this.program(controller.ignoreLighting());
      this.program(controller.lightColorMultiplier());
   }

   private void color(RenderControllerDef.@Nullable Color color) {
      if (color != null) {
         this.program(color.r());
         this.program(color.g());
         this.program(color.b());
         this.program(color.a());
      }
   }

   private void program(@Nullable MolangProgram program) {
      if (program != null) {
         this.queries.addAll(program.queries());
         this.cameraCalls.addAll(CameraCalls.in(program.source()));
         this.reads.addAll(program.variableReads());
         this.writes.addAll(program.variableWrites());
      }
   }

   public record Needs(
      Set<String> queries,
      Set<String> engineVariables,
      List<String> missingRenderControllers,
      List<String> missingAnimations,
      boolean anyGeometry,
      Set<String> cameraCalls
   ) {
   }
}
