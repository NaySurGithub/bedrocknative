package net.bedrocknative.client.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.base.BaseConsent;
import net.bedrocknative.content.base.BaseDownload;
import net.bedrocknative.content.base.DownloadedBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;

public final class DevBaseTest {
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   private DevBaseTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   private static Minecraft awaitInstance() {
      Minecraft instance;
      while ((instance = Minecraft.getInstance()) == null || instance.getWindow() == null) {
         try {
            Thread.sleep(200L);
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(var2);
         }
      }

      return instance;
   }

   public static void start() {
      Thread.ofVirtual().name("Dev base test").start(() -> {
         DevBaseTest test = new DevBaseTest(awaitInstance());
         Path answerFile = BaseDownloads.configFolder().resolve("base-download.json");
         Path noticeFile = BaseDownloads.configFolder().resolve("base-notice.json");
         byte[] answerBefore = read(answerFile);
         byte[] noticeBefore = read(noticeFile);

         try {
            test.run();
         } catch (Throwable var9) {
            BedrockNative.LOGGER.error("Dev base test: FAIL crashed", var9);
            test.failed++;
         } finally {
            restore(answerFile, answerBefore);
            restore(noticeFile, noticeBefore);
            BedrockNative.LOGGER.info("Dev base test: SUMMARY {} passed, {} failed", test.passed, test.failed);
            BedrockNative.LOGGER.info("Dev base test: done");
            test.sleep(500L);
            test.onClient(() -> {
               test.minecraft.stop();
               return null;
            });
         }
      });
   }

   private void run() {
      this.waitFor(
         "the title screen",
         () -> this.onClient(() -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() != null),
         180000L
      );
      this.sleep(2000L);
      BaseContent.Snapshot base = BaseContent.current();

      String mode = switch (base.kind()) {
         case INSTALL -> "install";
         case EMBEDDED -> base.pack() != null ? "embedded" : "embedded-folder";
         case NONE -> BaseContent.ownUi() ? "none-own" : "none";
      };
      log(
         "{} (policy {}, sound folder {}, packs {})",
         BaseContent.line(),
         BaseConsent.policy(),
         DownloadedBase.folder(),
         this.onClient(() -> PackStack.active().packs())
      );
      if (BaseConsent.policy() == BaseConsent.Policy.ASK && BaseDownloads.needed()) {
         this.notice();
         this.question();
      }

      boolean ui = BaseContent.hasUi();
      this.check(
         "the screens' source decides the lobby (" + BaseContent.uiSource() + ", lobby " + BedrockLobby.enabled() + ")",
         BedrockLobby.enabled() == (ui && JsonUi.mode() != JsonUi.Mode.OFF)
      );
      if (ui) {
         this.waitFor(
            "Bedrock's start screen",
            () -> this.onClient(() -> this.minecraft.gui.screen() != null && this.minecraft.gui.screen().getClass() != TitleScreen.class),
            30000L
         );
         this.sleep(2500L);
      }

      Screen screen = this.onClient(() -> this.minecraft.gui.screen());
      this.check("the title screen of this mode: " + name(screen), screen != null);
      this.check(
         "no download started (policy " + BaseConsent.policy() + ")",
         BaseConsent.policy() != BaseConsent.Policy.OFF || !BaseDownload.running() && BaseDownload.last() == null
      );
      this.screenshot("base-title-" + mode);
   }

   private void notice() {
      if (!BaseNotice.shouldShow(BaseDownloads.configFolder())) {
         log("the notice was seen before ({}): not shown", "base-notice.json");
      } else {
         this.waitFor("the base notice", () -> this.onClient(() -> this.minecraft.gui.screen() instanceof BaseNoticeScreen), 30000L);
         BaseNoticeScreen screen = this.onClient(() -> this.minecraft.gui.screen() instanceof BaseNoticeScreen notice ? notice : null);
         this.check("the notice shows over the title screen with the embedded base", screen != null);
         if (screen != null) {
            this.sleep(1000L);
            this.screenshot("base-notice");
            this.onClient(() -> {
               screen.dismiss();
               return null;
            });
            this.sleep(1000L);
            this.check("the notice is kept as seen", !BaseNotice.shouldShow(BaseDownloads.configFolder()));
            this.check(
               "the title screen is back (" + name(this.onClient(() -> this.minecraft.gui.screen())) + ")",
               this.onClient(() -> this.minecraft.gui.screen() instanceof TitleScreen)
            );
         }
      }
   }

   private void question() {
      if (!BaseConsent.shouldAsk(BaseDownloads.configFolder())) {
         log("the sound question was answered before: not asked");
      } else {
         this.onClient(() -> {
            BaseDownloads.queueForQuestion(List.of("sounds/mob/zombie/say1", "sounds/random/orb"));
            return null;
         });
         this.waitFor("the sound files question", () -> this.onClient(() -> this.minecraft.gui.screen() instanceof BaseDownloadScreen), 20000L);
         BaseDownloadScreen screen = this.onClient(() -> this.minecraft.gui.screen() instanceof BaseDownloadScreen question ? question : null);
         this.check("the question shows over the title screen once sound files are wanted", screen != null);
         if (screen != null) {
            this.sleep(1000L);
            this.screenshot("base-question");
            this.onClient(() -> {
               screen.choose(BaseConsent.Answer.LATER);
               return null;
            });
            this.sleep(1000L);
            this.check(
               "Later is kept (" + BaseConsent.stored(BaseDownloads.configFolder()) + ")",
               BaseConsent.stored(BaseDownloads.configFolder()) == BaseConsent.Answer.LATER
            );
            this.check("Later downloads nothing", !BaseDownload.running() && BaseDownload.last() == null);
            this.check(
               "the title screen is back (" + name(this.onClient(() -> this.minecraft.gui.screen())) + ")",
               this.onClient(() -> this.minecraft.gui.screen() instanceof TitleScreen)
            );
            this.check("asked again at the next start", BaseConsent.shouldAsk(BaseDownloads.configFolder()));
            this.sleep(2000L);
            this.check("not asked twice in one run", this.onClient(() -> !(this.minecraft.gui.screen() instanceof BaseDownloadScreen)));
         }
      }
   }

   @Nullable
   private static byte[] read(Path file) {
      try {
         return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
      } catch (IOException var2) {
         return null;
      }
   }

   private static void restore(Path file, @Nullable byte[] before) {
      try {
         if (before == null) {
            Files.deleteIfExists(file);
         } else {
            Files.write(file, before);
         }
      } catch (IOException var3) {
         BedrockNative.LOGGER.warn("Dev base test: couldn't put {} back: {}", file, var3.getMessage());
      }
   }

   private static String name(@Nullable Screen screen) {
      return screen == null ? "nothing" : screen.getClass().getSimpleName();
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev base test: {} {}", ok ? "PASS" : "FAIL", what);
   }

   private void screenshot(String name) {
      this.onClient(() -> {
         Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
         return null;
      });
      this.sleep(500L);
   }

   private void waitFor(String what, BooleanSupplier done, long millis) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > millis) {
            BedrockNative.LOGGER.info("Dev base test: FAIL waited too long for {} ({})", what, name(this.onClient(() -> this.minecraft.gui.screen())));
            this.failed++;
            return;
         }

         this.sleep(100L);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return this.minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
      }
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev base test: " + message, args);
   }
}
