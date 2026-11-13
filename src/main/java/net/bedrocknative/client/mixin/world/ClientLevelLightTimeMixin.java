package net.bedrocknative.client.mixin.world;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientLevel.class})
public abstract class ClientLevelLightTimeMixin {
   @Unique
   private long bedrocknative$lightStarted;

   @Inject(
      method = {"update"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bedrocknative$lightStart(CallbackInfo ci) {
      this.bedrocknative$lightStarted = System.nanoTime();
   }

   @Inject(
      method = {"update"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$lightEnd(CallbackInfo ci) {
      if (this.bedrocknative$lightStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.LIGHT_UPDATES, System.nanoTime() - this.bedrocknative$lightStarted);
         this.bedrocknative$lightStarted = 0L;
      }
   }
}
