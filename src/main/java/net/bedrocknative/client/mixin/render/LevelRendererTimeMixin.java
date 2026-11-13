package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public abstract class LevelRendererTimeMixin {
   @Unique
   private long bedrocknative$rebuildStarted;
   @Unique
   private long bedrocknative$compileStarted;
   @Unique
   private long bedrocknative$submitStarted;
   @Unique
   private long bedrocknative$submitNamed;
   @Unique
   private long bedrocknative$prepareStarted;
   @Unique
   private long bedrocknative$prepareNamed;

   @Inject(
      method = {"submitEntities", "submitBlockEntities"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bedrocknative$submitStart(CallbackInfo ci) {
      this.bedrocknative$submitStarted = System.nanoTime();
      this.bedrocknative$submitNamed = FrameTimes.watch().namedNanos();
   }

   @Inject(
      method = {"submitEntities"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$submitEntitiesEnd(CallbackInfo ci) {
      if (this.bedrocknative$submitStarted != 0L) {
         FrameTimes.endAround(FrameWatch.Bucket.ENTITY_RENDER, this.bedrocknative$submitStarted, this.bedrocknative$submitNamed);
         this.bedrocknative$submitStarted = 0L;
      }
   }

   @Inject(
      method = {"submitBlockEntities"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$submitBlockEntitiesEnd(CallbackInfo ci) {
      if (this.bedrocknative$submitStarted != 0L) {
         FrameTimes.endAround(FrameWatch.Bucket.BLOCK_ENTITY_RENDER, this.bedrocknative$submitStarted, this.bedrocknative$submitNamed);
         this.bedrocknative$submitStarted = 0L;
      }
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
      )},
      require = 0
   )
   private void bedrocknative$prepareStart(CallbackInfo ci) {
      this.bedrocknative$prepareStarted = System.nanoTime();
      this.bedrocknative$prepareNamed = FrameTimes.watch().namedNanos();
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;",
         shift = Shift.AFTER
      )},
      require = 0
   )
   private void bedrocknative$prepareEnd(CallbackInfo ci) {
      if (this.bedrocknative$prepareStarted != 0L) {
         FrameTimes.endAround(FrameWatch.Bucket.FEATURE_PREP, this.bedrocknative$prepareStarted, this.bedrocknative$prepareNamed);
         this.bedrocknative$prepareStarted = 0L;
      }
   }

   @Inject(
      method = {"invalidateCompiledGeometry"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bedrocknative$rebuildStart(CallbackInfo ci) {
      this.bedrocknative$rebuildStarted = System.nanoTime();
   }

   @Inject(
      method = {"invalidateCompiledGeometry"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$rebuildEnd(CallbackInfo ci) {
      if (this.bedrocknative$rebuildStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.VIEW_REBUILD, Math.max(1L, System.nanoTime() - this.bedrocknative$rebuildStarted));
         this.bedrocknative$rebuildStarted = 0L;
      }
   }

   @Inject(
      method = {"compileSections"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bedrocknative$compileStart(CallbackInfo ci) {
      this.bedrocknative$compileStarted = System.nanoTime();
   }

   @Inject(
      method = {"compileSections"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bedrocknative$compileEnd(CallbackInfo ci) {
      if (this.bedrocknative$compileStarted != 0L) {
         FrameTimes.watch().add(FrameWatch.Bucket.SECTION_COMPILE, System.nanoTime() - this.bedrocknative$compileStarted);
         this.bedrocknative$compileStarted = 0L;
      }
   }
}
