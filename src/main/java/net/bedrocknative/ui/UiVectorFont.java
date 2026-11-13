package net.bedrocknative.ui;

public interface UiVectorFont {
   String name();

   boolean has(int var1);

   float advance(int var1);

   float lineHeight();

   float baseline();

   UiVectorFont.Glyph rasterize(int var1, float var2);

   default void warm() {
   }

   record Glyph(int width, int height, int left, int top, int[] argb) {
      public static final UiVectorFont.Glyph EMPTY = new UiVectorFont.Glyph(0, 0, 0, 0, new int[0]);
   }
}
