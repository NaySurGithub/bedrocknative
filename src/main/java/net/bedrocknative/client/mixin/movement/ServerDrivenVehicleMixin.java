package net.bedrocknative.client.mixin.movement;

import net.bedrocknative.client.movement.BedrockVehicles;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ServerDrivenVehicleMixin {
   @Inject(method = "isLocalClientAuthoritative", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$serverDriven(CallbackInfoReturnable<Boolean> cir) {
      if (BedrockVehicles.serverDriven(this)) {
         cir.setReturnValue(false);
      }
   }
}
