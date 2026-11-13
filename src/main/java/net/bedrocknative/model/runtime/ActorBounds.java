package net.bedrocknative.model.runtime;

import net.bedrocknative.model.geometry.MeshedGeometry;

public final class ActorBounds {
   private ActorBounds() {
   }

   public static float[] local(MeshedGeometry mesh) {
      float[] visible = mesh.visibleBounds();
      if (visible != null && visible[0] > 0.0F && visible[1] > 0.0F) {
         float halfWidth = visible[0] / 2.0F;
         float halfHeight = visible[1] / 2.0F;
         float x = -visible[2];
         float y = visible[3];
         float z = visible[4];
         return new float[]{x - halfWidth, y - halfHeight, z - halfWidth, x + halfWidth, y + halfHeight, z + halfWidth};
      }

      float[] cubes = mesh.cubeBounds();
      float[] box = new float[6];

      for (int i = 0; i < 6; i++) {
         box[i] = cubes[i] / 16.0F;
      }

      return box;
   }

   public static void world(float[] local, double x, double y, double z, float bodyYaw, float scaleX, float scaleY, float scaleZ, double[] out) {
      float minX = local[0] * Math.abs(scaleX);
      float maxX = local[3] * Math.abs(scaleX);
      float minZ = local[2] * Math.abs(scaleZ);
      float maxZ = local[5] * Math.abs(scaleZ);
      double angle = Math.toRadians(180.0 - bodyYaw);
      double cos = Math.cos(angle);
      double sin = Math.sin(angle);
      double lowX = Double.MAX_VALUE;
      double lowZ = Double.MAX_VALUE;
      double highX = -Double.MAX_VALUE;
      double highZ = -Double.MAX_VALUE;

      for (int corner = 0; corner < 4; corner++) {
         float cx = (corner & 1) == 0 ? minX : maxX;
         float cz = (corner & 2) == 0 ? minZ : maxZ;
         double rx = cx * cos + cz * sin;
         double rz = -cx * sin + cz * cos;
         lowX = Math.min(lowX, rx);
         highX = Math.max(highX, rx);
         lowZ = Math.min(lowZ, rz);
         highZ = Math.max(highZ, rz);
      }

      out[0] = x + lowX;
      out[1] = y + local[1] * Math.abs(scaleY);
      out[2] = z + lowZ;
      out[3] = x + highX;
      out[4] = y + local[4] * Math.abs(scaleY);
      out[5] = z + highZ;
   }
}
