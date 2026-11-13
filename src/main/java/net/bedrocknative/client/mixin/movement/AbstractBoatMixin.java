package net.bedrocknative.client.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
   @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$noJavaFloat(CallbackInfo ci) {
      if (BedrockVehicles.predictable((AbstractBoat)(Object)this)) {
         ci.cancel();
      }
   }

   @Inject(method = "controlBoat", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$noJavaPaddles(CallbackInfo ci) {
      if (BedrockVehicles.predictable((AbstractBoat)(Object)this)) {
         ci.cancel();
      }
   }

   @WrapOperation(
      method = "tick",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
      )
   )
   private void bedrocknative$noJavaMove(AbstractBoat self, MoverType type, Vec3 delta, Operation<Void> original) {
      if (!BedrockVehicles.predictable(self)) {
         original.call(new Object[]{self, type, delta});
      }
   }

   @ModifyConstant(method = "clampRotation", constant = {@Constant(floatValue = 105.0F), @Constant(floatValue = -105.0F)})
   private float bedrocknative$seatLock(float limit) {
      return BedrockMovement.active() ? Math.signum(limit) * 90.0F : limit;
   }
}
