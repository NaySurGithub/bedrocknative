package net.bedrocknative.client.mixin.render;

import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({RenderSystem.class})
public abstract class WindowEventsTimeMixin {
   @Unique
   private static long bedrocknative$pollStarted;

   @Inject(
      method = {"pollEvents"},
      at = {@At("HEAD")},
      require = 0
   )
   private static void bedrocknative$pollStart(SDLEventHandler eventHandler, CallbackInfo ci) {
      bedrocknative$pollStarted = System.nanoTime();
   }

   @Inject(
      method = {"pollEvents"},
      at = {@At("RETURN")},
      require = 0
   )
   private static void bedrocknative$pollEnd(SDLEventHandler eventHandler, CallbackInfo ci) {
      if (bedrocknative$pollStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.WINDOW_EVENTS, System.nanoTime() - bedrocknative$pollStarted);
         bedrocknative$pollStarted = 0L;
      }
   }
}
