package net.bedrocknative.oreui;

public record OreWidget(String id, OreBox box, OreBox hit, boolean enabled, boolean focusable) {
   public static OreWidget of(String id, OreBox box) {
      return new OreWidget(id, box, box, true, true);
   }

   public static OreWidget clipped(String id, OreBox box, OreBox clip) {
      return new OreWidget(id, box, box.intersect(clip), true, true);
   }
}
