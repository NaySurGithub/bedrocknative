package net.bedrocknative.client.mixin.lobby;

import net.bedrocknative.client.net.BedrockSessionConnection;
import net.bedrocknative.client.net.SessionTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftLeaveMixin {
   @Unique
   private boolean bedrocknative$leavingBedrock;

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"), require = 0)
   private void bedrocknative$leaving(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
      this.bedrocknative$leavingBedrock = this.bedrocknative$bedrockWorld();
   }

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"), require = 0)
   private void bedrocknative$levelGone(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
      SessionTimeline.leaveMark("level gone");
      this.bedrocknative$releaseRenderData();
   }

   @Inject(method = "clearClientLevel", at = @At("HEAD"), require = 0)
   private void bedrocknative$clearing(Screen screen, CallbackInfo ci) {
      this.bedrocknative$leavingBedrock = this.bedrocknative$bedrockWorld();
   }

   @Inject(method = "clearClientLevel", at = @At("TAIL"), require = 0)
   private void bedrocknative$levelCleared(Screen screen, CallbackInfo ci) {
      SessionTimeline.leaveMark("level gone");
      this.bedrocknative$releaseRenderData();
   }

   @Unique
   private boolean bedrocknative$bedrockWorld() {
      Minecraft minecraft = (Minecraft)(Object)this;
      ClientPacketListener listener = minecraft.getConnection();
      return minecraft.level != null && listener != null && listener.getConnection() instanceof BedrockSessionConnection;
   }

   @Unique
   private void bedrocknative$releaseRenderData() {
      Minecraft minecraft = (Minecraft)(Object)this;
      boolean bedrock = this.bedrocknative$leavingBedrock;
      this.bedrocknative$leavingBedrock = false;
      if (bedrock && minecraft.level == null) {
         minecraft.levelRenderer.resetLevelRenderData();
         SessionTimeline.leaveMark("render data");
      }
   }
}
