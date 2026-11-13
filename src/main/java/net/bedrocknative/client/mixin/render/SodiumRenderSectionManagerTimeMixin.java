package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class SodiumRenderSectionManagerTimeMixin {
   @Unique
   private long bedrocknative$uploadStarted;
   @Unique
   private long bedrocknative$copyStarted;

   @Inject(method = "uploadChunks", at = @At("HEAD"), require = 0, remap = false)
   private void bedrocknative$uploadStart(CallbackInfo ci) {
      this.bedrocknative$uploadStarted = System.nanoTime();
   }

   @Inject(method = "uploadChunks", at = @At("RETURN"), require = 0, remap = false)
   private void bedrocknative$uploadEnd(CallbackInfo ci) {
      if (this.bedrocknative$uploadStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.TERRAIN_UPLOADS, System.nanoTime() - this.bedrocknative$uploadStarted);
         this.bedrocknative$uploadStarted = 0L;
      }
   }

   @Inject(method = "createRebuildTask", at = @At("HEAD"), require = 0, remap = false)
   private void bedrocknative$copyStart(CallbackInfoReturnable<?> cir) {
      this.bedrocknative$copyStarted = System.nanoTime();
   }

   @Inject(method = "createRebuildTask", at = @At("RETURN"), require = 0, remap = false)
   private void bedrocknative$copyEnd(CallbackInfoReturnable<?> cir) {
      if (this.bedrocknative$copyStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.SECTION_COPIES, System.nanoTime() - this.bedrocknative$copyStarted);
         this.bedrocknative$copyStarted = 0L;
      }
   }
}
