package net.bedrocknative.client.net;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.client.WindowCloseGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;

final class DevFakeChestsTest {
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final WindowCloseGuard guard;
   private int passed;
   private int failed;

   DevFakeChestsTest(Minecraft minecraft, Consumer<BedrockPacket> inject, WindowCloseGuard guard) {
      this.minecraft = minecraft;
      this.inject = inject;
      this.guard = guard;
   }

   void run() {
      BlockPos base = this.onClient(() -> this.minecraft.player.blockPosition());
      this.commands(
         "gamemode creative",
         "time set noon",
         "weather clear",
         "clear @s",
         fill(base.offset(-4, -1, -4), base.offset(8, -1, 4), "stone"),
         fill(base.offset(-4, 0, -4), base.offset(8, 4, 4), "air")
      );
      sleep(1000L);
      BlockPos far = base.offset(3, 0, 3);
      this.menu("double chest", ContainerType.CONTAINER, 54, 77, 90, far);
      this.menu("chest", ContainerType.CONTAINER, 27, 78, 63, far);
      this.menu("hopper", ContainerType.HOPPER, 5, 79, 41, far);
      this.menu("dispenser", ContainerType.DISPENSER, 9, 80, 45, far);
      int violations = this.guard.violations();
      this.run(() -> {
         this.open(81, ContainerType.CONTAINER, far);
         this.content(81, 54);
      });
      this.check("the next menu opens", this.awaitContainerScreen(5000L) != null, "screen " + this.describe());
      this.injectClose(81, false);
      boolean closed = this.await(() -> !(this.minecraft.gui.screen() instanceof AbstractContainerScreen), 2000L);
      this.check("a response with no open after it closes the window", closed, "screen " + this.describe());
      this.check("and is reported", this.guard.violations() == violations + 1, "violations " + violations + " -> " + this.guard.violations());
      int kept = this.guard.kept();
      this.run(() -> {
         this.open(82, ContainerType.CONTAINER, far);
         this.content(82, 54);
      });
      this.check("a menu opens", this.awaitContainerScreen(5000L) != null, "screen " + this.describe());
      this.run(() -> {
         this.injectClose(82, false);
         this.open(82, ContainerType.CONTAINER, far.offset(5, 0, 5));
      });
      sleep(500L);
      this.check(
         "another block under the same id closes the first window",
         !(this.onClient(() -> this.minecraft.gui.screen()) instanceof AbstractContainerScreen) && this.guard.kept() == kept,
         "screen " + this.describe() + ", kept " + kept + " -> " + this.guard.kept()
      );
      this.injectClose(82, true);
      sleep(300L);
      BlockPos chest = base.offset(2, 0, 0);
      this.commands("setblock " + at(chest) + " chest", "replaceitem block " + at(chest) + " slot.container 0 diamond 3");
      this.openChest(chest);
      AbstractContainerScreen<?> screen = this.awaitContainerScreen(5000L);
      this.check("a real chest opens", screen != null, "screen " + this.describe());
      if (screen != null) {
         violations = this.guard.violations();
         int answers = this.guard.answers();
         this.run(screen::onClose);
         sleep(1500L);
         this.check(
            "the client's own close ends the window",
            !(this.onClient(() -> this.minecraft.gui.screen()) instanceof AbstractContainerScreen),
            "screen " + this.describe()
         );
         this.check(
            "the server's response to it is no violation",
            this.guard.violations() == violations && this.guard.answers() == answers,
            "violations " + violations + " -> " + this.guard.violations() + ", answers " + answers + " -> " + this.guard.answers()
         );
      }

      this.commands("setblock " + at(chest) + " air");
      boolean alive = this.onClient(() -> this.minecraft.getConnection() != null && this.minecraft.player != null && this.minecraft.player.isAlive());
      this.check("still connected and alive", alive, "violations sent " + this.guard.violations() + ", answers " + this.guard.answers());
      log("done {} passed, {} failed", this.passed, this.failed);
   }

   private void menu(String what, ContainerType type, int size, int id, int menuSlots, BlockPos pos) {
      int violations = this.guard.violations();
      int answers = this.guard.answers();
      int kept = this.guard.kept();
      this.run(() -> {
         this.open(id, type, pos);
         this.content(id, size);
      });
      AbstractContainerScreen<?> screen = this.awaitContainerScreen(5000L);
      boolean shown = screen != null && this.onClient(() -> this.minecraft.player.containerMenu.slots.size()) == menuSlots;
      this.check(
         what + " opens",
         shown,
         "screen "
            + this.describe()
            + ", menu slots "
            + this.onClient(() -> this.minecraft.player.containerMenu.slots.size())
            + " (expected "
            + menuSlots
            + ")"
      );
      if (screen != null) {
         AbstractContainerMenu menu = this.onClient(() -> this.minecraft.player.containerMenu);

         for (int tick = 0; tick < 20; tick++) {
            this.run(() -> {
               this.injectClose(id, false);
               this.open(id, type, pos);
            });
            sleep(55L);
         }

         sleep(300L);
         this.check(
            what + ": twenty ticks of the loop keep the same screen",
            this.onClient(() -> this.minecraft.gui.screen()) == screen && this.onClient(() -> this.minecraft.player.containerMenu) == menu,
            "screen " + this.describe()
         );
         this.check(what + ": every response is reported", this.guard.violations() == violations + 20, "violations " + (this.guard.violations() - violations));
         this.check(what + ": every response is answered", this.guard.answers() == answers + 20, "answers " + (this.guard.answers() - answers));
         this.check(what + ": the window counts as kept each time", this.guard.kept() == kept + 20, "kept " + (this.guard.kept() - kept));
         this.run(() -> this.content(id, size));
         sleep(300L);
         this.check(
            what + ": the content sync after the loop reaches the kept window",
            this.onClient(() -> this.minecraft.gui.screen()) == screen && this.onClient(() -> this.minecraft.player.containerMenu) == menu,
            "screen " + this.describe()
         );
         if (id == 77) {
            this.screenshot("fakechests-" + what.replace(' ', '-'));
         }

         this.injectClose(id, true);
         boolean closed = this.await(() -> !(this.minecraft.gui.screen() instanceof AbstractContainerScreen), 2000L);
         this.check(what + ": the server's own close ends it", closed, "screen " + this.describe());
      }
   }

   private void open(int id, ContainerType type, BlockPos pos) {
      ContainerOpenPacket open = new ContainerOpenPacket();
      open.setId((byte)id);
      open.setType(type);
      open.setBlockPosition(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()));
      open.setUniqueEntityId(-1L);
      this.inject.accept(open);
   }

   private void content(int id, int size) {
      InventoryContentPacket content = new InventoryContentPacket();
      content.setContainerId(id);

      for (int slot = 0; slot < size; slot++) {
         content.getContents().add(ItemData.AIR);
      }

      this.inject.accept(content);
   }

   private void injectClose(int window, boolean serverInitiated) {
      ContainerClosePacket close = new ContainerClosePacket();
      close.setId((byte)window);
      close.setType(ContainerType.CONTAINER);
      close.setServerInitiated(serverInitiated);
      this.inject.accept(close);
   }

   private void openChest(BlockPos pos) {
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos)));
      sleep(300L);
      this.run(
         () -> this.minecraft
            .gameMode
            .useItemOn(
               this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(-0.5, 0.0, 0.0), Direction.WEST, pos, false)
            )
      );
      sleep(800L);
   }

   private AbstractContainerScreen<?> awaitContainerScreen(long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         AbstractContainerScreen<?> screen = this.onClient(
            () -> this.minecraft.gui.screen() instanceof AbstractContainerScreen<?> container && this.minecraft.player.containerMenu.slots.size() > 36
               ? container
               : null
         );
         if (screen != null) {
            return screen;
         }

         sleep(100L);
      }

      return null;
   }

   private String describeNow() {
      Screen screen = this.minecraft.gui.screen();
      return (screen == null ? "none" : screen.getClass().getSimpleName()) + " (window " + this.minecraft.player.containerMenu.containerId + ")";
   }

   private String describe() {
      return this.onClient(this::describeNow);
   }

   private boolean await(Supplier<Boolean> condition, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         if (this.onClient(condition)) {
            return true;
         }

         sleep(100L);
      }

      return false;
   }

   private void check(String name, boolean ok, String detail) {
      if (ok) {
         this.passed++;
         BedrockNative.LOGGER.info("Dev fakechests test PASS {}: {}", name, detail);
      } else {
         this.failed++;
         BedrockNative.LOGGER.info("Dev fakechests test FAIL {}: {}", name, detail);
      }
   }

   private static String at(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private static String fill(BlockPos from, BlockPos to, String block) {
      return "fill " + at(from) + " " + at(to) + " " + block;
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(600L);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
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
      BedrockNative.LOGGER.info("Dev fakechests test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
