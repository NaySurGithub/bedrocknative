package net.bedrocknative.client.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bedrocknative.client.mixin.model.EntityRendererInvoker;
import net.bedrocknative.client.text.BedrockNameTag;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderDispatcher.class})
public abstract class EntityTextOnlyMixin {
   @WrapOperation(
      method = {"submit"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
      )}
   )
   private void bedrocknative$textOnly(
      EntityRenderer<?, ?> renderer,
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      Operation<Void> original
   ) {
      BedrockNameTag.Drawn drawn = ((BedrockNameTag.StateHolder)state).bedrocknative$drawn();
      if (drawn != null && drawn.textOnly()) {
         state.shadowPieces.clear();
         state.displayFireAnimation = false;
         if (drawn.ownPath()) {
            BedrockNameTag.submitLines(drawn.lines(), drawn.below(), state, poseStack, collector, camera, 0);
         } else {
            ((EntityRendererInvoker)renderer).bedrocknative$submitNameDisplay(state, poseStack, collector, camera);
         }
      } else {
         original.call(new Object[]{renderer, state, poseStack, collector, camera});
         if (drawn != null && drawn.ownPath()) {
            BedrockNameTag.submitLines(drawn.lines(), drawn.below(), state, poseStack, collector, camera, 0);
         }
      }
   }
}
