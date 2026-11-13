package net.bedrocknative.client.mixin.world;

import net.bedrocknative.client.content.AtlasReload;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.bedrocknative.content.ImageLimits;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TextureAtlas.class})
public abstract class TextureAtlasMixin {
   @Inject(
      method = {"upload"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$serverBlockTextures(Preparations preparations, CallbackInfo ci) {
      TextureAtlas atlas = (TextureAtlas)this;
      ImageLimits.gpuMaxSide(atlas.maxSupportedTextureSize());
      if (atlas.location().equals(TextureAtlas.LOCATION_BLOCKS)) {
         AtlasReload.blockAtlasUploaded();
         BedrockBlockModels.atlasUploaded(atlas);
      }
   }

   @Inject(
      method = {"cycleAnimationFrames"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$serverBlockFlipbooks(CallbackInfo ci) {
      TextureAtlas atlas = (TextureAtlas)this;
      if (atlas.location().equals(TextureAtlas.LOCATION_BLOCKS)) {
         BedrockBlockModels.animate(atlas);
      }
   }
}
