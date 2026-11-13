package net.bedrocknative.client.mixin.model;

import net.bedrocknative.client.model.BedrockBodyRotation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBodyRotationMixin {
   @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$bodyRotationFlags(float yBodyRotT, CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self.level().isClientSide() && BedrockBodyRotation.apply(self)) {
         ci.cancel();
      }
   }
}
