package net.bedrocknative.ui;

import net.bedrocknative.content.PackStack;
import org.jspecify.annotations.Nullable;

public final class UiTextMetrics implements UiTextMeasurer {
   public static final float VECTOR_SIZE = 8.0F;
   public static final float BITMAP_BASELINE = 7.0F;
   public static final float BITMAP_CELL = 8.0F;
   private final BitmapFontMetrics bitmap;
   private final UiFonts fonts;
   private static PackStack cachedStack;
   private static boolean cachedModernDefault;

   public UiTextMetrics(BitmapFontMetrics bitmap, UiFonts fonts) {
      this.bitmap = bitmap;
      this.fonts = fonts;
   }

   public BitmapFontMetrics bitmap() {
      return this.bitmap;
   }

   public UiFonts fonts() {
      return this.fonts;
   }

   public @Nullable UiVectorFont vector(UiFont font) {
      if ("default".equals(font.type())) {
         UiVectorFont mojangles = this.fonts.defaultFont();
         if (mojangles != null) {
            return mojangles;
         } else {
            return modernDefault() ? this.fonts.font("MinecraftTen") : null;
         }
      } else {
         return this.fonts.font(font.type());
      }
   }

   private static boolean modernDefault() {
      PackStack stack = PackStack.active();
      synchronized (UiTextMetrics.class) {
         if (stack != cachedStack) {
            cachedStack = stack;

            try {
               cachedModernDefault = stack.read("font/default8.json").isPresent();
            } catch (RuntimeException e) {
               cachedModernDefault = false;
            }
         }

         return cachedModernDefault;
      }
   }

   public static float size(UiFont font) {
      return 8.0F * font.scale();
   }

   public float baseline(UiFont font) {
      return this.baseline(font, this.vector(font));
   }

   public float baseline(UiFont font, @Nullable UiVectorFont vector) {
      return vector != null ? vector.baseline() * size(font) : this.bitmapCellTop(font) + 7.0F * font.scale();
   }

   public float bitmapCellTop(UiFont font) {
      return (this.bitmap.lineHeight(font) - 8.0F * font.scale()) / 2.0F;
   }

   public float advance(int codePoint, boolean bold, UiFont font, @Nullable UiVectorFont vector) {
      return vector != null && vector.has(codePoint)
         ? vector.advance(codePoint) * size(font) + (bold ? font.scale() : 0.0F)
         : this.bitmap.advance(codePoint, bold) * font.scale();
   }

   @Override
   public float lineHeight(UiFont font) {
      UiVectorFont vector = this.vector(font);
      return vector != null ? vector.lineHeight() * size(font) : this.bitmap.lineHeight(font);
   }

   @Override
   public float width(String text, UiFont font) {
      UiVectorFont vector = this.vector(font);
      if (vector == null) {
         return this.bitmap.width(text, font);
      }

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
            width += this.advance(codePoint, bold, font, vector);
         }
      }

      return width;
   }
}
