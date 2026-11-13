package net.bedrocknative.client.world;

import net.minecraft.world.phys.Vec3;

public final class PositionLerp {
   private Vec3 target = Vec3.ZERO;
   private int remaining;

   public void begin(Vec3 target, int steps) {
      this.target = target;
      this.remaining = Math.max(steps, 1);
   }

   public boolean active() {
      return this.remaining > 0;
   }

   public Vec3 target() {
      return this.target;
   }

   public int remaining() {
      return this.remaining;
   }

   public boolean heading(Vec3 point) {
      return this.remaining > 0 && this.target.equals(point);
   }

   public Vec3 next(Vec3 current) {
      if (this.remaining <= 0) {
         return current;
      } else {
         Vec3 result = current.lerp(this.target, 1.0 / this.remaining);
         this.remaining--;
         return result;
      }
   }

   public void cancel() {
      this.remaining = 0;
   }
}
