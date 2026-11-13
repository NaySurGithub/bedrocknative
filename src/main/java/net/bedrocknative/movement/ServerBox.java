package net.bedrocknative.movement;

import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public record ServerBox(float width, float height, float scale) {
   public static final ServerBox NONE = new ServerBox(Float.NaN, Float.NaN, 1.0F);
   private static final float MAX = 64.0F;

   public ServerBox with(@Nullable Float width, @Nullable Float height, @Nullable Float scale) {
      return new ServerBox(valid(width) ? width : this.width, valid(height) ? height : this.height, valid(scale) ? scale : this.scale);
   }

   public boolean known() {
      return !Float.isNaN(this.width) || !Float.isNaN(this.height);
   }

   public AABB apply(AABB java) {
      double x = (java.minX + java.maxX) / 2.0;
      double z = (java.minZ + java.maxZ) / 2.0;
      double half = Float.isNaN(this.width) ? (java.maxX - java.minX) / 2.0 : this.width * this.scale / 2.0F;
      double tall = Float.isNaN(this.height) ? java.maxY - java.minY : this.height * this.scale;
      return new AABB(x - half, java.minY, z - half, x + half, java.minY + tall, z + half);
   }

   private static boolean valid(@Nullable Float value) {
      return value != null && value >= 0.0F && value <= 64.0F;
   }
}
