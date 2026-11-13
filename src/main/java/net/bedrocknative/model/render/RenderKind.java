package net.bedrocknative.model.render;

public enum RenderKind {
   SOLID,
   CUTOUT,
   CUTOUT_CULL,
   TRANSLUCENT,
   TRANSLUCENT_CULL,
   EMISSIVE_ONLY,
   GLINT_CUTOUT,
   GLINT_SOLID,
   ADDITIVE,
   DEPTH_ONLY,
   SEE_THROUGH,
   SEE_THROUGH_CULL;

   public static RenderKind of(MaterialTable.MaterialInfo material) {
      return of(material, false);
   }

   public static RenderKind of(MaterialTable.MaterialInfo material, boolean polish) {
      if (polish && material.depthOnly()) {
         return DEPTH_ONLY;
      }

      if (material.throughWalls()) {
         return material.disableCulling() ? SEE_THROUGH : SEE_THROUGH_CULL;
      }

      if (material.onlyEmissive()) {
         return EMISSIVE_ONLY;
      }

      if (!material.glint()) {
         if (polish && material.additive()) {
            return ADDITIVE;
         } else if (material.blending()) {
            return material.disableCulling() ? TRANSLUCENT : TRANSLUCENT_CULL;
         } else if (material.alphaTest()) {
            return material.disableCulling() ? CUTOUT : CUTOUT_CULL;
         } else {
            return material.disableCulling() ? CUTOUT : SOLID;
         }
      } else {
         return !material.alphaTest() && !material.disableCulling() ? GLINT_SOLID : GLINT_CUTOUT;
      }
   }

   public static boolean overlay(MaterialTable.MaterialInfo material) {
      return material.overlay();
   }

   public boolean blended() {
      return this == TRANSLUCENT || this == TRANSLUCENT_CULL || this == ADDITIVE || this == EMISSIVE_ONLY || this == SEE_THROUGH || this == SEE_THROUGH_CULL;
   }

   public RenderKind unblended() {
      return this == TRANSLUCENT ? CUTOUT : (this == TRANSLUCENT_CULL ? CUTOUT_CULL : this);
   }

   public static boolean partlyTransparent(int alpha) {
      return alpha >= 25 && alpha < 255;
   }
}
