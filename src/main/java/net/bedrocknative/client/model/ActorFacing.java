package net.bedrocknative.client.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.DefinitionNeeds;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.minecraft.world.entity.Entity;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

final class ActorFacing {
   static final int MAX = 128;
   private static final List<String> ROTATION_QUERIES = List.of(
      "body_y_rotation",
      "body_x_rotation",
      "head_y_rotation",
      "head_x_rotation",
      "target_y_rotation",
      "target_x_rotation",
      "rotation_to_camera",
      "camera_rotation",
      "yaw_speed"
   );
   private static final float DRAWN_AFTER_SECONDS = 1.0F;
   private final Set<String> spawned = new HashSet<>();
   private final Set<String> drawn = new HashSet<>();
   private static final int LOOKS = 6;

   void spawn(String identifier, float pitch, float yaw, float headYaw, float bodyYaw, ActorData data) {
      if (this.spawned.size() < 128 && this.spawned.add(identifier)) {
         BedrockNative.LOGGER
            .info(
               "Actor facing {}: added with yaw {}, head yaw {}, body yaw {}, pitch {}{}",
               new Object[]{identifier, round(yaw), round(headYaw), round(bodyYaw), round(pitch), flags(data)}
            );
      }
   }

   void frame(ActorState state, Entity entity, float lifeSeconds, float bodyYaw, float headYaw, @Nullable ModelRegistry registry, @Nullable DrawSnapshot draw) {
      String identifier = state.data().identifier();
      if (!(lifeSeconds < 1.0F) && this.drawn.size() < 128 && this.drawn.add(identifier)) {
         BedrockNative.LOGGER
            .info(
               "Actor facing {}: drawn with body yaw {}, head {} from the body, the actor's yaw {}{}{}{}",
               new Object[]{identifier, round(bodyYaw), round(headYaw), round(entity.getYRot()), flags(state.data()), reads(identifier, registry), looks(draw)}
            );
      }
   }

   static String looks(@Nullable DrawSnapshot draw) {
      if (draw != null && draw.calls() != 0) {
         Set<String> geometries = new LinkedHashSet<>();
         Set<String> textures = new LinkedHashSet<>();

         for (int c = 0; c < draw.calls(); c++) {
            DrawSnapshot.Call call = draw.call(c);
            if (geometries.size() < 6) {
               geometries.add(call.mesh().identifier());
            }

            for (int layer = 0; layer < call.layers() && textures.size() < 6; layer++) {
               String path = call.layer(layer);
               textures.add(path != null ? path : "-");
            }
         }

         return ", geometry " + geometries + ", textures " + textures;
      } else {
         return draw == null ? "" : ", nothing drawn";
      }
   }

   static String flags(ActorData data) {
      List<String> set = new ArrayList<>();

      for (EntityFlag flag : new EntityFlag[]{
         EntityFlag.BODY_ROTATION_BLOCKED,
         EntityFlag.BODY_ROTATION_AXIS_ALIGNED,
         EntityFlag.BODY_ROTATION_ALWAYS_FOLLOWS_HEAD,
         EntityFlag.BODY_ROTATION_LOCKED_TO_VEHICLE
      }) {
         if (data.flag(flag)) {
            set.add(flag.name().toLowerCase(Locale.ROOT));
         }
      }

      return set.isEmpty() ? "" : ", flags " + set;
   }

   private static String reads(String identifier, @Nullable ModelRegistry registry) {
      ClientEntityDef definition = registry != null ? registry.entity(identifier) : null;
      if (definition == null) {
         return registry != null ? ", no client entity" : "";
      }

      try {
         DefinitionNeeds.Needs needs = DefinitionNeeds.of(registry, definition);
         Set<String> queries = needs.queries();
         List<String> rotation = new ArrayList<>();

         for (String query : ROTATION_QUERIES) {
            if (queries.contains(query)) {
               List<String> calls = needs.cameraCalls().stream().filter(call -> call.startsWith(query + "(")).limit(6L).toList();
               rotation.addAll(calls.isEmpty() ? List.of(query) : calls);
            }
         }

         return rotation.isEmpty() ? ", reads no rotation query" : ", reads " + rotation;
      } catch (RuntimeException e) {
         return ", queries unreadable (" + e + ")";
      }
   }

   private static String round(float degrees) {
      return Float.isFinite(degrees) ? String.format(Locale.ROOT, "%.1f", degrees) : String.valueOf(degrees);
   }
}
