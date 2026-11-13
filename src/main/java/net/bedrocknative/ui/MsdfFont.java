package net.bedrocknative.ui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import org.jspecify.annotations.Nullable;

public final class MsdfFont implements UiVectorFont {
   public static final float CELL = 1.25F;
   public static final float BASELINE_IN_CELL = 0.78125F;
   private static final float RANGE = 8.0F;
   private static final int MAX_PAGE = 4096;
   private final String name;
   private final String prefix;
   private final Function<String, Optional<byte[]>> reader;
   private final Map<Integer, Optional<MsdfFont.Page>> pages = new ConcurrentHashMap<>();

   public MsdfFont(String name, String prefix, Function<String, Optional<byte[]>> reader) {
      this.name = name;
      this.prefix = prefix;
      this.reader = reader;
   }

   @Override
   public String name() {
      return this.name;
   }

   @Nullable
   private MsdfFont.Page page(int index) {
      return this.pages.computeIfAbsent(index, this::load).orElse(null);
   }

   private Optional<MsdfFont.Page> load(int index) {
      String base = this.prefix + String.format(Locale.ROOT, "%02X", index);
      Optional<byte[]> data = this.reader.apply(base + ".fontdata");
      if (!data.isEmpty() && data.get().length >= 1028) {
         ByteBuffer buffer = ByteBuffer.wrap(data.get()).order(ByteOrder.LITTLE_ENDIAN);
         float[] advances = new float[256];

         for (int i = 0; i < 256; i++) {
            float advance = buffer.getFloat(4 + i * 4);
            advances[i] = Float.isFinite(advance) && advance > 0.0F && advance < 4.0F ? advance : 0.0F;
         }

         ArgbImage image = this.reader.apply(base + ".png").flatMap(MsdfFont::decode).orElse(null);
         return Optional.of(new MsdfFont.Page(advances, image));
      } else {
         return Optional.empty();
      }
   }

   private static Optional<ArgbImage> decode(byte[] png) {
      try {
         ArgbImage image = BedrockImages.decode(png);
         return image.width() >= 16 && image.height() >= 16 && image.width() <= 4096 && image.height() <= 4096 ? Optional.of(image) : Optional.empty();
      } catch (RuntimeException | IOException var2) {
         return Optional.empty();
      }
   }

   @Override
   public void warm() {
      this.page(0);
      this.rasterize(65, 16.0F);
   }

   @Override
   public boolean has(int codePoint) {
      if (codePoint >= 0 && codePoint <= 65535) {
         MsdfFont.Page page = this.page(codePoint >> 8);
         return page != null && page.image != null && (page.advances[codePoint & 0xFF] > 0.0F || codePoint == 32);
      } else {
         return false;
      }
   }

   @Override
   public float advance(int codePoint) {
      MsdfFont.Page page = codePoint >= 0 && codePoint <= 65535 ? this.page(codePoint >> 8) : null;
      return page == null ? 0.0F : page.advances[codePoint & 0xFF] * 1.25F;
   }

   @Override
   public float lineHeight() {
      return 1.25F;
   }

   @Override
   public float baseline() {
      return 0.9765625F;
   }

   @Override
   public UiVectorFont.Glyph rasterize(int codePoint, float pixels) {
      MsdfFont.Page page = codePoint >= 0 && codePoint <= 65535 ? this.page(codePoint >> 8) : null;
      float cellPixels = pixels * 1.25F;
      if (page != null && page.image != null && !(pixels <= 0.0F) && !(cellPixels > 512.0F)) {
         ArgbImage image = page.image;
         int cell = image.width() / 16;
         int cellHeight = image.height() / 16;
         int cellX = (codePoint & 0xFF) % 16 * cell;
         int cellY = (codePoint & 0xFF) / 16 * cellHeight;
         float texelsPerPixel = cell / cellPixels;
         float texelsPerPixelY = cellHeight / cellPixels;
         float pixelsPerRange = 8.0F / texelsPerPixel;
         int size = (int)Math.ceil(cellPixels);
         int[] coverage = new int[size * size];
         int minX = size;
         int minY = size;
         int maxX = -1;
         int maxY = -1;

         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               float median = median(image, cellX, cellY, cell, cellHeight, (x + 0.5F) * texelsPerPixel - 0.5F, (y + 0.5F) * texelsPerPixelY - 0.5F);
               float alpha = Math.max(0.0F, Math.min(1.0F, (median - 0.5F) * pixelsPerRange + 0.5F));
               if (alpha > 0.0F) {
                  coverage[y * size + x] = Math.round(alpha * 255.0F) << 24 | 16777215;
                  minX = Math.min(minX, x);
                  minY = Math.min(minY, y);
                  maxX = Math.max(maxX, x);
                  maxY = Math.max(maxY, y);
               }
            }
         }

         if (maxX < 0) {
            return UiVectorFont.Glyph.EMPTY;
         } else {
            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            int[] argb = new int[width * height];

            for (int y = 0; y < height; y++) {
               System.arraycopy(coverage, (minY + y) * size + minX, argb, y * width, width);
            }

            int baseline = Math.round(0.78125F * cellPixels);
            return new UiVectorFont.Glyph(width, height, minX, minY - baseline, argb);
         }
      } else {
         return UiVectorFont.Glyph.EMPTY;
      }
   }

   private static float median(ArgbImage image, int cellX, int cellY, int cell, int cellHeight, float tx, float ty) {
      float fx = Math.max(0.0F, Math.min((float)(cell - 1), tx));
      float fy = Math.max(0.0F, Math.min((float)(cellHeight - 1), ty));
      int x0 = (int)fx;
      int y0 = (int)fy;
      int x1 = Math.min(cell - 1, x0 + 1);
      int y1 = Math.min(cellHeight - 1, y0 + 1);
      float ax = fx - x0;
      float ay = fy - y0;
      float[] channels = new float[3];

      for (int c = 0; c < 3; c++) {
         int shift = 16 - c * 8;
         float top = lerp(channel(image, cellX + x0, cellY + y0, shift), channel(image, cellX + x1, cellY + y0, shift), ax);
         float bottom = lerp(channel(image, cellX + x0, cellY + y1, shift), channel(image, cellX + x1, cellY + y1, shift), ax);
         channels[c] = lerp(top, bottom, ay);
      }

      float r = channels[0];
      float g = channels[1];
      float b = channels[2];
      return Math.max(Math.min(r, g), Math.min(Math.max(r, g), b));
   }

   private static float channel(ArgbImage image, int x, int y, int shift) {
      return (image.argb()[y * image.width() + x] >> shift & 0xFF) / 255.0F;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   @Override
   public String toString() {
      return this.name;
   }

   private record Page(float[] advances, @Nullable ArgbImage image) {
   }
}
