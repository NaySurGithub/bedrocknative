package net.bedrocknative.model.animation;

import net.bedrocknative.molang.MolangEnv;

public final class KeyframeSampler {
   public static final float EPSILON = 8.3333335E-4F;

   private KeyframeSampler() {
   }

   static int after(Keyframe[] keys, float time) {
      int low = 0;
      int high = keys.length;

      while (low < high) {
         int middle = low + high >>> 1;
         if (keys[middle].time() > time + 8.3333335E-4F) {
            high = middle;
         } else {
            low = middle + 1;
         }
      }

      return low;
   }

   public static float sample(Keyframe[] keys, float time, int axis, float current, MolangEnv env) {
      if (keys.length == 0) {
         return current;
      } else {
         env.thisValue = current;
         int after = after(keys, time);
         int before = after - 1;
         if (before < 0) {
            env.keyFrameLerpTime = 0.0F;
            return keys[0].pre().get(axis, env);
         } else {
            Keyframe previous = keys[before];
            if (after < keys.length && !(Math.abs(previous.time() - time) <= 8.3333335E-4F)) {
               Keyframe next = keys[after];
               float span = next.time() - previous.time();
               float alpha = span <= 0.0F ? 1.0F : (time - previous.time()) / span;
               env.keyFrameLerpTime = alpha;
               float p1 = previous.post().get(axis, env);
               float p2 = next.pre().get(axis, env);
               if (!previous.catmullRom() && !next.catmullRom()) {
                  return p1 + (p2 - p1) * alpha;
               } else {
                  float p0 = before > 0 && !previous.split() ? keys[before - 1].post().get(axis, env) : p1;
                  float p3 = after + 1 < keys.length && !next.split() ? keys[after + 1].pre().get(axis, env) : p2;
                  return catmullRom(p0, p1, p2, p3, alpha);
               }
            } else {
               env.keyFrameLerpTime = after >= keys.length ? 1.0F : 0.0F;
               return previous.post().get(axis, env);
            }
         }
      }
   }

   static float catmullRom(float p0, float p1, float p2, float p3, float t) {
      float t2 = t * t;
      float t3 = t2 * t;
      return 0.5F * (2.0F * p1 + (p2 - p0) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (3.0F * p1 - p0 - 3.0F * p2 + p3) * t3);
   }
}
