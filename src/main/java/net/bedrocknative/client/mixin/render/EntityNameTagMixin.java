package net.bedrocknative.client.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.rules.BedrockNameTags;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.text.NameTagLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityNameTagMixin {
   @Shadow
   @Final
   protected EntityRenderDispatcher entityRenderDispatcher;

   @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V", at = @At("TAIL"))
   private void bedrocknative$bedrockTag(
      Entity entity, EntityRenderState state, float partialTicks, double nameTagDistance, double belowNameDistance, CallbackInfo ci
   ) {
      BedrockNameTag tag = BedrockNameTag.of(entity);
      if (tag != null) {
         Minecraft minecraft = Minecraft.getInstance();
         boolean shown = this.entityRenderDispatcher.camera != null
            && state.distanceToCameraSq < nameTagDistance * nameTagDistance
            && entity != minecraft.getCameraEntity()
            && entity != minecraft.player
            && !minecraft.gui.hud.isHidden()
            && tag.shows(entity, entity == this.entityRenderDispatcher.crosshairPickEntity)
            && !BedrockNameTags.hides();
         state.nameTagAttachment = new Vec3(0.0, NameTagLayout.attachmentY(tag.boxHeight(entity)), 0.0);
         List<Component> lines = shown ? tag.paddedLines(minecraft.font) : List.of();
         List<Component> below = tag.below(shown, state.scoreText);
         if (LunarCompat.active()) {
            state.nameTag = null;
            state.scoreText = null;
            ((BedrockNameTag.StateHolder)state).bedrocknative$setDrawn(new BedrockNameTag.Drawn(lines, tag.textOnly(entity), below, true));
         } else {
            state.nameTag = shown ? tag.joined() : null;
            ((BedrockNameTag.StateHolder)state).bedrocknative$setDrawn(new BedrockNameTag.Drawn(lines, tag.textOnly(entity), below, false));
         }
      }
   }

   @Inject(
      method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void bedrocknative$drawBedrockTag(
      EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci
   ) {
      BedrockNameTag.Drawn drawn = ((BedrockNameTag.StateHolder)state).bedrocknative$drawn();
      if (drawn != null) {
         ci.cancel();
         if (!drawn.ownPath()) {
            BedrockNameTag.submitLines(state.nameTag == null ? List.of() : drawn.lines(), drawn.below(), state, poseStack, collector, camera, offset);
         }
      }
   }

   @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$keepTagInView(
      Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir
   ) {
      BedrockNameTag tag = BedrockNameTag.of(entity);
      if (tag != null && tag.joined() != null && tag.alwaysShown(entity) && !(entity.distanceToSqr(camX, camY, camZ) >= 4096.0)) {
         double centre = entity.getY() + tag.boxHeight(entity) + 0.6;
         double half = (tag.lines().size() + 2 * tag.scoreLines().size()) * 10 * 0.025 * 1.1312F / 2.0 + 0.5;
         AABB box = new AABB(
            entity.getX() - 2.0, Math.min(entity.getY(), centre - half), entity.getZ() - 2.0, entity.getX() + 2.0, centre + half, entity.getZ() + 2.0
         );
         if (culler.isVisible(box)) {
            cir.setReturnValue(true);
         }
      }
   }
}
