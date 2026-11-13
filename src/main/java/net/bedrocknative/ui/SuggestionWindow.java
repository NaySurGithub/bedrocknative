package net.bedrocknative.ui;

public final class SuggestionWindow {
   public static final int WHEEL_ROWS = 3;
   private final int size;
   private int rows;
   private int offset;
   private int selected;

   public SuggestionWindow(int size, int rows, int offset, int selected) {
      this.size = Math.max(0, size);
      this.rows = Math.max(1, rows);
      this.selected = this.size == 0 ? 0 : Math.max(0, Math.min(selected, this.size - 1));
      this.offset = offset;
      this.follow();
   }

   public static int rows(double available, double rowHeight) {
      return rowHeight > 0.0 && available > 0.0 ? Math.max(1, (int)Math.floor(available / rowHeight + 1.0E-6)) : 1;
   }

   public int size() {
      return this.size;
   }

   public int rows() {
      return this.rows;
   }

   public int offset() {
      return this.offset;
   }

   public int selected() {
      return this.selected;
   }

   public int visible() {
      return Math.min(this.size, this.rows);
   }

   public int above() {
      return this.offset;
   }

   public int below() {
      return Math.max(0, this.size - this.offset - this.visible());
   }

   public boolean scrolls() {
      return this.size > this.rows;
   }

   public int maxOffset() {
      return Math.max(0, this.size - this.rows);
   }

   public void select(int index) {
      if (this.size != 0) {
         this.selected = Math.floorMod(index, this.size);
         this.follow();
      }
   }

   public void move(int delta) {
      this.select(this.selected + delta);
   }

   public void page(int pages) {
      if (this.size != 0 && pages != 0) {
         this.selected = Math.max(0, Math.min(this.size - 1, this.selected + pages * this.rows));
         this.follow();
      }
   }

   public static int wheelLines(double notches) {
      return notches != 0.0 && !Double.isNaN(notches) ? -((int)Math.signum(notches)) * 3 : 0;
   }

   public void scroll(int lines) {
      if (this.size != 0) {
         this.offset = clamp(this.offset + lines, 0, this.maxOffset());
         if (this.selected < this.offset) {
            this.selected = this.offset;
         } else if (this.selected > this.offset + this.visible() - 1) {
            this.selected = this.offset + this.visible() - 1;
         }
      }
   }

   public void resize(int newRows) {
      this.rows = Math.max(1, newRows);
      this.follow();
   }

   public boolean shows(int index) {
      return index >= this.offset && index < this.offset + this.visible();
   }

   public float[] thumb(float track, float minHeight) {
      if (this.scrolls() && !(track <= 0.0F)) {
         float height = Math.min(track, Math.max(minHeight, track * this.rows / this.size));
         float top = this.maxOffset() == 0 ? 0.0F : (track - height) * this.offset / this.maxOffset();
         return new float[]{top, height};
      } else {
         return new float[]{0.0F, Math.max(0.0F, track)};
      }
   }

   private void follow() {
      if (this.selected < this.offset) {
         this.offset = this.selected;
      } else if (this.selected > this.offset + this.rows - 1) {
         this.offset = this.selected - this.rows + 1;
      }

      this.offset = clamp(this.offset, 0, this.maxOffset());
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}
