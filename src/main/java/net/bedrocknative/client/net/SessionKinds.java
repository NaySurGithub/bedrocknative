package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.camera.BedrockCamera;
import net.bedrocknative.client.command.BedrockCommands;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.client.hud.BedrockHud;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.bedrocknative.client.rules.BedrockInputLocks;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.client.world.ChunkStream;
import net.bedrocknative.net.transport.TransportKind;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.client.Minecraft;

public final class SessionKinds {
   private SessionKinds() {
   }

   public static void register() {
      ClientPlayConnectionEvents.JOIN.register((Join)(listener, sender, minecraft) -> joined(minecraft));
   }

   private static void joined(Minecraft minecraft) {
      ClientReport.serverJoined(minecraft);
      if (!BedrockMovement.active()) {
         BedrockDisconnects.connecting();
         BedrockLobby.connecting();
         BedrockVanillaTextures.restoreForJavaWorld(minecraft.hasSingleplayerServer() ? "a singleplayer world" : "a Java Edition server");
         BedrockGlyphs.restoreForJavaWorld();
         List<String> left = bedrockLeftovers();
         BedrockNative.LOGGER
            .info(
               "World joined: {}; Bedrock state {}",
               minecraft.hasSingleplayerServer() ? "singleplayer" : "Java Edition server",
               left.isEmpty() ? "all off" : "STILL ON: " + left
            );
      } else {
         TransportKind kind = BedrockConnector.lastTransport();
         BedrockNative.LOGGER.info("World joined: Bedrock session{}", kind != null && kind.bedrock() ? " over " + kind.shown() : "");
      }
   }

   public static List<String> bedrockLeftovers() {
      List<String> on = new ArrayList<>();
      if (BedrockMovement.active()) {
         on.add("movement");
      }

      if (BedrockGlyphs.active()) {
         on.add("glyphs");
      }

      if (BedrockModels.registry() != null) {
         on.add("models");
      }

      if (BedrockSoundEngine.catalog() != null) {
         on.add("sounds");
      }

      if (BedrockParticleEngine.active()) {
         on.add("particles");
      }

      if (BedrockVanillaTextures.isArmed()) {
         on.add("textures");
      }

      if (BedrockCreativeTabs.active()) {
         on.add("creative tabs");
      }

      if (BedrockCommands.active() != null) {
         on.add("commands");
      }

      if (ChunkStream.active() != null) {
         on.add("chunk stream");
      }

      if (!BedrockHud.hiddenElements().isEmpty()) {
         on.add("hud");
      }

      if (BedrockInputLocks.any()) {
         on.add("input locks");
      }

      if (BedrockCamera.forcedCameraType() != null) {
         on.add("camera");
      }

      return on;
   }
}
