package net.bedrocknative.particle;

import net.bedrocknative.molang.VariableStore;
import org.jspecify.annotations.Nullable;

final class ParticleInstance {
   double x;
   double y;
   double z;
   double prevX;
   double prevY;
   double prevZ;
   float vx;
   float vy;
   float vz;
   float dirX;
   float dirY;
   float dirZ;
   float rotation;
   float prevRotation;
   float rotationRate;
   float age;
   float lifetime;
   float random1;
   float random2;
   float random3;
   float random4;
   int timelineIndex;
   float killSide;
   @Nullable
   VariableStore variables;

   void reset() {
      this.x = this.y = this.z = this.prevX = this.prevY = this.prevZ = 0.0;
      this.vx = this.vy = this.vz = 0.0F;
      this.dirX = this.dirY = this.dirZ = 0.0F;
      this.rotation = this.prevRotation = this.rotationRate = 0.0F;
      this.age = 0.0F;
      this.lifetime = Float.POSITIVE_INFINITY;
      this.timelineIndex = 0;
      this.killSide = 0.0F;
      if (this.variables != null) {
         this.variables.clear();
      }
   }
}
