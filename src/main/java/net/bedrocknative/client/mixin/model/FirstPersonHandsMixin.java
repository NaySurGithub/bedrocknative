package net.bedrocknative.client.mixin.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.FirstPersonProbe;
import net.bedrocknative.client.model.FirstPersonSway;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FirstPersonHandsAndItemsRenderer.class})
public abstract class FirstPersonHandsMixin {
   @Inject(
      method = {"submitArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      )}
   )
   private void bedrocknative$probeHeldItem(
      PlayerRenderState playerState,
      FirstPersonHandsAndItemsRenderState state,
      float partialTicks,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int lightCoords,
      CallbackInfo info
   ) {
      if (hand == InteractionHand.MAIN_HAND) {
         FirstPersonProbe.item(poseStack.last().pose(), state.mainHandRenderState);
      }
   }

   @Inject(
      method = {"renderPlayerArm"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockArm(
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int lightCoords,
      float inverseArmHeight,
      float attackValue,
      HumanoidArm arm,
      PlayerRenderState playerState,
      CallbackInfo info
   ) {
      if (BedrockPlayers.enabled() && BedrockPlayers.submitFirstPersonArm(playerState.avatarRenderState, poseStack, collector, lightCoords)) {
         info.cancel();
      }
   }

   @Inject(
      method = {"renderPlayerHand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockMapArm(
      PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo info
   ) {
      if (BedrockPlayers.enabled() && BedrockPlayers.submitMapArm(playerState.avatarRenderState, arm, poseStack, collector, lightCoords)) {
         info.cancel();
      }
   }

   @ModifyArg(
      method = {"submitHandsWithItems"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V",
         ordinal = 0
      )},
      index = 1
   )
   private float bedrocknative$noPitchSway(float angle) {
      return FirstPersonSway.current(angle);
   }

   @ModifyArg(
      method = {"submitHandsWithItems"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V",
         ordinal = 1
      )},
      index = 1
   )
   private float bedrocknative$noYawSway(float angle) {
      return FirstPersonSway.current(angle);
   }
}
