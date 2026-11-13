package net.bedrocknative.content;

public final class AtlasPadding {
   private AtlasPadding() {
   }

   public static int contentOffset(int holderX, float u0, int atlasWidth) {
      return Math.round(u0 * atlasWidth) - holderX;
   }

   public static int[] extend(int[] pixels, int width, int height, int pad) {
      int paddedWidth = width + 2 * pad;
      int[] result = new int[paddedWidth * (height + 2 * pad)];

      for (int y = 0; y < height + 2 * pad; y++) {
         int row = Math.clamp((long)(y - pad), 0, height - 1) * width;

         for (int x = 0; x < paddedWidth; x++) {
            result[y * paddedWidth + x] = pixels[row + Math.clamp((long)(x - pad), 0, width - 1)];
         }
      }

      return result;
   }
}
