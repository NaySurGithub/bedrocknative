package net.bedrocknative.model.animation;

import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;

public record StateVariable(String name, MolangProgram input, float[] keys, float[] values) {
   public float evaluate(MolangEnv env) {
      return this.remap(this.input.evaluate(env));
   }

   public float remap(float x) {
      if (this.keys.length == 0) {
         return x;
      } else if (x <= this.keys[0]) {
         return this.values[0];
      } else {
         int last = this.keys.length - 1;
         if (x >= this.keys[last]) {
            return this.values[last];
         } else {
            for (int i = 1; i <= last; i++) {
               if (x <= this.keys[i]) {
                  float span = this.keys[i] - this.keys[i - 1];
                  float t = span <= 0.0F ? 1.0F : (x - this.keys[i - 1]) / span;
                  return this.values[i - 1] + (this.values[i] - this.values[i - 1]) * t;
               }
            }

            return this.values[last];
         }
      }
   }
}
