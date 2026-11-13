package net.bedrocknative.particle;

import net.bedrocknative.molang.MolangActor;

public interface ParticleAnchor {
   boolean valid();

   void position(double[] var1);

   default float yaw() {
      return 0.0F;
   }

   default float scale() {
      return 1.0F;
   }

   default void bounds(double[] out) {
      out[0] = -0.3;
      out[1] = 0.0;
      out[2] = -0.3;
      out[3] = 0.3;
      out[4] = 1.8;
      out[5] = 0.3;
   }

   default MolangActor actor() {
      return MolangActor.NONE;
   }
}
