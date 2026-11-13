package net.bedrocknative.client.mixin.movement;

import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class BoatPushMixin {
   @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$noPlayerPush(Entity entity, CallbackInfo ci) {
      if (BedrockMovement.appliesTo(entity)) {
         ci.cancel();
      }
   }
}
