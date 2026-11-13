package net.bedrocknative.client.mixin;

import net.bedrocknative.client.net.BedrockConnector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
   @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$chooseTheWay(
      Minecraft minecraft, ServerAddress hostAndPort, ServerData server, @Nullable TransferState transferState, CallbackInfo ci
   ) {
      if (!BedrockConnector.handingToJava()) {
         BedrockConnector.connect((ConnectScreen)(Object)this, minecraft, hostAndPort, server, transferState);
         ci.cancel();
      }
   }
}
