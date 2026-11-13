package net.bedrocknative.ui;

public final class UiScale {
   public static final int MIN_WIDTH = 320;
   public static final int MIN_HEIGHT = 240;

   private UiScale() {
   }

   public static int base(int width, int height) {
      return Math.max(1, Math.min(width / 320, height / 240));
   }

   public static int scale(int width, int height, int modifier, boolean accessibility) {
      int base = base(width, height);
      int smallest = Math.max(1, (base + 1) / 2);
      int scale = Math.max(smallest, Math.min(base, base + modifier));
      return accessibility ? scale + 1 : scale;
   }

   public static int followingJava(int javaOption, int width, int height, int bedrockScale) {
      return javaOption <= 0 ? bedrockScale : Math.max(1, Math.min(javaOption, base(width, height)));
   }
}
