package net.bedrocknative.model.entity;

public final class ActorMotion {
   public static final double TELEPORT = 8.0;
   private double x;
   private double y;
   private double z;
   private float yaw;
   private boolean started;
   private float dx;
   private float dy;
   private float dz;
   private float yawDelta;
   private float distance;
   private float walked;

   public void tick(double x, double y, double z, float bodyYaw, boolean onGround, boolean sneaking) {
      if (!this.started) {
         this.started = true;
      } else {
         double mx = x - this.x;
         double my = y - this.y;
         double mz = z - this.z;
         if (mx * mx + my * my + mz * mz > 64.0) {
            mz = 0.0;
            my = 0.0;
            mx = 0.0;
         }

         this.dx = (float)mx;
         this.dy = (float)my;
         this.dz = (float)mz;
         float turned = bodyYaw - this.yaw;
         turned -= 360.0F * (float)Math.floor((turned + 180.0F) / 360.0F);
         this.yawDelta = turned;
         float horizontal = (float)Math.sqrt(mx * mx + mz * mz);
         this.distance += horizontal;
         if (onGround && !sneaking) {
            this.walked += horizontal;
         }
      }

      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = bodyYaw;
   }

   public float positionDelta(int axis) {
      return switch (axis) {
         case 0 -> this.dx;
         case 1 -> this.dy;
         case 2 -> this.dz;
         default -> 0.0F;
      };
   }

   public float groundSpeed() {
      return (float)Math.sqrt(this.dx * this.dx + this.dz * this.dz) * 20.0F;
   }

   public float verticalSpeed() {
      return this.dy * 20.0F;
   }

   public float yawSpeed() {
      return this.yawDelta * 20.0F;
   }

   public float distanceMoved() {
      return this.distance;
   }

   public float walkDistance() {
      return this.walked;
   }

   public boolean moving() {
      return this.dx != 0.0F || this.dy != 0.0F || this.dz != 0.0F;
   }
}
