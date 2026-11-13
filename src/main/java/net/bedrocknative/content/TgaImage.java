package net.bedrocknative.content;

import java.io.IOException;

public record TgaImage(int width, int height, int[] argb) {
   public static TgaImage decode(byte[] data) throws IOException {
      if (data.length < 18) {
         throw new IOException("TGA too short");
      }

      int idLength = u8(data, 0);
      int colourMapType = u8(data, 1);
      int type = u8(data, 2);
      int mapLength = u16(data, 5);
      int mapEntryBits = u8(data, 7);
      int width = u16(data, 12);
      int height = u16(data, 14);
      int bits = u8(data, 16);
      int descriptor = u8(data, 17);
      boolean grey = type == 3 || type == 11;
      boolean rle = type == 10 || type == 11;
      if ((type == 2 || type == 3 || rle) && (colourMapType == 0 || !grey) && width != 0 && height != 0 && (long)width * height <= 16777216L) {
         int bytesPerPixel = bits / 8;
         if (grey ? bits == 8 : bits == 24 || bits == 32) {
            int position = 18 + idLength + (colourMapType != 0 ? mapLength * ((mapEntryBits + 7) / 8) : 0);
            int[] pixels = new int[width * height];
            int index = 0;

            while (index < pixels.length) {
               int count = 1;
               boolean repeat = false;
               if (rle) {
                  int packet = u8(data, position++);
                  count = (packet & 127) + 1;
                  repeat = (packet & 128) != 0;
               }

               int colour = 0;

               for (int i = 0; i < count && index < pixels.length; i++) {
                  if (i == 0 || !repeat) {
                     if (position + bytesPerPixel > data.length) {
                        throw new IOException("TGA truncated");
                     }

                     colour = grey
                        ? 0xFF000000 | u8(data, position) * 65793
                        : (bits == 32 ? u8(data, position + 3) << 24 : -16777216)
                           | u8(data, position + 2) << 16
                           | u8(data, position + 1) << 8
                           | u8(data, position);
                     position += bytesPerPixel;
                  }

                  pixels[index++] = colour;
               }
            }

            boolean topDown = (descriptor & 32) != 0;
            boolean rightToLeft = (descriptor & 16) != 0;
            int[] argb = new int[pixels.length];

            for (int y = 0; y < height; y++) {
               int sourceRow = topDown ? y : height - 1 - y;

               for (int x = 0; x < width; x++) {
                  argb[y * width + x] = pixels[sourceRow * width + (rightToLeft ? width - 1 - x : x)];
               }
            }

            return new TgaImage(width, height, argb);
         } else {
            throw new IOException("Unsupported TGA depth " + bits);
         }
      } else {
         throw new IOException("Unsupported TGA type " + type);
      }
   }

   private static int u8(byte[] data, int index) throws IOException {
      if (index >= data.length) {
         throw new IOException("TGA truncated");
      } else {
         return data[index] & 0xFF;
      }
   }

   private static int u16(byte[] data, int index) throws IOException {
      return u8(data, index) | u8(data, index + 1) << 8;
   }
}
