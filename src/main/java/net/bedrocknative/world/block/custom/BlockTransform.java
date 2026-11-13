package net.bedrocknative.world.block.custom;

import org.cloudburstmc.nbt.NbtMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public record BlockTransform(int rx, int ry, int rz, float sx, float sy, float sz, float tx, float ty, float tz) {
   public static final BlockTransform IDENTITY = new BlockTransform(0, 0, 0, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);

   static BlockTransform read(Object component) {
      if (component instanceof NbtMap nbt) {
         BlockTransform transform = new BlockTransform(
            quarterTurns(nbt.get("RX")),
            quarterTurns(nbt.get("RY")),
            quarterTurns(nbt.get("RZ")),
            (float)CustomBlockState.number(nbt.get("SX"), 1.0),
            (float)CustomBlockState.number(nbt.get("SY"), 1.0),
            (float)CustomBlockState.number(nbt.get("SZ"), 1.0),
            (float)CustomBlockState.number(nbt.get("TX"), 0.0),
            (float)CustomBlockState.number(nbt.get("TY"), 0.0),
            (float)CustomBlockState.number(nbt.get("TZ"), 0.0)
         );
         return transform.equals(IDENTITY) ? IDENTITY : transform;
      } else {
         return IDENTITY;
      }
   }

   private static int quarterTurns(Object value) {
      return Math.floorMod(CustomBlockState.integer(value, 0), 4);
   }

   public boolean isIdentity() {
      return this.equals(IDENTITY);
   }

   public boolean keepsCell() {
      return this.sx == 1.0F && this.sy == 1.0F && this.sz == 1.0F && this.tx == 0.0F && this.ty == 0.0F && this.tz == 0.0F;
   }

   public Matrix4f matrix() {
      return new Matrix4f()
         .translate(0.5F + this.tx, 0.5F + this.ty, 0.5F + this.tz)
         .rotateZ((float)Math.toRadians(90.0 * this.rz))
         .rotateY((float)Math.toRadians(90.0 * this.ry))
         .rotateX((float)Math.toRadians(90.0 * this.rx))
         .scale(this.sx, this.sy, this.sz)
         .translate(-0.5F, -0.5F, -0.5F);
   }

   public Vector3f apply(Vector3f point) {
      return this.matrix().transformPosition(point);
   }
}
