package net.bedrocknative.particle;

import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;

final class ParticleBillboards {
   private ParticleBillboards() {
   }

   static void draw(
      ParticleEffect effect,
      ParticleEffect.Billboard billboard,
      ParticleInstance particle,
      MolangEnv env,
      ParticleCamera camera,
      double wx,
      double wy,
      double wz,
      float age,
      float emitterYaw,
      float partialTick,
      QuadSink sink
   ) {
      float width = billboard.size()[0].evaluate(env);
      float height = billboard.size()[1].evaluate(env);
      if (width != 0.0F || height != 0.0F) {
         float textureWidth = billboard.textureWidth();
         float textureHeight = billboard.textureHeight();
         ParticleEffect.Flipbook flipbook = billboard.flipbook();
         float u;
         float v;
         float sizeU;
         float sizeV;
         if (flipbook != null) {
            int frames = Math.max(1, (int)flipbook.maxFrame().evaluate(env));
            int frame;
            if (flipbook.stretchToLifetime() && particle.lifetime > 0.0F && !Float.isInfinite(particle.lifetime)) {
               frame = (int)(age / particle.lifetime * frames);
            } else {
               frame = (int)(age * flipbook.framesPerSecond().evaluate(env));
            }

            frame = flipbook.loop() ? Math.floorMod(frame, frames) : Math.max(0, Math.min(frame, frames - 1));
            u = flipbook.baseUv()[0].evaluate(env) + flipbook.stepUv()[0] * frame;
            v = flipbook.baseUv()[1].evaluate(env) + flipbook.stepUv()[1] * frame;
            sizeU = flipbook.sizeUv()[0];
            sizeV = flipbook.sizeUv()[1];
         } else {
            u = billboard.uv()[0].evaluate(env);
            v = billboard.uv()[1].evaluate(env);
            sizeU = billboard.uvSize()[0].evaluate(env);
            sizeV = billboard.uvSize()[1].evaluate(env);
         }

         float u0 = u / textureWidth;
         float v0 = v / textureHeight;
         float u1 = (u + sizeU) / textureWidth;
         float v1 = (v + sizeV) / textureHeight;
         ParticleEffect.Tint tint = effect.particle().tint();
         int argb = tint == null ? -1 : color(tint, env);
         if (argb >>> 24 != 0 || effect.material() == ParticleEffect.Material.ADD) {
            float fx = (float)(camera.x - wx);
            float fy = (float)(camera.y - wy);
            float fz = (float)(camera.z - wz);
            float dx = particle.dirX;
            float dy = particle.dirY;
            float dz = particle.dirZ;
            if (billboard.customDirection()) {
               dx = billboard.direction()[0].evaluate(env);
               dy = billboard.direction()[1].evaluate(env);
               dz = billboard.direction()[2].evaluate(env);
            }

            float directionLength = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (directionLength > 1.0E-6F) {
               dx /= directionLength;
               dy /= directionLength;
               dz /= directionLength;
            } else {
               dx = 0.0F;
               dy = 1.0F;
               dz = 0.0F;
            }

            float ry;
            float ux;
            float uy;
            float uz;
            float rx;
            float rz;
            label95:
            switch (billboard.facing()) {
               case EMITTER_TRANSFORM_XY:
               case EMITTER_TRANSFORM_XZ:
               case EMITTER_TRANSFORM_YZ:
                  float radians = (float)Math.toRadians(emitterYaw);
                  float cos = (float)Math.cos(radians);
                  float sin = (float)Math.sin(radians);
                  switch (billboard.facing()) {
                     case EMITTER_TRANSFORM_XY:
                        rx = cos;
                        ry = 0.0F;
                        rz = sin;
                        ux = 0.0F;
                        uy = 1.0F;
                        uz = 0.0F;
                        break label95;
                     case EMITTER_TRANSFORM_XZ:
                        rx = cos;
                        ry = 0.0F;
                        rz = sin;
                        ux = -sin;
                        uy = 0.0F;
                        uz = cos;
                        break label95;
                     default:
                        rx = -sin;
                        ry = 0.0F;
                        rz = cos;
                        ux = 0.0F;
                        uy = 1.0F;
                        uz = 0.0F;
                        break label95;
                  }
               case ROTATE_XYZ:
                  rx = camera.rightX;
                  ry = camera.rightY;
                  rz = camera.rightZ;
                  ux = camera.upX;
                  uy = camera.upY;
                  uz = camera.upZ;
                  break;
               case ROTATE_Y:
                  float lengthxxxx = (float)Math.sqrt(camera.rightX * camera.rightX + camera.rightZ * camera.rightZ);
                  rx = lengthxxxx > 1.0E-6F ? camera.rightX / lengthxxxx : 1.0F;
                  ry = 0.0F;
                  rz = lengthxxxx > 1.0E-6F ? camera.rightZ / lengthxxxx : 0.0F;
                  ux = 0.0F;
                  uy = 1.0F;
                  uz = 0.0F;
                  break;
               case LOOKAT_Y:
                  float lengthxxx = (float)Math.sqrt(fx * fx + fz * fz);
                  float nx = lengthxxx > 1.0E-6F ? fx / lengthxxx : 0.0F;
                  float nz = lengthxxx > 1.0E-6F ? fz / lengthxxx : 1.0F;
                  rx = nz;
                  ry = 0.0F;
                  rz = -nx;
                  ux = 0.0F;
                  uy = 1.0F;
                  uz = 0.0F;
                  break;
               case LOOKAT_DIRECTION:
                  ux = dx;
                  uy = dy;
                  uz = dz;
                  rx = uy * fz - uz * fy;
                  ry = uz * fx - ux * fz;
                  rz = ux * fy - uy * fx;
                  float lengthxx = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
                  if (lengthxx > 1.0E-6F) {
                     rx /= lengthxx;
                     ry /= lengthxx;
                     rz /= lengthxx;
                  } else {
                     rx = camera.rightX;
                     ry = camera.rightY;
                     rz = camera.rightZ;
                  }
                  break;
               case DIRECTION_X:
                  rx = dx;
                  ry = dy;
                  rz = dz;
                  float dot = dy;
                  ux = -dx * dot;
                  uy = 1.0F - dy * dot;
                  uz = -dz * dot;
                  float lengthx = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
                  if (lengthx > 1.0E-6F) {
                     ux /= lengthx;
                     uy /= lengthx;
                     uz /= lengthx;
                  } else {
                     ux = camera.upX;
                     uy = camera.upY;
                     uz = camera.upZ;
                  }
                  break;
               case DIRECTION_Y:
                  ux = dx;
                  uy = dy;
                  uz = dz;
                  rx = uy * fz - uz * fy;
                  ry = uz * fx - ux * fz;
                  rz = ux * fy - uy * fx;
                  float length = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
                  if (length > 1.0E-6F) {
                     rx /= length;
                     ry /= length;
                     rz /= length;
                  } else {
                     rx = 1.0F;
                     ry = 0.0F;
                     rz = 0.0F;
                  }
                  break;
               case DIRECTION_Z:
                  float dot2 = dy;
                  ux = -dx * dot2;
                  uy = 1.0F - dy * dot2;
                  uz = -dz * dot2;
                  float length2 = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
                  if (length2 > 1.0E-6F) {
                     ux /= length2;
                     uy /= length2;
                     uz /= length2;
                  } else {
                     ux = 0.0F;
                     uy = 0.0F;
                     uz = 1.0F;
                  }

                  rx = uy * dz - uz * dy;
                  ry = uz * dx - ux * dz;
                  rz = ux * dy - uy * dx;
                  break;
               default:
                  float length3 = (float)Math.sqrt(fx * fx + fy * fy + fz * fz);
                  if (length3 > 1.0E-6F) {
                     fx /= length3;
                     fy /= length3;
                     fz /= length3;
                  }

                  rx = fz;
                  ry = 0.0F;
                  rz = -fx;
                  float right = (float)Math.sqrt(rx * rx + rz * rz);
                  if (right > 1.0E-4F) {
                     rx /= right;
                     rz /= right;
                  } else {
                     rx = camera.rightX;
                     ry = camera.rightY;
                     rz = camera.rightZ;
                  }

                  ux = fy * rz - fz * ry;
                  uy = fz * rx - fx * rz;
                  uz = fx * ry - fy * rx;
            }

            float rotation = particle.prevRotation + (particle.rotation - particle.prevRotation) * partialTick;
            if (rotation != 0.0F) {
               float radians = (float)Math.toRadians(rotation);
               float cos = (float)Math.cos(radians);
               float sin = (float)Math.sin(radians);
               float nrx = rx * cos + ux * sin;
               float nry = ry * cos + uy * sin;
               float nrz = rz * cos + uz * sin;
               float nux = ux * cos - rx * sin;
               float nuy = uy * cos - ry * sin;
               float nuz = uz * cos - rz * sin;
               rx = nrx;
               ry = nry;
               rz = nrz;
               ux = nux;
               uy = nuy;
               uz = nuz;
            }

            sink.quad(
               (float)(wx - camera.x),
               (float)(wy - camera.y),
               (float)(wz - camera.z),
               rx * width,
               ry * width,
               rz * width,
               ux * height,
               uy * height,
               uz * height,
               u0,
               v0,
               u1,
               v1,
               argb,
               wx,
               wy,
               wz,
               effect.particle().lighting()
            );
         }
      }
   }

   static int color(ParticleEffect.Tint tint, MolangEnv env) {
      MolangProgram[] constant = tint.color();
      if (constant != null) {
         return argb(constant[0].evaluate(env), constant[1].evaluate(env), constant[2].evaluate(env), constant[3].evaluate(env));
      }

      float[] keys = tint.keys();
      MolangProgram[][] colors = tint.gradient();
      if (keys.length == 0) {
         return -1;
      }

      float t = tint.interpolant() != null ? tint.interpolant().evaluate(env) : 0.0F;
      if (keys.length != 1 && !(t <= keys[0])) {
         int last = keys.length - 1;
         if (t >= keys[last]) {
            MolangProgram[] end = colors[last];
            return argb(end[0].evaluate(env), end[1].evaluate(env), end[2].evaluate(env), end[3].evaluate(env));
         }

         int index = 0;

         while (index < last - 1 && t >= keys[index + 1]) {
            index++;
         }

         float span = keys[index + 1] - keys[index];
         float local = span > 0.0F ? (t - keys[index]) / span : 0.0F;
         MolangProgram[] from = colors[index];
         MolangProgram[] to = colors[index + 1];
         return argb(
            lerp(from[0].evaluate(env), to[0].evaluate(env), local),
            lerp(from[1].evaluate(env), to[1].evaluate(env), local),
            lerp(from[2].evaluate(env), to[2].evaluate(env), local),
            lerp(from[3].evaluate(env), to[3].evaluate(env), local)
         );
      } else {
         MolangProgram[] first = colors[0];
         return argb(first[0].evaluate(env), first[1].evaluate(env), first[2].evaluate(env), first[3].evaluate(env));
      }
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   static int argb(float r, float g, float b, float a) {
      return channel(a) << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
   }

   private static int channel(float value) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
   }
}
