package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
   @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V"))
   private void bedrocknative$noAttackDip(Player player, Operation<Void> original) {
      if (!BedrockMovement.active()) {
         original.call(new Object[]{player});
      }
   }

   @WrapOperation(
      method = "attack",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;attack(Lnet/minecraft/world/entity/Entity;)V")
   )
   private void bedrocknative$noLocalAttack(Player player, Entity target, Operation<Void> original) {
      if (!BedrockMovement.active()) {
         original.call(new Object[]{player, target});
      }
   }

   @ModifyReturnValue(method = "hasMissTime", at = @At("RETURN"))
   private boolean bedrocknative$noMissTime(boolean original) {
      return original && !BedrockMovement.active();
   }
}
