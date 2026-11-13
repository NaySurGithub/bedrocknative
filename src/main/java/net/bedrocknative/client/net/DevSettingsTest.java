package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.content.BedrockOptions;
import net.bedrocknative.ui.UiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

final class DevSettingsTest {
   private static final int WIDTH = 1280;
   private static final int HEIGHT = 720;
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;

   DevSettingsTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      int renderDistance = this.onClient(() -> (Integer)this.minecraft.options.renderDistance().get());
      int guiScale = this.onClient(() -> (Integer)this.minecraft.options.guiScale().get());
      Window window = this.minecraft.getWindow();
      int width = this.<Integer>onClient(window::getScreenWidth);
      int height = this.<Integer>onClient(window::getScreenHeight);

      try {
         this.renderDistance(renderDistance);
         this.guiScale(window);
      } catch (RuntimeException var10) {
         this.failures.add("exception: " + var10);
         BedrockNative.LOGGER.error("Dev settings test failed with an exception", var10);
      } finally {
         this.minecraft.execute(() -> {
            this.minecraft.options.renderDistance().set(renderDistance);
            this.minecraft.options.guiScale().set(guiScale);
            window.setWindowed(width, height);
         });
      }

      BedrockNative.LOGGER
         .info(
            "Dev settings test: {} of {} checks passed{}",
            new Object[]{this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures}
         );
      BedrockNative.LOGGER.info("Dev settings test: done");
   }

   private void renderDistance(int initial) {
      int first = this.settle();
      BedrockNative.LOGGER
         .info(
            "Dev settings test: render distance {} at the start: {} chunks loaded, effective distance {}",
            new Object[]{initial, first, this.onClient(() -> this.minecraft.options.getEffectiveRenderDistance())}
         );
      int previous = first;

      for (int distance : new int[]{8, 16, 4}) {
         this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(distance));
         int loaded = this.settle();
         int effective = this.onClient(() -> this.minecraft.options.getEffectiveRenderDistance());
         int expected = (int)(Math.PI * distance * distance);
         BedrockNative.LOGGER
            .info(
               "Dev settings test: render distance {} -> {} chunks loaded (a circle of that radius is about {}), effective distance {}",
               new Object[]{distance, loaded, expected, effective}
            );
         this.check("render distance " + distance + ": the server's radius is " + effective + " (asked " + distance + ")", effective == distance);
         if (distance > 4) {
            this.check(
               "render distance " + distance + ": " + loaded + " chunks loaded without rejoining (at least 70% of about " + expected + ")",
               loaded >= expected * 0.7
            );
            this.check("render distance " + distance + ": more chunks than before (" + previous + " -> " + loaded + ")", loaded > previous);
         } else {
            this.check("render distance 4: chunks unloaded again (" + previous + " -> " + loaded + ")", loaded < previous * 0.5);
         }

         previous = loaded;
      }
   }

   private int settle() {
      int last = -1;
      int stable = 0;

      for (int i = 0; i < 120 && stable < 6; i++) {
         sleep(500L);
         int now = this.onClient(() -> this.minecraft.level == null ? 0 : this.minecraft.level.getChunkSource().getLoadedChunksCount());
         stable = now == last ? stable + 1 : 0;
         last = now;
      }

      return last;
   }

   private void guiScale(Window window) {
      this.minecraft.execute(() -> window.setWindowed(1280, 720));

      for (int i = 0; i < 20 && this.<Integer>onClient(window::getWidth) != 1280; i++) {
         sleep(250L);
      }

      sleep(1000L);
      int actualWidth = this.<Integer>onClient(window::getWidth);
      int actualHeight = this.<Integer>onClient(window::getHeight);
      BedrockOptions bedrock = BedrockOptions.read();
      int automatic = UiScale.scale(actualWidth, actualHeight, bedrock.guiScaleModifier(), bedrock.accessibilityScaling());
      int fits = UiScale.base(actualWidth, actualHeight);
      BedrockNative.LOGGER
         .info(
            "Dev settings test: window {}x{}, Bedrock's rule gives {} (base {}, modifier {}), the window fits {}",
            new Object[]{actualWidth, actualHeight, automatic, fits, bedrock.guiScaleModifier(), fits}
         );
      this.check("the window is 1280x720 (" + actualWidth + "x" + actualHeight + ")", actualWidth == 1280 && actualHeight == 720);
      this.commands("gamemode survival", "effect @s instant_health 1 10 true", "effect @s saturation 1 10 true");

      for (int option : new int[]{0, 2, 3, 1, 4, 0}) {
         this.minecraft.execute(() -> this.minecraft.options.guiScale().set(option));
         sleep(1000L);
         int ui = this.onClient(JsonUi::uiScale);
         int java = this.onClient(() -> window.getGuiScale());
         int expected = option == 0 ? automatic : Math.min(option, fits);
         this.check(
            "Java GUI scale " + (option == 0 ? "Auto" : option) + ": JSON UI scale " + ui + " (expected " + expected + "; Java's own " + java + ")",
            ui == expected
         );
         String name = "settings-" + (option == 0 ? "auto" : String.valueOf(option));
         if (option == 0 || option == 2 || option == 3) {
            this.screenshot(name + "-hud");
            this.minecraft.execute(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
            sleep(1200L);
            this.screenshot(name + "-inventory");
            this.minecraft.execute(() -> this.minecraft.gui.setScreen(null));
            sleep(500L);
         }
      }

      this.liveChange();
   }

   private void liveChange() {
      this.minecraft.execute(() -> this.minecraft.options.guiScale().set(2));
      sleep(800L);
      this.minecraft.execute(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(1200L);
      this.screenshot("settings-live-before");
      this.minecraft.execute(() -> this.minecraft.options.guiScale().set(3));
      sleep(1000L);
      int ui = this.onClient(JsonUi::uiScale);
      this.check("GUI scale 2 -> 3 with the inventory open: JSON UI scale " + ui + " (expected 3)", ui == 3);
      this.screenshot("settings-live-after");
      this.minecraft.execute(() -> this.minecraft.gui.setScreen(null));
      sleep(500L);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(800L);
   }

   private void screenshot(String name) {
      this.minecraft
         .execute(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev settings test: ok: {}", what);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev settings test FAILED: {}", what);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
