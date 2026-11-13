package net.bedrocknative.particle;

import java.util.SplittableRandom;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

final class ParticleShapes {
   private ParticleShapes() {
   }

   static void sample(ParticleEffect effect, MolangEnv env, SplittableRandom random, @Nullable ParticleAnchor anchor, float[] out, double[] scratch) {
      ParticleEffect.Emitter emitter = effect.emitter();
      MolangProgram[] offset = emitter.offset();
      float ox = offset[0].evaluate(env);
      float oy = offset[1].evaluate(env);
      float oz = offset[2].evaluate(env);
      float px = 0.0F;
      float py = 0.0F;
      float pz = 0.0F;
      float cx = 0.0F;
      float cy = 0.0F;
      float cz = 0.0F;
      boolean surface = emitter.surfaceOnly();
      switch (emitter.shape()) {
         case SPHERE:
            float radius = emitter.radius().evaluate(env);
            float z = (float)(random.nextDouble() * 2.0 - 1.0);
            float angle = (float)(random.nextDouble() * Math.PI * 2.0);
            float ring = (float)Math.sqrt(Math.max(0.0F, 1.0F - z * z));
            float distance = surface ? radius : radius * (float)Math.cbrt(random.nextDouble());
            px = ring * (float)Math.cos(angle) * distance;
            py = ring * (float)Math.sin(angle) * distance;
            pz = z * distance;
            break;
         case BOX:
            MolangProgram[] half = emitter.halfDimensions();
            float hx = half[0].evaluate(env);
            float hy = half[1].evaluate(env);
            float hz = half[2].evaluate(env);
            box(random, -hx, -hy, -hz, hx, hy, hz, surface, out);
            px = out[0];
            py = out[1];
            pz = out[2];
            break;
         case DISC:
            MolangProgram[] normal = emitter.planeNormal();
            float nx = normal[0].evaluate(env);
            float ny = normal[1].evaluate(env);
            float nz = normal[2].evaluate(env);
            float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1.0E-6F) {
               nx = 0.0F;
               ny = 1.0F;
               nz = 0.0F;
            } else {
               nx /= length;
               ny /= length;
               nz /= length;
            }

            float ax;
            float ay;
            float az;
            if (Math.abs(ny) < 0.99F) {
               ax = nz;
               ay = 0.0F;
               az = -nx;
            } else {
               ax = 0.0F;
               ay = -nz;
               az = ny;
            }

            float axisLength = (float)Math.sqrt(ax * ax + ay * ay + az * az);
            ax /= axisLength;
            ay /= axisLength;
            az /= axisLength;
            float bx = ny * az - nz * ay;
            float by = nz * ax - nx * az;
            float bz = nx * ay - ny * ax;
            float radius = emitter.radius().evaluate(env);
            float angle = (float)(random.nextDouble() * Math.PI * 2.0);
            float distance = surface ? radius : radius * (float)Math.sqrt(random.nextDouble());
            float cos = (float)Math.cos(angle) * distance;
            float sin = (float)Math.sin(angle) * distance;
            px = ax * cos + bx * sin;
            py = ay * cos + by * sin;
            pz = az * cos + bz * sin;
            break;
         case ENTITY_AABB:
            if (anchor != null) {
               anchor.bounds(scratch);
               box(random, (float)scratch[0], (float)scratch[1], (float)scratch[2], (float)scratch[3], (float)scratch[4], (float)scratch[5], surface, out);
               px = out[0];
               py = out[1];
               pz = out[2];
               cx = (float)(scratch[0] + scratch[3]) / 2.0F;
               cy = (float)(scratch[1] + scratch[4]) / 2.0F;
               cz = (float)(scratch[2] + scratch[5]) / 2.0F;
            }
      }

      float dx;
      float dy;
      float dz;
      if (emitter.direction() == ParticleEffect.Direction.CUSTOM) {
         MolangProgram[] direction = emitter.customDirection();
         dx = direction[0].evaluate(env);
         dy = direction[1].evaluate(env);
         dz = direction[2].evaluate(env);
      } else {
         boolean point = emitter.shape() == ParticleEffect.Shape.POINT || emitter.shape() == ParticleEffect.Shape.CUSTOM;
         dx = point ? ox : px - cx;
         dy = point ? oy : py - cy;
         dz = point ? oz : pz - cz;
         if (emitter.direction() == ParticleEffect.Direction.INWARDS) {
            dx = -dx;
            dy = -dy;
            dz = -dz;
         }
      }

      float lengthx = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (lengthx > 1.0E-6F) {
         dx /= lengthx;
         dy /= lengthx;
         dz /= lengthx;
      }

      out[0] = ox + px;
      out[1] = oy + py;
      out[2] = oz + pz;
      out[3] = dx;
      out[4] = dy;
      out[5] = dz;
   }

   private static void box(SplittableRandom random, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, boolean surface, float[] out) {
      float x = minX + (float)random.nextDouble() * (maxX - minX);
      float y = minY + (float)random.nextDouble() * (maxY - minY);
      float z = minZ + (float)random.nextDouble() * (maxZ - minZ);
      if (surface) {
         float sx = maxX - minX;
         float sy = maxY - minY;
         float sz = maxZ - minZ;
         float xy = sx * sy;
         float xz = sx * sz;
         float yz = sy * sz;
         float roll = (float)random.nextDouble() * (xy + xz + yz);
         boolean high = random.nextBoolean();
         if (roll < xy) {
            z = high ? maxZ : minZ;
         } else if (roll < xy + xz) {
            y = high ? maxY : minY;
         } else {
            x = high ? maxX : minX;
         }
      }

      out[0] = x;
      out[1] = y;
      out[2] = z;
   }
}
