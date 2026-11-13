package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public abstract class ViewAreaTimeMixin {
   @Unique
   private long bedrocknative$started;

   @Inject(method = "repositionCamera", at = @At("HEAD"), require = 0)
   private void bedrocknative$start(CallbackInfoReturnable<Boolean> cir) {
      this.bedrocknative$started = System.nanoTime();
   }

   @Inject(method = "repositionCamera", at = @At("RETURN"), require = 0)
   private void bedrocknative$end(CallbackInfoReturnable<Boolean> cir) {
      if (this.bedrocknative$started != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.VIEW_MOVE, System.nanoTime() - this.bedrocknative$started);
         this.bedrocknative$started = 0L;
      }
   }
}
