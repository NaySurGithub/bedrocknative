package net.bedrocknative.content.own;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import javax.imageio.ImageIO;
import net.bedrocknative.content.ArgbImage;

public final class PixelArt {
   public static final int CLEAR = 0;

   private PixelArt() {
   }

   public static ArgbImage canvas(int width, int height) {
      return ArgbImage.blank(width, height);
   }

   public static ArgbImage solid(int width, int height, int argb) {
      ArgbImage image = canvas(width, height);
      Arrays.fill(image.argb(), argb);
      return image;
   }

   public static void set(ArgbImage image, int x, int y, int argb) {
      if (x >= 0 && y >= 0 && x < image.width() && y < image.height()) {
         image.argb()[y * image.width() + x] = argb;
      }
   }

   public static void fill(ArgbImage image, int x, int y, int width, int height, int argb) {
      for (int row = Math.max(0, y); row < Math.min(image.height(), y + height); row++) {
         for (int column = Math.max(0, x); column < Math.min(image.width(), x + width); column++) {
            image.argb()[row * image.width() + column] = argb;
         }
      }
   }

   public static void outline(ArgbImage image, int x, int y, int width, int height, int argb) {
      fill(image, x, y, width, 1, argb);
      fill(image, x, y + height - 1, width, 1, argb);
      fill(image, x, y, 1, height, argb);
      fill(image, x + width - 1, y, 1, height, argb);
   }

   public static void bevel(ArgbImage image, int x, int y, int width, int height, int face, int light, int shade, int corner) {
      fill(image, x, y, width, height, face);
      fill(image, x, y, width, 1, light);
      fill(image, x, y, 1, height, light);
      fill(image, x, y + height - 1, width, 1, shade);
      fill(image, x + width - 1, y, 1, height, shade);
      set(image, x + width - 1, y, corner);
      set(image, x, y + height - 1, corner);
   }

   public static ArgbImage grid(Map<Character, Integer> palette, String... rows) {
      int width = 0;

      for (String row : rows) {
         width = Math.max(width, row.length());
      }

      ArgbImage image = canvas(width, rows.length);

      for (int y = 0; y < rows.length; y++) {
         String row = rows[y];

         for (int x = 0; x < row.length(); x++) {
            char c = row.charAt(x);
            if (c != '.' && c != ' ') {
               Integer argb = palette.get(c);
               if (argb == null) {
                  throw new IllegalArgumentException("no colour for '" + c + "' in row " + y);
               }

               image.argb()[y * width + x] = argb;
            }
         }
      }

      return image;
   }

   public static void draw(ArgbImage image, ArgbImage picture, int x, int y) {
      for (int row = 0; row < picture.height(); row++) {
         for (int column = 0; column < picture.width(); column++) {
            int source = picture.pixel(column, row);
            int alpha = source >>> 24;
            if (alpha != 0) {
               int tx = x + column;
               int ty = y + row;
               if (tx >= 0 && ty >= 0 && tx < image.width() && ty < image.height()) {
                  int index = ty * image.width() + tx;
                  image.argb()[index] = alpha == 255 ? source : blend(image.argb()[index], source);
               }
            }
         }
      }
   }

   public static int blend(int bottom, int top) {
      int ta = top >>> 24;
      int ba = bottom >>> 24;
      int outA = ta + ba * (255 - ta) / 255;
      if (outA == 0) {
         return 0;
      }

      int[] channels = new int[3];

      for (int i = 0; i < 3; i++) {
         int shift = 16 - 8 * i;
         int tc = top >> shift & 0xFF;
         int bc = bottom >> shift & 0xFF;
         channels[i] = (tc * ta + bc * ba * (255 - ta) / 255) / outA;
      }

      return outA << 24 | channels[0] << 16 | channels[1] << 8 | channels[2];
   }

   public static int rgb(int rgb) {
      return 0xFF000000 | rgb;
   }

   public static int rgba(int rgb, int alpha) {
      return alpha << 24 | rgb & 16777215;
   }

   public static ArgbImage scale(ArgbImage image, int factor) {
      ArgbImage out = canvas(image.width() * factor, image.height() * factor);

      for (int y = 0; y < out.height(); y++) {
         for (int x = 0; x < out.width(); x++) {
            out.argb()[y * out.width() + x] = image.pixel(x / factor, y / factor);
         }
      }

      return out;
   }

   public static ArgbImage mirror(ArgbImage image) {
      ArgbImage out = canvas(image.width(), image.height());

      for (int y = 0; y < image.height(); y++) {
         for (int x = 0; x < image.width(); x++) {
            out.argb()[y * image.width() + x] = image.pixel(image.width() - 1 - x, y);
         }
      }

      return out;
   }

   public static ArgbImage tint(ArgbImage image, int rgb) {
      ArgbImage out = canvas(image.width(), image.height());

      for (int i = 0; i < image.argb().length; i++) {
         int alpha = image.argb()[i] >>> 24;
         out.argb()[i] = alpha == 0 ? 0 : alpha << 24 | rgb & 16777215;
      }

      return out;
   }

   public static byte[] png(ArgbImage image) {
      BufferedImage buffered = new BufferedImage(image.width(), image.height(), 2);
      buffered.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try {
         ImageIO.write(buffered, "png", out);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }

      return out.toByteArray();
   }

   public static String nineSlice(int slice, int width, int height) {
      return "{\"nineslice_size\":" + slice + ",\"base_size\":[" + width + "," + height + "]}";
   }

   public static String nineSlice(int left, int top, int right, int bottom, int width, int height) {
      return "{\"nineslice_size\":[" + left + "," + top + "," + right + "," + bottom + "],\"base_size\":[" + width + "," + height + "]}";
   }
}
