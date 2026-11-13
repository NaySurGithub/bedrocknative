package net.bedrocknative.movement;

import org.jspecify.annotations.Nullable;

public final class Depenetration {
   public static final double TOLERANCE = 0.001;
   private static final double TOUCHING = 1.0E-7;

   private Depenetration() {
   }

   public static Depenetration.@Nullable Push push(double[] movingMin, double[] movingMax, double[] blockMin, double[] blockMax) {
      if (blockMin[0] == blockMax[0] && blockMin[1] == blockMax[1] && blockMin[2] == blockMax[2]) {
         return null;
      }

      int best = -1;
      double bestDepth = Double.MAX_VALUE;
      double bestNormal = 0.0;

      for (int axis = 0; axis < 3; axis++) {
         double low = movingMax[axis] - blockMin[axis];
         double high = blockMax[axis] - movingMin[axis];
         if (low <= 1.0E-7 || high <= 1.0E-7) {
            return null;
         }

         double depth = Math.min(low, high);
         if (depth < bestDepth) {
            best = axis;
            bestDepth = depth;
            bestNormal = low < high ? -1.0 : 1.0;
         }
      }

      return bestDepth <= 0.001 ? null : new Depenetration.Push(best, bestDepth * bestNormal);
   }

   public static void apply(double[] velocity, Depenetration.Push push) {
      int axis = push.axis();
      velocity[axis] = push.amount() > 0.0 ? Math.max(push.amount(), velocity[axis]) : Math.min(push.amount(), velocity[axis]);
   }

   public record Push(int axis, double amount) {
   }
}
