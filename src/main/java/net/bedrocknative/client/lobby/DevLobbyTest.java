package net.bedrocknative.client.lobby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.account.AccountsScreen;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.ui.StartData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;

public final class DevLobbyTest {
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   private DevLobbyTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   private static Minecraft awaitInstance() {
      Minecraft instance;
      while ((instance = Minecraft.getInstance()) == null || instance.getWindow() == null) {
         try {
            Thread.sleep(200L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
         }
      }

      return instance;
   }

   public static void start() {
      Thread.ofVirtual()
         .name("Dev lobby test")
         .start(
            () -> {
               DevLobbyTest test = new DevLobbyTest(awaitInstance());
               Path serversDat = test.minecraft.gameDirectory.toPath().resolve("servers.dat");
               Path serversOld = test.minecraft.gameDirectory.toPath().resolve("servers.dat_old");
               byte[] datBefore = read(serversDat);
               byte[] oldBefore = read(serversOld);

               try {
                  test.run(serversDat, datBefore);
               } catch (Throwable e) {
                  BedrockNative.LOGGER.error("Dev lobby test: FAIL crashed", e);
                  test.failed++;
               } finally {
                  test.sleep(3000L);
                  test.restore(serversDat, datBefore);
                  test.restore(serversOld, oldBefore);
                  test.check(
                     "servers.dat and servers.dat_old are back as they were, byte for byte",
                     Arrays.equals(datBefore, read(serversDat)) && Arrays.equals(oldBefore, read(serversOld))
                  );
                  BedrockNative.LOGGER.info("Dev lobby test: SUMMARY {} passed, {} failed", test.passed, test.failed);
                  BedrockNative.LOGGER.info("Dev lobby test: done");
                  test.sleep(500L);
                  test.run(test.minecraft::stop);
               }
            }
         );
   }

   private void run(Path serversDat, byte @Nullable [] datBefore) throws Exception {
      this.waitFor(
         "the title screen",
         () -> this.onClient(
            () -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() instanceof TitleScreen
         ),
         180000L
      );
      this.check("the lobby is on by default (JSON UI not off, a Bedrock install)", BedrockLobby.enabled());
      this.waitFor("Bedrock's start screen", () -> this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiStartScreen), 30000L);
      this.sleep(1500L);
      JsonUiStartScreen start = this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiStartScreen screen ? screen : null);
      this.check("the title screen is Bedrock's start screen (" + name(this.screen()) + ")", start != null);
      if (start != null) {
         List<String> labels = this.labels(start);
         log("start screen labels {}, problems {}", labels, start.ui().instance().problems());
         this.check(
            "Play, Settings, Realms and the store show",
            labels.containsAll(List.of("Play", "Settings", "Realms")) && this.firing(start, "button.menu_store", null, -1) != null
         );
         this.check("no Quit or Mods row (Bedrock's has none)", labels.stream().noneMatch(text -> text.equals("Quit") || text.equals("Mods")));
         boolean signedIn = !start.data().gamertag().isEmpty();
         this.check(
            signedIn ? "the Profile button and the gamertag " + start.data().gamertag() + " show" : "Bedrock's Sign In shows (no Xbox account)",
            signedIn ? labels.contains("Profile") : labels.contains("Sign In")
         );
         this.screenshot("lobby-start");

         for (String service : StartData.services()) {
            this.click(start, service, null, -1);
            this.sleep(400L);
            this.check(
               "Bedrock's service "
                  + service
                  + " only shows a notice ("
                  + this.onClient(() -> start.notice().text() == null ? "none" : start.notice().text().getString())
                  + ")",
               this.onClient(() -> this.minecraft.gui.screen() == start && start.notice().text() != null)
            );
         }

         this.opens(start, "button.menu_settings", OptionsScreen.class, "Settings opens Java's options");
         this.opens(start, signedIn ? "button.menu_profile" : "button.signin", AccountsScreen.class, "the account button opens the Xbox accounts");
         this.run(() -> start.keyPressed(new KeyEvent(41, 0, 0)));
         this.sleep(500L);
         StartExitScreen exit = this.onClient(() -> this.minecraft.gui.screen() instanceof StartExitScreen screen ? screen : null);
         this.check("Esc opens the quit screen (" + name(this.screen()) + ")", exit != null);
         if (exit != null) {
            this.screenshot("lobby-quit");
            this.check(
               "it offers Quit Game and Back" + (StartExitScreen.modsAvailable() ? " and Mods" : ""),
               exit.quitButton() != null && exit.backButton() != null && exit.modsButton() != null == StartExitScreen.modsAvailable()
            );
            this.run(exit::onClose);
            this.sleep(500L);
            this.check("Back returns to the start screen", this.screen() == start);
         }

         this.click(start, "button.menu_play", null, -1);
         this.waitFor("the play screen", () -> this.screen() instanceof OrePlayScreen || this.screen() instanceof JoinMultiplayerScreen, 10000L);
         this.sleep(1000L);
         OrePlayScreen play = this.onClient(() -> this.minecraft.gui.screen() instanceof OrePlayScreen screen ? screen : null);
         this.check("Play opens Bedrock's Play screen (Ore UI; " + name(this.screen()) + ")", play != null);
         if (play != null) {
            DevOrePlayTest.Reporter reporter = new DevOrePlayTest.Reporter() {
               @Override
               public void check(String what, boolean ok) {
                  DevLobbyTest.this.check(what, ok);
               }

               @Override
               public void screenshot(String name) {
                  DevLobbyTest.this.screenshot(name);
               }

               @Override
               public void log(String message, Object... args) {
                  DevLobbyTest.log(message, args);
               }
            };
            DevOrePlayTest.scenario(this.minecraft, play, reporter, serversDat, "lobby-play", false);
            log("LAN games found {}", this.onClient(() -> play.model().lan()));
            boolean joined = DevOrePlayTest.join(this.minecraft, play, reporter, "lobby-play");
            this.sleep(4000L);
            this.screenshot(joined ? "lobby-joined" : "lobby-join-failed");
         }
      }
   }

   private void opens(JsonUiStartScreen start, String id, Class<? extends Screen> type, String what) {
      this.click(start, id, null, -1);
      this.sleep(800L);
      this.check(what + " (" + name(this.screen()) + ")", type.isInstance(this.screen()));
      this.back(start);
   }

   private void back(JsonUiStartScreen start) {
      this.run(() -> {
         Screen shown = this.minecraft.gui.screen();
         if (shown != start && shown != null) {
            shown.onClose();
         }

         if (this.minecraft.gui.screen() != start) {
            this.minecraft.gui.setScreen(start);
         }
      });
      this.sleep(800L);
   }

   private static List<String> labelsOf(Screen screen) {
      List<String> labels = new ArrayList<>();
      if (screen instanceof JsonUiHost host && host.ui().instance().root() != null) {
         host.ui().instance().root().walk(control -> {
            if (control.type() == UiType.LABEL && control.visible() && !control.text().isEmpty()) {
               labels.add(control.text());
            }
         });
      }

      return labels;
   }

   private List<String> labels(JsonUiHost host) {
      return this.onClient(() -> labelsOf((Screen)host));
   }

   private @Nullable UiControl firing(JsonUiHost host, String id, @Nullable String collection, int index) {
      return this.onClient(() -> host.ui().input().firing(id, collection, index));
   }

   private void click(JsonUiHost host, String id, @Nullable String collection, int index) {
      UiControl control = this.firing(host, id, collection, index);
      if (control == null) {
         this.check("a control fires " + id, false);
      } else {
         this.run(() -> host.ui().input().reveal(control));
         this.sleep(200L);
         this.press((Screen)host, control);
      }
   }

   private void press(Screen screen, UiControl control) {
      this.run(() -> {
         float x = control.x() + control.width() / 2.0F;
         float y = control.y() + control.height() / 2.0F;
         MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
         screen.mouseMoved(x, y);
         screen.mouseClicked(event, false);
         screen.mouseReleased(event);
      });
   }

   private static byte @Nullable [] read(Path file) {
      try {
         return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
      } catch (IOException e) {
         return null;
      }
   }

   private void restore(Path file, byte @Nullable [] bytes) {
      try {
         if (bytes == null) {
            Files.deleteIfExists(file);
         } else {
            Files.write(file, bytes);
         }
      } catch (IOException e) {
         BedrockNative.LOGGER.error("Dev lobby test: FAIL couldn't put {} back", file, e);
         this.failed++;
      }
   }

   private static String name(@Nullable Screen screen) {
      return screen == null ? "nothing" : screen.getClass().getSimpleName();
   }

   private Screen screen() {
      return this.onClient(() -> this.minecraft.gui.screen());
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev lobby test: {} {}", ok ? "PASS" : "FAIL", what);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      this.sleep(500L);
   }

   private void waitFor(String what, BooleanSupplier done, long millis) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > millis) {
            BedrockNative.LOGGER.info("Dev lobby test: FAIL waited too long for {} ({})", what, name(this.screen()));
            this.failed++;
            return;
         }

         this.sleep(100L);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return this.minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev lobby test: " + message, args);
   }
}
