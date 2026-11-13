package net.bedrocknative.client.mixin.lobby;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiTitleScreenMixin {
   @ModifyExpressionValue(method = "setScreen", at = @At(value = "NEW", target = "()Lnet/minecraft/client/gui/screens/TitleScreen;"), require = 0)
   private TitleScreen bedrocknative$bedrockStartScreen(TitleScreen java) {
      return BedrockLobby.titleScreen(java);
   }
}
