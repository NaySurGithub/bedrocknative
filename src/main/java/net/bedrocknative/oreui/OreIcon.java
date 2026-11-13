package net.bedrocknative.oreui;

import org.jspecify.annotations.Nullable;

public record OreIcon(String sprite, float sourceWidth, float sourceHeight, float sourceScale, float canvas, boolean mask) {
   public static final OreIcon PLUS = new OreIcon("plus@1x.icon", 24.0F, 24.0F, 1.0F, 12.0F, true);
   public static final OreIcon GRID = new OreIcon("grid@0.5x.icon", 6.0F, 6.0F, 0.5F, 12.0F, true);
   public static final OreIcon LIST = new OreIcon("list@0.5x.icon", 8.0F, 7.0F, 0.5F, 12.0F, true);
   public static final OreIcon EDIT = new OreIcon("edit@0.5x.icon", 12.0F, 12.0F, 0.5F, 12.0F, true);
   public static final OreIcon FRIENDS = new OreIcon("friends@0.5x.icon", 12.0F, 12.0F, 0.5F, 12.0F, true);
   public static final OreIcon DELETE = new OreIcon("delete", 24.0F, 24.0F, 1.0F, 24.0F, false);
   public static final OreIcon SAVE = new OreIcon("save", 12.0F, 12.0F, 1.0F, 12.0F, false);
   public static final OreIcon IMPORT_WORLD = new OreIcon("import-world-icon", 24.0F, 24.0F, 1.0F, 24.0F, false);

   public float elementRem(@Nullable Float widthTenths) {
      return !this.mask ? 2.4F : (widthTenths != null ? widthTenths : this.canvas / this.sourceScale) / 10.0F;
   }

   public float maskWidthRem(float elementRem) {
      return this.mask ? elementRem * this.sourceWidth / this.canvas : elementRem;
   }

   public float maskHeightRem(float elementRem) {
      return this.mask ? elementRem * this.sourceHeight / this.canvas : elementRem;
   }
}
