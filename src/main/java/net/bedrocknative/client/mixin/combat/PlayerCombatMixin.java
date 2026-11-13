package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.movement.EntityPick;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerCombatMixin {
   @ModifyReturnValue(method = "getAttackStrengthScale", at = @At("RETURN"))
   private float bedrocknative$noCooldown(float original) {
      return BedrockMovement.appliesTo(this) ? 1.0F : original;
   }

   @ModifyReturnValue(method = "blockInteractionRange", at = @At("RETURN"))
   private double bedrocknative$blockReach(double original) {
      return BedrockMovement.appliesTo(this) ? Math.max(original, 5.0) : original;
   }

   @ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
   private double bedrocknative$entityReach(double original) {
      return BedrockMovement.appliesTo(this) ? EntityPick.reach(original, ((Player)(Object)this).isCreative()) : original;
   }
}
