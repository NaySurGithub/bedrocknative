package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderRegionCache.class)
public abstract class RenderRegionCacheTimeMixin {
   @Unique
   private long bedrocknative$started;

   @Inject(method = "createRegion", at = @At("HEAD"), require = 0)
   private void bedrocknative$start(CallbackInfoReturnable<RenderSectionRegion> cir) {
      this.bedrocknative$started = System.nanoTime();
   }

   @Inject(method = "createRegion", at = @At("RETURN"), require = 0)
   private void bedrocknative$end(CallbackInfoReturnable<RenderSectionRegion> cir) {
      if (this.bedrocknative$started != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.SECTION_COPIES, System.nanoTime() - this.bedrocknative$started);
         this.bedrocknative$started = 0L;
      }
   }
}
