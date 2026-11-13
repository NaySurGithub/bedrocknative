package net.bedrocknative.particle;

public final class ParticleCamera {
   public double x;
   public double y;
   public double z;
   public float rightX = 1.0F;
   public float rightY;
   public float rightZ;
   public float upX;
   public float upY = 1.0F;
   public float upZ;
   public float forwardX;
   public float forwardY;
   public float forwardZ = -1.0F;

   public void set(double x, double y, double z, float forwardX, float forwardY, float forwardZ, float upX, float upY, float upZ) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.forwardX = forwardX;
      this.forwardY = forwardY;
      this.forwardZ = forwardZ;
      this.upX = upX;
      this.upY = upY;
      this.upZ = upZ;
      this.rightX = forwardY * upZ - forwardZ * upY;
      this.rightY = forwardZ * upX - forwardX * upZ;
      this.rightZ = forwardX * upY - forwardY * upX;
      float length = (float)Math.sqrt(this.rightX * this.rightX + this.rightY * this.rightY + this.rightZ * this.rightZ);
      if (length > 1.0E-6F) {
         this.rightX /= length;
         this.rightY /= length;
         this.rightZ /= length;
      }
   }
}
