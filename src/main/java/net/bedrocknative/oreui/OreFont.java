package net.bedrocknative.oreui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class OreFont {
   private static final float REFERENCE = 1000.0F;
   private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
   private static final int MAX_BYTES = 16777216;
   private final String name;
   private final @Nullable Font reference;
   private final float ascent;
   private final float descent;
   private final Map<Integer, Float> advances = new ConcurrentHashMap<>();
   private final OreFont.@Nullable PixelFace pixels;
   private final float texelX;
   private final float texelY;
   private final float bearing;

   private OreFont(String name, Font font, float ascent, float descent) {
      this.name = name;
      this.reference = font.deriveFont(1000.0F);
      this.ascent = ascent;
      this.descent = descent;
      this.pixels = null;
      this.texelX = 0.0F;
      this.texelY = 0.0F;
      this.bearing = 0.0F;
   }

   private OreFont(String name, OreFont.PixelFace pixels, float texelX, float texelY, float bearing, float ascent, float descent) {
      this.name = name;
      this.reference = null;
      this.ascent = ascent;
      this.descent = descent;
      this.pixels = pixels;
      this.texelX = texelX;
      this.texelY = texelY;
      this.bearing = bearing;
   }

   public static OreFont pixel(String name, OreFont.PixelFace face, float texelX, float texelY, float bearing, float ascent, float descent) {
      return new OreFont(name, face, texelX, texelY, bearing, ascent, descent);
   }

   public static @Nullable OreFont load(String name, byte[] data) {
      try {
         if (data.length > 16777216) {
            return null;
         }

         float[] metrics = hhea(data);
         Font font = Font.createFont(0, new ByteArrayInputStream(data));
         return metrics == null ? null : new OreFont(name, font, metrics[0], metrics[1]);
      } catch (IOException | FontFormatException | RuntimeException e) {
         return null;
      }
   }

   public static @Nullable OreFont load(String name, Path file) {
      try {
         if (Files.size(file) > 16777216L) {
            return null;
         }

         byte[] data = Files.readAllBytes(file);
         float[] metrics = hhea(data);
         Font font = Font.createFont(0, new ByteArrayInputStream(data));
         return metrics == null ? null : new OreFont(name, font, metrics[0], metrics[1]);
      } catch (IOException | FontFormatException | RuntimeException e) {
         return null;
      }
   }

   static float @Nullable [] hhea(byte[] data) {
      if (data.length < 12) {
         return null;
      }

      int tables = u16(data, 4);
      int head = -1;
      int hhea = -1;

      for (int i = 0; i < tables && 12 + 16 * i + 16 <= data.length; i++) {
         int entry = 12 + 16 * i;
         String tag = new String(data, entry, 4, StandardCharsets.ISO_8859_1);
         int offset = s32(data, entry + 8);
         if (tag.equals("head")) {
            head = offset;
         } else if (tag.equals("hhea")) {
            hhea = offset;
         }
      }

      if (head >= 0 && hhea >= 0 && head + 20 <= data.length && hhea + 10 <= data.length) {
         int unitsPerEm = u16(data, head + 18);
         if (unitsPerEm <= 0) {
            return null;
         }

         short ascender = (short)u16(data, hhea + 4);
         short descender = (short)u16(data, hhea + 6);
         return new float[]{(float)ascender / unitsPerEm, (float)(-descender) / unitsPerEm};
      } else {
         return null;
      }
   }

   private static int u16(byte[] data, int at) {
      return (data[at] & 0xFF) << 8 | data[at + 1] & 0xFF;
   }

   private static int s32(byte[] data, int at) {
      return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
   }

   public String name() {
      return this.name;
   }

   public float ascent() {
      return this.ascent;
   }

   public float descent() {
      return this.descent;
   }

   public boolean has(int codePoint) {
      return this.pixels != null ? this.pixels.glyph(codePoint) != null : this.reference != null && this.reference.canDisplay(codePoint);
   }

   public float advance(int codePoint) {
      Float cached = this.advances.get(codePoint);
      if (cached != null) {
         return cached;
      }

      if (this.pixels == null && this.reference != null) {
         float advance;
         try {
            GlyphVector vector = this.reference.createGlyphVector(CONTEXT, Character.toString(codePoint));
            advance = vector.getNumGlyphs() == 0 ? 0.0F : vector.getGlyphMetrics(0).getAdvance() / 1000.0F;
         } catch (RuntimeException e) {
            advance = 0.0F;
         }

         if (this.advances.size() < 65536) {
            this.advances.put(codePoint, advance);
         }

         return advance;
      } else {
         OreFont.PixelGlyph glyph = this.pixels == null ? null : this.pixels.glyph(codePoint);
         float advance = glyph == null ? 0.0F : glyph.advance() * this.texelX;
         if (this.advances.size() < 65536) {
            this.advances.put(codePoint, advance);
         }

         return advance;
      }
   }

   public OreFont.Glyph rasterize(int codePoint, float pixelSize) {
      if (pixelSize <= 0.0F || pixelSize > 512.0F) {
         return OreFont.Glyph.EMPTY;
      }

      if (this.pixels == null && this.reference != null) {
         try {
            Font sized = this.reference.deriveFont(pixelSize);
            GlyphVector vector = sized.createGlyphVector(CONTEXT, Character.toString(codePoint));
            Rectangle box = vector.getPixelBounds(CONTEXT, 0.0F, 0.0F);
            if (box.isEmpty()) {
               return OreFont.Glyph.EMPTY;
            }

            box.grow(1, 1);
            BufferedImage image = new BufferedImage(box.width, box.height, 2);
            Graphics2D graphics = image.createGraphics();

            try {
               graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
               graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
               graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
               graphics.setColor(Color.WHITE);
               graphics.drawGlyphVector(vector, -box.x, -box.y);
            } finally {
               graphics.dispose();
            }

            int[] argb = image.getRGB(0, 0, box.width, box.height, null, 0, box.width);
            return new OreFont.Glyph(box.width, box.height, box.x, box.y, argb);
         } catch (RuntimeException e) {
            return OreFont.Glyph.EMPTY;
         }
      } else {
         OreFont.PixelGlyph glyph = this.pixels == null ? null : this.pixels.glyph(codePoint);
         return glyph == null ? OreFont.Glyph.EMPTY : this.rasterizePixels(glyph, pixelSize);
      }
   }

   private OreFont.Glyph rasterizePixels(OreFont.PixelGlyph glyph, float pixelSize) {
      if (glyph.width() > 0 && glyph.height() > 0) {
         float sx = this.texelX * pixelSize;
         float sy = this.texelY * pixelSize;
         float left = this.bearing * sx;
         float top = -glyph.top() * sy;
         int x0 = (int)Math.floor(left);
         int y0 = (int)Math.floor(top);
         int x1 = (int)Math.ceil(left + glyph.width() * sx);
         int y1 = (int)Math.ceil(top + glyph.height() * sy);
         int width = x1 - x0;
         int height = y1 - y0;
         if (width > 0 && height > 0 && width <= 4096 && height <= 4096) {
            int[] argb = new int[width * height];

            for (int py = 0; py < height; py++) {
               float rowTop = y0 + py;
               int ty0 = Math.max(0, (int)Math.floor((rowTop - top) / sy));
               int ty1 = Math.min(glyph.height() - 1, (int)Math.floor((rowTop + 1.0F - top) / sy));

               for (int px = 0; px < width; px++) {
                  float columnLeft = x0 + px;
                  int tx0 = Math.max(0, (int)Math.floor((columnLeft - left) / sx));
                  int tx1 = Math.min(glyph.width() - 1, (int)Math.floor((columnLeft + 1.0F - left) / sx));
                  float covered = 0.0F;

                  for (int ty = ty0; ty <= ty1; ty++) {
                     float cy = Math.min(rowTop + 1.0F, top + (ty + 1) * sy) - Math.max(rowTop, top + ty * sy);
                     if (!(cy <= 0.0F)) {
                        for (int tx = tx0; tx <= tx1; tx++) {
                           int alpha = glyph.coverage(tx, ty);
                           if (alpha != 0) {
                              float cx = Math.min(columnLeft + 1.0F, left + (tx + 1) * sx) - Math.max(columnLeft, left + tx * sx);
                              if (cx > 0.0F) {
                                 covered += cx * cy * alpha / 255.0F;
                              }
                           }
                        }
                     }
                  }

                  int a = Math.min(255, Math.round(covered * 255.0F));
                  argb[py * width + px] = a << 24 | 16777215;
               }
            }

            return new OreFont.Glyph(width, height, x0, y0, argb);
         } else {
            return OreFont.Glyph.EMPTY;
         }
      } else {
         return OreFont.Glyph.EMPTY;
      }
   }

   @Override
   public String toString() {
      return this.name;
   }

   public record Glyph(int width, int height, int left, int top, int[] argb) {
      public static final OreFont.Glyph EMPTY = new OreFont.Glyph(0, 0, 0, 0, new int[0]);
   }

   public interface PixelFace {
      OreFont.@Nullable PixelGlyph glyph(int var1);
   }

   public record PixelGlyph(int width, int height, int top, int advance, byte[] alpha) {
      public int coverage(int x, int y) {
         return x >= 0 && y >= 0 && x < this.width && y < this.height ? this.alpha[y * this.width + x] & 0xFF : 0;
      }
   }
}
