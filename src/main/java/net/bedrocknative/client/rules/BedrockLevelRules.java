package net.bedrocknative.client.rules;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.Difficulty;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockLevelRules {
   private static final int NOTICE_REFRESH_TICKS = 40;
   private static final AtomicInteger IGNORED_EVENTS = new AtomicInteger();
   private final Minecraft minecraft;
   private @Nullable Difficulty pendingDifficulty;
   private int ableToSleep = 1;
   private int overworldPlayers;
   private int sleepingPlayers;
   private int noticeIn;
   private static final AtomicInteger NOTICES = new AtomicInteger();
   private static volatile @Nullable String lastNotice;

   public BedrockLevelRules(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public void onDifficulty(SetDifficultyPacket packet) {
      Difficulty difficulty = Difficulty.byId(packet.getDifficulty());
      this.pendingDifficulty = difficulty;
      this.applyDifficulty();
   }

   public void levelCreated() {
      this.applyDifficulty();
   }

   private void applyDifficulty() {
      ClientLevel level = this.minecraft.level;
      Difficulty difficulty = this.pendingDifficulty;
      if (level != null && difficulty != null) {
         level.getLevelData().setDifficulty(difficulty);
         BedrockNative.LOGGER.debug("Difficulty {}", difficulty);
      }
   }

   public void onGenericEvent(LevelEventGenericPacket packet) {
      if (packet.getType() == LevelEvent.SLEEPING_PLAYERS && packet.getTag() instanceof NbtMap tag) {
         this.ableToSleep = tag.getInt("ableToSleep", 1);
         this.overworldPlayers = tag.getInt("overworldPlayerCount", 0);
         this.sleepingPlayers = tag.getInt("sleepingPlayerCount", 0);
         this.noticeIn = 0;
         this.showNotice();
      } else {
         IGNORED_EVENTS.incrementAndGet();
         BedrockNative.LOGGER.debug("Level event {} ignored", packet.getType());
      }
   }

   public void tick() {
      if (this.sleepingPlayers > 0 && --this.noticeIn <= 0) {
         this.showNotice();
      }
   }

   private void showNotice() {
      if (this.sleepingPlayers > 0) {
         this.noticeIn = 40;
         List<String> parameters = List.of();
         String text;
         if (this.ableToSleep == 0) {
            text = "%multiplayer.playersSleepingNotPossible";
         } else if (this.overworldPlayers > 0 && this.sleepingPlayers >= this.overworldPlayers) {
            text = "%multiplayer.playersSkippingNight";
         } else {
            text = "%multiplayer.playersSleeping";
            parameters = List.of(String.valueOf(this.sleepingPlayers), String.valueOf(this.overworldPlayers));
         }

         lastNotice = BedrockText.translate(text, parameters);
         NOTICES.incrementAndGet();
         this.minecraft.gui.hud.setOverlayMessage(BedrockText.message(text, parameters, true), false);
      }
   }

   public void reset() {
      this.pendingDifficulty = null;
      this.ableToSleep = 1;
      this.overworldPlayers = 0;
      this.sleepingPlayers = 0;
      this.noticeIn = 0;
   }

   public static @Nullable String lastNotice() {
      return lastNotice;
   }

   public static int noticesShown() {
      return NOTICES.get();
   }

   public static int ignoredEvents() {
      return IGNORED_EVENTS.get();
   }
}
