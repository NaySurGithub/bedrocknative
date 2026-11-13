package net.bedrocknative.client.mixin.rules;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.rules.BedrockAdventureSettings;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LocalPlayer.class})
public abstract class AutoJumpMixin {
   @ModifyReturnValue(
      method = {"isAutoJumpEnabled"},
      at = {@At("RETURN")}
   )
   private boolean bedrocknative$serverPermission(boolean ownOption) {
      return BedrockAdventureSettings.effectiveAutoJump(ownOption, BedrockAdventureSettings.serverAllowsAutoJump());
   }
}
