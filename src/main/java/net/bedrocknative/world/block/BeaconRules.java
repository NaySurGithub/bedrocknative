package net.bedrocknative.world.block;

public final class BeaconRules {
   public static final int NONE = 0;
   public static final int SPEED = 1;
   public static final int HASTE = 3;
   public static final int STRENGTH = 5;
   public static final int JUMP_BOOST = 8;
   public static final int REGENERATION = 10;
   public static final int RESISTANCE = 11;
   public static final int MAX_LEVELS = 4;
   private static final int UNAVAILABLE = Integer.MAX_VALUE;

   private BeaconRules() {
   }

   public static int requiredLevel(int effect) {
      return switch (effect) {
         case 0 -> 0;
         case 1, 3 -> 1;
         default -> Integer.MAX_VALUE;
         case 5 -> 3;
         case 8, 11 -> 2;
         case 10 -> 4;
      };
   }

   public static boolean valid(int primary, int secondary, int levels) {
      if (secondary != 0 && levels < 4) {
         return false;
      } else {
         int primaryLevel = requiredLevel(primary);
         int secondaryLevel = requiredLevel(secondary);
         if (primaryLevel > levels || secondaryLevel > levels) {
            return false;
         } else {
            return primaryLevel >= 4 ? false : secondaryLevel == 0 || secondaryLevel >= 4 || primary == secondary;
         }
      }
   }

   public static int pyramidLevels(int x, int y, int z, int minY, BeaconRules.Base base) {
      int levels = 0;

      for (int step = 1; step <= 4; levels = step++) {
         int layer = y - step;
         if (layer < minY) {
            break;
         }

         for (int layerX = x - step; layerX <= x + step; layerX++) {
            for (int layerZ = z - step; layerZ <= z + step; layerZ++) {
               if (!base.at(layerX, layer, layerZ)) {
                  return levels;
               }
            }
         }
      }

      return levels;
   }

   @FunctionalInterface
   public interface Base {
      boolean at(int var1, int var2, int var3);
   }
}
