package net.bedrocknative.text;

public final class NameTagLayout {
   public static final int LINE_HEIGHT = 10;
   public static final double JAVA_PIXEL = 0.025;
   public static final float SCALE = 1.1312F;
   public static final double CENTER_ABOVE_BOX = 0.6;
   public static final double JAVA_LIFT = 0.5;
   public static final float TEXT_ONLY_SCALE = 0.1F;

   private NameTagLayout() {
   }

   public static boolean textOnly(float scale, boolean hasName, boolean alwaysShown) {
      return scale < 0.1F && hasName && alwaysShown;
   }

   public static int firstLineTop(int lines) {
      return 1 - lines * 10 / 2;
   }

   public static double attachmentY(double boxHeight) {
      return (boxHeight + 0.6) / 1.1312F - 0.5;
   }

   public static int[] padding(int width, int widest) {
      int extra = Math.max(0, widest - width);
      return new int[]{extra / 2, extra - extra / 2};
   }

   public static double boxHeight(float height, float scale) {
      return height * scale;
   }
}
