package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorTimeMixin {
   @Unique
   private long bedrocknative$entitiesStarted;
   @Unique
   private long bedrocknative$entitiesNamed;
   @Unique
   private long bedrocknative$blockEntitiesStarted;
   @Unique
   private long bedrocknative$blockEntitiesNamed;

   @Inject(method = "extractVisibleEntities", at = @At("HEAD"), require = 0)
   private void bedrocknative$entitiesStart(CallbackInfo ci) {
      this.bedrocknative$entitiesStarted = System.nanoTime();
      this.bedrocknative$entitiesNamed = FrameTimes.watch().namedNanos();
   }

   @Inject(method = "extractVisibleEntities", at = @At("RETURN"), require = 0)
   private void bedrocknative$entitiesEnd(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, CallbackInfo ci) {
      if (this.bedrocknative$entitiesStarted != 0L) {
         FrameTimes.endAround(FrameWatch.Bucket.ENTITY_RENDER, this.bedrocknative$entitiesStarted, this.bedrocknative$entitiesNamed);
         FrameTimes.watch().count(FrameWatch.Bucket.ENTITY_RENDER, output.entityRenderStates.size());
         this.bedrocknative$entitiesStarted = 0L;
      }
   }

   @Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"), require = 0)
   private void bedrocknative$blockEntitiesStart(CallbackInfo ci) {
      this.bedrocknative$blockEntitiesStarted = System.nanoTime();
      this.bedrocknative$blockEntitiesNamed = FrameTimes.watch().namedNanos();
   }

   @Inject(method = "extractVisibleBlockEntities", at = @At("RETURN"), require = 0)
   private void bedrocknative$blockEntitiesEnd(Camera camera, float deltaPartialTick, LevelRenderState levelRenderState, CallbackInfo ci) {
      if (this.bedrocknative$blockEntitiesStarted != 0L) {
         FrameTimes.endAround(FrameWatch.Bucket.BLOCK_ENTITY_RENDER, this.bedrocknative$blockEntitiesStarted, this.bedrocknative$blockEntitiesNamed);
         FrameTimes.watch().count(FrameWatch.Bucket.BLOCK_ENTITY_RENDER, levelRenderState.blockEntityRenderStates.size());
         this.bedrocknative$blockEntitiesStarted = 0L;
      }
   }
}
