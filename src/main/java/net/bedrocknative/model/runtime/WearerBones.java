package net.bedrocknative.model.runtime;

import net.bedrocknative.model.geometry.MeshedGeometry;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public interface WearerBones {
   float[] BOUND_POINT = new float[]{0.0F, 24.0F, 0.0F};

   @Nullable Matrix4f bone(String var1);

   default boolean has(String name) {
      return this.bone(name) != null;
   }

   default float @Nullable [] pivot(String name) {
      return null;
   }

   default @Nullable Matrix4f bind(String name, MeshedGeometry.Bone root, Matrix4f out) {
      Matrix4f world = this.bone(name);
      if (world == null) {
         return null;
      }

      out.set(world);
      float[] wearerPivot = this.pivot(name);
      if (wearerPivot != null) {
         float[] attach = attachPoint(root);
         out.translate(wearerPivot[0] - attach[0], wearerPivot[1] - attach[1], wearerPivot[2] - attach[2]);
      }

      return out;
   }

   static float[] attachPoint(MeshedGeometry.Bone root) {
      return root.binding() != null ? BOUND_POINT : root.pivot();
   }
}
