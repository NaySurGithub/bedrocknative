package net.bedrocknative.model.render;

public final class DyeColors {
   private static final int[] RGB = new int[]{
      16383998, 16351261, 13061821, 3847130, 16701501, 8439583, 15961002, 4673362, 10329495, 1481884, 8991416, 3949738, 8606770, 6192150, 11546150, 1908001
   };
   private static final String[] NAMES = new String[]{
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   };

   private DyeColors() {
   }

   public static int argb(int index) {
      return 0xFF000000 | (index >= 0 && index < RGB.length ? RGB[index] : RGB[0]);
   }

   public static int ofItem(String identifier, String suffix) {
      if (identifier.startsWith("minecraft:") && identifier.endsWith(suffix)) {
         int start = "minecraft:".length();
         int end = identifier.length() - suffix.length();

         for (int i = 0; i < NAMES.length; i++) {
            if (end - start == NAMES[i].length() && identifier.startsWith(NAMES[i], start)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }
}
