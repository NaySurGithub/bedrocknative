package net.bedrocknative.client.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.model.render.RenderKind;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class MaterialTypes {
   private static final Map<Identifier, RenderType> ADDITIVE = new HashMap<>();
   private static final boolean UNBLEND = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.unblendOpaque"));
   private static final String SEE_THROUGH_PROBE = System.getProperty("bedrocknative.seeThroughProbe", "");

   private MaterialTypes() {
   }

   static RenderType renderType(RenderKind kind, Identifier texture) {
      return switch (kind) {
         case SOLID -> RenderTypes.entitySolid(texture);
         case CUTOUT, DEPTH_ONLY -> RenderTypes.entityCutout(texture);
         case CUTOUT_CULL -> RenderTypes.entityCutoutCull(texture);
         case TRANSLUCENT -> RenderTypes.entityTranslucent(texture);
         case TRANSLUCENT_CULL -> RenderTypes.entityTranslucentCull(texture);
         case EMISSIVE_ONLY -> RenderTypes.eyes(texture);
         case GLINT_CUTOUT -> RenderTypes.armorCutoutNoCullGlint(texture);
         case GLINT_SOLID -> RenderTypes.entitySolidGlint(texture);
         case ADDITIVE -> (RenderType)ADDITIVE.computeIfAbsent(texture, id -> RenderTypes.energySwirl(id, 0.0F, 0.0F));
         case SEE_THROUGH -> SeeThroughTypes.renderType(texture, false);
         case SEE_THROUGH_CULL -> SeeThroughTypes.renderType(texture, true);
      };
   }

   static RenderKind unblended(RenderKind kind, Identifier texture, boolean opaqueColor) {
      return UNBLEND && opaqueColor && kind.unblended() != kind && !BedrockTextures.needsBlending(texture) ? kind.unblended() : kind;
   }

   static RenderKind probed(RenderKind kind, @Nullable String texturePath) {
      if (!SEE_THROUGH_PROBE.isEmpty() && texturePath != null && texturePath.contains(SEE_THROUGH_PROBE)) {
         return kind != RenderKind.SOLID && kind != RenderKind.CUTOUT_CULL && kind != RenderKind.TRANSLUCENT_CULL
            ? RenderKind.SEE_THROUGH
            : RenderKind.SEE_THROUGH_CULL;
      } else {
         return kind;
      }
   }

   static RenderType dissolve(Identifier texture, Identifier mask) {
      return RenderTypes.entityCutoutDissolve(texture, mask);
   }

   static void clear() {
      ADDITIVE.clear();
      SeeThroughTypes.clear();
   }

   static RenderType outline(Identifier texture) {
      return RenderTypes.outline(texture);
   }

   static RenderType glow(Identifier emissive) {
      return RenderTypes.eyes(emissive);
   }

   static Identifier texture(@Nullable String path) {
      if (path == null) {
         return MissingTextureAtlasSprite.getLocation();
      } else if (SkinTextures.handles(path)) {
         Identifier skin = SkinTextures.texture(path);
         return skin != null ? skin : MissingTextureAtlasSprite.getLocation();
      } else {
         Optional<BedrockTextures.Texture> texture = BedrockTextures.pack(path);
         return texture.isPresent() ? texture.get().id() : MissingTextureAtlasSprite.getLocation();
      }
   }
}
