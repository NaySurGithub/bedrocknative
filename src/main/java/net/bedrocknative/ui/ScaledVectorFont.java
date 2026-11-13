package net.bedrocknative.ui;

public record ScaledVectorFont(UiVectorFont font, float scale) implements UiVectorFont {
   @Override
   public String name() {
      return this.font.name();
   }

   @Override
   public boolean has(int codePoint) {
      return this.font.has(codePoint);
   }

   @Override
   public float advance(int codePoint) {
      return this.font.advance(codePoint) * this.scale;
   }

   @Override
   public float lineHeight() {
      return this.font.lineHeight() * this.scale;
   }

   @Override
   public float baseline() {
      return this.font.baseline() * this.scale;
   }

   @Override
   public UiVectorFont.Glyph rasterize(int codePoint, float pixels) {
      return this.font.rasterize(codePoint, pixels * this.scale);
   }
}
