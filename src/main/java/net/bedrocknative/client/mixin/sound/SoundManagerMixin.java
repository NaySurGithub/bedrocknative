package net.bedrocknative.client.mixin.sound;

import net.bedrocknative.client.sound.BedrockSoundInstance;
import net.bedrocknative.client.sound.JavaSoundBridge;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundManager.class})
public abstract class SoundManagerMixin {
   @Inject(
      method = {"play"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bridge(SoundInstance instance, CallbackInfoReturnable<PlayResult> cir) {
      PlayResult result = JavaSoundBridge.play(instance);
      if (result != null) {
         cir.setReturnValue(result);
      }
   }

   @Inject(
      method = {"isActive"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$standInActive(SoundInstance instance, CallbackInfoReturnable<Boolean> cir) {
      BedrockSoundInstance standIn = JavaSoundBridge.standIn(instance);
      if (standIn != null) {
         cir.setReturnValue(((SoundManager)this).isActive(standIn));
      }
   }

   @Inject(
      method = {"stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$stopStandIn(SoundInstance instance, CallbackInfo ci) {
      if (JavaSoundBridge.stop(instance)) {
         ci.cancel();
      }
   }
}
