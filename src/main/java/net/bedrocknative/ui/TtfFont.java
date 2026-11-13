package net.bedrocknative.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class TtfFont implements UiVectorFont {
   public static final int MAX_BYTES = 1048576;
   private static final int SFNT_TRUETYPE = 65536;
   private static final int SFNT_TRUE = 1953658213;
   private static final int SFNT_OPENTYPE = 1330926671;
   private static final int MAX_TABLES = 64;
   private static final int HEAD_MAGIC = 1594834165;
   private static final float REFERENCE = 1000.0F;
   private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
   private final String name;
   private final Font reference;
   private final float ascent;
   private final float descent;
   private final Map<Integer, Float> advances = new ConcurrentHashMap<>();
   static final int MAX_GLYPH_SIDE = 1024;
   private final Map<Float, Font> sizes = new ConcurrentHashMap<>();
   private static final int MAX_SIZES = 64;
   private static final ThreadLocal<TtfFont.Scratch> SCRATCH = ThreadLocal.withInitial(TtfFont.Scratch::new);

   private TtfFont(String name, Font font) {
      this.name = name;
      this.reference = font.deriveFont(1000.0F);
      LineMetrics metrics = this.reference.getLineMetrics("Hg", CONTEXT);
      this.ascent = metrics.getAscent() / 1000.0F;
      this.descent = metrics.getDescent() / 1000.0F;
   }

   public static boolean plausible(byte[] data) {
      if (data.length >= 12 && data.length <= 1048576) {
         int version = int32(data, 0);
         if (version != 65536 && version != 1953658213 && version != 1330926671) {
            return false;
         }

         int count = uint16(data, 4);
         if (count >= 1 && count <= 64 && 12 + 16 * count <= data.length) {
            Map<String, int[]> tables = new HashMap<>();

            for (int i = 0; i < count; i++) {
               int entry = 12 + 16 * i;

               for (int c = 0; c < 4; c++) {
                  if (data[entry + c] < 32 || data[entry + c] > 126) {
                     return false;
                  }
               }

               long offset = int32(data, entry + 8) & 4294967295L;
               long length = int32(data, entry + 12) & 4294967295L;
               if (offset < 12L + 16L * count || offset + length > data.length) {
                  return false;
               }

               if (tables.put(new String(data, entry, 4, StandardCharsets.ISO_8859_1), new int[]{(int)offset, (int)length}) != null) {
                  return false;
               }
            }

            int[] head = tables.get("head");
            int[] hhea = tables.get("hhea");
            int[] maxp = tables.get("maxp");
            return head != null
                  && head[1] >= 54
                  && int32(data, head[0] + 12) == 1594834165
                  && hhea != null
                  && hhea[1] >= 36
                  && maxp != null
                  && maxp[1] >= 6
                  && uint16(data, maxp[0] + 4) != 0
                  && tables.containsKey("hmtx")
                  && tables.containsKey("cmap")
               ? tables.containsKey("glyf") && tables.containsKey("loca") || tables.containsKey("CFF ") || tables.containsKey("CFF2")
               : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static int uint16(byte[] data, int at) {
      return (data[at] & 0xFF) << 8 | data[at + 1] & 0xFF;
   }

   private static int int32(byte[] data, int at) {
      return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
   }

   public static @Nullable TtfFont load(String name, byte[] data) {
      if (!plausible(data)) {
         return null;
      }

      try {
         return new TtfFont(name, Font.createFont(0, new ByteArrayInputStream(data)));
      } catch (FontFormatException | IOException | RuntimeException e) {
         return null;
      }
   }

   @Override
   public String name() {
      return this.name;
   }

   Font reference() {
      return this.reference;
   }

   public float ascent() {
      return this.ascent;
   }

   public float descent() {
      return this.descent;
   }

   @Override
   public boolean has(int codePoint) {
      return this.reference.canDisplay(codePoint);
   }

   @Override
   public float lineHeight() {
      return this.ascent + this.descent;
   }

   @Override
   public float baseline() {
      return this.ascent;
   }

   @Override
   public float advance(int codePoint) {
      Float cached = this.advances.get(codePoint);
      if (cached != null) {
         return cached;
      }

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
   }

   @Override
   public void warm() {
      for (int codePoint = 32; codePoint <= 126; codePoint++) {
         this.has(codePoint);
         this.advance(codePoint);
      }

      this.rasterize(65, 16.0F);
   }

   private Font sized(float pixelSize) {
      Font font = this.sizes.get(pixelSize);
      if (font == null) {
         font = this.reference.deriveFont(pixelSize);
         if (this.sizes.size() < 64) {
            this.sizes.put(pixelSize, font);
         }
      }

      return font;
   }

   @Override
   public UiVectorFont.Glyph rasterize(int codePoint, float pixelSize) {
      if (!(pixelSize <= 0.0F) && !(pixelSize > 512.0F)) {
         try {
            GlyphVector vector = this.sized(pixelSize).createGlyphVector(CONTEXT, Character.toString(codePoint));
            Rectangle box = vector.getPixelBounds(CONTEXT, 0.0F, 0.0F);
            if (box.isEmpty()) {
               return UiVectorFont.Glyph.EMPTY;
            } else {
               box.grow(1, 1);
               if (box.width <= 1024 && box.height <= 1024) {
                  TtfFont.Scratch scratch = SCRATCH.get();
                  scratch.box(box.width, box.height).drawGlyphVector(vector, -box.x, -box.y);
                  int[] argb = scratch.image.getRGB(0, 0, box.width, box.height, null, 0, box.width);
                  return new UiVectorFont.Glyph(box.width, box.height, box.x, box.y, argb);
               } else {
                  return UiVectorFont.Glyph.EMPTY;
               }
            }
         } catch (RuntimeException e) {
            return UiVectorFont.Glyph.EMPTY;
         }
      } else {
         return UiVectorFont.Glyph.EMPTY;
      }
   }

   @Override
   public String toString() {
      return this.name;
   }

   private static final class Scratch {
      private @Nullable BufferedImage image;
      private @Nullable Graphics2D graphics;

      Graphics2D box(int width, int height) {
         BufferedImage current = this.image;
         if (current == null || current.getWidth() < width || current.getHeight() < height) {
            if (this.graphics != null) {
               this.graphics.dispose();
            }

            current = new BufferedImage(
               Math.max(64, Integer.highestOneBit(Math.max(width, 1) * 2 - 1)), Math.max(64, Integer.highestOneBit(Math.max(height, 1) * 2 - 1)), 2
            );
            this.image = current;
            Graphics2D made = current.createGraphics();
            made.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            made.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            made.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            this.graphics = made;
         }

         Graphics2D drawing = this.graphics;
         drawing.setClip(0, 0, width, height);
         drawing.setComposite(AlphaComposite.Clear);
         drawing.fillRect(0, 0, width, height);
         drawing.setComposite(AlphaComposite.SrcOver);
         drawing.setColor(Color.WHITE);
         return drawing;
      }
   }
}
