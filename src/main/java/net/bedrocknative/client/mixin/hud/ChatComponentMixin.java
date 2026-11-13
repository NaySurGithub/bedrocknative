package net.bedrocknative.client.mixin.hud;

import net.bedrocknative.client.ui.JsonUiHud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ChatComponent.class})
public abstract class ChatComponentMixin {
   @Inject(
      method = {"logChatMessage"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$chatLine(GuiMessage message, CallbackInfo ci) {
      JsonUiHud.chat(message.content());
   }

   @Inject(
      method = {"clearMessages"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$clear(boolean history, CallbackInfo ci) {
      JsonUiHud.clearChat();
   }
}
