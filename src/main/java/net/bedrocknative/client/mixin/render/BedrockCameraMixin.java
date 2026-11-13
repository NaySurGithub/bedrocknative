package net.bedrocknative.client.mixin.render;

import net.bedrocknative.camera.CameraDirector;
import net.bedrocknative.client.camera.BedrockCamera;
import net.bedrocknative.client.hud.BedrockCameraShake;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class BedrockCameraMixin {
   @Shadow
   private float xRot;
   @Shadow
   private float yRot;
   @Shadow
   private Vec3 position;
   @Shadow
   private Entity entity;
   @Shadow
   private Level level;
   @Shadow
   private float eyeHeight;
   @Shadow
   private float eyeHeightOld;
   @Shadow
   private boolean isPanoramicMode;
   @Shadow
   @Final
   private Minecraft minecraft;

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Inject(
      method = {"alignWithEntity"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$view(float partialTicks, CallbackInfo ci) {
      BedrockCamera.beforeAlign();
   }

   @Inject(
      method = {"alignWithEntity"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$camera(float partialTicks, CallbackInfo ci) {
      Vec3 eye = new Vec3(
         Mth.lerp(partialTicks, this.entity.xo, this.entity.getX()),
         Mth.lerp(partialTicks, this.entity.yo, this.entity.getY()) + Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight),
         Mth.lerp(partialTicks, this.entity.zo, this.entity.getZ())
      );
      CameraDirector.Pose pose = BedrockCamera.align(
         this.entity,
         this.level,
         partialTicks,
         eye,
         this.entity.getViewYRot(partialTicks),
         this.entity.getViewXRot(partialTicks),
         this.position,
         this.yRot,
         this.xRot
      );
      if (pose != null) {
         this.setRotation(pose.yaw(), pose.pitch());
         this.setPosition(BedrockCamera.toVec3(pose.position()));
      }

      if (BedrockCameraShake.active()) {
         BedrockCameraShake.Offset offset = BedrockCameraShake.offset();
         if (offset.yaw() != 0.0F || offset.pitch() != 0.0F) {
            this.setRotation(this.yRot + offset.yaw(), this.xRot + offset.pitch());
         }

         if (offset.x() != 0.0F || offset.y() != 0.0F || offset.z() != 0.0F) {
            this.setPosition(this.position.add(offset.x(), offset.y(), offset.z()));
         }
      }
   }

   @Inject(
      method = {"calculateFov"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bedrocknative$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
      if (!this.isPanoramicMode) {
         float factor = BedrockCamera.fovFactor(((Integer)this.minecraft.options.fov().get()).intValue());
         if (factor != 1.0F) {
            cir.setReturnValue((Float)cir.getReturnValue() * factor);
         }
      }
   }
}
