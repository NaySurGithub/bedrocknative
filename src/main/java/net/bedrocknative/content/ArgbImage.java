package net.bedrocknative.content;

public record ArgbImage(int width, int height, int[] argb) {
   public ArgbImage(int width, int height, int[] argb) {
      if (width > 0 && height > 0 && argb.length == width * height) {
         this.width = width;
         this.height = height;
         this.argb = argb;
      } else {
         throw new IllegalArgumentException("image " + width + "x" + height + " with " + argb.length + " pixels");
      }
   }

   public static ArgbImage blank(int width, int height) {
      return new ArgbImage(width, height, new int[width * height]);
   }

   public int pixel(int x, int y) {
      return this.argb[y * this.width + x];
   }

   public static int alpha(int argb) {
      return argb >>> 24;
   }

   public boolean samePixels(ArgbImage other) {
      if (this.width == other.width && this.height == other.height) {
         for (int i = 0; i < this.argb.length; i++) {
            int a = this.argb[i];
            int b = other.argb[i];
            if (a != b && (a >>> 24 != 0 || b >>> 24 != 0)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public ArgbImage crop(int x, int y, int cropWidth, int cropHeight) {
      int[] out = new int[cropWidth * cropHeight];

      for (int row = 0; row < cropHeight; row++) {
         System.arraycopy(this.argb, (y + row) * this.width + x, out, row * cropWidth, cropWidth);
      }

      return new ArgbImage(cropWidth, cropHeight, out);
   }

   public boolean hasTranslucent() {
      for (int pixel : this.argb) {
         int alpha = alpha(pixel);
         if (alpha != 0 && alpha != 255) {
            return true;
         }
      }

      return false;
   }

   public boolean hasAlpha() {
      for (int pixel : this.argb) {
         if (alpha(pixel) != 255) {
            return true;
         }
      }

      return false;
   }
}
