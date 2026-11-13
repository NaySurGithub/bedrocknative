package net.bedrocknative.client.mixin.rules;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.rules.BedrockAdventureSettings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerRulesMixin {
   @ModifyReturnValue(method = "mayBuild", at = @At("RETURN"))
   private boolean bedrocknative$immutableWorld(boolean original) {
      return original && !BedrockAdventureSettings.blocksBuilding(this);
   }

   @ModifyVariable(method = "blockActionRestricted", at = @At("HEAD"), argsOnly = true)
   private GameType bedrocknative$immutableMode(GameType gameType) {
      return gameType != GameType.SPECTATOR && BedrockAdventureSettings.blocksBuilding(this) ? GameType.ADVENTURE : gameType;
   }
}
