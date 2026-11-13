package net.bedrocknative.model.entity;

public final class BodyRotation {
   private BodyRotation() {
   }

   public static BodyRotation.Rule rule(boolean blocked, boolean axisAligned, boolean followsHead, boolean lockedToVehicle, boolean riding) {
      if (blocked) {
         return axisAligned ? BodyRotation.Rule.LOCKED_TO_AXIS : BodyRotation.Rule.BLOCKED;
      } else if (followsHead) {
         return BodyRotation.Rule.FOLLOWS_HEAD;
      } else if (lockedToVehicle && riding) {
         return BodyRotation.Rule.LOCKED_TO_VEHICLE;
      } else {
         return axisAligned ? BodyRotation.Rule.AXIS_ALIGNED : BodyRotation.Rule.FREE;
      }
   }

   public static float cardinal(float yaw) {
      return Math.round(yaw / 90.0F) * 90.0F;
   }

   public enum Rule {
      FREE,
      BLOCKED,
      LOCKED_TO_AXIS,
      AXIS_ALIGNED,
      FOLLOWS_HEAD,
      LOCKED_TO_VEHICLE;
   }

   public static final class Tracker {
      private boolean snapped;

      public float next(BodyRotation.Rule rule, float body, float yaw, float headYaw, float vehicleYaw) {
         if (rule != BodyRotation.Rule.LOCKED_TO_AXIS) {
            this.snapped = false;
         }
         return switch (rule) {
            case FREE -> Float.NaN;
            case BLOCKED -> body;
            case LOCKED_TO_AXIS -> {
               if (this.snapped) {
                  yield body;
               } else {
                  this.snapped = true;
                  yield BodyRotation.cardinal(yaw);
               }
            }
            case AXIS_ALIGNED -> BodyRotation.cardinal(yaw);
            case FOLLOWS_HEAD -> headYaw;
            case LOCKED_TO_VEHICLE -> vehicleYaw;
         };
      }
   }
}
