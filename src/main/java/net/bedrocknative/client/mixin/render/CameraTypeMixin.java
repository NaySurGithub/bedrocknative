package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.camera.BedrockCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Options.class})
public abstract class CameraTypeMixin {
   @Inject(
      method = {"getCameraType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$forced(CallbackInfoReturnable<CameraType> cir) {
      CameraType forced = BedrockCamera.forcedCameraType();
      if (forced != null) {
         cir.setReturnValue(forced);
      }
   }

   @Inject(
      method = {"setCameraType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$locked(CameraType cameraType, CallbackInfo ci) {
      if (BedrockCamera.forcedCameraType() != null) {
         ci.cancel();
      }
   }
}
