package net.bedrocknative.client.model;

import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.entity.BodyRotation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

public final class BedrockBodyRotation {
   private BedrockBodyRotation() {
   }

   public static boolean apply(LivingEntity entity) {
      ActorState state = BedrockActors.state(entity);
      if (state == null) {
         return false;
      } else {
         ActorData data = state.data();
         Entity vehicle = entity.getVehicle();
         BodyRotation.Rule rule = BodyRotation.rule(
            data.flag(EntityFlag.BODY_ROTATION_BLOCKED),
            data.flag(EntityFlag.BODY_ROTATION_AXIS_ALIGNED),
            data.flag(EntityFlag.BODY_ROTATION_ALWAYS_FOLLOWS_HEAD),
            data.flag(EntityFlag.BODY_ROTATION_LOCKED_TO_VEHICLE),
            vehicle != null
         );
         BodyRotation.Tracker tracker = rule == BodyRotation.Rule.FREE ? state.existingBodyTracker() : state.bodyTracker();
         if (tracker == null) {
            return false;
         } else {
            float next = tracker.next(rule, entity.yBodyRot, entity.getYRot(), entity.getYHeadRot(), vehicleYaw(vehicle));
            if (Float.isNaN(next)) {
               return false;
            } else {
               entity.yBodyRot = next;
               return true;
            }
         }
      }
   }

   private static float vehicleYaw(@Nullable Entity vehicle) {
      if (vehicle instanceof LivingEntity living) {
         return living.yBodyRot;
      } else {
         return vehicle != null ? vehicle.getYRot() : Float.NaN;
      }
   }
}
