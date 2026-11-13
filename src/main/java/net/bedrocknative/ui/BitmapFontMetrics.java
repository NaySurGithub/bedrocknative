package net.bedrocknative.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BitmapFontMetrics implements UiTextMeasurer {
   public static final float CELL_HEIGHT = 8.0F;
   public static final float LINE_HEIGHT = 10.0F;
   public static final float BLANK_ADVANCE = 4.0F;
   public static final float MISSING_ADVANCE = 6.0F;
   private static final int PRIVATE_FIRST = 224;
   private static final int PRIVATE_LAST = 248;
   public static final int GLYPH_00 = 256;
   private static final String DEFAULT8 = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";
   private static final Map<Integer, Integer> DEFAULT8_CELLS = new HashMap<>();
   private static final short[] DEFAULT8_CELL;
   public static final long MAX_PAGE_PIXELS = 67108864L;
   private static final int PAGES = 257;
   private final BitmapFontMetrics.Page[] pages = new BitmapFontMetrics.Page[257];
   private int pageCount;
   private final BitmapFontMetrics.Glyph[][] glyphs = new BitmapFontMetrics.Glyph[257][];
   private long pagePixels;
   private long pageBudget = 67108864L;
   private final boolean[] asked = new boolean[257];
   @Nullable
   private final BitmapFontMetrics.PageLoader loader;
   private boolean latinStandIn;
   private static final String BEDROCK_DEFAULT8_ADVANCES = "6666664666666664467666666699963302566663555626266666666666225656766666666466666666666666666464663666665662653666666646666665257566666666666463666666666666666646636666666566626689936688686888666996996777997999999766799669955987787888788789677777667863396558";

   static int default8Cell(int codePoint) {
      return codePoint >= 0 && codePoint < DEFAULT8_CELL.length ? DEFAULT8_CELL[codePoint] : -1;
   }

   BitmapFontMetrics withPageBudget(long pixels) {
      this.pageBudget = pixels;
      return this;
   }

   public BitmapFontMetrics() {
      this(null);
   }

   public BitmapFontMetrics(@Nullable BitmapFontMetrics.PageLoader loader) {
      this.loader = loader;
   }

   public static boolean privateUse(int pageIndex) {
      return pageIndex >= 224 && pageIndex <= 248;
   }

   public BitmapFontMetrics page(int index, int width, int height, int[] argb) {
      if (index >= 0 && index < 257 && width >= 16 && height >= 16 && argb.length >= (long)width * height) {
         BitmapFontMetrics.Page previous = this.pages[index];
         long others = this.pagePixels - (previous == null ? 0L : (long)previous.width() * previous.height());
         if (others + (long)width * height > this.pageBudget) {
            return this;
         } else {
            int cellWidth = width / 16;
            int cellHeight = height / 16;
            short[] left = new short[256];
            short[] right = new short[256];

            for (int glyph = 0; glyph < 256; glyph++) {
               int cellX = glyph % 16 * cellWidth;
               int cellY = glyph / 16 * cellHeight;
               int first = -1;
               int last = -1;

               for (int x = 0; x < cellWidth; x++) {
                  for (int y = 0; y < cellHeight; y++) {
                     if (argb[(cellY + y) * width + cellX + x] >>> 24 != 0) {
                        if (first < 0) {
                           first = x;
                        }

                        last = x;
                        break;
                     }
                  }
               }

               left[glyph] = (short)first;
               right[glyph] = (short)last;
            }

            if (previous == null) {
               this.pageCount++;
            }

            this.pages[index] = new BitmapFontMetrics.Page(width, height, argb, left, right);
            this.glyphs[index] = null;
            this.pagePixels = others + (long)width * height;
            return this;
         }
      } else {
         return this;
      }
   }

   public static Set<Integer> default8CodePoints() {
      return Collections.unmodifiableSet(DEFAULT8_CELLS.keySet());
   }

   @Nullable
   public BitmapFontMetrics.Page page(int index) {
      if (index >= 0 && index < 257) {
         BitmapFontMetrics.Page page = this.pages[index];
         if (page == null && this.loader != null && !this.asked[index]) {
            this.asked[index] = true;
            BitmapFontMetrics.Pixels pixels = this.loader.load(index);
            if (pixels != null) {
               this.page(index, pixels.width(), pixels.height(), pixels.argb());
               page = this.pages[index];
            }
         }

         return page;
      } else {
         return null;
      }
   }

   public boolean hasPages() {
      return this.page(0) != null || this.pageCount > 0;
   }

   public BitmapFontMetrics latinStandIn(boolean standIn) {
      this.latinStandIn = standIn;
      return this;
   }

   private float standInAdvance(int codePoint) {
      if (this.latinStandIn && codePoint != 32 && Character.getType(codePoint) != 12) {
         int cell = default8Cell(codePoint);
         if (cell >= 0 && this.page(0) != null) {
            int digit = Character.digit(
               "6666664666666664467666666699963302566663555626266666666666225656766666666466666666666666666464663666665662653666666646666665257566666666666463666666666666666646636666666566626689936688686888666996996777997999999766799669955987787888788789677777667863396558"
                  .charAt(cell),
               16
            );
            return digit <= 0 ? 4.0F : digit;
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   @Nullable
   public BitmapFontMetrics.Glyph glyph(int codePoint) {
      if (codePoint >= 0 && codePoint <= 65535) {
         int classic = default8Cell(codePoint);
         BitmapFontMetrics.Page page = classic >= 0 ? this.page(0) : null;
         if (page == null) {
            return this.pageGlyph(codePoint);
         } else {
            BitmapFontMetrics.Glyph glyph = this.glyphOf(page, 0, classic, codePoint);
            return this.latinStandIn && glyph.width() == 0 && codePoint != 32 && Character.getType(codePoint) != 12 ? this.pageGlyph(codePoint) : glyph;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public BitmapFontMetrics.Glyph latinGlyph(int codePoint) {
      int cell = default8Cell(codePoint);
      BitmapFontMetrics.Page page = cell < 0 ? null : this.page(0);
      BitmapFontMetrics.Glyph glyph = page == null ? null : this.glyphOf(page, 0, cell, codePoint);
      return glyph != null && glyph.width() != 0 ? glyph : null;
   }

   @Nullable
   public BitmapFontMetrics.Glyph pageGlyph(int codePoint) {
      if (codePoint >= 0 && codePoint <= 65535) {
         int pageIndex = codePoint >> 8 == 0 ? 256 : codePoint >> 8;
         BitmapFontMetrics.Page page = this.page(pageIndex);
         return page == null ? null : this.glyphOf(page, pageIndex, codePoint & 0xFF, codePoint);
      } else {
         return null;
      }
   }

   private BitmapFontMetrics.Glyph glyphOf(BitmapFontMetrics.Page page, int pageIndex, int cell, int codePoint) {
      BitmapFontMetrics.Glyph[] made = this.glyphs[pageIndex];
      if (made == null) {
         made = new BitmapFontMetrics.Glyph[256];
         this.glyphs[pageIndex] = made;
      }

      BitmapFontMetrics.Glyph glyph = made[cell];
      if (glyph == null) {
         glyph = makeGlyph(page, pageIndex, cell, codePoint);
         made[cell] = glyph;
      }

      return glyph;
   }

   private static BitmapFontMetrics.Glyph makeGlyph(BitmapFontMetrics.Page page, int pageIndex, int cell, int codePoint) {
      int cellWidth = page.cellWidth();
      int cellHeight = page.cellHeight();
      float scale = privateUse(pageIndex) ? 1.0F : 8.0F / cellHeight;
      int first = page.left[cell];
      int last = page.right[cell];
      int u = cell % 16 * cellWidth;
      int v = cell / 16 * cellHeight;
      return first >= 0 && codePoint != 32 && Character.getType(codePoint) != 12
         ? new BitmapFontMetrics.Glyph(pageIndex, u + first, v, last - first + 1, cellHeight, (last - first + 2) * scale, scale)
         : new BitmapFontMetrics.Glyph(pageIndex, u, v, 0, cellHeight, 4.0F, scale);
   }

   public float advance(int codePoint, boolean bold) {
      float advance = this.standInAdvance(codePoint);
      if (advance == 0.0F) {
         BitmapFontMetrics.Glyph glyph = this.glyph(codePoint);
         advance = glyph == null ? 6.0F : glyph.advance();
      }

      return bold ? advance + 1.0F : advance;
   }

   public float advance(int codePoint, @Nullable BitmapFontMetrics.Glyph glyph, boolean bold) {
      float advance = this.standInAdvance(codePoint);
      if (advance == 0.0F) {
         advance = glyph == null ? 6.0F : glyph.advance();
      }

      return bold ? advance + 1.0F : advance;
   }

   @Override
   public float lineHeight(UiFont font) {
      return 10.0F * font.scale();
   }

   @Override
   public float width(String text, UiFont font) {
      float width = 0.0F;
      boolean bold = false;
      int i = 0;

      while (i < text.length()) {
         int codePoint = text.codePointAt(i);
         i += Character.charCount(codePoint);
         if (codePoint == 167 && i < text.length()) {
            char code = text.charAt(i);
            i += Character.charCount(text.codePointAt(i));
            if (code == 'l') {
               bold = true;
            } else if (code == 'r') {
               bold = false;
            }
         } else {
            width += this.advance(codePoint, bold);
         }
      }

      return width * font.scale();
   }

   static {
      int highest = 0;

      for (int cell = 0;
         cell
            < "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
               .length();
         cell++
      ) {
         char c = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
            .charAt(cell);
         if (c != 0) {
            DEFAULT8_CELLS.putIfAbsent(Integer.valueOf(c), cell);
            highest = Math.max(highest, c);
         }
      }

      DEFAULT8_CELL = new short[highest + 1];
      Arrays.fill(DEFAULT8_CELL, (short)-1);
      DEFAULT8_CELLS.forEach((codePoint, cellx) -> DEFAULT8_CELL[codePoint] = (short)cellx.intValue());
   }

   public record Glyph(int page, int u, int v, int width, int height, float advance, float scale) {
      public float top() {
         return (8.0F - this.height * this.scale) / 2.0F;
      }
   }

   public record Page(int width, int height, int[] argb, short[] left, short[] right) {
      public int cellWidth() {
         return this.width / 16;
      }

      public int cellHeight() {
         return this.height / 16;
      }

      @Nullable
      public int[] opaqueRows(int cell) {
         int cellWidth = this.cellWidth();
         int cellHeight = this.cellHeight();
         int u0 = cell % 16 * cellWidth;
         int v0 = cell / 16 * cellHeight;
         int first = -1;
         int last = -1;

         for (int y = 0; y < cellHeight; y++) {
            for (int x = 0; x < cellWidth; x++) {
               if (this.argb[(v0 + y) * this.width + u0 + x] >>> 24 != 0) {
                  if (first < 0) {
                     first = y;
                  }

                  last = y;
                  break;
               }
            }
         }

         return first < 0 ? null : new int[]{first, last};
      }
   }

   public interface PageLoader {
      @Nullable
      BitmapFontMetrics.Pixels load(int var1);
   }

   public record Pixels(int width, int height, int[] argb) {
   }
}
