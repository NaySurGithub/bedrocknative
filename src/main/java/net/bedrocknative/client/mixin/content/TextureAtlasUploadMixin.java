package net.bedrocknative.client.mixin.content;

import net.bedrocknative.client.content.ComposedAtlasUpload;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TextureAtlas.class})
public abstract class TextureAtlasUploadMixin {
   @Shadow
   private int maxMipLevel;

   @Shadow
   protected abstract void uploadAnimationFrames();

   @Inject(
      method = {"upload"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$uploadStarts(Preparations preparations, CallbackInfo ci) {
      ComposedAtlasUpload.uploadStarts(preparations);
   }

   @Inject(
      method = {"uploadInitialContents"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$composedStatics(CallbackInfo ci) {
      if (ComposedAtlasUpload.writeComposed((TextureAtlas)this, this.maxMipLevel)) {
         this.uploadAnimationFrames();
         ComposedAtlasUpload.initialContentsDone();
         ci.cancel();
      }
   }

   @Inject(
      method = {"uploadInitialContents"},
      at = {@At("RETURN")}
   )
   private void bedrocknative$javaStaticsDone(CallbackInfo ci) {
      ComposedAtlasUpload.initialContentsDone();
   }

   @Inject(
      method = {"upload"},
      at = {@At("RETURN")}
   )
   private void bedrocknative$uploadDone(Preparations preparations, CallbackInfo ci) {
      ComposedAtlasUpload.uploadDone((TextureAtlas)this);
   }
}
