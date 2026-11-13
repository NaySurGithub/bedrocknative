package net.bedrocknative.content;

import java.io.IOException;

public final class ImageLimits {
   public static final int DEFAULT_MAX_SIDE = 16384;
   public static final long MAX_PIXELS = 16777216L;
   public static final long MAX_SOURCE_PIXELS = 67108864L;
   public static final long MAX_SOURCE_JPEG_PIXELS = 33554432L;
   static final int MIN_GPU_SIDE = 2048;
   private static volatile int maxSide = 16384;

   private ImageLimits() {
   }

   public static int maxSide() {
      return maxSide;
   }

   public static void gpuMaxSide(int side) {
      if (side >= 2048) {
         maxSide = side;
      }
   }

   public static int step(int width, int height) throws IOException {
      return step(width, height, maxSide, 16777216L);
   }

   public static void requireSource(int width, int height, long maxPixels) throws IOException {
      if (width > 0 && height > 0) {
         if ((long)width * height > maxPixels) {
            throw new IOException("image too large: " + width + "x" + height);
         }
      } else {
         throw new IOException("image size " + width + "x" + height);
      }
   }

   static int step(int width, int height, int side, long pixels) throws IOException {
      if (width > 0 && height > 0) {
         int step = 1;

         while (Math.ceilDiv(width, step) > side || Math.ceilDiv(height, step) > side || (long)Math.ceilDiv(width, step) * Math.ceilDiv(height, step) > pixels) {
            step++;
         }

         return step;
      } else {
         throw new IOException("image size " + width + "x" + height);
      }
   }
}
