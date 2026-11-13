package net.bedrocknative.client.mixin.render;

import com.mojang.renderpearl.frontend.FrontendGpuSurface;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendGpuSurface.class)
public abstract class FramePresentTimeMixin {
   @Unique
   private long bedrocknative$waitStarted;

   @Inject(method = {"present", "acquireNextTexture"}, at = @At("HEAD"), require = 0)
   private void bedrocknative$waitStart(CallbackInfo ci) {
      this.bedrocknative$waitStarted = System.nanoTime();
   }

   @Inject(method = {"present", "acquireNextTexture"}, at = @At("RETURN"), require = 0)
   private void bedrocknative$waitEnd(CallbackInfo ci) {
      if (this.bedrocknative$waitStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.PRESENT, System.nanoTime() - this.bedrocknative$waitStarted);
         this.bedrocknative$waitStarted = 0L;
      }
   }
}
