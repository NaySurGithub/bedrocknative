package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.perf.StallSampler;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.client.world.light.ThreadedLight;
import net.bedrocknative.client.world.light.ThreadedLightEngine;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.perf.FrameStats;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.perf.MeshStats;
import net.bedrocknative.world.block.custom.BlockProblems;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

final class DevLookTest {
   private static final int SETTLE_MS = Integer.getInteger("bedrocknative.lookSettle", 10000);
   private static final int WALK_MS = 3000;
   private static final int ENTITY_VIEWS = 6;
   private static final String TOUR = System.getProperty("bedrocknative.lookTour", "");
   private static final String OPEN = System.getProperty("bedrocknative.lookOpen", "");
   private static final int JUMP_ROUNDS = Integer.getInteger("bedrocknative.lookJumps", 0);
   private static final String PROBE = System.getProperty("bedrocknative.lookProbe", "");
   private static final String MODES = System.getProperty("bedrocknative.lookProbeModes", "walk,sprint,jump,sprint-jump,sprint-strafe,sprint-strafe-jump");
   private final Minecraft minecraft;
   private final MovementStats movement;
   private final String name;
   private int shots;

   DevLookTest(Minecraft minecraft, MovementStats movement) {
      this.minecraft = minecraft;
      this.movement = movement;
      this.name = System.getProperty("bedrocknative.devTest", "look");
   }

   void run() {
      DevCpuSampler cpu = new DevCpuSampler();
      MeshStats.Snapshot meshesBefore = MeshStats.snapshot();

      try {
         Object session = this.onClient(() -> this.minecraft.getConnection());
         sleep(SETTLE_MS);
         if (session == null || this.onClient(() -> this.minecraft.getConnection()) != session || this.movement.ended()) {
            log("the session that started this run is gone (transfer); this run stops");
            return;
         }

         cpu.start();
         meshesBefore = MeshStats.snapshot();
         this.run(() -> {
            FrameTimes.watch().startRecording(this.name);
            StallSampler.start();
         });
         this.command();
         this.renderDistances();
         this.inventory();
         if (System.getProperty("bedrocknative.lookInventory") != null && Set.of("only", "armour").contains(System.getProperty("bedrocknative.lookInventory"))) {
            this.frames(cpu, meshesBefore);
            log("done");
            return;
         }

         this.report("spawn");
         if (JUMP_ROUNDS > 0) {
            this.screenshot("jumps-spawn");

            for (int round = 1; round <= JUMP_ROUNDS; round++) {
               this.jumps(round);
            }

            this.report("end");
            log("done");
            return;
         }

         if (!PROBE.isEmpty()) {
            this.probe();
            this.report("end");
            log("done");
            return;
         }

         if (!OPEN.isEmpty()) {
            this.open();
            this.report("end");
            log("done");
            return;
         }

         this.views("spawn");
         this.run(() -> this.minecraft.options.keyUp.setDown(true));
         sleep(1500L);
         this.screenshot("walking");
         sleep(1500L);
         this.run(() -> this.minecraft.options.keyUp.setDown(false));
         sleep(1000L);
         this.report("after walking");
         this.views("walked");
         this.entities();
         this.tour();
         this.report("end");
         this.frames(cpu, meshesBefore);
         this.missingTexturePixels();
      } catch (RuntimeException var5) {
         BedrockNative.LOGGER.error("Dev look test failed", var5);
      }

      log("done");
   }

   private void frames(DevCpuSampler cpu, MeshStats.Snapshot meshesBefore) {
      cpu.stop();
      String[] stalls = new String[1];
      FrameWatch.Recording recording = this.onClient(() -> {
         stalls[0] = StallSampler.stop();
         return FrameTimes.watch().stopRecording();
      });
      if (recording != null) {
         FrameStats inGame = recording.inGame();
         String lightThread = this.onClient(() -> {
            ThreadedLightEngine engine = ThreadedLight.of(this.minecraft.level);
            return engine == null ? "light on the client thread" : engine.stats().describe();
         });
         log(
            String.format(
               Locale.ROOT,
               "frames of the visit: in game %s; over 10/16/33/50 ms: %d/%d/%d/%d of %d; longest client tick %.1f ms; %d columns applied, peak %d waiting; apply %.0f ms (%s), light %.0f ms, light updates %.0f ms; %s; %s; client thread parts: %s; GC %.0f ms; loading screen frames %d, view rebuild frames %d; CPU other processes %.0f%%, ours %.0f%%",
               inGame.summary(),
               inGame.over(10000000L),
               inGame.over(16700000L),
               inGame.over(33300000L),
               inGame.over(50000000L),
               inGame.count(),
               recording.ticks().maxMillis(),
               recording.columns(),
               recording.maxWaiting(),
               recording.applyMillis(),
               recording.applyParts().isEmpty() ? "no parts" : recording.applyParts(),
               recording.lightMillis(),
               recording.bucketMillis(FrameWatch.Bucket.LIGHT_UPDATES),
               lightThread,
               MeshStats.snapshot().since(meshesBefore).describe(),
               recording.bucketsLargestFirst(10),
               recording.gcMillis(),
               recording.loading().count(),
               recording.viewRebuilds().count(),
               cpu.meanOthers() * 100.0,
               cpu.meanOurs() * 100.0
            )
         );
         log("slow frames: {}", recording.slowFrames(8));
         int shown = 0;

         for (FrameWatch.Hitch hitch : recording.worst()) {
            if (shown++ == 4) {
               break;
            }

            log(
               String.format(
                  Locale.ROOT,
                  "hitch %.0f ms%s: %s",
                  hitch.frameMillis(),
                  hitch.loading() ? " (loading screen)" : (hitch.viewRebuild() ? " (Java's view rebuild)" : ""),
                  hitch.causes()
               )
            );
         }

         if (!stalls[0].isEmpty()) {
            log("stall samples: {}", stalls[0]);
         }
      }
   }

   private void missingTexturePixels() {
      sleep(1500L);
      File[] files = new File(this.minecraft.gameDirectory, "screenshots")
         .listFiles((dir, filex) -> filex.startsWith(this.name + "-") && filex.endsWith(".png"));
      if (files != null) {
         Arrays.sort((Object[])files);
         long total = 0L;
         int worst = 0;
         String worstFile = "-";

         for (File file : files) {
            try {
               BufferedImage image = ImageIO.read(file);
               int count = 0;

               for (int y = 0; y < image.getHeight(); y++) {
                  for (int x = 0; x < image.getWidth(); x++) {
                     int rgb = image.getRGB(x, y);
                     if ((rgb >> 16 & 0xFF) > 220 && (rgb >> 8 & 0xFF) < 40 && (rgb & 0xFF) > 220) {
                        count++;
                     }
                  }
               }

               total += count;
               if (count > worst) {
                  worst = count;
                  worstFile = file.getName();
               }
            } catch (RuntimeException | IOException var15) {
               log("cannot read {}: {}", file.getName(), var15.toString());
            }
         }

         log("missing-texture pixels: {} in {} screenshots, worst {} in {}", total, files.length, worst, worstFile);
      }
   }

   private void dumpUi() {
      String folder = System.getProperty("bedrocknative.lookDumpUi", "");
      if (!folder.isEmpty()) {
         int index = 0;
         int files = 0;

         for (PackSource pack : PackStack.active().packs()) {
            if (PackStack.isServerPack(pack)) {
               for (String path : pack.list("ui")) {
                  try {
                     Optional<byte[]> data = pack.read(path);
                     if (data.isPresent()) {
                        Path out = Path.of(folder, String.valueOf(index), path);
                        Files.createDirectories(out.getParent());
                        Files.write(out, data.get());
                        files++;
                     }
                  } catch (IOException var10) {
                     log("dump failed for {}: {}", path, var10.toString());
                  }
               }

               index++;
            }
         }

         log("dumped {} ui files of {} server packs", files, index);
      }
   }

   private void command() {
      this.dumpUi();
      String command = System.getProperty("bedrocknative.devJoinCommand", "");
      if (!command.isEmpty()) {
         for (int i = 0; i < 60 && this.onClient(() -> this.minecraft.gui.screen() != null || this.minecraft.player == null); i++) {
            sleep(1000L);
         }

         String sent = command.startsWith("/") ? command.substring(1) : command;
         if (!sent.matches("[a-z]+")) {
            log("not sending '{}': only a single plain word is allowed", command);
         } else {
            log("sending the allowed command /{} once", sent);
            this.run(() -> this.minecraft.getConnection().sendCommand(sent));
            sleep(Long.getLong("bedrocknative.lookCommandWait", 8000L));
         }
      }
   }

   private void renderDistances() {
      String list = System.getProperty("bedrocknative.lookRenderDistances", "");
      if (!list.isEmpty()) {
         for (int i = 0; i < 30 && this.onClient(() -> this.minecraft.gui.screen() != null || this.minecraft.player == null); i++) {
            sleep(1000L);
         }

         int initial = this.onClient(() -> (Integer)this.minecraft.options.renderDistance().get());

         for (String value : list.split(",")) {
            int distance = Integer.parseInt(value.trim());
            this.run(() -> this.minecraft.options.renderDistance().set(distance));
            int last = -1;
            int stable = 0;

            for (int i = 0; i < 60 && stable < 6; i++) {
               sleep(500L);
               int now = this.onClient(() -> this.minecraft.level.getChunkSource().getLoadedChunksCount());
               stable = now == last ? stable + 1 : 0;
               last = now;
            }

            log(
               "render distance {}: {} chunks loaded, effective distance {}, view distance of the client {}",
               distance,
               last,
               this.onClient(() -> this.minecraft.options.getEffectiveRenderDistance()),
               distance
            );
            this.screenshot("distance" + distance);
         }

         this.run(() -> this.minecraft.options.renderDistance().set(initial));
      }
   }

   private void inventory() {
      if (System.getProperty("bedrocknative.lookInventory") != null) {
         for (int i = 0; i < 30 && this.onClient(() -> this.minecraft.gui.screen() != null || this.minecraft.player == null); i++) {
            sleep(1000L);
         }

         this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
         sleep(2500L);
         this.onClient(() -> {
            Screen screen = this.minecraft.gui.screen();
            log("inventory screen {}", screen == null ? "none" : screen.getClass().getSimpleName());
            if (screen instanceof JsonUiContainerScreen json) {
               json.trace().forEach(line -> log("inventory trace: {}", line));
            }

            for (int slot = 5; slot <= 8; slot++) {
               log("inventory menu slot {}: {}", slot, this.minecraft.player.inventoryMenu.getSlot(slot).getItem());
            }

            for (EquipmentSlot equipment : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
               log("worn {}: {}", equipment, this.minecraft.player.getItemBySlot(equipment));
            }

            return null;
         });
         this.screenshot("inventory");
         if ("armour".equals(System.getProperty("bedrocknative.lookInventory"))) {
            this.armour();
         }

         this.run(() -> this.minecraft.gui.setScreen(null));
         sleep(500L);
      }
   }

   private void armour() {
      for (int slot = 9; slot <= 44; slot++) {
         int from = slot;
         int target = this.onClient(() -> {
            Equippable equippable = (Equippable)this.minecraft.player.inventoryMenu.getSlot(from).getItem().get(DataComponents.EQUIPPABLE);
            byte var10000;
            if (equippable == null) {
               var10000 = -1;
            } else {
               switch (equippable.slot()) {
                  case HEAD:
                     var10000 = 5;
                     break;
                  case CHEST:
                     var10000 = 6;
                     break;
                  case LEGS:
                     var10000 = 7;
                     break;
                  case FEET:
                     var10000 = 8;
                     break;
                  default:
                     var10000 = -1;
               }
            }

            int armourSlot = var10000;
            return armourSlot >= 0 && this.minecraft.player.inventoryMenu.getSlot(armourSlot).getItem().isEmpty() ? armourSlot : -1;
         });
         if (target >= 0) {
            boolean made = this.shiftClick(from);
            sleep(2500L);
            log(
               "armour: put on from slot {}: shift-click {}; slot {} now {}",
               from,
               made ? "made" : "MISSED",
               target,
               this.onClient(() -> this.minecraft.player.inventoryMenu.getSlot(target).getItem())
            );
         }
      }

      int worn = this.onClient(() -> {
         for (int slotx = 5; slotx <= 8; slotx++) {
            if (!this.minecraft.player.inventoryMenu.getSlot(slotx).getItem().isEmpty()) {
               return slotx;
            }
         }

         return -1;
      });
      if (worn < 0) {
         log("armour: none worn, nothing to take off");
      } else {
         ItemStack piece = this.onClient(() -> this.minecraft.player.inventoryMenu.getSlot(worn).getItem().copy());
         boolean off = this.shiftClick(worn);
         sleep(2500L);
         int now = this.onClient(() -> {
            for (int slotx = 9; slotx <= 44; slotx++) {
               if (ItemStack.isSameItemSameComponents(piece, this.minecraft.player.inventoryMenu.getSlot(slotx).getItem())) {
                  return slotx;
               }
            }

            return -1;
         });
         log(
            "armour: shift-click on slot {} {}; slot {} now {}, the piece is in slot {}",
            worn,
            off ? "made" : "MISSED (no cell under the mouse)",
            worn,
            this.onClient(() -> this.minecraft.player.inventoryMenu.getSlot(worn).getItem()),
            now
         );
         this.screenshot("inventory-armour-off");
         if (now >= 0) {
            boolean back = this.shiftClick(now);
            sleep(2500L);
            log(
               "armour: shift-click on slot {} {}; slot {} now {}",
               now,
               back ? "made" : "MISSED",
               worn,
               this.onClient(() -> this.minecraft.player.inventoryMenu.getSlot(worn).getItem())
            );
            this.screenshot("inventory-armour-back");
         }

         this.onClient(() -> {
            for (int slotx = 5; slotx <= 8; slotx++) {
               log("armour: slot {} ends as {}", slotx, this.minecraft.player.inventoryMenu.getSlot(slotx).getItem());
            }

            return null;
         });
      }
   }

   private boolean shiftClick(int slot) {
      return this.onClient(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen screen) {
            float[] at = screen.cellCentre(slot);
            Slot under = at == null ? null : screen.slotAt(at[0], at[1]);
            if (under != null && under.index == slot) {
               MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(1, 1));
               screen.mouseMoved(at[0], at[1]);
               screen.mouseClicked(event, false);
               screen.mouseReleased(event);
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      });
   }

   private void open() {
      this.closeServerForm();
      String wanted = OPEN.toLowerCase(Locale.ROOT);
      Entity target = this.onClient(
         () -> {
            List<Entity> found = new ArrayList<>();
            this.minecraft.level.entitiesForRendering().forEach(entity -> {
               if (entity != this.minecraft.player && names(entity).toLowerCase(Locale.ROOT).contains(wanted)) {
                  found.add(entity);
               }
            });
            found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(this.minecraft.player)));
            found.stream()
               .limit(10L)
               .forEach(
                  entity -> log(
                     "open: candidate '{}' ({}) at {} distance {}",
                     names(entity).replace('\n', '|'),
                     entity.typeHolder().getRegisteredName(),
                     entity.position(),
                     Math.round(entity.distanceTo(this.minecraft.player) * 10.0F) / 10.0
                  )
               );
            return found.isEmpty() ? null : found.get(0);
         }
      );
      if (target == null) {
         log("open: no entity named like '{}' in range", OPEN);
      } else {
         double left = this.walkTo(target.getX(), target.getZ(), 2.0);
         log("open: stopped {} blocks from it", Math.round(left * 10.0) / 10.0);
         String[] keys = this.formScreen() != null ? new String[]{"walk"} : new String[]{"use"};

         for (String key : keys) {
            if (!key.equals("walk")) {
               this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, target.getBoundingBox().getCenter()));
               sleep(400L);
               String hit = this.onClient(() -> String.valueOf(this.minecraft.hitResult == null ? null : this.minecraft.hitResult.getType()));
               KeyMapping mapping = key.equals("use") ? this.minecraft.options.keyUse : this.minecraft.options.keyAttack;
               Key code = InputConstants.getKey(mapping.saveString());
               this.run(() -> {
                  KeyMapping.set(code, true);
                  KeyMapping.click(code);
               });
               sleep(120L);
               this.run(() -> KeyMapping.set(code, false));
               log("open: {} with the crosshair on {}", key, hit);
            }

            String screen = null;

            for (int i = 0; i < 40 && screen == null; i++) {
               sleep(100L);
               screen = this.formScreen();
            }

            if (screen != null) {
               sleep(2500L);
               log("open: form screen {} after the {}", screen, key);
               String file = String.format("%s-%02d-%s.png", this.name, this.shots++, "opened-form");
               this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, file, this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
               sleep(1500L);
               this.closeServerForm();
               return;
            }
         }

         log("open: no form came");
         this.screenshot("open-none");
      }
   }

   private String formScreen() {
      return this.onClient(() -> {
         Screen open = this.minecraft.gui.screen();
         return open != null && open.getClass().getPackageName().equals("net.bedrocknative.client.form") ? open.getClass().getSimpleName() : null;
      });
   }

   private static String names(Entity entity) {
      StringBuilder names = new StringBuilder(entity.getName().getString());
      if (entity.getCustomName() != null) {
         names.append(' ').append(entity.getCustomName().getString());
      }

      if (entity instanceof Player player) {
         names.append(' ').append(player.getGameProfile().name());
      }

      String plain = ChatFormatting.stripFormatting(names.toString());
      return plain == null ? "" : plain;
   }

   private void jumps(int round) {
      float yaw = this.onClient(() -> this.minecraft.player.getYRot());
      if (!"fight".equals(System.getProperty("bedrocknative.lookJumpPhases", ""))) {
         this.lobbyJumps(round, yaw);
      }

      this.fightJumps(round, yaw);
   }

   private void lobbyJumps(int round, float yaw) {
      this.jumpPhase(round, "standing taps", () -> {
         for (int i = 0; i < 4; i++) {
            this.tapJump();
         }
      });
      this.jumpPhase(round, "held in place", () -> this.holdKeys(4000L, this.minecraft.options.keyJump));
      this.jumpPhase(round, "double taps", () -> {
         for (int i = 0; i < 2; i++) {
            this.run(() -> this.minecraft.options.keyJump.setDown(true));
            sleep(100L);
            this.run(() -> this.minecraft.options.keyJump.setDown(false));
            sleep(100L);
            this.tapJump();
            sleep(500L);
         }
      });

      for (int back = 0; back < 2; back++) {
         float facing = yaw + 180.0F * back;
         this.run(() -> this.minecraft.player.setYRot(facing));
         this.jumpPhase(round, back == 0 ? "walking taps" : "walking taps back", () -> {
            this.run(() -> this.minecraft.options.keyUp.setDown(true));

            for (int i = 0; i < 3; i++) {
               this.tapJump();
            }

            this.run(() -> this.minecraft.options.keyUp.setDown(false));
         });
      }

      for (int back = 0; back < 2; back++) {
         float facing = yaw + 180.0F * back;
         this.run(() -> this.minecraft.player.setYRot(facing));
         this.jumpPhase(
            round,
            back == 0 ? "sprinting, held" : "sprinting back, held",
            () -> this.holdKeys(2500L, this.minecraft.options.keyUp, this.minecraft.options.keySprint, this.minecraft.options.keyJump)
         );
      }

      this.run(() -> this.minecraft.player.setYRot(yaw));
      this.jumpPhase(round, "strafing, held", () -> {
         this.holdKeys(2000L, this.minecraft.options.keyLeft, this.minecraft.options.keyJump);
         this.holdKeys(2000L, this.minecraft.options.keyRight, this.minecraft.options.keyJump);
      });
   }

   private void fightJumps(int round, float yaw) {
      for (int back = 0; back < 2; back++) {
         float facing = yaw + 180.0F * back;
         this.run(() -> this.minecraft.player.setYRot(facing));
         this.jumpPhase(round, back == 0 ? "sprint jumps changing side" : "sprint jumps changing side back", () -> {
            this.run(() -> {
               this.minecraft.options.keyUp.setDown(true);
               this.minecraft.options.keySprint.setDown(true);
               this.minecraft.options.keyJump.setDown(true);
            });

            for (int i = 0; i < 6; i++) {
               KeyMapping side = i % 2 == 0 ? this.minecraft.options.keyLeft : this.minecraft.options.keyRight;
               this.holdKeys(300L, side);
            }

            this.releaseMoveKeys();
         });
      }

      for (int back = 0; back < 2; back++) {
         float facing = yaw + 180.0F * back;
         this.run(() -> this.minecraft.player.setYRot(facing));
         this.jumpPhase(round, back == 0 ? "w-taps" : "w-taps back", () -> {
            this.run(() -> {
               this.minecraft.options.keySprint.setDown(true);
               this.minecraft.options.keyJump.setDown(true);
            });

            for (int i = 0; i < 3; i++) {
               this.holdKeys(500L, this.minecraft.options.keyUp);
               sleep(150L);
            }

            this.releaseMoveKeys();
         });
      }

      this.run(() -> this.minecraft.player.setYRot(yaw));
      this.jumpPhase(round, "sneaking taps", () -> {
         this.run(() -> this.minecraft.options.keyShift.setDown(true));
         this.tapJump();
         this.tapJump();
         this.run(() -> this.minecraft.options.keyShift.setDown(false));
      });
      this.jumpPhase(round, "sprint jumps in a circle", () -> {
         this.run(() -> {
            this.minecraft.options.keyUp.setDown(true);
            this.minecraft.options.keySprint.setDown(true);
            this.minecraft.options.keyJump.setDown(true);
         });

         for (int step = 0; step < 240; step++) {
            float turned = yaw + step * 6.0F;
            this.run(() -> this.minecraft.player.setYRot(turned));
            sleep(50L);
         }

         this.releaseMoveKeys();
      });
      this.run(() -> this.minecraft.player.setYRot(yaw));
   }

   private void releaseMoveKeys() {
      this.run(
         () -> {
            for (KeyMapping key : new KeyMapping[]{
               this.minecraft.options.keyUp,
               this.minecraft.options.keyDown,
               this.minecraft.options.keyLeft,
               this.minecraft.options.keyRight,
               this.minecraft.options.keySprint,
               this.minecraft.options.keyJump,
               this.minecraft.options.keyShift
            }) {
               key.setDown(false);
            }
         }
      );
   }

   private void jumpPhase(int round, String phase, Runnable steps) {
      this.closeServerForm();
      int predictions = this.movement.count();
      int moves = MovementStats.teleports();
      String from = this.onClient(() -> this.minecraft.player.position().toString());
      steps.run();
      sleep(1000L);
      String to = this.onClient(() -> this.minecraft.player.position().toString());
      log(
         "jumps round {} {}: {} CorrectPlayerMovePrediction, {} MovePlayer; from {} to {}",
         round,
         phase,
         this.movement.count() - predictions,
         MovementStats.teleports() - moves,
         from,
         to
      );
   }

   private void tapJump() {
      this.run(() -> this.minecraft.options.keyJump.setDown(true));
      sleep(100L);
      this.run(() -> this.minecraft.options.keyJump.setDown(false));
      sleep(900L);
   }

   private void holdKeys(long millis, KeyMapping... keys) {
      this.run(() -> {
         for (KeyMapping key : keys) {
            key.setDown(true);
         }
      });
      sleep(millis);
      this.run(() -> {
         for (KeyMapping key : keys) {
            key.setDown(false);
         }
      });
   }

   private void tour() {
      if (!TOUR.isEmpty()) {
         List<String> labels = new ArrayList<>();
         List<double[]> stops = new ArrayList<>();
         if (TOUR.startsWith("actors")) {
            int limit = TOUR.contains(",") ? Integer.parseInt(TOUR.substring(TOUR.indexOf(44) + 1).trim()) : 8;
            List<BedrockActorEntity> actors = new ArrayList<>(this.onClient(() -> {
               List<BedrockActorEntity> all = new ArrayList<>();
               this.minecraft.level.entitiesForRendering().forEach(entity -> {
                  if (entity instanceof BedrockActorEntity actor) {
                     all.add(actor);
                  }
               });
               all.sort(Comparator.comparingDouble(actor -> actor.distanceToSqr(this.minecraft.player)));
               return all;
            }));
            this.onClient(
               () -> {
                  for (BedrockActorEntity actor : actors) {
                     log(
                        "actor {} at {} distance {}",
                        actor.bedrockIdentifier(),
                        actor.position(),
                        Math.round(actor.distanceTo(this.minecraft.player) * 10.0F) / 10.0
                     );
                  }

                  return null;
               }
            );
            Map<String, BedrockActorEntity> nearest = new TreeMap<>();

            for (BedrockActorEntity actor : actors) {
               nearest.putIfAbsent(actor.bedrockIdentifier(), actor);
            }

            List<BedrockActorEntity> pool = new ArrayList<>(nearest.values());
            double[] here = this.onClient(() -> new double[]{this.minecraft.player.getX(), this.minecraft.player.getZ()});

            while (!pool.isEmpty() && stops.size() < limit) {
               double[] from = stops.isEmpty() ? here : stops.get(stops.size() - 1);
               BedrockActorEntity next = pool.stream()
                  .min(Comparator.comparingDouble(actor -> Math.hypot(actor.getX() - from[0], actor.getZ() - from[1])))
                  .get();
               pool.remove(next);
               stops.add(new double[]{next.getX(), next.getZ(), next.getY() + next.getBbHeight() * 0.6});
               labels.add(next.bedrockIdentifier().replace(':', '_'));
            }
         } else {
            for (String point : TOUR.split(";")) {
               String[] parts = point.split(",");
               stops.add(new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.NaN});
               labels.add("point" + labels.size());
            }
         }

         for (int i = 0; i < stops.size(); i++) {
            double[] stop = stops.get(i);
            double distance = this.walkTo(stop[0], stop[1], 4.0);
            double[] at = this.onClient(() -> new double[]{this.minecraft.player.getX(), this.minecraft.player.getY(), this.minecraft.player.getZ()});
            log(
               "tour {} {}: stopped {} blocks from ({}, {}) at ({}, {}, {})",
               i,
               labels.get(i),
               Math.round(distance * 10.0) / 10.0,
               Math.round(stop[0]),
               Math.round(stop[1]),
               Math.round(at[0] * 10.0) / 10.0,
               Math.round(at[1] * 10.0) / 10.0,
               Math.round(at[2] * 10.0) / 10.0
            );
            float yaw = (float)Math.toDegrees(Math.atan2(-(stop[0] - at[0]), stop[1] - at[2]));
            float pitch = Double.isNaN(stop[2])
               ? 0.0F
               : (float)(-Math.toDegrees(Math.atan2(stop[2] - (at[1] + 1.62), Math.max(0.5, Math.hypot(stop[0] - at[0], stop[1] - at[2])))));
            float[] turns = new float[]{0.0F, -50.0F, 50.0F};
            String[] names = new String[]{"front", "left", "right"};

            for (int v = 0; v < turns.length; v++) {
               float target = yaw + turns[v];
               float tilt = v == 0 ? pitch : 0.0F;
               this.run(() -> {
                  this.minecraft.player.setYRot(target);
                  this.minecraft.player.setXRot(tilt);
               });
               sleep(1200L);
               this.screenshot("tour" + i + "-" + labels.get(i) + "-" + names[v]);
            }
         }
      }
   }

   private void probe() {
      for (String text : PROBE.split("/")) {
         String[] parts = text.split(",");
         double sx = Double.parseDouble(parts[0].trim());
         double sz = Double.parseDouble(parts[1].trim());
         double tx = Double.parseDouble(parts[2].trim());
         double tz = Double.parseDouble(parts[3].trim());
         log("probe {}: start reached within {}", text, Math.round(this.walkTo(sx, sz, 0.8) * 100.0) / 100.0);
         this.onClient(
            () -> {
               BlockPos middle = BlockPos.containing(tx, this.minecraft.player.getY() + 0.01, tz);

               for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-1, -1, -1), middle.offset(1, 2, 1))) {
                  BlockState state = this.minecraft.level.getBlockState(pos);
                  if (!state.isAir()) {
                     CustomBlockState custom = CarrierBlock.data(state);
                     log(
                        "probe block {}: {} boxes {}",
                        pos.toShortString(),
                        custom == null ? state : custom.state() + " collision " + custom.collision(),
                        state.getCollisionShape(this.minecraft.level, pos, CollisionContext.of(this.minecraft.player)).toAabbs()
                     );
                  }
               }

               return null;
            }
         );

         for (String mode : MODES.split(",")) {
            StringBuilder trail = new StringBuilder();

            for (int i = 0; i < 50; i++) {
               int step = i;
               trail.append(
                  this.onClient(
                     () -> {
                        Player player = this.minecraft.player;
                        player.setYRot(
                           (float)Math.toDegrees(
                              mode.contains("fixed") ? Math.atan2(-(tx - sx), tz - sz) : Math.atan2(-(tx - player.getX()), tz - player.getZ())
                           )
                        );
                        player.setXRot(30.0F);
                        this.minecraft.options.keyUp.setDown(true);
                        this.minecraft.options.autoJump().set(false);
                        this.minecraft.options.keyJump.setDown(mode.contains("jump") && (!mode.contains("fixed") || step % 20 < 3));
                        this.minecraft.options.keySprint.setDown(mode.contains("sprint"));
                        this.minecraft.options.keyRight.setDown(mode.contains("strafe") && step / (mode.contains("fixed") ? 3 : 8) % 2 == 0);
                        this.minecraft.options.keyLeft.setDown(mode.contains("strafe") && step / (mode.contains("fixed") ? 3 : 8) % 2 == 1);
                        return String.format(
                           Locale.ROOT,
                           "%.5f,%.4f,%.5f%s%s%s ",
                           player.getX(),
                           player.getY(),
                           player.getZ(),
                           player.onGround() ? "g" : "",
                           player.isSprinting() ? "s" : "",
                           player.horizontalCollision ? "h" : ""
                        );
                     }
                  )
               );
               sleep(50L);
            }

            this.run(() -> {
               this.minecraft.options.keyUp.setDown(false);
               this.minecraft.options.keyJump.setDown(false);
               this.minecraft.options.keySprint.setDown(false);
            });
            log("probe {} {}: {}", text, mode, trail);
            this.screenshot("probe-" + mode);
            this.walkTo(sx, sz, 0.8);
         }
      }
   }

   private double walkTo(double x, double z, double stop) {
      long end = System.currentTimeMillis() + 40000L;
      double[] checked = null;
      long checkedAt = 0L;
      double distance = Double.MAX_VALUE;
      this.run(() -> {
         this.minecraft.options.keyUp.setDown(true);
         this.minecraft.options.keySprint.setDown(true);
      });

      for (; System.currentTimeMillis() < end; sleep(100L)) {
         double[] state = this.onClient(() -> {
            double dx = x - this.minecraft.player.getX();
            double dz = z - this.minecraft.player.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > stop) {
               this.minecraft.player.setYRot((float)Math.toDegrees(Math.atan2(-dx, dz)));
               this.minecraft.player.setXRot(0.0F);
               this.minecraft.options.keyJump.setDown(this.minecraft.player.horizontalCollision);
            }

            return new double[]{dist, this.minecraft.player.getX(), this.minecraft.player.getZ()};
         });
         distance = state[0];
         if (distance <= stop) {
            break;
         }

         long now = System.currentTimeMillis();
         if (checked == null || now - checkedAt > 5000L) {
            if (checked != null && Math.hypot(state[1] - checked[0], state[2] - checked[1]) < 0.5) {
               break;
            }

            checked = new double[]{state[1], state[2]};
            checkedAt = now;
         }
      }

      this.run(() -> {
         this.minecraft.options.keyUp.setDown(false);
         this.minecraft.options.keyJump.setDown(false);
         this.minecraft.options.keySprint.setDown(false);
      });
      sleep(600L);
      return distance;
   }

   private void views(String where) {
      float yaw = this.onClient(() -> this.minecraft.player.getYRot());

      for (int i = 0; i < 4; i++) {
         float target = yaw + 90.0F * i;
         this.run(() -> {
            this.minecraft.player.setYRot(target);
            this.minecraft.player.setXRot(0.0F);
         });
         sleep(1200L);
         this.screenshot(where + "-dir" + i);
      }

      this.run(() -> this.minecraft.player.setXRot(70.0F));
      sleep(1000L);
      this.screenshot(where + "-floor");
      this.run(() -> this.minecraft.player.setXRot(-40.0F));
      sleep(1000L);
      this.screenshot(where + "-sky");
      this.run(() -> {
         this.minecraft.player.setYRot(yaw);
         this.minecraft.player.setXRot(0.0F);
      });
   }

   private void playerTags(String when) {
      List<Player> players = new ArrayList<>();
      this.minecraft.level.entitiesForRendering().forEach(entity -> {
         if (entity instanceof Player playerx && entity != this.minecraft.player) {
            players.add(playerx);
         }
      });
      players.sort(Comparator.comparingDouble(playerx -> playerx.distanceToSqr(this.minecraft.player)));
      List<String> tags = new ArrayList<>();

      for (Player player : players.subList(0, Math.min(8, players.size()))) {
         BedrockNameTag tag = BedrockNameTag.of(player);
         tags.add(
            tag == null
               ? player.getGameProfile().name() + " (no Bedrock tag)"
               : BedrockNameTag.escape(tag.name())
                  + (tag.score().isEmpty() ? "" : " / score " + BedrockNameTag.escape(tag.score()))
                  + (tag.alwaysShown(player) ? "" : " (looked at only)")
                  + " at "
                  + Math.round(player.distanceTo(this.minecraft.player))
         );
      }

      log("{}: player tags {}", when, tags);
   }

   private void entities() {
      List<Entity> near = this.onClient(() -> {
         List<Entity> all = new ArrayList<>();
         this.minecraft.level.entitiesForRendering().forEach(entityx -> {
            if (entityx != this.minecraft.player) {
               all.add(entityx);
            }
         });
         all.sort(Comparator.comparingDouble(entityx -> entityx.distanceToSqr(this.minecraft.player)));
         return new ArrayList<>(all.subList(0, Math.min(6, all.size())));
      });
      int index = 0;

      for (Entity entity : near) {
         String label = this.onClient(
            () -> entity.typeHolder().getRegisteredName()
               + (entity instanceof Player player ? " " + player.getGameProfile().name() : "")
               + " at "
               + entity.position()
               + " distance "
               + Math.round(entity.distanceTo(this.minecraft.player) * 10.0F) / 10.0
         );
         log("entity view {}: {}", index, label);
         this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, entity.getEyePosition()));
         sleep(1500L);
         this.screenshot("entity" + index++);
      }
   }

   private void report(String when) {
      this.run(
         () -> {
            Map<String, Integer> seen = new TreeMap<>();
            this.minecraft.level.entitiesForRendering().forEach(entityx -> seen.merge(entityx.typeHolder().getRegisteredName(), 1, Integer::sum));
            log(
               "{}: position {}, {} server movement corrections, entities {}, tab list {}",
               when,
               this.minecraft.player.position(),
               this.movement.count(),
               seen,
               this.minecraft.getConnection().getListedOnlinePlayers().size()
            );
            int drawn = 0;
            List<String> open = new ArrayList<>();

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               ActorState state = BedrockActors.state(entity);
               if (state != null && state.instance() != null && state.evaluations() > 0L) {
                  drawn++;
                  if (state.instance().openLids() > 0) {
                     open.add(state.data().identifier());
                  }
               }
            }

            log(
               "{}: {} server actors evaluated, {} with a lid open at rest {}; glyph pages {}",
               when,
               drawn,
               open.size(),
               open,
               BedrockGlyphs.pages().stream().map(page -> String.format("%02X", page)).toList()
            );
            this.playerTags(when);
            this.customBlocks(when);
         }
      );
   }

   private void customBlocks(String when) {
      Map<String, Set<BlockProblems.Reason>> reasons = new HashMap<>();
      Map<BlockProblems.Reason, Integer> byReason = new TreeMap<>();

      for (BlockProblems.Problem problem : BedrockBlockModels.problems()) {
         reasons.computeIfAbsent(problem.block(), block -> EnumSet.noneOf(BlockProblems.Reason.class)).add(problem.reason());
         byReason.merge(problem.reason(), 1, Integer::sum);
      }

      BlockPos center = this.minecraft.player.blockPosition();
      int placed = 0;
      int missing = 0;
      Set<String> kinds = new TreeSet<>();
      Set<String> missingKinds = new TreeSet<>();

      for (BlockPos pos : BlockPos.betweenClosed(center.offset(-24, -12, -24), center.offset(24, 12, 24))) {
         CustomBlockState custom = CarrierBlock.data(this.minecraft.level.getBlockState(pos));
         if (custom != null) {
            placed++;
            kinds.add(custom.state().name());
            Set<BlockProblems.Reason> found = reasons.get(custom.state().name());
            if (found != null && found.stream().anyMatch(BlockProblems.Reason::missingTexture)) {
               missing++;
               missingKinds.add(custom.state().name());
            }
         }
      }

      log(
         "{}: custom blocks within 24: {} placed ({} kinds), {} of them ({} kinds) with missing textures {}; block problems this session {}",
         when,
         placed,
         kinds.size(),
         missing,
         missingKinds.size(),
         missingKinds.stream().limit(12L).toList(),
         byReason
      );
   }

   private void closeServerForm() {
      String closed = this.onClient(() -> {
         Screen screen = this.minecraft.gui.screen();
         if (screen != null && screen.getClass().getPackageName().equals("net.bedrocknative.client.form")) {
            screen.onClose();
            return screen.getClass().getSimpleName();
         } else {
            return null;
         }
      });
      if (closed != null) {
         log("closed the server form {} (UserClosed)", closed);
         sleep(800L);
      }
   }

   private void screenshot(String step) {
      this.closeServerForm();
      String file = String.format("%s-%02d-%s.png", this.name, this.shots++, step);
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, file, this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev look test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
