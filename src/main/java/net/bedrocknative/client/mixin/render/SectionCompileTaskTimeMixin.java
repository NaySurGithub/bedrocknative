package net.bedrocknative.client.mixin.render;

import net.bedrocknative.perf.MeshStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask"}
)
public abstract class SectionCompileTaskTimeMixin {
   @Unique
   private long bedrocknative$started;

   @Inject(
      method = {"doTask"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bedrocknative$start(CallbackInfoReturnable<?> cir) {
      this.bedrocknative$started = System.nanoTime();
   }

   @Inject(
      method = {"doTask"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$end(CallbackInfoReturnable<?> cir) {
      if (this.bedrocknative$started != 0L) {
         MeshStats.built(System.nanoTime() - this.bedrocknative$started, false);
         this.bedrocknative$started = 0L;
      }
   }
}
