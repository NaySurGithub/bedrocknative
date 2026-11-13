package net.bedrocknative.movement;

public final class StepHeights {
   private StepHeights() {
   }

   public static float raise(double minY, float height, float maxStep) {
      if (height <= 0.0F) {
         return height;
      }

      double target = minY + height;
      float top = (float)target;
      if (top < target) {
         top = Math.nextUp(top);
      }

      float step = (float)(top - minY);
      return step <= maxStep ? step : height;
   }
}
