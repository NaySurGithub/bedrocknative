package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SectionRenderDispatcher.class})
public abstract class SectionUploadTimeMixin {
   @Unique
   private long bedrocknative$started;
   @Unique
   private int bedrocknative$depth;

   @Inject(
      method = {"uploadTerrainBuffersToGpu"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bedrocknative$start(CallbackInfo ci) {
      if (this.bedrocknative$depth++ == 0) {
         this.bedrocknative$started = System.nanoTime();
      }
   }

   @Inject(
      method = {"uploadTerrainBuffersToGpu"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$end(CallbackInfo ci) {
      if (--this.bedrocknative$depth == 0 && this.bedrocknative$started != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.TERRAIN_UPLOADS, System.nanoTime() - this.bedrocknative$started);
         this.bedrocknative$started = 0L;
      }
   }
}
