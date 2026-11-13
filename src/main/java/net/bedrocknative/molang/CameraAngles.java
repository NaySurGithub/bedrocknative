package net.bedrocknative.molang;

public final class CameraAngles {
   private CameraAngles() {
   }

   public static float cameraRotation(float pitch, float yaw, int axis) {
      return axis == 0 ? pitch : turn(yaw);
   }

   public static float rotationToCamera(double dx, double dy, double dz, int axis) {
      return axis == 0 ? (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))) : turn((float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
   }

   public static float turn(float degrees) {
      float turned = degrees % 360.0F;
      if (turned < 0.0F) {
         turned += 360.0F;
      }

      return turned >= 360.0F ? 0.0F : turned;
   }
}
