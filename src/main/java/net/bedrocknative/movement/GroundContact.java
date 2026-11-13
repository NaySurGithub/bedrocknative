package net.bedrocknative.movement;

public final class GroundContact {
   public static final float EYE_HEIGHT = 1.62001F;
   private static final double NO_VERTICAL_MOVE = 1.0E-5;

   private GroundContact() {
   }

   public static float eyeY(double feetY) {
      return (float)(feetY + 1.62001F);
   }

   public static float feetY(float eyeY) {
      return eyeY - 1.62001F;
   }

   public static boolean keepsGround(boolean flying, boolean wasOnGround, boolean verticalCollision, double moveY) {
      return !flying && wasOnGround && !verticalCollision && Math.abs(moveY) <= 1.0E-5;
   }
}
