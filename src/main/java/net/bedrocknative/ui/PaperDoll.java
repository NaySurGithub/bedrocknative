package net.bedrocknative.ui;

public final class PaperDoll {
   public static final String LIVE_PLAYER = "live_player_renderer";
   private static final float HALF_WIDTH = 0.81666666F;
   private static final float HALF_HEIGHT = 1.1666666F;
   private static final float DROP = 0.46666667F;
   static final float STAND_HEIGHT = 0.784F;
   static final float STAND_FEET = 1.065F;

   private PaperDoll() {
   }

   public static PaperDoll.Box of(float x, float y, float w, float h) {
      float size = Math.max(0.0F, h);
      float centreX = x + w / 2.0F;
      float centreY = y + h / 2.0F + size * 0.46666667F;
      return new PaperDoll.Box(centreX - size * 0.81666666F, centreY - size * 1.1666666F, centreX + size * 0.81666666F, centreY + size * 1.1666666F, size);
   }

   public static PaperDoll.Stand stand(float x, float y, float w, float h) {
      float size = Math.max(0.0F, h);
      return new PaperDoll.Stand(x + w / 2.0F, y + size * 1.065F, size * 0.784F);
   }

   public record Box(float x0, float y0, float x1, float y1, float scale) {
   }

   public record Stand(float centreX, float feetY, float height) {
   }
}
