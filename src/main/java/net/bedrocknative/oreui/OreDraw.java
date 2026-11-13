package net.bedrocknative.oreui;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class OreDraw {
   private final OrePainter painter;
   private final OreText text;
   private final OreTheme theme;

   public OreDraw(OrePainter painter, OreText text, OreTheme theme) {
      this.painter = painter;
      this.text = text;
      this.theme = theme;
   }

   public OrePainter painter() {
      return this.painter;
   }

   public OreText text() {
      return this.text;
   }

   public OreScale scale() {
      return this.text.scale();
   }

   public OreTheme theme() {
      return this.theme;
   }

   public void fill(OreBox box, int argb) {
      if (!box.empty() && argb >>> 24 != 0) {
         this.painter.fill(box, argb);
      }
   }

   public void borderImage(@Nullable OreBorderImage image, OreBox box) {
      if (image != null) {
         OreScale scale = this.scale();
         float[] widths = image.widths();
         this.nineSlice(
            image.sprite(),
            image.sliceTop(),
            image.sliceRight(),
            image.sliceBottom(),
            image.sliceLeft(),
            image.fill(),
            image.area(box, scale),
            scale.px(widths[0]),
            scale.px(widths[1]),
            scale.px(widths[2]),
            scale.px(widths[3]),
            -1
         );
      }
   }

   public void nineSlice(
      String sprite,
      int sliceTop,
      int sliceRight,
      int sliceBottom,
      int sliceLeft,
      boolean fill,
      OreBox area,
      float top,
      float right,
      float bottom,
      float left,
      int argb
   ) {
      int[] size = this.painter.spriteSize(sprite);
      if (size != null && !area.empty()) {
         float factor = Math.min(1.0F, Math.min(left + right > 0.0F ? area.w() / (left + right) : 1.0F, top + bottom > 0.0F ? area.h() / (top + bottom) : 1.0F));
         top *= factor;
         right *= factor;
         bottom *= factor;
         left *= factor;
         int w = size[0];
         int h = size[1];
         float midW = area.w() - left - right;
         float midH = area.h() - top - bottom;
         int srcMidW = w - sliceLeft - sliceRight;
         int srcMidH = h - sliceTop - sliceBottom;
         float x0 = area.x();
         float x1 = area.x() + left;
         float x2 = area.right() - right;
         float y0 = area.y();
         float y1 = area.y() + top;
         float y2 = area.bottom() - bottom;
         this.part(sprite, 0.0F, 0.0F, sliceLeft, sliceTop, x0, y0, left, top, argb);
         this.part(sprite, w - sliceRight, 0.0F, sliceRight, sliceTop, x2, y0, right, top, argb);
         this.part(sprite, 0.0F, h - sliceBottom, sliceLeft, sliceBottom, x0, y2, left, bottom, argb);
         this.part(sprite, w - sliceRight, h - sliceBottom, sliceRight, sliceBottom, x2, y2, right, bottom, argb);
         this.part(sprite, sliceLeft, 0.0F, srcMidW, sliceTop, x1, y0, midW, top, argb);
         this.part(sprite, sliceLeft, h - sliceBottom, srcMidW, sliceBottom, x1, y2, midW, bottom, argb);
         this.part(sprite, 0.0F, sliceTop, sliceLeft, srcMidH, x0, y1, left, midH, argb);
         this.part(sprite, w - sliceRight, sliceTop, sliceRight, srcMidH, x2, y1, right, midH, argb);
         if (fill) {
            this.part(sprite, sliceLeft, sliceTop, srcMidW, srcMidH, x1, y1, midW, midH, argb);
         }
      }
   }

   private void part(String sprite, float u, float v, float uw, float vh, float x, float y, float w, float h, int argb) {
      if (uw > 0.0F && vh > 0.0F && w > 0.0F && h > 0.0F) {
         this.painter.sprite(sprite, u, v, uw, vh, new OreBox(x, y, w, h), argb);
      }
   }

   public void sprite(String sprite, OreBox dest, int argb) {
      int[] size = this.painter.spriteSize(sprite);
      if (size != null) {
         this.painter.sprite(sprite, 0.0F, 0.0F, size[0], size[1], dest, argb);
      }
   }

   public boolean hasSprite(String sprite) {
      return this.painter.spriteSize(sprite) != null;
   }

   public float iconSize(OreIcon icon, @Nullable Float widthTenths) {
      return this.scale().px(icon.elementRem(widthTenths));
   }

   public OreBox icon(OreIcon icon, @Nullable Float widthTenths, float cx, float cy, int tint) {
      float element = this.iconSize(icon, widthTenths);
      OreBox box = new OreBox(cx - element / 2.0F, cy - element / 2.0F, element, element);
      if (!icon.mask()) {
         this.sprite(icon.sprite(), box, -1);
         return box;
      } else {
         float rem = icon.elementRem(widthTenths);
         float w = this.scale().px(icon.maskWidthRem(rem));
         float h = this.scale().px(icon.maskHeightRem(rem));
         this.painter.pushClip(box);

         try {
            int[] size = this.painter.spriteSize(icon.sprite());
            if (size != null) {
               this.painter.mask(icon.sprite(), 0.0F, 0.0F, size[0], size[1], new OreBox(cx - w / 2.0F, cy - h / 2.0F, w, h), tint);
            }
         } finally {
            this.painter.popClip();
         }

         return box;
      }
   }

   public void lines(OreBox box, int first, int second, float width, boolean top, boolean right, boolean bottom, boolean left) {
      if (!box.empty()) {
         if (top) {
            this.fill(new OreBox(box.x(), box.y(), box.w(), width), first);
         }

         if (left) {
            float from = top ? width : 0.0F;
            this.fill(new OreBox(box.x(), box.y() + from, width, box.h() - from), first);
         }

         if (bottom) {
            this.fill(new OreBox(box.x(), box.bottom() - width, box.w(), width), second);
         }

         if (right) {
            float to = bottom ? width : 0.0F;
            this.fill(new OreBox(box.right() - width, box.y(), width, box.h() - to), second);
         }
      }
   }

   public void border(OreBox box, int argb, float width) {
      this.border(box, argb, width, true, true, true, true);
   }

   public void border(OreBox box, int argb, float width, boolean top, boolean right, boolean bottom, boolean left) {
      if (!box.empty() && argb >>> 24 != 0) {
         if (top) {
            this.fill(new OreBox(box.x(), box.y(), box.w(), width), argb);
         }

         if (bottom) {
            this.fill(new OreBox(box.x(), box.bottom() - width, box.w(), width), argb);
         }

         float y0 = top ? box.y() + width : box.y();
         float y1 = bottom ? box.bottom() - width : box.bottom();
         if (left) {
            this.fill(new OreBox(box.x(), y0, width, y1 - y0), argb);
         }

         if (right) {
            this.fill(new OreBox(box.right() - width, y0, width, y1 - y0), argb);
         }
      }
   }

   public void focusRing(OreBox box, int argb, boolean inset) {
      float b2 = this.scale().b2();
      OreBox ring = inset ? box : box.outset(2.0F * b2);
      this.border(ring, argb, b2);
   }

   public void text(OreText.Type type, String line, float x, float width, float lineTop, OreDraw.Align align, int argb) {
      this.text(type, line, x, width, lineTop, align, argb, false);
   }

   public void text(OreText.Type type, String line, float x, float width, float lineTop, OreDraw.Align align, int argb, boolean shadow) {
      if (!line.isEmpty() && argb >>> 24 != 0) {
         float lineWidth = this.text.width(type, line);

         float start = switch (align) {
            case LEFT -> x;
            case CENTER -> x + (width - lineWidth) / 2.0F;
            case RIGHT -> x + width - lineWidth;
         };
         float baseline = lineTop + this.text.baseline(type);
         List<OreText.Placed> glyphs = this.text.place(type, line);
         float size = this.text.sizePx(type);
         if (shadow) {
            float b2 = this.scale().b2();
            int shade = this.theme.textShadow();

            for (OreText.Placed glyph : glyphs) {
               this.painter.glyph(glyph.font(), size, glyph.codePoint(), start + glyph.x() + b2, baseline + b2, shade);
            }
         }

         for (OreText.Placed glyph : glyphs) {
            this.painter.glyph(glyph.font(), size, glyph.codePoint(), start + glyph.x(), baseline, argb);
         }
      }
   }

   public void textCentered(OreText.Type type, String line, OreBox box, int argb, boolean shadow) {
      float lineTop = box.y() + (box.h() - this.text.linePx(type)) / 2.0F;
      this.text(type, line, box.x(), box.w(), lineTop, OreDraw.Align.CENTER, argb, shadow);
   }

   public float lines(OreText.Type type, List<String> lines, float x, float width, float top, OreDraw.Align align, int argb) {
      float y = top;

      for (String line : lines) {
         this.text(type, line, x, width, y, align, argb);
         y += this.text.linePx(type);
      }

      return y;
   }

   public static enum Align {
      LEFT,
      CENTER,
      RIGHT;
   }
}
