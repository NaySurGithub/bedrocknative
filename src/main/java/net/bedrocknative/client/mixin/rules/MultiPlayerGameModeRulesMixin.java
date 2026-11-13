package net.bedrocknative.client.mixin.rules;

import net.bedrocknative.client.rules.BedrockAdventureSettings;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeRulesMixin {
   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$noPvM(Player player, Entity entity, CallbackInfo ci) {
      if (BedrockAdventureSettings.blocksAttack(player, entity)) {
         ci.cancel();
      }
   }
}
