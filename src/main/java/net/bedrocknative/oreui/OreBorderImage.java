package net.bedrocknative.oreui;

public record OreBorderImage(
   String sprite, int sliceTop, int sliceRight, int sliceBottom, int sliceLeft, boolean fill, float[] widths, float[] outset, float[] borders
) {
   public OreBox area(OreBox box, OreScale scale) {
      return new OreBox(
         box.x() - scale.px(this.outset[3]),
         box.y() - scale.px(this.outset[0]),
         box.w() + scale.px(this.outset[1] + this.outset[3]),
         box.h() + scale.px(this.outset[0] + this.outset[2])
      );
   }

   public OreBox content(OreBox box, OreScale scale) {
      return box.inset(scale.px(this.borders[3]), scale.px(this.borders[0]), scale.px(this.borders[1]), scale.px(this.borders[2]));
   }
}
