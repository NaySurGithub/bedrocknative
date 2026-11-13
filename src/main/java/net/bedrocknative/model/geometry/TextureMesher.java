package net.bedrocknative.model.geometry;

import net.bedrocknative.BedrockNative;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class TextureMesher {
   static final int OPAQUE_ALPHA = 140;
   static final int MAX_SIDE_QUADS = 65536;

   private TextureMesher() {
   }

   static float[] anchor(float[] position, float[] bonePivot) {
      return new float[]{position[0], 2.0F * bonePivot[1] - position[1], position[2]};
   }

   static void mesh(
      GeometryMesher.QuadSink quads, BoneDef bone, TextureMeshDef mesh, TextureMesher.Pixels image, float textureWidth, float textureHeight, float[] bounds
   ) {
      int width = image.width();
      int height = image.height();
      if (width > 0 && height > 0) {
         float kx = textureWidth / width * mesh.scale()[0];
         float kz = textureHeight / height * mesh.scale()[2];
         float depth = (mesh.usePixelDepth() ? 1.0F : textureWidth / width) * mesh.scale()[1];
         float[] lp = mesh.localPivot();
         float[] anchor = anchor(mesh.position(), bone.pivot());
         Matrix4f place = new Matrix4f().translate(-anchor[0], anchor[1], anchor[2]);
         GeometryMesher.rotateJ(place, mesh.rotation());
         if (sideQuads(image) > 65536) {
            BedrockNative.LOGGER.warn("texture_meshes {}: the sprite needs more than {} side quads, left out", mesh.texture(), 65536);
         } else {
            TextureMesher.Placer placer = new TextureMesher.Placer(place, kx, kz, depth, lp, quads, bounds);
            placer.face(0.0F, 0.0F, width, height, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
            placer.face(0.0F, 0.0F, width, height, depth, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);

            for (int v = 0; v < height; v++) {
               for (int u = 0; u < width; u++) {
                  if (image.opaque(u, v)) {
                     float u0 = (float)u / width;
                     float u1 = (float)(u + 1) / width;
                     float v0 = (float)v / height;
                     float v1 = (float)(v + 1) / height;
                     if (!image.opaque(u - 1, v)) {
                        placer.side(u, v, u, v + 1, -1.0F, 0.0F, u0, v0, u1, v1);
                     }

                     if (!image.opaque(u + 1, v)) {
                        placer.side(u + 1, v, u + 1, v + 1, 1.0F, 0.0F, u0, v0, u1, v1);
                     }

                     if (!image.opaque(u, v - 1)) {
                        placer.side(u, v, u + 1, v, 0.0F, -1.0F, u0, v0, u1, v1);
                     }

                     if (!image.opaque(u, v + 1)) {
                        placer.side(u, v + 1, u + 1, v + 1, 0.0F, 1.0F, u0, v0, u1, v1);
                     }
                  }
               }
            }
         }
      }
   }

   private static int sideQuads(TextureMesher.Pixels image) {
      int count = 0;

      for (int v = 0; v < image.height(); v++) {
         for (int u = 0; u < image.width(); u++) {
            if (image.opaque(u, v)) {
               count += (image.opaque(u - 1, v) ? 0 : 1)
                  + (image.opaque(u + 1, v) ? 0 : 1)
                  + (image.opaque(u, v - 1) ? 0 : 1)
                  + (image.opaque(u, v + 1) ? 0 : 1);
            }
         }
      }

      return count;
   }

   public record Pixels(int width, int height, int[] argb) {
      boolean opaque(int u, int v) {
         return u >= 0 && v >= 0 && u < this.width && v < this.height && this.argb[v * this.width + u] >>> 24 > 140;
      }
   }

   private static final class Placer {
      private final Matrix4f place;
      private final float kx;
      private final float kz;
      private final float depth;
      private final float[] lp;
      private final GeometryMesher.QuadSink quads;
      private final float[] bounds;
      private final Vector3f[] corners = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
      private final float[] uvs = new float[8];
      private final Vector3f normal = new Vector3f();
      private final Vector3f edge1 = new Vector3f();
      private final Vector3f edge2 = new Vector3f();

      Placer(Matrix4f place, float kx, float kz, float depth, float[] lp, GeometryMesher.QuadSink quads, float[] bounds) {
         this.place = place;
         this.kx = kx;
         this.kz = kz;
         this.depth = depth;
         this.lp = lp;
         this.quads = quads;
         this.bounds = bounds;
      }

      private Vector3f point(Vector3f into, float u, float v, float t) {
         return this.place.transformPosition(into.set(-(u * this.kx - this.lp[0]), this.lp[1] - t, v * this.kz - this.lp[2]));
      }

      void face(float u0, float v0, float u1, float v1, float t, float nx, float ny, float nz, float uv0, float vv0, float uv1, float vv1) {
         this.point(this.corners[0], u0, v0, t);
         this.point(this.corners[1], u1, v0, t);
         this.point(this.corners[2], u1, v1, t);
         this.point(this.corners[3], u0, v1, t);
         this.uv(0, uv0, vv0);
         this.uv(1, uv1, vv0);
         this.uv(2, uv1, vv1);
         this.uv(3, uv0, vv1);
         this.emit(nx, ny, nz);
      }

      void side(float ua, float va, float ub, float vb, float nx, float nz, float u0, float v0, float u1, float v1) {
         this.point(this.corners[0], ua, va, 0.0F);
         this.point(this.corners[1], ub, vb, 0.0F);
         this.point(this.corners[2], ub, vb, this.depth);
         this.point(this.corners[3], ua, va, this.depth);
         this.uv(0, u0, v0);
         this.uv(1, u1, v0);
         this.uv(2, u1, v1);
         this.uv(3, u0, v1);
         this.emit(nx, 0.0F, nz);
      }

      private void uv(int corner, float u, float v) {
         this.uvs[corner * 2] = u;
         this.uvs[corner * 2 + 1] = v;
      }

      private void emit(float nx, float ny, float nz) {
         this.place.transformDirection(this.normal.set(-nx, ny, nz)).normalize();
         this.corners[1].sub(this.corners[0], this.edge1);
         this.corners[2].sub(this.corners[0], this.edge2);
         boolean counterClockwise = this.edge1.cross(this.edge2).dot(this.normal) >= 0.0F;
         float[] quad = new float[32];

         for (int i = 0; i < 4; i++) {
            int corner = counterClockwise ? i : 3 - i;
            Vector3f p = this.corners[corner];
            int o = i * 8;
            quad[o] = p.x;
            quad[o + 1] = p.y;
            quad[o + 2] = p.z;
            quad[o + 3] = this.uvs[corner * 2];
            quad[o + 4] = this.uvs[corner * 2 + 1];
            quad[o + 5] = this.normal.x;
            quad[o + 6] = this.normal.y;
            quad[o + 7] = this.normal.z;
            GeometryMesher.include(this.bounds, p.x, p.y, p.z);
         }

         this.quads.quad(quad);
      }
   }
}
