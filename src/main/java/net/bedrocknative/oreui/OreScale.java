package net.bedrocknative.oreui;

public record OreScale(int guiScale) {
   public static final float TABLET_REM = 70.0F;
   public static final float DESKTOP_REM = 128.0F;

   public OreScale(int guiScale) {
      guiScale = Math.max(1, guiScale);
      this.guiScale = guiScale;
   }

   public float remPx() {
      return 5.0F * this.guiScale;
   }

   public float px(float rem) {
      return rem * 5.0F * this.guiScale;
   }

   public float b2() {
      return this.guiScale;
   }

   public float b1() {
      return Math.max((float)Math.floor(this.guiScale / 2.0F), 0.5F);
   }

   public float minBorder() {
      return Math.max(this.b1(), 1.0F);
   }

   public float widthRem(float windowWidth) {
      return windowWidth / this.remPx();
   }

   public OreScale.Breakpoint breakpoint(float windowWidth) {
      float rem = this.widthRem(windowWidth);
      return rem < 70.0F ? OreScale.Breakpoint.NARROW : (rem < 128.0F ? OreScale.Breakpoint.TABLET : OreScale.Breakpoint.DESKTOP);
   }

   public static enum Breakpoint {
      NARROW,
      TABLET,
      DESKTOP;
   }
}
