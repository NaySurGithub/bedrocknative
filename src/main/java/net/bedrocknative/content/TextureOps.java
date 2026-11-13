package net.bedrocknative.content;

import org.jspecify.annotations.Nullable;

public final class TextureOps {
   private static final int CUTOUT_THRESHOLD = 128;

   private TextureOps() {
   }

   public static ArgbImage applyClass(ArgbImage image, TextureOps.AlphaClass java) {
      return switch (java) {
         case OPAQUE -> forceOpaque(fillClear(image));
         case CUTOUT -> cutout(image);
         case TRANSLUCENT -> image;
      };
   }

   public static ArgbImage fillClear(ArgbImage image) {
      long r = 0L;
      long g = 0L;
      long b = 0L;
      int count = 0;

      for (int pixel : image.argb()) {
         if (ArgbImage.alpha(pixel) != 0) {
            r += pixel >> 16 & 0xFF;
            g += pixel >> 8 & 0xFF;
            b += pixel & 0xFF;
            count++;
         }
      }

      if (count != 0 && count != image.argb().length) {
         int average = (int)(r / count) << 16 | (int)(g / count) << 8 | (int)(b / count);
         int[] out = (int[])image.argb().clone();

         for (int i = 0; i < out.length; i++) {
            if (ArgbImage.alpha(out[i]) == 0) {
               out[i] = average;
            }
         }

         return new ArgbImage(image.width(), image.height(), out);
      } else {
         return image;
      }
   }

   public static ArgbImage forceOpaque(ArgbImage image) {
      int[] out = (int[])image.argb().clone();

      for (int i = 0; i < out.length; i++) {
         out[i] |= -16777216;
      }

      return new ArgbImage(image.width(), image.height(), out);
   }

   public static ArgbImage cutout(ArgbImage image) {
      int[] out = (int[])image.argb().clone();

      for (int i = 0; i < out.length; i++) {
         out[i] = ArgbImage.alpha(out[i]) >= 128 ? out[i] | 0xFF000000 : 0;
      }

      return new ArgbImage(image.width(), image.height(), out);
   }

   public static ArgbImage copyAlpha(ArgbImage image, int frameHeight, ArgbImage java, int javaFrameWidth, int javaFrameHeight) {
      int[] out = new int[image.argb().length];

      for (int y = 0; y < image.height(); y++) {
         int javaY = y % frameHeight * javaFrameHeight / frameHeight;

         for (int x = 0; x < image.width(); x++) {
            int javaX = x * javaFrameWidth / image.width();
            int alpha = ArgbImage.alpha(java.pixel(javaX, javaY));
            out[y * image.width() + x] = image.pixel(x, y) & 16777215 | alpha << 24;
         }
      }

      return new ArgbImage(image.width(), image.height(), out);
   }

   public static ArgbImage chain(ArgbImage chain1, ArgbImage chain2) {
      int width = chain1.width();
      int height = chain1.height();
      int strand = Math.max(1, width * 3 / 16);
      ArgbImage second = chain2.width() == width && chain2.height() == height ? chain2 : scale(chain2, width, height);
      int[] out = new int[width * height];

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < strand && 2 * strand <= width; x++) {
            out[y * width + x] = second.pixel(x, y);
            out[y * width + strand + x] = chain1.pixel(x, y);
         }
      }

      return new ArgbImage(width, height, out);
   }

   public static ArgbImage fringe(ArgbImage grassSide) {
      int[] out = (int[])grassSide.argb().clone();

      for (int i = 0; i < out.length; i++) {
         if (ArgbImage.alpha(out[i]) == 0) {
            out[i] = 0;
         }
      }

      return new ArgbImage(grassSide.width(), grassSide.height(), out);
   }

   public static @Nullable ArgbImage maskOverlay(ArgbImage icon) {
      int[] out = new int[icon.argb().length];
      boolean any = false;

      for (int i = 0; i < out.length; i++) {
         int alpha = ArgbImage.alpha(icon.argb()[i]);
         if (alpha != 0 && alpha != 255) {
            out[i] = icon.argb()[i] | 0xFF000000;
            any = true;
         }
      }

      return any ? new ArgbImage(icon.width(), icon.height(), out) : null;
   }

   public static ArgbImage maskBase(ArgbImage icon) {
      int[] out = (int[])icon.argb().clone();

      for (int i = 0; i < out.length; i++) {
         if (ArgbImage.alpha(out[i]) != 255) {
            out[i] = 0;
         }
      }

      return new ArgbImage(icon.width(), icon.height(), out);
   }

   public static @Nullable ArgbImage transform(ArgbImage image, String name) {
      int w = image.width();
      int h = image.height();
      boolean swaps;
      switch (name) {
         case "rot90":
         case "rot270":
         case "transpose":
         case "antitranspose":
            swaps = true;
            break;
         case "none":
         case "rot180":
         case "flip_u":
         case "flip_v":
            swaps = false;
            break;
         default:
            return null;
      }

      int outWidth = swaps ? h : w;
      int outHeight = swaps ? w : h;
      int[] out = new int[w * h];

      for (int y = 0; y < outHeight; y++) {
         for (int x = 0; x < outWidth; x++) {
            int pixel = switch (name) {
               case "rot90" -> image.pixel(w - 1 - y, x);
               case "rot180" -> image.pixel(w - 1 - x, h - 1 - y);
               case "rot270" -> image.pixel(y, h - 1 - x);
               case "flip_u" -> image.pixel(w - 1 - x, y);
               case "flip_v" -> image.pixel(x, h - 1 - y);
               case "transpose" -> image.pixel(y, x);
               case "antitranspose" -> image.pixel(w - 1 - y, h - 1 - x);
               default -> image.pixel(x, y);
            };
            out[y * outWidth + x] = pixel;
         }
      }

      return new ArgbImage(outWidth, outHeight, out);
   }

   public static ArgbImage frame(ArgbImage strip, int index, int count) {
      int position = count > 0 ? Math.floorMod(index, count) : 0;
      if (strip.height() % strip.width() == 0) {
         int frames = strip.height() / strip.width();
         int scaled = count > 0 ? position * frames / count : 0;
         return strip.crop(0, Math.min(scaled, frames - 1) * strip.width(), strip.width(), strip.width());
      } else if (count > 0 && strip.height() % count == 0) {
         int frameHeight = strip.height() / count;
         return strip.crop(0, position * frameHeight, strip.width(), frameHeight);
      } else {
         return firstFrame(strip);
      }
   }

   public static ArgbImage firstFrame(ArgbImage strip) {
      return strip.height() <= strip.width() ? strip : strip.crop(0, 0, strip.width(), strip.width());
   }

   public static ArgbImage downscale(ArgbImage image, int factor) {
      if (factor <= 1) {
         return image;
      }

      int width = Math.max(1, image.width() / factor);
      int height = Math.max(1, image.height() / factor);
      int[] out = new int[width * height];

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            long a = 0L;
            long r = 0L;
            long g = 0L;
            long b = 0L;
            int count = 0;

            for (int dy = 0; dy < factor && y * factor + dy < image.height(); dy++) {
               for (int dx = 0; dx < factor && x * factor + dx < image.width(); dx++) {
                  int pixel = image.pixel(x * factor + dx, y * factor + dy);
                  int alpha = ArgbImage.alpha(pixel);
                  a += alpha;
                  r += (long)(pixel >> 16 & 0xFF) * alpha;
                  g += (long)(pixel >> 8 & 0xFF) * alpha;
                  b += (long)(pixel & 0xFF) * alpha;
                  count++;
               }
            }

            int alpha = (int)(a / count);
            out[y * width + x] = a == 0L ? 0 : alpha << 24 | (int)(r / a) << 16 | (int)(g / a) << 8 | (int)(b / a);
         }
      }

      return new ArgbImage(width, height, out);
   }

   public static ArgbImage scale(ArgbImage image, int width, int height) {
      int[] out = new int[width * height];

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            out[y * width + x] = image.pixel(x * image.width() / width, y * image.height() / height);
         }
      }

      return new ArgbImage(width, height, out);
   }

   public enum AlphaClass {
      OPAQUE,
      CUTOUT,
      TRANSLUCENT;

      public static TextureOps.AlphaClass of(ArgbImage image) {
         boolean transparent = false;

         for (int pixel : image.argb()) {
            int alpha = ArgbImage.alpha(pixel);
            if (alpha == 0) {
               transparent = true;
            } else if (alpha != 255) {
               return TRANSLUCENT;
            }
         }

         return transparent ? CUTOUT : OPAQUE;
      }
   }
}
