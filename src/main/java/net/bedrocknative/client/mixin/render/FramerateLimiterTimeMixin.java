package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FramerateLimiter.class})
public abstract class FramerateLimiterTimeMixin {
   @Unique
   private static long bedrocknative$sleepStarted;

   @Inject(
      method = {"limitDisplayFPS"},
      at = {@At("HEAD")},
      require = 0
   )
   private static void bedrocknative$sleepStart(int framerateLimit, CallbackInfo ci) {
      bedrocknative$sleepStarted = System.nanoTime();
   }

   @Inject(
      method = {"limitDisplayFPS"},
      at = {@At("RETURN")},
      require = 0
   )
   private static void bedrocknative$sleepEnd(int framerateLimit, CallbackInfo ci) {
      if (bedrocknative$sleepStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.PRESENT, System.nanoTime() - bedrocknative$sleepStarted);
         bedrocknative$sleepStarted = 0L;
      }
   }
}
