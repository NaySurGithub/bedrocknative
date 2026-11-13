package net.bedrocknative.client.rules;

import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;

public final class BedrockAdventureSettings {
   private static volatile boolean noPvM;
   private static volatile boolean noMvP;
   private static volatile boolean immutableWorld;
   private static volatile boolean showNameTags;
   private static volatile String lastRequest = "";
   private static volatile boolean serverAllowsAutoJump = true;
   private static final AtomicInteger BLOCKED_ATTACKS = new AtomicInteger();

   private BedrockAdventureSettings() {
   }

   public static void apply(UpdateAdventureSettingsPacket packet) {
      noPvM = packet.isNoPvM();
      noMvP = packet.isNoMvP();
      immutableWorld = packet.isImmutableWorld();
      showNameTags = packet.isShowNameTags();
      serverAllowsAutoJump = packet.isAutoJump();
      lastRequest = packet.toString();
      BedrockNative.LOGGER.debug("Adventure settings: {}", lastRequest);
   }

   public static void reset() {
      noPvM = false;
      noMvP = false;
      immutableWorld = false;
      showNameTags = false;
      serverAllowsAutoJump = true;
      lastRequest = "";
   }

   public static boolean effectiveAutoJump(boolean ownOption, boolean serverFlag) {
      return ownOption && serverFlag;
   }

   public static boolean serverAllowsAutoJump() {
      return serverAllowsAutoJump;
   }

   public static boolean immutableWorld() {
      return immutableWorld;
   }

   public static boolean noPvM() {
      return noPvM;
   }

   public static boolean noMvP() {
      return noMvP;
   }

   public static boolean showNameTags() {
      return showNameTags;
   }

   public static boolean blocksAttack(Player player, Entity target) {
      if (noPvM && BedrockMovement.appliesTo(player) && !(target instanceof Player)) {
         BLOCKED_ATTACKS.incrementAndGet();
         return true;
      } else {
         return false;
      }
   }

   public static boolean blocksBuilding(Object player) {
      return immutableWorld && BedrockMovement.appliesTo(player);
   }

   public static String lastRequest() {
      return lastRequest;
   }

   public static int blockedAttacks() {
      return BLOCKED_ATTACKS.get();
   }
}
