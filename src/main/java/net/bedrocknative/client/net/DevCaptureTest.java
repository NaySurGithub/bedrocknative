package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class DevCaptureTest {
   private static final int PICKER_HOTBAR = 45;
   private final Minecraft minecraft;

   DevCaptureTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      BlockPos origin = this.supply(() -> this.minecraft.player.blockPosition());
      int x = origin.getX();
      int y = origin.getY();
      int z = origin.getZ();
      this.step("setup");
      this.commands(
         "gamemode creative",
         "clear @s",
         "effect @s clear",
         "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 14) + " " + (y + 4) + " " + (z + 3) + " air",
         "fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 14) + " " + (y - 1) + " " + (z + 3) + " stone",
         "tp @s " + x + " " + y + " " + z + " -90 0",
         "give @s acacia_planks 64"
      );
      this.hotbar(0);
      this.step("idle");
      sleep(10000L);
      this.step("walk");
      this.hold(this.minecraft.options.keyUp, 1500L);
      sleep(1000L);
      this.step("jump");

      for (int i = 0; i < 2; i++) {
         this.hold(this.minecraft.options.keyJump, 100L);
         sleep(1000L);
      }

      this.step("sprint");
      this.commands("tp @s " + x + " " + y + " " + z + " -90 0");
      sleep(500L);
      this.press(this.minecraft.options.keySprint, true);
      this.hold(this.minecraft.options.keyUp, 1500L);
      this.press(this.minecraft.options.keySprint, false);
      sleep(1000L);
      this.step("sneak");
      this.press(this.minecraft.options.keyShift, true);
      sleep(1500L);
      this.hold(this.minecraft.options.keyDown, 1000L);
      this.press(this.minecraft.options.keyShift, false);
      sleep(1000L);
      this.commands("tp @s " + x + " " + y + " " + z + " -90 0");
      sleep(500L);
      BlockPos floor = new BlockPos(x + 2, y - 1, z);
      this.step("break");
      this.look(floor);
      this.hold(this.minecraft.options.keyAttack, 150L);
      sleep(1500L);
      this.step("place");
      BlockPos ground = new BlockPos(x + 2, y - 1, z + 1);
      this.look(ground);
      this.hold(this.minecraft.options.keyUse, 120L);
      sleep(1500L);
      this.step("inventory move");
      this.hold(this.minecraft.options.keyInventory, 60L);
      sleep(1000L);
      this.clickHotbar(0);
      this.clickHotbar(2);
      this.run(() -> this.minecraft.gui.screen().onClose());
      sleep(1500L);
      this.step("chest");
      this.commands("give @s chest");
      sleep(1000L);
      BlockPos chestOn = new BlockPos(x + 2, y - 1, z - 1);
      this.look(chestOn);
      this.hold(this.minecraft.options.keyUse, 120L);
      sleep(1000L);
      this.hotbar(1);
      sleep(500L);
      this.look(chestOn.above());
      this.hold(this.minecraft.options.keyUse, 120L);
      sleep(1500L);
      this.run(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (Slot slot : menu.slots) {
            if (slot.container == this.minecraft.player.getInventory() && slot.getContainerSlot() == 2) {
               this.minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
               return;
            }
         }
      });
      sleep(1000L);
      this.run(() -> this.minecraft.gui.screen().onClose());
      sleep(1500L);
      this.step("chat");
      this.run(() -> this.minecraft.getConnection().sendChat("Salut c'est un test !"));
      sleep(3000L);
      this.step("command");
      this.commands("give @s bread 5");
      sleep(1000L);
      this.run(() -> {
         for (int slot = 0; slot < 9; slot++) {
            if (this.minecraft.player.getInventory().getItem(slot).is(Items.BREAD)) {
               KeyMapping.click(key(this.minecraft.options.keyHotbarSlots[slot]));
               return;
            }
         }
      });
      sleep(1000L);
      this.step("eat creative");
      this.look(new BlockPos(x - 2, y + 3, z));
      this.hold(this.minecraft.options.keyUse, 2000L);
      sleep(1000L);
      this.step("survival");
      this.commands("gamemode survival", "effect @s hunger 5 255");
      sleep(6000L);
      int bread = this.supply(() -> this.minecraft.player.getInventory().getSelectedSlot());
      this.hotbar(bread == 3 ? 4 : 3);
      sleep(500L);
      this.hotbar(bread);
      sleep(1000L);
      this.step("eat survival");

      for (int i = 0; i < 3; i++) {
         this.hold(this.minecraft.options.keyUse, i == 2 ? 3800L : 2000L);
         sleep(1000L);
      }

      this.step("held place");
      this.commands("gamemode creative", "effect @s clear", "give @s rail 8", "tp @s " + x + " " + y + " " + z + " -90 0");
      sleep(500L);
      this.select(Items.RAIL);
      this.look(new BlockPos(x + 3, y - 1, z + 2));
      this.hold(this.minecraft.options.keyUse, 1300L);
      sleep(1000L);
      this.step("entity use");
      this.commands(
         "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 14) + " " + (y + 4) + " " + (z + 3) + " air",
         "fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 14) + " " + (y - 1) + " " + (z + 3) + " stone",
         "tp @s " + x + " " + y + " " + z + " -90 0",
         "summon boat " + (x + 2) + " " + y + " " + z
      );
      sleep(800L);
      this.lookAtEntity(EntityTypes.OAK_BOAT);
      this.hold(this.minecraft.options.keyUse, 120L);
      sleep(1500L);
      this.hold(this.minecraft.options.keyShift, 300L);
      sleep(1000L);
      this.commands("kill @e[type=boat]");
      this.step("attack");
      this.commands("summon pig " + (x + 2) + " " + y + " " + z);
      sleep(800L);
      this.lookAtEntity(EntityTypes.PIG);
      this.hold(this.minecraft.options.keyAttack, 150L);
      sleep(1500L);
      this.commands("kill @e[type=pig]");
      this.step("leave");
      sleep(2000L);
      BedrockNative.LOGGER.info("Dev capture test: done");
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
   }

   private void step(String name) {
      BedrockNative.LOGGER.info("Dev capture step: {}", name);
   }

   private void hold(KeyMapping mapping, long millis) {
      this.press(mapping, true);
      sleep(millis);
      this.press(mapping, false);
   }

   private void press(KeyMapping mapping, boolean down) {
      this.run(() -> {
         Key key = key(mapping);
         KeyMapping.set(key, down);
         if (down) {
            KeyMapping.click(key);
         }
      });
   }

   private void hotbar(int slot) {
      this.run(() -> KeyMapping.click(key(this.minecraft.options.keyHotbarSlots[slot])));
      sleep(300L);
   }

   private static Key key(KeyMapping mapping) {
      return InputConstants.getKey(mapping.saveString());
   }

   private void clickHotbar(int hotbar) {
      this.run(() -> {
         Screen screen = this.minecraft.gui.screen();
         if (screen instanceof CreativeModeInventoryScreen creative) {
            int index = 45 + hotbar;

            try {
               Method slotClicked = CreativeModeInventoryScreen.class.getDeclaredMethod("slotClicked", Slot.class, int.class, int.class, ContainerInput.class);
               slotClicked.setAccessible(true);
               slotClicked.invoke(creative, creative.getMenu().slots.get(index), index, 0, ContainerInput.PICKUP);
            } catch (ReflectiveOperationException e) {
               throw new IllegalStateException(e);
            }
         } else {
            BedrockNative.LOGGER.warn("Dev capture test: no creative screen ({})", screen);
         }
      });
      sleep(600L);
   }

   private void select(Item item) {
      this.run(() -> {
         for (int slot = 0; slot < 9; slot++) {
            if (this.minecraft.player.getInventory().getItem(slot).is(item)) {
               KeyMapping.click(key(this.minecraft.options.keyHotbarSlots[slot]));
               return;
            }
         }
      });
      sleep(300L);
   }

   private void lookAtEntity(EntityType<?> type) {
      this.run(
         () -> this.minecraft
            .level
            .getEntitiesOfClass(Entity.class, this.minecraft.player.getBoundingBox().inflate(8.0), entity -> entity.getType() == type)
            .stream()
            .findFirst()
            .ifPresent(entity -> this.minecraft.player.lookAt(Anchor.EYES, entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0)))
      );
      sleep(400L);
   }

   private void look(BlockPos pos) {
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0)));
      sleep(300L);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(400L);
   }

   private void run(Runnable action) {
      this.minecraft.execute(action);
   }

   private <T> T supply(Supplier<T> value) {
      return CompletableFuture.supplyAsync(value, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
