package net.bedrocknative.client.lobby;

import com.mojang.blaze3d.platform.Window;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.client.ui.JsonUiPauseScreen;
import net.bedrocknative.ui.MenuReference;
import net.bedrocknative.ui.PauseData;
import net.bedrocknative.ui.StartData;
import net.bedrocknative.ui.UiControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLVideo;

public final class DevOreMenusTest {
   private static final String SPLASH = "Enter the Render Dragon!";
   private final Minecraft minecraft;
   private int passed;
   private int failed;
   private boolean borderless;

   private DevOreMenusTest(Minecraft minecraft) {
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
         .name("Dev oremenus test")
         .start(
            () -> {
               DevOreMenusTest test = new DevOreMenusTest(awaitInstance());
               Window window = test.minecraft.getWindow();
               int[] restore = new int[3];

               try {
                  test.waitFor(
                     "the title screen",
                     () -> test.onClient(
                        () -> test.minecraft.isGameLoadFinished() && test.minecraft.gui.overlay() == null && test.minecraft.gui.screen() instanceof TitleScreen
                     ),
                     180000L
                  );
                  restore[0] = test.onClient(window::getScreenWidth);
                  restore[1] = test.onClient(() -> window.getScreenHeight());
                  restore[2] = test.onClient(() -> test.minecraft.options.guiScale().get());
                  test.run(window);
               } catch (Throwable e) {
                  BedrockNative.LOGGER.error("Dev oremenus test: FAIL crashed", e);
                  test.failed++;
               } finally {
                  test.restore(window, restore);
                  BedrockNative.LOGGER.info("Dev oremenus test: SUMMARY {} passed, {} failed", test.passed, test.failed);
                  BedrockNative.LOGGER.info("Dev oremenus test: done");
                  test.sleep(500L);
                  test.run(test.minecraft::stop);
               }
            }
         );
   }

   private void run(Window window) {
      boolean sized = this.size(window);
      this.run(() -> this.minecraft.options.guiScale().set(2));
      this.sleep(1000L);
      int scale = this.onClient(JsonUi::uiScale);
      this.check("JSON UI scale " + scale + " (the references' 2)", scale == 2);
      JsonUiStartScreen.pinSplash("Enter the Render Dragon!");
      this.run(() -> this.minecraft.gui.setScreen(new TitleScreen()));
      this.waitFor("Bedrock's start screen", () -> this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiStartScreen), 30000L);
      this.sleep(2500L);
      JsonUiStartScreen start = this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiStartScreen screen ? screen : null);
      this.check("the title screen is Bedrock's start screen (" + name(this.screen()) + ")", start != null);
      if (start != null) {
         this.check("the splash is the reference's (" + start.splash() + ")", "Enter the Render Dragon!".equals(start.splash()));
         this.check(
            "the panorama is the alternate dungeon set (" + BedrockPanorama.faces() + ")", "textures/ui/panorama_alternate_".equals(BedrockPanorama.faces())
         );
         this.rects("start", start, MenuReference.start(), sized);
         this.screenshot("oremenus-start");

         for (String service : StartData.services()) {
            this.click(start, service);
            this.sleep(400L);
            this.check(
               "start: " + service + " only shows a notice (" + noticeText(start.notice().text()) + ")",
               this.onClient(() -> this.minecraft.gui.screen() == start && start.notice().text() != null)
            );
         }

         this.screenshot("oremenus-start-notice");
         JsonUiStartScreen.pinSplash(null);
         this.join();
         boolean joined = this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null);
         this.check("joined 127.0.0.1 (" + name(this.screen()) + ")", joined);
         if (joined) {
            this.sleep(8000L);
            this.run(() -> this.minecraft.pauseGame(false));
            this.waitFor("Bedrock's pause menu", () -> this.screen() instanceof JsonUiPauseScreen, 5000L);
            this.sleep(1500L);
            JsonUiPauseScreen pause = this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiPauseScreen screen ? screen : null);
            this.check("Esc opens Bedrock's pause menu (" + name(this.screen()) + ")", pause != null);
            if (pause != null) {
               this.check("the HUD is out of the frame under the pause menu", this.onClient(JsonUiPauseScreen::hidesHud));
               this.rects("pause", pause, MenuReference.pause(), sized);
               this.screenshot("oremenus-pause");

               for (String service : PauseData.services()) {
                  this.click(pause, service);
                  this.sleep(400L);
                  this.check(
                     "pause: " + service + " only shows a notice (" + noticeText(pause.notice().text()) + ")",
                     this.onClient(() -> this.minecraft.gui.screen() == pause && pause.notice().text() != null)
                  );
               }

               File folder = new File(this.minecraft.gameDirectory, "screenshots");
               int before = count(folder);
               this.click(pause, "button.screenshot");
               this.sleep(2000L);
               this.check(
                  "the camera closes the menu and saves a screenshot (" + count(folder) + " files, " + before + " before)",
                  this.onClient(() -> this.minecraft.gui.screen() == null) && count(folder) > before
               );
               this.run(() -> this.minecraft.pauseGame(false));
               this.sleep(1000L);
               this.check("Esc opens it again", this.screen() instanceof JsonUiPauseScreen);
            }
         }
      }
   }

   private boolean size(Window window) {
      this.run(() -> window.setWindowed(1920, 1080));
      this.sleep(2000L);
      this.waitFor("a 1920x1080 window", () -> this.onClient(() -> window.getWidth() == 1920 && window.getHeight() == 1080), 5000L);
      log("the framed window is {}x{}; trying it borderless at 0,0", this.onClient(window::getWidth), this.onClient(window::getHeight));
      this.run(() -> {
         this.borderless = true;
         SDLVideo.SDL_SetWindowBordered(window.handle(), false);
         SDLVideo.SDL_SetWindowPosition(window.handle(), 0, 0);
         SDLVideo.SDL_SetWindowSize(window.handle(), 1920, 1080);
         SDLVideo.SDL_SyncWindow(window.handle());
      });
      this.waitFor("a 1920x1080 borderless window", () -> this.onClient(() -> window.getWidth() == 1920 && window.getHeight() == 1080), 5000L);
      this.sleep(1000L);
      log(
         "window sizes: framebuffer {}x{}, screen {}x{}, gui scaled {}x{}",
         this.onClient(window::getWidth),
         this.onClient(window::getHeight),
         this.onClient(window::getScreenWidth),
         this.onClient(window::getScreenHeight),
         this.onClient(window::getGuiScaledWidth),
         this.onClient(window::getGuiScaledHeight)
      );
      int width = this.onClient(window::getWidth);
      int height = this.onClient(window::getHeight);
      boolean ok = width == 1920 && height == 1080;
      this.check("the window is 1920x1080 (" + width + "x" + height + (this.borderless ? ", borderless" : "") + ")", ok);
      return ok;
   }

   private void restore(Window window, int[] restore) {
      if (restore[0] > 0) {
         this.run(() -> {
            this.minecraft.options.guiScale().set(restore[2]);
            if (this.borderless) {
               SDLVideo.SDL_SetWindowBordered(window.handle(), true);
            }

            window.setWindowed(restore[0], restore[1]);
         });
         this.sleep(500L);
      }
   }

   private void rects(String screen, JsonUiHost host, List<MenuReference.Rect> rects, boolean sized) {
      if (!sized) {
         log("SKIP the {} rects: the window isn't 1920x1080", screen);
      } else {
         for (MenuReference.Result result : this.onClient(
            () -> MenuReference.compare(new MenuReference.Found(host.ui().instance(), host.ui().input()), rects, JsonUi.uiScale())
         )) {
            this.check(screen + " " + result, result.ok());
         }
      }
   }

   private void join() {
      this.run(() -> {
         ServerData server = new ServerData("BDS", "127.0.0.1", Type.OTHER);
         server.setResourcePackStatus(ServerPackStatus.ENABLED);
         Screen parent = this.minecraft.gui.screen() != null ? this.minecraft.gui.screen() : new TitleScreen();
         ConnectScreen.startConnecting(parent, this.minecraft, ServerAddress.parseString("127.0.0.1"), server, false, null);
      });
      this.waitFor(
         "the world after joining 127.0.0.1",
         () -> this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null && this.minecraft.gui.screen() == null),
         120000L
      );
   }

   private static String noticeText(@Nullable Component text) {
      return text == null ? "none" : text.getString();
   }

   private static int count(File folder) {
      String[] files = folder.list();
      return files == null ? 0 : files.length;
   }

   private void click(JsonUiHost host, String id) {
      UiControl control = this.onClient(() -> host.ui().input().firing(id, null, -1));
      if (control == null) {
         this.check("a control fires " + id, false);
      } else {
         this.run(() -> {
            float x = JsonUi.toGui(control.x() + control.width() / 2.0F);
            float y = JsonUi.toGui(control.y() + control.height() / 2.0F);
            Screen screen = (Screen)host;
            MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
            screen.mouseMoved(x, y);
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
         });
      }
   }

   private static String name(@Nullable Screen screen) {
      return screen == null ? "nothing" : screen.getClass().getSimpleName();
   }

   private @Nullable Screen screen() {
      return this.onClient(() -> this.minecraft.gui.screen());
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev oremenus test: {} {}", ok ? "PASS" : "FAIL", what);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      this.sleep(800L);
   }

   private void waitFor(String what, BooleanSupplier done, long millis) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > millis) {
            log("waited too long for {} ({})", what, name(this.screen()));
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
      BedrockNative.LOGGER.info("Dev oremenus test: " + message, args);
   }
}
