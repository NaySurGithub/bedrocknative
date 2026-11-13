package net.bedrocknative.content;

import java.nio.IntBuffer;

public final class AtlasComposer {
   private AtlasComposer() {
   }

   public static void place(IntBuffer atlas, int atlasWidth, int atlasHeight, IntBuffer sprite, int width, int height, int x, int y, int pad) {
      int holderWidth = width + 2 * pad;
      int holderHeight = height + 2 * pad;
      if (width > 0 && height > 0 && pad >= 0 && x >= 0 && y >= 0 && x + holderWidth <= atlasWidth && y + holderHeight <= atlasHeight) {
         if (sprite.limit() >= width * height && atlas.limit() >= atlasWidth * atlasHeight) {
            for (int row = 0; row < holderHeight; row++) {
               int source = Math.clamp(row - pad, 0, height - 1) * width;
               int target = (y + row) * atlasWidth + x;
               if (pad > 0) {
                  int left = sprite.get(source);
                  int right = sprite.get(source + width - 1);

                  for (int i = 0; i < pad; i++) {
                     atlas.put(target + i, left);
                     atlas.put(target + pad + width + i, right);
                  }
               }

               atlas.put(target + pad, sprite, source, width);
            }
         } else {
            throw new IllegalArgumentException("sprite or atlas holds fewer pixels than its size says");
         }
      } else {
         throw new IllegalArgumentException(
            "sprite " + width + "x" + height + " padded " + pad + " at " + x + "," + y + " is outside the atlas " + atlasWidth + "x" + atlasHeight
         );
      }
   }

   public static long pixels(int width, int height, int levels) {
      long total = 0L;

      for (int level = 0; level < levels; level++) {
         total += (long)Math.max(1, width >> level) * Math.max(1, height >> level);
      }

      return total;
   }
}
