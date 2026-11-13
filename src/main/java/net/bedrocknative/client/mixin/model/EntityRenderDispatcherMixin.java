package net.bedrocknative.client.mixin.model;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bedrocknative.client.model.BedrockMobs;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderDispatcher.class})
public abstract class EntityRenderDispatcherMixin {
   @WrapOperation(
      method = {"submit"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
      )}
   )
   private void bedrocknative$bedrockLook(
      EntityRenderer<?, EntityRenderState> renderer,
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      Operation<Void> original
   ) {
      if (BedrockMobs.enabled()
         && !(renderer instanceof LivingEntityRenderer)
         && state.getData(BedrockMobs.FRAME) != null
         && BedrockMobs.submitActor(state, poseStack, collector)) {
         if (state instanceof EnderDragonRenderState dragon) {
            dragonEffects(dragon, poseStack, collector);
         }

         if (state.leashStates != null) {
            for (int i = 0; i < state.leashStates.size(); i++) {
               collector.submitLeash(poseStack, (LeashState)state.leashStates.get(i));
            }
         }

         ((EntityRendererInvoker)renderer).bedrocknative$submitNameDisplay(state, poseStack, collector, camera);
      } else {
         original.call(new Object[]{renderer, state, poseStack, collector, camera});
      }
   }

   private static void dragonEffects(EnderDragonRenderState dragon, PoseStack poseStack, SubmitNodeCollector collector) {
      if (dragon.deathTime > 0.0F) {
         poseStack.pushPose();
         poseStack.rotateDegrees(Axis.YP, -dragon.getHistoricalPos(7).yRot());
         poseStack.rotateDegrees(Axis.XP, (float)(dragon.getHistoricalPos(5).y() - dragon.getHistoricalPos(10).y()) * 10.0F);
         poseStack.translate(0.0F, 0.0F, 1.0F);
         poseStack.scale(-1.0F, -1.0F, 1.0F);
         poseStack.translate(0.0F, -1.501F, 0.0F);
         poseStack.translate(0.0F, -1.0F, -2.0F);
         EnderDragonRendererInvoker.bedrocknative$submitRays(poseStack, dragon.deathTime / 200.0F, collector, RenderTypes.dragonRays());
         poseStack.popPose();
      }

      if (dragon.beamOffset != null) {
         EnderDragonRenderer.submitCrystalBeams(
            (float)dragon.beamOffset.x, (float)dragon.beamOffset.y, (float)dragon.beamOffset.z, dragon.ageInTicks, poseStack, collector, dragon.lightCoords
         );
      }
   }
}
