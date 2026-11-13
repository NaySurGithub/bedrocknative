package net.bedrocknative.client.mixin.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bedrocknative.client.model.BedrockMobs;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.OverlayActors;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
   extends EntityRenderer<T, S> {
   private LivingEntityRendererMixin(Context context) {
      super(context);
   }

   @Shadow
   protected abstract float getFlipDegrees();

   @Shadow
   protected abstract float getWhiteOverlayProgress(S var1);

   @Shadow
   protected abstract boolean isShaking(S var1);

   @Inject(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockLook(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
      if (BedrockPlayers.enabled() && state instanceof AvatarRenderState avatar && state.getData(BedrockPlayers.FRAME) != null) {
         if (BedrockPlayers.submit(avatar, poseStack, collector, this.getFlipDegrees())) {
            super.submit(state, poseStack, collector, camera);
            info.cancel();
         }
      } else if (BedrockMobs.enabled() && state.getData(BedrockMobs.FRAME) != null) {
         if (BedrockMobs.submit(state, poseStack, collector, this.getFlipDegrees(), this.getWhiteOverlayProgress(state), this.isShaking(state))) {
            super.submit(state, poseStack, collector, camera);
            info.cancel();
         }
      }
   }

   @Inject(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At("RETURN")}
   )
   private void bedrocknative$serverOverlay(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
      if (state.getData(OverlayActors.FRAME) != null) {
         OverlayActors.submit(state, poseStack, collector, this.getFlipDegrees());
      }
   }
}
