package net.bedrocknative.world.block.custom;

import java.util.Locale;
import org.cloudburstmc.nbt.NbtMap;
import org.jspecify.annotations.Nullable;

public record BlockMaterial(
   String texture,
   BlockMaterial.RenderMethod renderMethod,
   boolean faceDimming,
   boolean ambientOcclusion,
   BlockMaterial.TintMethod tint,
   @Nullable String unknownRenderMethod
) {
   public static final BlockMaterial DEFAULT = new BlockMaterial("", BlockMaterial.RenderMethod.OPAQUE, true, true, BlockMaterial.TintMethod.NONE);

   public BlockMaterial(String texture, BlockMaterial.RenderMethod renderMethod, boolean faceDimming, boolean ambientOcclusion, BlockMaterial.TintMethod tint) {
      this(texture, renderMethod, faceDimming, ambientOcclusion, tint, null);
   }

   static BlockMaterial read(NbtMap nbt) {
      Object packed = nbt.get("packed_bools");
      Object dimming = nbt.get("face_dimming");
      boolean faceDimming = packed != null ? (CustomBlockState.integer(packed, 1) & 1) != 0 : dimming == null || CustomBlockState.flag(dimming);
      Object occlusion = nbt.get("ambient_occlusion");
      boolean ambientOcclusion = occlusion == null || CustomBlockState.number(occlusion, 1.0) > 0.0;
      String method = nbt.getString("render_method", "opaque");
      BlockMaterial.RenderMethod renderMethod = BlockMaterial.RenderMethod.lookup(method);
      return new BlockMaterial(
         nbt.getString("texture", ""),
         renderMethod != null ? renderMethod : BlockMaterial.RenderMethod.OPAQUE,
         faceDimming,
         ambientOcclusion,
         BlockMaterial.TintMethod.parse(nbt.getString("tint_method", "none")),
         renderMethod != null ? null : method
      );
   }

   public static enum RenderMethod {
      OPAQUE(false, false, false),
      DOUBLE_SIDED(false, false, true),
      ALPHA_TEST(true, false, true),
      ALPHA_TEST_SINGLE_SIDED(true, false, false),
      BLEND(false, true, true);

      private final boolean cutout;
      private final boolean translucent;
      private final boolean twoSided;

      private RenderMethod(boolean cutout, boolean translucent, boolean twoSided) {
         this.cutout = cutout;
         this.translucent = translucent;
         this.twoSided = twoSided;
      }

      public boolean cutout() {
         return this.cutout;
      }

      public boolean translucent() {
         return this.translucent;
      }

      public boolean twoSided() {
         return this.twoSided;
      }

      public boolean opaque() {
         return !this.cutout && !this.translucent;
      }

      static BlockMaterial.RenderMethod parse(String name) {
         BlockMaterial.RenderMethod method = lookup(name);
         return method != null ? method : OPAQUE;
      }

      @Nullable
      static BlockMaterial.RenderMethod lookup(String name) {
         String lower = name.trim().toLowerCase(Locale.ROOT);
         String base = lower.endsWith("_to_opaque") ? lower.substring(0, lower.length() - "_to_opaque".length()) : lower;

         return switch (base) {
            case "opaque" -> lower.equals(base) ? OPAQUE : null;
            case "double_sided" -> lower.equals(base) ? DOUBLE_SIDED : null;
            case "alpha_test" -> ALPHA_TEST;
            case "alpha_test_single_sided" -> ALPHA_TEST_SINGLE_SIDED;
            case "blend" -> BLEND;
            default -> null;
         };
      }
   }

   public static enum TintMethod {
      NONE,
      GRASS,
      DEFAULT_FOLIAGE,
      BIRCH_FOLIAGE,
      EVERGREEN_FOLIAGE,
      DRY_FOLIAGE,
      WATER;

      public int tintIndex() {
         return this.ordinal() - 1;
      }

      static BlockMaterial.TintMethod parse(String name) {
         try {
            return valueOf(name.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
            return NONE;
         }
      }
   }
}
