package net.bedrocknative.client.net;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.hud.BedrockCameraShake;
import net.bedrocknative.client.hud.BedrockCredits;
import net.bedrocknative.client.hud.BedrockHud;
import net.bedrocknative.client.hud.BedrockToasts;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.rules.BedrockAdventureSettings;
import net.bedrocknative.client.rules.BedrockInputLocks;
import net.bedrocknative.client.rules.BedrockLevelRules;
import net.bedrocknative.client.rules.BedrockNameTags;
import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.HudElement;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket;
import org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket.Status;

final class DevQuickTest {
   private static final Map<HudElement, String> SCRIPT_NAMES = new EnumMap<>(HudElement.class);
   private static volatile boolean transferred;
   private static int checks;
   private static int failed;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final MovementStats movement;
   private final Path log;
   private BlockPos base;
   private int walkCorrections;
   private static final Set<PlayerAuthInputData> MOVE_FLAGS = EnumSet.of(
      PlayerAuthInputData.UP,
      PlayerAuthInputData.DOWN,
      PlayerAuthInputData.LEFT,
      PlayerAuthInputData.RIGHT,
      PlayerAuthInputData.SPRINT_DOWN,
      PlayerAuthInputData.SPRINTING,
      PlayerAuthInputData.START_SPRINTING
   );
   private static final Set<PlayerAuthInputData> JUMP_FLAGS = EnumSet.of(
      PlayerAuthInputData.JUMP_DOWN,
      PlayerAuthInputData.JUMPING,
      PlayerAuthInputData.START_JUMPING,
      PlayerAuthInputData.WANT_UP,
      PlayerAuthInputData.JUMP_CURRENT_RAW,
      PlayerAuthInputData.JUMP_PRESSED_RAW
   );
   private static final Set<PlayerAuthInputData> SNEAK_FLAGS = EnumSet.of(
      PlayerAuthInputData.SNEAK_DOWN,
      PlayerAuthInputData.SNEAKING,
      PlayerAuthInputData.START_SNEAKING,
      PlayerAuthInputData.WANT_DOWN,
      PlayerAuthInputData.SNEAK_CURRENT_RAW,
      PlayerAuthInputData.SNEAK_PRESSED_RAW
   );

   DevQuickTest(Minecraft minecraft, Consumer<BedrockPacket> inject, MovementStats movement) {
      this.minecraft = minecraft;
      this.inject = inject;
      this.movement = movement;
      this.log = minecraft.gameDirectory.toPath().resolve("logs/latest.log");
   }

   void run() {
      if (transferred) {
         this.afterTransfer();
      } else {
         String devTest = System.getProperty("bedrocknative.devTest", "");
         if (devTest.startsWith("quick-")) {
            this.runPart(devTest.substring("quick-".length()));
         } else {
            this.commands(
               "gamemode survival",
               "clear @s",
               "effect @s clear",
               "replaceitem entity @s slot.armor.chest 0 iron_chestplate",
               "xp -1000L @s",
               "xp 5L @s",
               "effect @s speed 900 0 true",
               "give @s diamond_sword",
               "give @s apple 4"
            );
            this.minecraft.execute(() -> {
               this.minecraft.player.setXRot(10.0F);
               this.minecraft.player.getInventory().setSelectedSlot(0);
            });
            sleep(2500L);
            BedrockNative.LOGGER
               .info(
                  "Dev quick test: armor {}, effects {}, xp level {}, health {}, food {}",
                  new Object[]{
                     this.supply(() -> this.minecraft.player.getArmorValue()),
                     this.supply(
                        () -> this.minecraft
                           .player
                           .getActiveEffects()
                           .stream()
                           .map(effect -> effect.getEffect().getRegisteredName() + (effect.showIcon() ? "" : " (no icon)"))
                           .toList()
                     ),
                     this.supply(() -> this.minecraft.player.experienceLevel),
                     this.supply(() -> this.minecraft.player.getHealth()),
                     this.supply(() -> this.minecraft.player.getFoodData().getFoodLevel())
                  }
               );
            this.hud();
            this.shake();
            this.toasts();
            this.credits();
            this.difficulty();
            this.adventureSettings();
            this.nameTags();
            this.inputLocks();
            this.picks();
            this.genericEvents();
            this.dimensionData();
            this.commands("scriptevent bedrocknative:hud hide crosshair hotbar", "camerashake add @s 4 120 positional");
            this.inject.accept(inputLocks(6));
            this.inject.accept(adventure(true, true, true, true, false));
            sleep(500L);
            this.check(
               "before the transfer: locks "
                  + BedrockInputLocks.locks()
                  + ", immutable world "
                  + this.supply(BedrockAdventureSettings::immutableWorld)
                  + ", server auto-jump "
                  + this.supply(BedrockAdventureSettings::serverAllowsAutoJump),
               BedrockInputLocks.locks() == 6
                  && this.supply(BedrockAdventureSettings::immutableWorld)
                  && !this.supply(BedrockAdventureSettings::serverAllowsAutoJump)
            );
            this.check(
               "before the transfer: crosshair and hotbar hidden, shake running",
               BedrockHud.hiddenElements().equals(Set.of(HudElement.CROSSHAIR, HudElement.HOTBAR)) && this.supply(BedrockCameraShake::active)
            );
            transferred = true;
            TransferPacket transfer = new TransferPacket();
            transfer.setAddress("127.0.0.1");
            String joined = this.supply(() -> this.minecraft.getCurrentServer() == null ? "" : this.minecraft.getCurrentServer().ip);
            transfer.setPort(joined.contains(":") ? Integer.parseInt(joined.substring(joined.lastIndexOf(58) + 1)) : 19132);
            this.inject.accept(transfer);
         }
      }
   }

   private void runPart(String part) {
      switch (part) {
         case "difficulty":
            this.difficulty();
            break;
         case "adventure":
            this.adventureSettings();
            break;
         case "nametags":
            this.nameTags();
            break;
         case "locks":
            this.inputLocks();
            break;
         case "picks":
            this.picks();
            break;
         case "events":
            this.genericEvents();
            break;
         case "dimension":
            this.dimensionData();
            break;
         case "freeze":
            this.freeze();
            break;
         default:
            BedrockNative.LOGGER.info("Dev quick test: no part called {}", part);
      }

      BedrockNative.LOGGER.info("Dev quick test summary: {} checks, {} failed", checks, failed);
      BedrockNative.LOGGER.info("Dev quick test: done");
   }

   private void afterTransfer() {
      sleep(1000L);
      this.check(
         "after the transfer nothing is hidden (" + BedrockHud.hiddenElements() + ") and no shake runs",
         BedrockHud.hiddenElements().isEmpty() && !this.supply(BedrockCameraShake::active)
      );
      this.check(
         "after the transfer no lock ("
            + BedrockInputLocks.locks()
            + "), no immutable world, no no-PvM, and auto-jump is the new server's ("
            + this.supply(BedrockAdventureSettings::serverAllowsAutoJump)
            + ")",
         BedrockInputLocks.locks() == 0
            && !this.supply(BedrockAdventureSettings::immutableWorld)
            && !this.supply(BedrockAdventureSettings::noPvM)
            && this.supply(BedrockAdventureSettings::serverAllowsAutoJump)
      );
      this.screenshot("quick-transfer");
      int before = BedrockToasts.shown();
      this.inject.accept(toast("Back on the server", "A toast works in the new session too"));
      sleep(500L);
      this.check("the new session shows toasts", BedrockToasts.shown() == before + 1);
      this.commands("scriptevent bedrocknative:hud hide health");
      sleep(300L);
      this.check("and takes SetHud again", BedrockHud.hiddenElements().equals(Set.of(HudElement.HEALTH)));
      this.commands("scriptevent bedrocknative:hud resetall", "effect @s clear", "clear @s", "xp -1000L @s", "gamemode creative");
      BedrockNative.LOGGER.info("Dev quick test summary: {} checks, {} failed", checks, failed);
      BedrockNative.LOGGER.info("Dev quick test: done");
   }

   private void hud() {
      this.commands("scriptevent bedrocknative:hud keys");
      this.check("nothing hidden at the start", BedrockHud.hiddenElements().isEmpty());
      this.screenshot("quick-hud-all");

      for (Entry<HudElement, String> entry : SCRIPT_NAMES.entrySet()) {
         HudElement element = entry.getKey();
         this.commands("scriptevent bedrocknative:hud hide " + entry.getValue());
         sleep(400L);
         Map<HudElement, int[]> before = this.counts();
         if (element == HudElement.ITEM_TEXT_POPUP) {
            this.switchSlot();
         }

         sleep(700L);
         Map<HudElement, int[]> after = this.counts();
         boolean ownHidden = BedrockHud.hiddenElements().equals(Set.of(element));
         int[] own = delta(before, after, element);
         StringBuilder others = new StringBuilder();
         boolean othersDrawn = true;

         for (HudElement other : HudElement.values()) {
            if (other != element) {
               int[] change = delta(before, after, other);
               if (change[1] != 0) {
                  othersDrawn = false;
                  others.append(other).append(" skipped ").append(change[1]).append(' ');
               }
            }
         }

         this.check(
            element + " hidden alone (" + BedrockHud.lastRequest() + "): asked " + own[0] + ", skipped " + own[1] + " " + others,
            ownHidden && own[0] > 0 && own[0] == own[1] && othersDrawn
         );
         if (element == HudElement.HOTBAR || element == HudElement.PROGRESS_BAR || element == HudElement.CROSSHAIR) {
            this.screenshot("quick-hud-" + entry.getValue());
         }

         this.commands("scriptevent bedrocknative:hud reset " + entry.getValue());
         sleep(300L);
         this.check(element + " back after RESET (" + BedrockHud.lastRequest() + ")", BedrockHud.hiddenElements().isEmpty());
      }

      this.commands("scriptevent bedrocknative:hud hide armor health hunger");
      sleep(400L);
      this.check(
         "armor, health and hunger hidden together (" + BedrockHud.lastRequest() + "): " + BedrockHud.hiddenElements(),
         BedrockHud.hiddenElements().equals(Set.of(HudElement.ARMOR, HudElement.HEALTH, HudElement.FOOD_BAR))
      );
      this.screenshot("quick-hud-bars");
      this.commands("scriptevent bedrocknative:hud resetall");
      sleep(300L);
      this.check("resetall clears everything (" + BedrockHud.lastRequest() + "): " + BedrockHud.hiddenElements(), BedrockHud.hiddenElements().isEmpty());
      this.commands("scriptevent bedrocknative:hud hide");
      sleep(500L);
      BedrockNative.LOGGER.info("Dev quick test: hide without a list arrived as '{}', hidden now {}", BedrockHud.lastRequest(), BedrockHud.hiddenElements());
      this.check("hide without a list hides every element the HUD draws", BedrockHud.hiddenElements().containsAll(SCRIPT_NAMES.keySet()));
      this.screenshot("quick-hud-none");
      this.commands("scriptevent bedrocknative:hud reset");
      sleep(300L);
      BedrockNative.LOGGER.info("Dev quick test: reset without a list arrived as '{}', hidden now {}", BedrockHud.lastRequest(), BedrockHud.hiddenElements());
      this.check("reset without a list restores everything", BedrockHud.hiddenElements().isEmpty());
      this.commands("scriptevent bedrocknative:hud hide crosshair");
      sleep(300L);
      this.check("state is held for the connection", BedrockHud.hiddenElements().equals(Set.of(HudElement.CROSSHAIR)));
      BedrockHud.reset();
      this.check("reset clears it (disconnect, transfer, new login)", BedrockHud.hiddenElements().isEmpty());
      this.commands("scriptevent bedrocknative:hud resetall");
   }

   private void shake() {
      double[] still = this.sample();
      BedrockNative.LOGGER
         .info(
            "Dev quick test: camera at rest differs from the player by {} blocks, {} yaw, {} pitch",
            new Object[]{round(still[0]), round(still[1]), round(still[2])}
         );
      float[] player = this.playerLook();
      BedrockCameraShake.takePeakOffset();
      this.commands("camerashake add @s 2 3 positional");
      sleep(200L);
      BedrockNative.LOGGER.info("Dev quick test: shake received as {}", BedrockCameraShake.lastRequest());
      double[] worst = this.worstSample(1800L);
      this.screenshot("quick-shake-positional");
      float peak = BedrockCameraShake.takePeakOffset();
      this.check(
         "positional shake moves the camera (" + round(worst[0]) + " blocks, peak " + round(peak) + "), not its rotation (" + round(worst[1]) + " yaw)",
         worst[0] > 0.01 && worst[0] < 0.2 && worst[1] < 0.1 && peak > 0.01
      );
      this.waitFor(() -> !BedrockCameraShake.active(), 4000L);
      sleep(400L);
      double[] after = this.sample();
      this.check("camera is back after the 3 seconds (" + round(after[0]) + " blocks)", !this.supply(BedrockCameraShake::active) && after[0] < 0.01);
      BedrockCameraShake.takePeakOffset();
      this.commands("camerashake add @s 3 2 rotational");
      sleep(200L);
      BedrockNative.LOGGER.info("Dev quick test: shake received as {}", BedrockCameraShake.lastRequest());
      double[] rotation = this.worstSample(1200L);
      this.screenshot("quick-shake-rotational");
      float[] during = this.playerLook();
      this.check(
         "rotational shake turns the camera (" + round(rotation[1]) + " yaw, " + round(rotation[2]) + " pitch) not its position (" + round(rotation[0]) + ")",
         Math.max(rotation[1], rotation[2]) > 0.2 && rotation[0] < 0.02
      );
      this.check("the player's own rotation stays what PlayerAuthInput reports", player[0] == during[0] && player[1] == during[1]);
      this.waitFor(() -> !BedrockCameraShake.active(), 3000L);
      this.commands("camerashake add @s 4 30 positional");
      sleep(600L);
      this.check("a long shake is running", this.supply(BedrockCameraShake::active));
      this.commands("camerashake stop @s");
      sleep(400L);
      BedrockNative.LOGGER.info("Dev quick test: stop received as {}", BedrockCameraShake.lastRequest());
      this.check("STOP ends it at once", !this.supply(BedrockCameraShake::active));
   }

   private void toasts() {
      int before = BedrockToasts.shown();
      this.inject.accept(toast("Â§lÂ§eBedrockNative toast", "Plain Â§bcolouredÂ§r text from the server, long enough to wrap onto a second line of the toast"));
      this.inject.accept(toast("%gui.done", "{\"rawtext\":[{\"text\":\"Â§aRaw \"},{\"translate\":\"gui.cancel\"}]}"));
      this.inject.accept(toast("Title only", ""));
      sleep(1800L);
      this.screenshot("quick-toasts");
      this.check("three toasts shown (" + (BedrockToasts.shown() - before) + ")", BedrockToasts.shown() - before == 3);
      sleep(6000L);
   }

   private void credits() {
      int answered = BedrockCredits.answered();
      ShowCreditsPacket start = new ShowCreditsPacket();
      start.setRuntimeEntityId(1L);
      start.setStatus(Status.START_CREDITS);
      this.inject.accept(start);
      this.waitFor(() -> this.minecraft.gui.screen() instanceof WinScreen, 3000L);
      this.check("START_CREDITS opens the credits", this.supply(() -> this.minecraft.gui.screen() instanceof WinScreen));
      this.screenshot("quick-credits-start");
      this.minecraft.execute(() -> this.minecraft.gui.screen().keyPressed(new KeyEvent(57, 32, 0)));
      sleep(9000L);
      this.screenshot("quick-credits");
      this.inject.accept(start);
      sleep(300L);
      this.check(
         "a second START_CREDITS leaves the one screen",
         this.supply(() -> this.minecraft.gui.screen() instanceof WinScreen) && BedrockCredits.answered() == answered
      );
      this.minecraft.execute(() -> this.minecraft.gui.screen().onClose());
      this.waitFor(() -> BedrockCredits.answered() > answered, 3000L);
      sleep(500L);
      this.check(
         "closing them answers END_CREDITS once (" + (BedrockCredits.answered() - answered) + ") and returns to the game",
         BedrockCredits.answered() == answered + 1 && this.supply(() -> this.minecraft.gui.screen() == null)
      );
      this.commands("testfor @s");
      this.check("the server still talks to us after the answer", this.supply(() -> this.minecraft.player != null && this.minecraft.getConnection() != null));
   }

   private int x(int dx) {
      return this.base.getX() + dx;
   }

   private int y(int dy) {
      return this.base.getY() + dy;
   }

   private int z(int dz) {
      return this.base.getZ() + dz;
   }

   private BlockPos at(int dx, int dy, int dz) {
      return this.base.offset(dx, dy, dz);
   }

   private String pos(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private void arena(String mode) {
      this.base = this.supply(() -> this.minecraft.player.blockPosition());
      this.commands(
         "gamemode " + mode,
         "clear @s",
         "effect @s clear",
         "fill " + this.x(-4) + " " + this.y(-1) + " " + this.z(-4) + " " + this.x(4) + " " + this.y(-1) + " " + this.z(4) + " stone",
         "fill " + this.x(-4) + " " + this.y(0) + " " + this.z(-4) + " " + this.x(4) + " " + this.y(3) + " " + this.z(4) + " air",
         "tp @s " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 0.5)
      );
      this.minecraft.execute(() -> {
         this.minecraft.player.setYRot(0.0F);
         this.minecraft.player.setXRot(0.0F);
         this.minecraft.player.getInventory().setSelectedSlot(0);
      });
      sleep(600L);
   }

   private String ask(String command) {
      long before = this.logSize();
      this.commands(command);
      sleep(400L);
      return this.chatSince(before);
   }

   private long logSize() {
      try {
         return Files.size(this.log);
      } catch (IOException e) {
         return 0L;
      }
   }

   private String chatSince(long offset) {
      try (RandomAccessFile file = new RandomAccessFile(this.log.toFile(), "r")) {
         file.seek(Math.min(offset, file.length()));
         byte[] bytes = new byte[(int)(file.length() - file.getFilePointer())];
         file.readFully(bytes);
         StringBuilder chat = new StringBuilder();

         for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            int index = line.indexOf("[CHAT]");
            if (index >= 0) {
               chat.append(line.substring(index + 7).trim()).append(" | ");
            }
         }

         return chat.toString();
      } catch (IOException e) {
         return "";
      }
   }

   private void lookAt(Vec3 target) {
      this.minecraft.execute(() -> this.minecraft.player.lookAt(Anchor.EYES, target));
      sleep(300L);
   }

   private Entity find(Predicate<Entity> wanted, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (true) {
         Entity found = this.supply(() -> {
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               if (entity != this.minecraft.player && wanted.test(entity)) {
                  return entity;
               }
            }

            return null;
         });
         if (found != null || System.currentTimeMillis() > end) {
            return found;
         }

         sleep(150L);
      }
   }

   private boolean effectiveAutoJump() {
      return this.supply(() -> this.minecraft.player.isAutoJumpEnabled());
   }

   private void setOwnAutoJump(boolean on) {
      this.minecraft.execute(() -> this.minecraft.options.autoJump().set(on));
      sleep(300L);
   }

   private static UpdateAdventureSettingsPacket adventure(boolean noPvM, boolean noMvP, boolean immutable, boolean nameTags, boolean autoJump) {
      UpdateAdventureSettingsPacket packet = new UpdateAdventureSettingsPacket();
      packet.setNoPvM(noPvM);
      packet.setNoMvP(noMvP);
      packet.setImmutableWorld(immutable);
      packet.setShowNameTags(nameTags);
      packet.setAutoJump(autoJump);
      return packet;
   }

   private static UpdateClientInputLocksPacket inputLocks(int locks) {
      UpdateClientInputLocksPacket packet = new UpdateClientInputLocksPacket();
      packet.setLockComponentData(locks);
      return packet;
   }

   private void difficulty() {
      Difficulty initial = this.supply(() -> this.minecraft.level.getDifficulty());
      BedrockNative.LOGGER.info("Dev quick test: the difficulty at the join is {}", initial);

      for (Difficulty wanted : new Difficulty[]{Difficulty.HARD, Difficulty.PEACEFUL, Difficulty.NORMAL, Difficulty.EASY}) {
         this.commands("difficulty " + wanted.name().toLowerCase(Locale.ROOT));
         sleep(300L);
         Difficulty now = this.supply(() -> this.minecraft.level.getDifficulty());
         this.check("/difficulty " + wanted.name().toLowerCase(Locale.ROOT) + " reaches the client level (" + now + ")", now == wanted);
      }

      this.commands("difficulty " + initial.name().toLowerCase(Locale.ROOT));
   }

   private void adventureSettings() {
      this.arena("creative");
      String joined = BedrockAdventureSettings.lastRequest();
      this.check("the settings BDS sent at the join arrived (" + joined + ")", joined.contains("autoJump=true"));
      boolean ownAutoJump = this.supply(() -> this.minecraft.options.autoJump().get());
      this.setOwnAutoJump(true);
      this.check("the server's true leaves the player's own option in charge: on is on (" + this.effectiveAutoJump() + ")", this.effectiveAutoJump());
      this.setOwnAutoJump(false);
      this.check("and off stays off (" + this.effectiveAutoJump() + ")", !this.effectiveAutoJump());
      this.setOwnAutoJump(true);

      for (String mode : new String[]{"adventure", "spectator", "survival", "creative"}) {
         this.commands("gamemode " + mode);
         sleep(400L);
         BedrockNative.LOGGER.info("Dev quick test: BDS after /gamemode {} sent {}", mode, BedrockAdventureSettings.lastRequest());
      }

      this.inject.accept(adventure(false, false, false, false, false));
      sleep(300L);
      this.check(
         "the server's false forbids auto-jump ("
            + this.effectiveAutoJump()
            + ") without touching the option ("
            + this.supply(() -> this.minecraft.options.autoJump().get())
            + ")",
         !this.effectiveAutoJump() && this.supply(() -> this.minecraft.options.autoJump().get())
      );
      this.inject.accept(adventure(false, false, false, false, true));
      sleep(300L);
      this.check("and its true gives the player's own value back", this.effectiveAutoJump());
      this.setOwnAutoJump(false);
      this.inject.accept(adventure(false, false, false, false, false));
      sleep(300L);
      this.inject.accept(adventure(false, false, false, false, true));
      sleep(300L);
      this.check("a server's true never switches auto-jump on for a player who has it off (" + this.effectiveAutoJump() + ")", !this.effectiveAutoJump());
      this.setOwnAutoJump(ownAutoJump);
      BlockPos target = this.at(0, 0, 2);
      this.commands("setblock " + this.pos(target) + " stone", "replaceitem entity @s slot.hotbar 0 stone 16");
      sleep(500L);
      this.lookAt(Vec3.atCenterOf(target));
      this.inject.accept(adventure(false, false, true, false, true));
      sleep(300L);
      this.check("an immutable world arrives (" + BedrockAdventureSettings.lastRequest() + ")", this.supply(BedrockAdventureSettings::immutableWorld));
      boolean started = this.supply(() -> this.minecraft.gameMode.startDestroyBlock(target, Direction.NORTH));
      this.check("immutable: breaking does not even start (" + started + ")", !started);
      sleep(700L);
      String stays = this.ask("testforblock " + this.pos(target) + " stone");
      this.check("immutable: the server still has the stone (" + stays + ")", stays.contains("Successfully found"));
      BlockPos above = target.above();
      BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(target).add(0.0, 0.5, 0.0), Direction.UP, target, false);
      boolean predicted = this.supply(() -> {
         this.minecraft.gameMode.useItemOn(this.minecraft.player, InteractionHand.MAIN_HAND, top);
         return !this.minecraft.level.getBlockState(above).isAir();
      });
      this.check("immutable: placing is not predicted (" + predicted + ")", !predicted);
      sleep(700L);
      this.commands("setblock " + this.pos(above) + " air");
      this.inject.accept(adventure(false, false, false, false, true));
      sleep(300L);
      this.check("a mutable world again", !this.supply(BedrockAdventureSettings::immutableWorld));
      predicted = this.supply(() -> {
         this.minecraft.gameMode.useItemOn(this.minecraft.player, InteractionHand.MAIN_HAND, top);
         return this.minecraft.level.getBlockState(above).is(Blocks.STONE);
      });
      this.check("mutable: the placed stone is predicted at once (" + predicted + ")", predicted);
      sleep(700L);
      this.check("mutable: and the server has it", this.ask("testforblock " + this.pos(above) + " stone").contains("Successfully found"));
      started = this.supply(() -> this.minecraft.gameMode.startDestroyBlock(target, Direction.NORTH));
      this.check("mutable: breaking starts (" + started + ")", started);
      sleep(700L);
      this.check("mutable: the server's stone is gone", this.ask("testforblock " + this.pos(target) + " air").contains("Successfully found"));
      this.commands("setblock " + this.pos(above) + " air");
      this.screenshot("quick-immutable-world");
      this.commands("replaceitem entity @s slot.hotbar 0 diamond_sword 1", "summon chicken " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 3.5));
      Entity chicken = this.find(entity -> entity.getType() == EntityTypes.CHICKEN, 4000L);
      this.check("a chicken is there to hit", chicken != null);
      if (chicken != null) {
         this.inject.accept(adventure(true, false, false, false, true));
         sleep(300L);
         int blocked = BedrockAdventureSettings.blockedAttacks();
         this.attack();
         this.check(
            "no-PvM: the attack on the chicken is not sent (" + (BedrockAdventureSettings.blockedAttacks() - blocked) + " stopped)",
            BedrockAdventureSettings.blockedAttacks() == blocked + 1
         );
         sleep(900L);
         String alive = this.ask("testfor @e[type=chicken,r=12]");
         this.check("no-PvM: the server's chicken is alive (" + alive + ")", alive.contains("Found"));
         this.inject.accept(adventure(false, true, false, false, true));
         sleep(300L);
         blocked = BedrockAdventureSettings.blockedAttacks();
         boolean dead = false;

         for (int attempt = 0; attempt < 4 && !dead; attempt++) {
            this.attack();
            sleep(900L);
            dead = this.ask("testfor @e[type=chicken,r=12]").contains("No targets matched");
         }

         this.check(
            "without no-PvM (no-MvP alone changes nothing) the attack kills it ("
               + dead
               + ", "
               + (BedrockAdventureSettings.blockedAttacks() - blocked)
               + " stopped)",
            dead && BedrockAdventureSettings.blockedAttacks() == blocked
         );
         this.inject.accept(adventure(false, false, false, false, true));
         sleep(200L);
         this.commands("clear @s");
      }
   }

   private void attack() {
      Entity chicken = this.find(entity -> entity.getType() == EntityTypes.CHICKEN, 1000L);
      if (chicken != null) {
         this.lookAt(chicken.getBoundingBox().getCenter());
         this.minecraft.execute(() -> this.minecraft.gameMode.attack(this.minecraft.player, chicken));
         sleep(150L);
      }
   }

   private void nameTags() {
      this.arena("creative");
      this.commands("gamerule showtags true", "summon cow Bob " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 3.5));
      this.commands("effect @e[type=cow,r=12] slowness 999 255 true");
      Entity cow = this.find(entity -> entity.getType() == EntityTypes.COW, 4000L);
      this.check(
         "a named cow is there (" + (cow == null ? "none" : cow.getCustomName() + ", always visible " + cow.isCustomNameVisible()) + ")",
         cow != null && cow.hasCustomName()
      );
      if (cow != null) {
         this.lookAt(cow.getBoundingBox().getCenter());
         sleep(600L);
         this.check("its name tag shows with showtags on", this.tagOf(cow) != null);
         this.screenshot("quick-nametag-on");
         this.inject.accept(adventure(false, false, false, true, true));
         sleep(300L);
         this.check("the show-name-tags adventure flag (true) changes nothing", this.tagOf(cow) != null && BedrockAdventureSettings.showNameTags());
         this.inject.accept(adventure(false, false, false, false, true));
         sleep(300L);
         this.check("and false hides nothing either", this.tagOf(cow) != null && !BedrockAdventureSettings.showNameTags());
         int hidden = BedrockNameTags.hiddenTags();
         this.commands("gamerule showtags false");
         sleep(500L);
         this.check(
            "/gamerule showtags false hides the tag (" + this.tagOf(cow) + ")",
            BedrockNameTags.hidden() && this.tagOf(cow) == null && BedrockNameTags.hiddenTags() > hidden
         );
         this.screenshot("quick-nametag-off");
         this.commands("gamerule showtags true");
         sleep(500L);
         this.check("and true brings it back", !BedrockNameTags.hidden() && this.tagOf(cow) != null);
         this.commands("kill @e[type=cow,r=12]");
      }
   }

   private Component tagOf(Entity entity) {
      this.lookAt(entity.getBoundingBox().getCenter());
      return this.supply(() -> this.minecraft.getEntityRenderDispatcher().extractEntity(entity, 1.0F).nameTag);
   }

   private void press(String keys, boolean down) {
      this.minecraft.execute(() -> {
         Options options = this.minecraft.options;
         options.keyUp.setDown(down && keys.contains("w"));
         options.keyLeft.setDown(down && keys.contains("a"));
         options.keyDown.setDown(down && keys.contains("s"));
         options.keyRight.setDown(down && keys.contains("d"));
         options.keyJump.setDown(down && keys.contains("j"));
         options.keyShift.setDown(down && keys.contains("c"));
         options.keySprint.setDown(down && keys.contains("r"));
      });
   }

   private DevQuickTest.Walk walk(String keys, long millis) {
      Vec3 start = this.supply(() -> this.minecraft.player.position());
      int correctionsBefore = this.movement.count();
      Set<PlayerAuthInputData> flags = EnumSet.noneOf(PlayerAuthInputData.class);
      double vector = 0.0;
      double top = start.y;
      this.press(keys, true);
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         PlayerAuthInputPacket input = PlayerInputSender.lastBuilt;
         if (input != null) {
            flags.addAll(input.getInputData());
            vector = Math.max(
               vector,
               Math.max(
                  Math.hypot(input.getMotion().getX(), input.getMotion().getY()), Math.hypot(input.getRawMoveVector().getX(), input.getRawMoveVector().getY())
               )
            );
         }

         top = Math.max(top, this.supply(() -> this.minecraft.player.getY()));
         sleep(25L);
      }

      this.press(keys, false);
      sleep(500L);
      Vec3 stop = this.supply(() -> this.minecraft.player.position());
      int corrected = this.movement.count() - correctionsBefore;
      this.walkCorrections += corrected;
      return new DevQuickTest.Walk(Math.hypot(stop.x - start.x, stop.z - start.z), stop.z - start.z, top - start.y, flags, vector, corrected);
   }

   private void home() {
      this.commands("tp @s " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 0.5));
      this.minecraft.execute(() -> this.minecraft.player.setYRot(0.0F));
      sleep(600L);
   }

   private void lock(String category, boolean on) {
      this.commands("scriptevent bedrocknative:rules lock " + category + " " + (on ? "on" : "off"));
      sleep(300L);
   }

   private void lockCase(String category, int bit, String blockedKeys, String freeKeys, boolean blockedIsMove) {
      this.home();
      this.lock(category, true);
      this.check(
         category + " lock arrives as bit " + bit + " (locks " + BedrockInputLocks.locks() + ", " + BedrockInputLocks.lastRequest() + ")",
         BedrockInputLocks.locks() == bit
      );
      DevQuickTest.Walk blocked = this.walk(blockedKeys, 700L);
      BedrockNative.LOGGER.info("Dev quick test: under the {} lock holding {}: {}", new Object[]{category, blockedKeys, blocked.describe()});
      Set<PlayerAuthInputData> flagsOf = blockedIsMove ? MOVE_FLAGS : (category.equals("jump") ? JUMP_FLAGS : SNEAK_FLAGS);
      if (blockedIsMove) {
         this.check(
            category + " lock: " + blockedKeys + " moves nothing and reports no direction flag, a zero vector",
            blocked.horizontal() < 0.05 && !blocked.has(flagsOf) && blocked.vector() == 0.0
         );
      } else if (category.equals("jump")) {
         this.check("jump lock: the jump key does nothing and sets no jump flag", blocked.rise() < 0.05 && !blocked.has(JUMP_FLAGS));
      } else {
         this.check("sneak lock: the sneak key sets no sneak flag", !blocked.has(SNEAK_FLAGS));
      }

      this.home();
      DevQuickTest.Walk free = this.walk(freeKeys, 700L);
      BedrockNative.LOGGER.info("Dev quick test: under the {} lock holding {}: {}", new Object[]{category, freeKeys, free.describe()});
      if (freeKeys.equals("j")) {
         this.check(category + " lock: jumping still works (rose " + round(free.rise()) + ")", free.rise() > 0.3 && free.has(JUMP_FLAGS));
      } else if (freeKeys.equals("c")) {
         this.check(category + " lock: sneaking still works", free.has(SNEAK_FLAGS));
      } else {
         this.check(category + " lock: " + freeKeys + " still moves (" + round(free.horizontal()) + ")", free.horizontal() > 1.0);
      }

      this.lock(category, false);
      this.check(category + " unlocked: no lock left (" + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 0);
   }

   private void freeze() {
      this.arena("survival");
      long logStart = this.logSize();
      Vec3 start = this.position();
      int corrections = this.movement.count();
      this.commands("scriptevent bedrocknative:rules knock 0 0.6 0.3");
      sleep(1200L);
      double free = horizontal(start, this.position());
      int freeCorrections = this.movement.count() - corrections;
      this.home();
      this.commands("inputpermission set @s movement disabled");
      start = this.position();
      corrections = this.movement.count();
      this.commands("scriptevent bedrocknative:rules knock 0 0.6 0.3");
      sleep(1200L);
      double locked = horizontal(start, this.position());
      int lockCorrections = this.movement.count() - corrections;
      BedrockNative.LOGGER
         .info(
            "Dev quick test: freeze, the server's knockback moved the player {} unlocked ({} corrections) and {} under the movement lock ({} corrections)",
            new Object[]{round(free), freeCorrections, round(locked), lockCorrections}
         );
      this.check("movement lock: the server's knockback still moves the player (" + round(locked) + ")", locked > 0.3);
      this.check(
         "movement lock: as far as without the lock ("
            + round(locked)
            + " and "
            + round(free)
            + ") and BDS, which simulates the locked player, corrects no more ("
            + lockCorrections
            + " and "
            + freeCorrections
            + ")",
         Math.abs(locked - free) < 0.05 && lockCorrections <= Math.max(1, freeCorrections)
      );
      this.commands("inputpermission set @s movement enabled");
      this.home();
      long runtimeId = BedrockLocalPlayer.runtimeId();
      this.inject.accept(localFlag(runtimeId, EntityFlag.NO_AI, true));
      sleep(300L);
      this.check("NO_AI arrives: the player is held", BedrockMovement.immobile());
      this.inject.accept(localFlag(runtimeId, EntityFlag.BLOCKING, false));
      sleep(200L);
      this.check("a SetActorData with only FLAGS_2 (BLOCKING) keeps it held", BedrockMovement.immobile());
      start = this.position();
      corrections = this.movement.count();
      this.inject.accept(localMotion(runtimeId, 0.0F, 0.4F, 0.8F));
      sleep(1000L);
      Vec3 held = this.position();
      this.check("NO_AI: a knockback moves nothing (" + round(held.distanceTo(start)) + ")", held.distanceTo(start) < 0.01);
      this.check(
         "NO_AI: and BDS, which never knocked it, has nothing to correct (" + (this.movement.count() - corrections) + ")", this.movement.count() == corrections
      );
      this.commands("tp @s " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 1.5));
      Vec3 teleported = this.position();
      this.check("NO_AI: a teleport still moves it (" + round(teleported.z - start.z) + ")", Math.abs(teleported.z - start.z - 1.0) < 0.05);
      String lines = this.logSince(logStart);
      this.check("the freeze watch logged NO_AI on", lines.contains("Freeze watch: flags NO_AI on"));
      this.check(
         "the freeze watch logged the knockback while held", lines.contains("Freeze watch: SetActorMotion (0.0000, 0.4000, 0.8000) tick 0 while held by NO_AI")
      );
      this.check("the freeze watch logged the teleport while held", lines.contains("Freeze watch: MovePlayer"));
      this.check("the freeze watch logged the movement lock", lines.contains("Freeze watch: input locks 0x0 [] -> 0x4 [movement]"));
      this.inject.accept(localFlag(runtimeId, EntityFlag.NO_AI, false));
      sleep(300L);
      this.check("NO_AI off releases the player", !BedrockMovement.immobile());
      start = this.position();
      corrections = this.movement.count();
      PlayerAuthInputPacket before = PlayerInputSender.lastBuilt;
      this.inject.accept(localMotion(runtimeId, 0.0F, 0.4F, 0.8F));
      double farthest = 0.0;

      for (long end = System.currentTimeMillis() + 800L; System.currentTimeMillis() < end; sleep(5L)) {
         PlayerAuthInputPacket input = PlayerInputSender.lastBuilt;
         if (input != null && input != before) {
            Vector3f eyes = input.getPosition();
            farthest = Math.max(farthest, Math.hypot(eyes.getX() - start.x, eyes.getZ() - start.z));
         }
      }

      this.check(
         "released, the same knockback moves the player (reported up to "
            + round(farthest)
            + ", "
            + (this.movement.count() - corrections)
            + " corrections from BDS, the negative control)",
         farthest > 0.3
      );
      sleep(1000L);
      this.home();
   }

   private Vec3 position() {
      return this.supply(() -> this.minecraft.player.position());
   }

   private static double horizontal(Vec3 from, Vec3 to) {
      return Math.hypot(to.x - from.x, to.z - from.z);
   }

   private static SetEntityDataPacket localFlag(long runtimeId, EntityFlag flag, boolean on) {
      SetEntityDataPacket packet = new SetEntityDataPacket();
      packet.setRuntimeEntityId(runtimeId);
      packet.getMetadata().setFlag(flag, on);
      return packet;
   }

   private static SetEntityMotionPacket localMotion(long runtimeId, float x, float y, float z) {
      SetEntityMotionPacket packet = new SetEntityMotionPacket();
      packet.setRuntimeEntityId(runtimeId);
      packet.setMotion(Vector3f.from(x, y, z));
      return packet;
   }

   private String logSince(long offset) {
      try (RandomAccessFile file = new RandomAccessFile(this.log.toFile(), "r")) {
         file.seek(Math.min(offset, file.length()));
         byte[] bytes = new byte[(int)(file.length() - file.getFilePointer())];
         file.readFully(bytes);
         return new String(bytes, StandardCharsets.UTF_8);
      } catch (IOException e) {
         return "";
      }
   }

   private void inputLocks() {
      this.arena("survival");
      this.walkCorrections = 0;
      int corrections = this.movement.count();
      DevQuickTest.Walk baseline = this.walk("wjc", 700L);
      BedrockNative.LOGGER.info("Dev quick test: without a lock holding w, jump, sneak: {}", baseline.describe());
      this.check(
         "no lock: the keys move, jump and sneak",
         baseline.horizontal() > 0.3 && baseline.rise() > 0.3 && baseline.has(MOVE_FLAGS) && baseline.has(SNEAK_FLAGS)
      );
      this.home();
      this.commands("inputpermission set @s movement disabled");
      sleep(300L);
      this.check("/inputpermission movement disabled arrives as bit 4 (locks " + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 4);
      DevQuickTest.Walk still = this.walk("wacjr", 900L);
      BedrockNative.LOGGER.info("Dev quick test: under the movement lock holding w, a, sneak, jump, sprint: {}", still.describe());
      this.check(
         "movement lock: nothing moves, jumps or sneaks, no direction, jump or sneak flag, a zero vector",
         still.horizontal() < 0.05
            && still.rise() < 0.05
            && !still.has(MOVE_FLAGS)
            && !still.has(JUMP_FLAGS)
            && !still.has(SNEAK_FLAGS)
            && still.vector() == 0.0
      );
      this.screenshot("quick-lock-movement");
      this.commands("inputpermission set @s movement enabled");
      sleep(300L);
      this.check("movement enabled: no lock (" + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 0);
      this.home();
      this.lockCase("lateralmovement", 16, "w", "j", true);
      this.lockCase("sneak", 32, "c", "w", false);
      this.lockCase("jump", 64, "j", "w", false);
      this.lockCase("moveforward", 512, "w", "s", true);
      this.lockCase("movebackward", 1024, "s", "w", true);
      this.lockCase("moveleft", 2048, "a", "d", true);
      this.lockCase("moveright", 4096, "d", "a", true);
      BedrockNative.LOGGER
         .info(
            "Dev quick test: {} server movement corrections in all, {} of them while keys were held (the others follow the /tp and lock commands)",
            this.movement.count() - corrections,
            this.walkCorrections
         );
      this.check("the server never corrected our movement while keys were held, locked or not (" + this.walkCorrections + ")", this.walkCorrections == 0);
      float yaw = this.supply(() -> this.minecraft.player.getYRot());
      this.mouseTurn(40.0);
      float turned = this.supply(() -> this.minecraft.player.getYRot());
      this.check("no lock: the mouse turns the player (" + yaw + " to " + turned + ")", Math.abs(turned - yaw) > 0.5F);
      this.lock("camera", true);
      this.check("camera lock arrives as bit 2 (" + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 2);
      this.mouseTurn(40.0);
      float locked = this.supply(() -> this.minecraft.player.getYRot());
      this.check("camera lock: the mouse turns nothing (" + turned + " to " + locked + ")", locked == turned);
      DevQuickTest.Walk forward = this.walk("w", 500L);
      this.check("camera lock: walking still works (" + round(forward.horizontal()) + ")", forward.horizontal() > 0.5);
      this.lock("camera", false);
      this.mouseTurn(40.0);
      this.check("camera unlocked: the mouse turns again", Math.abs(this.supply(() -> this.minecraft.player.getYRot()) - locked) > 0.5F);
      this.home();
      this.commands("summon boat " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 2.5));
      Entity boat = this.find(entity -> entity instanceof AbstractBoat, 4000L);
      this.check("a boat is there", boat != null);
      if (boat != null) {
         this.lock("mount", true);
         this.check("mount lock arrives as bit 128 (" + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 128);
         this.interact(boat);
         sleep(1200L);
         this.check("mount lock: using the boat does not get the player on", !this.supply(() -> this.minecraft.player.isPassenger()));
         this.lock("mount", false);
         this.interact(boat);
         this.waitFor(() -> this.minecraft.player.isPassenger(), 3000L);
         this.check("unlocked: it does", this.supply(() -> this.minecraft.player.isPassenger()));
         this.lock("dismount", true);
         this.check("dismount lock arrives as bit 256 (" + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 256);
         this.press("c", true);
         sleep(700L);
         this.press("c", false);
         sleep(500L);
         this.check("dismount lock: sneaking does not get the player off", this.supply(() -> this.minecraft.player.isPassenger()));
         this.lock("dismount", false);
         this.press("c", true);
         this.waitFor(() -> !this.minecraft.player.isPassenger(), 3000L);
         this.press("c", false);
         sleep(500L);
         this.check("unlocked: sneaking gets the player off", !this.supply(() -> this.minecraft.player.isPassenger()));
         this.commands("kill @e[type=boat,r=12]");
      }

      this.commands("scriptevent bedrocknative:rules unlockall");
      sleep(300L);
      this.check("everything unlocked at the end (" + BedrockInputLocks.locks() + ")", BedrockInputLocks.locks() == 0);
   }

   private void mouseTurn(double pixels) {
      this.supply(() -> {
         try {
            Field field = MouseHandler.class.getDeclaredField("accumulatedDX");
            field.setAccessible(true);
            field.setDouble(this.minecraft.mouseHandler, pixels);
            Method turn = MouseHandler.class.getDeclaredMethod("turnPlayer", double.class);
            turn.setAccessible(true);
            turn.invoke(this.minecraft.mouseHandler, 0.05);
            return null;
         } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
         }
      });
      sleep(150L);
   }

   private void interact(Entity entity) {
      this.minecraft
         .execute(
            () -> this.minecraft.gameMode.interact(this.minecraft.player, entity, new EntityHitResult(entity, entity.position()), InteractionHand.MAIN_HAND)
         );
      sleep(200L);
   }

   private void picks() {
      this.arena("creative");
      BlockPos emerald = this.at(0, 0, 2);
      BlockPos chest = this.at(2, 0, 2);
      this.commands(
         "setblock " + this.pos(emerald) + " emerald_block",
         "setblock " + this.pos(chest) + " chest",
         "replaceitem block " + this.pos(chest) + " slot.container 0 diamond 3"
      );
      sleep(500L);
      this.lookAt(Vec3.atCenterOf(emerald));
      boolean localPick = this.supply(() -> {
         this.minecraft.gameMode.handlePickItemFromBlock(emerald, false);
         return this.minecraft.player.getInventory().hasAnyMatching(stack -> stack.is(Items.EMERALD_BLOCK));
      });
      this.check("middle-click on a block: Java picks nothing locally", !localPick);
      sleep(800L);
      BedrockNative.LOGGER.info("Dev quick test: pick request sent: {}", BedrockInteractions.lastPick);
      this.check(
         "the BlockPickRequest names the block, no data and hotbar slot 9 (" + BedrockInteractions.lastPick + ")",
         BedrockInteractions.lastPick.contains("blockPosition=(" + emerald.getX() + ", " + emerald.getY() + ", " + emerald.getZ() + ")")
            && BedrockInteractions.lastPick.contains("addUserData=false")
            && BedrockInteractions.lastPick.contains("hotbarSlot=9")
      );
      String has = this.ask("testfor @s[hasitem={item=emerald_block}]");
      this.check("creative: the server gave the emerald block (" + has + ")", has.contains("Found"));
      this.check(
         "and the client holds it ("
            + this.supply(() -> this.minecraft.player.getInventory().hasAnyMatching(stack -> stack.is(Items.EMERALD_BLOCK)))
            + ", main hand "
            + this.supply(() -> this.minecraft.player.getMainHandItem().getItem())
            + ")",
         this.supply(() -> this.minecraft.player.getInventory().hasAnyMatching(stack -> stack.is(Items.EMERALD_BLOCK)))
      );
      this.screenshot("quick-pick-block");
      this.commands("clear @s");
      sleep(300L);
      this.supply(() -> {
         this.minecraft.gameMode.handlePickItemFromBlock(chest, true);
         return null;
      });
      sleep(800L);
      this.check("ctrl asks for the block data in creative (" + BedrockInteractions.lastPick + ")", BedrockInteractions.lastPick.contains("addUserData=true"));
      this.check(
         "the picked chest arrived (" + this.ask("testfor @s[hasitem={item=chest}]") + ")", this.ask("testfor @s[hasitem={item=chest}]").contains("Found")
      );
      this.commands("summon cow " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 3.5), "clear @s");
      Entity cow = this.find(entity -> entity.getType() == EntityTypes.COW, 4000L);
      this.check("a cow to pick", cow != null);
      if (cow != null) {
         this.lookAt(cow.getBoundingBox().getCenter());
         this.supply(() -> {
            this.minecraft.gameMode.handlePickItemFromEntity(cow, false);
            return null;
         });
         sleep(800L);
         BedrockNative.LOGGER.info("Dev quick test: pick request sent: {}", BedrockInteractions.lastPick);
         this.check(
            "the ActorPickRequest names the cow and hotbar slot 9 (" + BedrockInteractions.lastPick + ")",
            BedrockInteractions.lastPick.contains("EntityPickRequestPacket")
               && BedrockInteractions.lastPick.contains("hotbarSlot=9")
               && BedrockInteractions.lastPick.contains("withData=false")
         );
         String egg = this.ask("testfor @s[hasitem={item=cow_spawn_egg}]");
         this.check("creative: the server gave a cow spawn egg (" + egg + ")", egg.contains("Found"));
         this.screenshot("quick-pick-entity");
         this.commands("gamemode survival", "clear @s", "give @s stone 4");
         sleep(500L);
         String before = BedrockInteractions.lastPick;
         this.supply(() -> {
            this.minecraft.gameMode.handlePickItemFromEntity(cow, false);
            return null;
         });
         sleep(500L);
         this.check("survival: no entity pick is sent", BedrockInteractions.lastPick.equals(before));
         this.commands("kill @e[type=cow,r=12]");
      }

      BlockPos stone = this.at(0, 0, 3);
      this.commands("setblock " + this.pos(stone) + " stone", "clear @s", "replaceitem entity @s slot.inventory 5 stone 7");
      sleep(500L);
      this.supply(() -> {
         this.minecraft.gameMode.handlePickItemFromBlock(stone, true);
         return null;
      });
      sleep(900L);
      this.check("survival: ctrl sends no block data (" + BedrockInteractions.lastPick + ")", BedrockInteractions.lastPick.contains("addUserData=false"));
      String hand = this.ask("testfor @s[hasitem={item=stone,quantity=7,location=slot.weapon.mainhand}]");
      this.check("survival: the server moved the stone into the hand (" + hand + ")", hand.contains("Found"));
      this.check(
         "and the client's hand has it (" + this.supply(() -> this.minecraft.player.getMainHandItem()) + ")",
         this.supply(() -> this.minecraft.player.getMainHandItem().is(Items.STONE))
      );
      this.commands(
         "setblock " + this.pos(stone) + " air",
         "setblock " + this.pos(emerald) + " air",
         "setblock " + this.pos(chest) + " air",
         "clear @s",
         "gamemode creative"
      );
      this.serverSelection();
   }

   private void serverSelection() {
      this.commands("gamemode survival", "clear @s", "replaceitem entity @s slot.hotbar 0 stone 1", "replaceitem entity @s slot.hotbar 3 emerald 1");
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
      sleep(800L);
      String before = this.ask("testfor @s[hasitem={item=stone,location=slot.weapon.mainhand}]");
      this.check("the server's hand starts on the stone (" + before + ")", before.contains("Found"));
      PlayerHotbarPacket select = new PlayerHotbarPacket();
      select.setSelectedHotbarSlot(3);
      select.setContainerId(0);
      select.setSelectHotbarSlot(true);
      this.inject.accept(select);
      sleep(800L);
      this.check(
         "the client moved to slot 3 (" + this.supply(() -> this.minecraft.player.getInventory().getSelectedSlot()) + ")",
         this.supply(() -> this.minecraft.player.getInventory().getSelectedSlot()) == 3
      );
      String after = this.ask("testfor @s[hasitem={item=emerald,location=slot.weapon.mainhand}]");
      this.check("and reported it: the server's hand is on the emerald (" + after + ")", after.contains("Found"));
      this.commands("clear @s", "gamemode creative");
   }

   private static LevelEventGenericPacket sleepers(int able, int total, int sleeping) {
      LevelEventGenericPacket packet = new LevelEventGenericPacket();
      packet.setType(LevelEvent.SLEEPING_PLAYERS);
      packet.setTag(NbtMap.builder().putInt("ableToSleep", able).putInt("overworldPlayerCount", total).putInt("sleepingPlayerCount", sleeping).build());
      return packet;
   }

   private void genericEvents() {
      int shown = BedrockLevelRules.noticesShown();
      this.inject.accept(sleepers(1, 2, 1));
      sleep(400L);
      this.check(
         "1 of 2 asleep: \"" + BedrockLevelRules.lastNotice() + "\"",
         BedrockLevelRules.noticesShown() == shown + 1 && "1/2 players sleeping".equals(BedrockLevelRules.lastNotice())
      );
      this.screenshot("quick-sleeping-notice");
      this.inject.accept(sleepers(1, 2, 2));
      sleep(300L);
      this.check("all asleep: \"" + BedrockLevelRules.lastNotice() + "\"", "Sleeping through this night".equals(BedrockLevelRules.lastNotice()));
      this.inject.accept(sleepers(0, 2, 1));
      sleep(300L);
      this.check("not possible: \"" + BedrockLevelRules.lastNotice() + "\"", "No amount of rest can pass this night".equals(BedrockLevelRules.lastNotice()));
      shown = BedrockLevelRules.noticesShown();
      this.inject.accept(sleepers(1, 2, 0));
      sleep(300L);
      this.check("nobody asleep: no notice", BedrockLevelRules.noticesShown() == shown);
      sleep(2500L);
      this.check("the notice is not repeated once nobody sleeps", BedrockLevelRules.noticesShown() == shown);
      int ignored = BedrockLevelRules.ignoredEvents();
      LevelEventGenericPacket other = new LevelEventGenericPacket();
      other.setType(LevelEvent.START_RAINING);
      other.setTag(NbtMap.EMPTY);
      this.inject.accept(other);
      sleep(300L);
      this.check(
         "another generic event is ignored without an error", BedrockLevelRules.ignoredEvents() == ignored + 1 && BedrockLevelRules.noticesShown() == shown
      );
   }

   private void dimensionData() {
      this.arena("creative");
      BlockPos stone = this.at(1, 0, 1);
      this.commands("setblock " + this.pos(stone) + " stone");
      int minY = this.supply(() -> this.minecraft.level.getMinY());
      int height = this.supply(() -> this.minecraft.level.getHeight());
      BedrockNative.LOGGER.info("Dev quick test: the overworld has minY {} and height {} before DimensionData", minY, height);
      DimensionDataPacket packet = new DimensionDataPacket();
      packet.getDefinitions().add(new DimensionDefinition("minecraft:overworld", 256, -32, 1, 0, null, "", 192, true));
      this.inject.accept(packet);
      sleep(300L);
      this.check(
         "DimensionData alone changes nothing yet (minY " + this.supply(() -> this.minecraft.level.getMinY()) + ")",
         this.supply(() -> this.minecraft.level.getMinY()) == minY
      );
      this.commands("execute in nether run tp @s 0 100 0");
      this.waitFor(() -> this.minecraft.level.dimension() == Level.NETHER && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(1000L);
      this.check(
         "the nether keeps its own heights (minY " + this.supply(() -> this.minecraft.level.getMinY()) + ")",
         this.supply(() -> this.minecraft.level.getMinY()) == 0
      );
      this.commands("execute in overworld run tp @s " + (this.x(0) + 0.5) + " " + this.y(0) + " " + (this.z(0) + 0.5));
      this.waitFor(() -> this.minecraft.level.dimension() == Level.OVERWORLD && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(4000L);
      int newMin = this.supply(() -> this.minecraft.level.getMinY());
      int newHeight = this.supply(() -> this.minecraft.level.getHeight());
      this.check("back in the overworld its heights are the server's (minY " + newMin + ", height " + newHeight + ")", newMin == -32 && newHeight == 288);
      int floor = this.supply(() -> {
         for (int y = this.minecraft.level.getMaxY(); y >= this.minecraft.level.getMinY(); y--) {
            if (!this.minecraft.level.getBlockState(new BlockPos(stone.getX(), y, stone.getZ())).isAir()) {
               return y;
            }
         }

         return Integer.MIN_VALUE;
      });
      BedrockNative.LOGGER
         .info(
            "Dev quick test: in the column of the stone the highest block is at y {} (the stone is at {}); the level spans {} to {}",
            new Object[]{floor, stone.getY(), this.supply(() -> this.minecraft.level.getMinY()), this.supply(() -> this.minecraft.level.getMaxY())}
         );
      this.check(
         "and the chunks line up: the stone is where it was (" + this.supply(() -> this.minecraft.level.getBlockState(stone)) + ")",
         this.supply(() -> this.minecraft.level.getBlockState(stone).is(Blocks.STONE))
      );
      this.screenshot("quick-dimension-heights");
      this.commands("setblock " + this.pos(stone) + " air");
   }

   private static ToastRequestPacket toast(String title, String content) {
      ToastRequestPacket packet = new ToastRequestPacket();
      packet.setTitle(title);
      packet.setContent(content);
      return packet;
   }

   private void switchSlot() {
      this.minecraft.execute(() -> {
         Inventory inventory = this.minecraft.player.getInventory();
         inventory.setSelectedSlot(inventory.getSelectedSlot() == 0 ? 1 : 0);
      });
   }

   private Map<HudElement, int[]> counts() {
      Map<HudElement, int[]> counts = new EnumMap<>(HudElement.class);

      for (HudElement element : HudElement.values()) {
         counts.put(element, BedrockHud.counts(element));
      }

      return counts;
   }

   private static int[] delta(Map<HudElement, int[]> before, Map<HudElement, int[]> after, HudElement element) {
      return new int[]{after.get(element)[0] - before.get(element)[0], after.get(element)[1] - before.get(element)[1]};
   }

   private double[] sample() {
      return this.supply(
         () -> {
            Camera camera = this.minecraft.gameRenderer.mainCamera();
            LocalPlayer player = this.minecraft.player;
            return new double[]{
               camera.position().distanceTo(player.getEyePosition()),
               Math.abs(Mth.wrapDegrees(camera.yRot() - player.getYRot())),
               Math.abs(camera.xRot() - player.getXRot())
            };
         }
      );
   }

   private double[] worstSample(long millis) {
      double[] worst = new double[3];
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         double[] now = this.sample();

         for (int i = 0; i < worst.length; i++) {
            worst[i] = Math.max(worst[i], now[i]);
         }

         sleep(15L);
      }

      return worst;
   }

   private float[] playerLook() {
      return this.supply(() -> new float[]{this.minecraft.player.getYRot(), this.minecraft.player.getXRot()});
   }

   private void check(String what, boolean ok) {
      checks++;
      if (!ok) {
         failed++;
      }

      BedrockNative.LOGGER.info("Dev quick test {}: {}", ok ? "ok" : "FAILED", what);
   }

   private static double round(double value) {
      return Math.round(value * 1000.0) / 1000.0;
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.supply(condition::getAsBoolean)) {
         sleep(100L);
      }
   }

   private <T> T supply(Supplier<T> value) {
      return CompletableFuture.supplyAsync(value, this.minecraft::execute).join();
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.minecraft.execute(() -> {
            BedrockNative.LOGGER.info("Dev test command: /{}", command);
            this.minecraft.getConnection().sendCommand(command);
         });
         sleep(400L);
      }

      sleep(300L);
   }

   private void screenshot(String name) {
      this.minecraft.execute(() -> this.minecraft.gui.hud.getChat().clearMessages(false));
      sleep(250L);
      this.minecraft
         .execute(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   static {
      SCRIPT_NAMES.put(HudElement.ARMOR, "armor");
      SCRIPT_NAMES.put(HudElement.HEALTH, "health");
      SCRIPT_NAMES.put(HudElement.FOOD_BAR, "hunger");
      SCRIPT_NAMES.put(HudElement.AIR_BUBBLES_BAR, "airbubbles");
      SCRIPT_NAMES.put(HudElement.VEHICLE_HEALTH, "horsehealth");
      SCRIPT_NAMES.put(HudElement.CROSSHAIR, "crosshair");
      SCRIPT_NAMES.put(HudElement.HOTBAR, "hotbar");
      SCRIPT_NAMES.put(HudElement.PROGRESS_BAR, "progressbar");
      SCRIPT_NAMES.put(HudElement.EFFECTS_BAR, "statuseffects");
      SCRIPT_NAMES.put(HudElement.ITEM_TEXT_POPUP, "itemtext");
   }

   private record Walk(double horizontal, double dz, double rise, Set<PlayerAuthInputData> flags, double vector, int corrections) {
      boolean has(Set<PlayerAuthInputData> wanted) {
         return this.flags.stream().anyMatch(wanted::contains);
      }

      String describe() {
         return String.format(
            "moved %.2f (z %+.2f), rose %.2f, flags %s, move vector %.2f, %d corrections",
            this.horizontal,
            this.dz,
            this.rise,
            this.flags,
            this.vector,
            this.corrections
         );
      }
   }
}
