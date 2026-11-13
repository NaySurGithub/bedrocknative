package net.bedrocknative.client.mixin.model;

import net.bedrocknative.client.model.BedrockBodyRotation;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobBodyRotationMixin {
   @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$mobBodyRotationFlags(float yBodyRotT, CallbackInfo ci) {
      Mob self = (Mob)(Object)this;
      if (self.level().isClientSide() && BedrockBodyRotation.apply(self)) {
         ci.cancel();
      }
   }
}
