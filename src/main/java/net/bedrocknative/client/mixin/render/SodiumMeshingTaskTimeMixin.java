package net.bedrocknative.client.mixin.render;

import net.bedrocknative.perf.MeshStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class SodiumMeshingTaskTimeMixin {
   @Unique
   private long bedrocknative$started;

   @Inject(method = "execute", at = @At("HEAD"), require = 0, remap = false)
   private void bedrocknative$start(CallbackInfoReturnable<?> cir) {
      this.bedrocknative$started = System.nanoTime();
   }

   @Inject(method = "execute", at = @At("RETURN"), require = 0, remap = false)
   private void bedrocknative$end(CallbackInfoReturnable<?> cir) {
      if (this.bedrocknative$started != 0L) {
         MeshStats.built(System.nanoTime() - this.bedrocknative$started, true);
         this.bedrocknative$started = 0L;
      }
   }
}
