package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererTimeMixin {
   @Unique
   private long bedrocknative$started;

   @Inject(method = "render", at = @At("HEAD"), require = 0)
   private void bedrocknative$start(CallbackInfo ci) {
      this.bedrocknative$started = System.nanoTime();
   }

   @Inject(method = "render", at = @At("RETURN"), require = 0)
   private void bedrocknative$end(CallbackInfo ci) {
      if (this.bedrocknative$started != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.GUI_DRAW, System.nanoTime() - this.bedrocknative$started);
         this.bedrocknative$started = 0L;
      }
   }
}
