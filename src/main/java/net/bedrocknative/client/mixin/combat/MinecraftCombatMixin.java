package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Minecraft.class})
public abstract class MinecraftCombatMixin {
   @WrapOperation(
      method = {"startAttack"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"
      )}
   )
   private void bedrocknative$noMissDip(LocalPlayer player, Operation<Void> original) {
      if (!BedrockMovement.active()) {
         original.call(new Object[]{player});
      }
   }
}
