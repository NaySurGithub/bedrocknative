package net.bedrocknative.model.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.BoneTimeline;
import net.bedrocknative.model.animation.ControllerDef;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.render.RenderControllerDef;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangResources;
import org.jspecify.annotations.Nullable;

public record OverlayPlan(ClientEntityDef definition, Set<String> controllers, boolean[] animateKept) {
   private static final Pattern GEOMETRY_REF = Pattern.compile("geometry\\.([a-z0-9_.:-]+)", 2);
   private static final Pattern WHITESPACE = Pattern.compile("\\s+");
   private static final Pattern SHORT_QUERY = Pattern.compile("(?<![a-z0-9_.])q\\.");
   private static final Pattern SHORT_VARIABLE = Pattern.compile("(?<![a-z0-9_.])v\\.");
   private static final Pattern SHORT_CONTEXT = Pattern.compile("(?<![a-z0-9_.])c\\.");
   private static final Pattern SHORT_TEMP = Pattern.compile("(?<![a-z0-9_.])t\\.");
   private static final int MAX_NESTING = 8;

   @Nullable
   public static OverlayPlan of(ModelRegistry registry, String identifier) {
      ClientEntityDef winning = registry.entity(identifier);
      ClientEntityDef base = registry.baseEntity(identifier);
      if (winning != null && base != null && winning.serverPack()) {
         List<ClientEntityDef.RenderControllerRef> added = added(base, winning);
         if (!added.isEmpty() && replacement(registry, base, winning, added) == null) {
            Set<String> names = new HashSet<>();
            Set<String> bones = new HashSet<>();

            for (ClientEntityDef.RenderControllerRef reference : added) {
               names.add(reference.name().toLowerCase(Locale.ROOT));
               RenderControllerDef controller = registry.renderController(reference.name());
               if (controller != null) {
                  drawn(registry, winning, controller, new HashSet<>(), bones);
               }
            }

            List<ClientEntityDef.AnimateEntry> animate = winning.scripts().animate();
            boolean[] kept = new boolean[animate.size()];

            for (int i = 0; i < kept.length; i++) {
               OverlayPlan.Reach reach = new OverlayPlan.Reach(registry, winning);
               reach.named(animate.get(i).name(), 0);
               kept[i] = reach.writes || reach.touches(bones);
            }

            return new OverlayPlan(winning, Set.copyOf(names), kept);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static OverlayPlan.Replacement replacement(ModelRegistry registry, String identifier) {
      ClientEntityDef winning = registry.entity(identifier);
      ClientEntityDef base = registry.baseEntity(identifier);
      return winning != null && base != null && winning.serverPack() ? replacement(registry, base, winning, added(base, winning)) : null;
   }

   private static List<ClientEntityDef.RenderControllerRef> added(ClientEntityDef base, ClientEntityDef winning) {
      List<ClientEntityDef.RenderControllerRef> added = new ArrayList<>();

      for (ClientEntityDef.RenderControllerRef reference : winning.renderControllers()) {
         if (find(base, reference.name()) == null) {
            added.add(reference);
         }
      }

      return added;
   }

   @Nullable
   private static OverlayPlan.Replacement replacement(
      ModelRegistry registry, ClientEntityDef base, ClientEntityDef winning, List<ClientEntityDef.RenderControllerRef> added
   ) {
      if (added.isEmpty()) {
         return null;
      } else {
         for (ClientEntityDef.RenderControllerRef reference : base.renderControllers()) {
            ClientEntityDef.RenderControllerRef kept = find(winning, reference.name());
            if (kept == null || !condition(kept.condition()).equals(condition(reference.condition()))) {
               RenderControllerDef replaced = registry.renderController(reference.name());
               if (replaced != null) {
                  Set<String> slots = new HashSet<>();
                  Set<String> bones = new HashSet<>();
                  drawn(registry, base, replaced, slots, bones);

                  for (ClientEntityDef.RenderControllerRef candidate : added) {
                     RenderControllerDef controller = registry.renderController(candidate.name());
                     if (controller != null) {
                        Set<String> candidateSlots = new HashSet<>();
                        Set<String> candidateBones = new HashSet<>();
                        drawn(registry, winning, controller, candidateSlots, candidateBones);
                        if (sameModel(slots, bones, candidateSlots, candidateBones)) {
                           return new OverlayPlan.Replacement(reference.name(), candidate.name());
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private static boolean sameModel(Set<String> slots, Set<String> bones, Set<String> otherSlots, Set<String> otherBones) {
      for (String slot : otherSlots) {
         if (slots.contains(slot)) {
            return true;
         }
      }

      int shared = 0;

      for (String bone : otherBones) {
         if (bones.contains(bone)) {
            shared++;
         }
      }

      return !bones.isEmpty() && shared * 2 >= bones.size();
   }

   @Nullable
   private static ClientEntityDef.RenderControllerRef find(ClientEntityDef definition, String name) {
      for (ClientEntityDef.RenderControllerRef reference : definition.renderControllers()) {
         if (reference.name().equalsIgnoreCase(name)) {
            return reference;
         }
      }

      return null;
   }

   private static String condition(@Nullable MolangProgram program) {
      if (program == null) {
         return "1";
      } else {
         String text = WHITESPACE.matcher(program.source()).replaceAll("").toLowerCase(Locale.ROOT);

         while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1);
         }

         text = SHORT_QUERY.matcher(text).replaceAll("query.");
         text = SHORT_VARIABLE.matcher(text).replaceAll("variable.");
         text = SHORT_CONTEXT.matcher(text).replaceAll("context.");
         text = SHORT_TEMP.matcher(text).replaceAll("temp.");
         return !text.isEmpty() && !text.equals("1.0") && !text.equals("true") ? text : "1";
      }
   }

   public boolean draws(String controller) {
      return this.controllers.contains(controller.toLowerCase(Locale.ROOT));
   }

   private static void drawn(ModelRegistry registry, ClientEntityDef definition, RenderControllerDef controller, Set<String> slots, Set<String> bones) {
      Set<String> shortNames = new HashSet<>();
      Matcher matcher = GEOMETRY_REF.matcher(controller.geometry().source());

      while (matcher.find()) {
         shortNames.add(matcher.group(1));
      }

      for (Object[] array : controller.arrays().values()) {
         for (Object element : array) {
            if (element instanceof MolangResources.Ref ref && ref.kind().equals("geometry")) {
               shortNames.add(ref.name());
            }
         }
      }

      for (String shortName : shortNames) {
         slots.add(shortName.toLowerCase(Locale.ROOT));
         String identifier = definition.geometry(shortName);
         MeshedGeometry mesh = identifier != null ? registry.mesh(identifier, definition) : null;

         for (int bone = 0; mesh != null && bone < mesh.boneCount(); bone++) {
            bones.add(mesh.bone(bone).name().toLowerCase(Locale.ROOT));
         }
      }
   }

   private static final class Reach {
      private final ModelRegistry registry;
      private final ClientEntityDef definition;
      private final Set<String> visited = new HashSet<>();
      private final Set<String> moved = new HashSet<>();
      private boolean writes;

      Reach(ModelRegistry registry, ClientEntityDef definition) {
         this.registry = registry;
         this.definition = definition;
      }

      boolean touches(Set<String> bones) {
         for (String bone : this.moved) {
            if (bones.contains(bone)) {
               return true;
            }
         }

         return false;
      }

      void named(String name, int depth) {
         String id = this.definition.animation(name);
         this.playable(id != null ? id : name, depth);
      }

      private void playable(String id, int depth) {
         if (depth <= 8 && this.visited.add(id)) {
            ControllerDef controller = this.registry.controller(id);
            if (controller != null) {
               for (ControllerDef.State state : controller.states().values()) {
                  this.writes = this.writes | !state.variables().isEmpty();
                  this.writes = this.writes | state.onEntry().stream().anyMatch(program -> !program.variableWrites().isEmpty());
                  this.writes = this.writes | state.onExit().stream().anyMatch(program -> !program.variableWrites().isEmpty());

                  for (ControllerDef.StateAnimation animation : state.animations()) {
                     this.named(animation.name(), depth + 1);
                  }
               }
            } else {
               AnimationDef animation = this.registry.animation(id);
               if (animation != null) {
                  for (BoneTimeline bone : animation.bones()) {
                     this.moved.add(bone.key());
                  }

                  this.writes = this.writes
                     | animation.timeline().stream().anyMatch(script -> script.programs().stream().anyMatch(program -> !program.variableWrites().isEmpty()));
               }
            }
         }
      }
   }

   public record Replacement(String base, String server) {
   }
}
