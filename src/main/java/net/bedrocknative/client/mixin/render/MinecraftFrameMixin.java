package net.bedrocknative.client.mixin.render;

import net.bedrocknative.PerfProbe;
import net.bedrocknative.client.model.ActorPrep;
import net.bedrocknative.client.net.DevPerfTest;
import net.bedrocknative.client.perf.FrameClock;
import net.bedrocknative.client.world.ChunkStream;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
   @Inject(method = "runTick", at = @At("HEAD"), require = 0)
   private void bedrocknative$frame(boolean advanceGameTime, CallbackInfo ci) {
      ChunkStream.frameStarted();
      FrameClock.frameStarted();
      if (PerfProbe.ENABLED) {
         DevPerfTest.frame();
      }
   }

   @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = Shift.AFTER), require = 0)
   private void bedrocknative$drainChunks(boolean advanceGameTime, CallbackInfo ci) {
      ChunkStream.drainActive();
      ActorPrep.drainActive();
   }
}
