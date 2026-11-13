package net.bedrocknative.oreui;

public record OreBox(float x, float y, float w, float h) {
   public static final OreBox EMPTY = new OreBox(0.0F, 0.0F, 0.0F, 0.0F);

   public float right() {
      return this.x + this.w;
   }

   public float bottom() {
      return this.y + this.h;
   }

   public float centerX() {
      return this.x + this.w / 2.0F;
   }

   public float centerY() {
      return this.y + this.h / 2.0F;
   }

   public boolean empty() {
      return this.w <= 0.0F || this.h <= 0.0F;
   }

   public boolean contains(double px, double py) {
      return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
   }

   public boolean overlaps(OreBox other) {
      return this.x < other.right() && other.x < this.right() && this.y < other.bottom() && other.y < this.bottom();
   }

   public OreBox inset(float left, float top, float right, float bottom) {
      return new OreBox(this.x + left, this.y + top, Math.max(0.0F, this.w - left - right), Math.max(0.0F, this.h - top - bottom));
   }

   public OreBox inset(float all) {
      return this.inset(all, all, all, all);
   }

   public OreBox outset(float all) {
      return new OreBox(this.x - all, this.y - all, this.w + 2.0F * all, this.h + 2.0F * all);
   }

   public OreBox offset(float dx, float dy) {
      return new OreBox(this.x + dx, this.y + dy, this.w, this.h);
   }

   public OreBox withY(float newY) {
      return new OreBox(this.x, newY, this.w, this.h);
   }

   public OreBox withHeight(float newHeight) {
      return new OreBox(this.x, this.y, this.w, newHeight);
   }

   public OreBox intersect(OreBox clip) {
      float x0 = Math.max(this.x, clip.x);
      float y0 = Math.max(this.y, clip.y);
      float x1 = Math.min(this.right(), clip.right());
      float y1 = Math.min(this.bottom(), clip.bottom());
      return !(x1 <= x0) && !(y1 <= y0) ? new OreBox(x0, y0, x1 - x0, y1 - y0) : new OreBox(x0, y0, 0.0F, 0.0F);
   }

   public int px0() {
      return Math.round(this.x);
   }

   public int py0() {
      return Math.round(this.y);
   }

   public int px1() {
      return Math.round(this.x + this.w);
   }

   public int py1() {
      return Math.round(this.y + this.h);
   }

   @Override
   public String toString() {
      return "x " + this.px0() + ".." + (this.px1() - 1) + ", y " + this.py0() + ".." + (this.py1() - 1);
   }
}
