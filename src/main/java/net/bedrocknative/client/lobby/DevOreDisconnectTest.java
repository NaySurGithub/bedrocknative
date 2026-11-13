package net.bedrocknative.client.lobby;

import com.mojang.blaze3d.platform.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.net.client.SessionEnd;
import net.bedrocknative.oreui.OreBox;
import net.bedrocknative.oreui.OreDisconnect;
import net.bedrocknative.oreui.OreDisconnectUi;
import net.bedrocknative.oreui.OreImagePainter;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OreWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.jspecify.annotations.Nullable;

public final class DevOreDisconnectTest {
   static final String UNREACHABLE = "bedrocknative-test.invalid";
   static final String KICK_WORDS = "BedrockNative test kick";
   private static final int WIDTH = 1920;
   private static final int HEIGHT = 1080;
   private final Minecraft minecraft;
   private int passed;
   private int failed;
   private boolean javaScreenSeen;

   private DevOreDisconnectTest(Minecraft minecraft) {
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
      Thread.ofVirtual()
         .name("Dev oredisconnect test")
         .start(
            () -> {
               DevOreDisconnectTest test = new DevOreDisconnectTest(awaitInstance());
               Path serversDat = test.minecraft.gameDirectory.toPath().resolve("servers.dat");
               Path serversOld = test.minecraft.gameDirectory.toPath().resolve("servers.dat_old");
               byte[] datBefore = DevOrePlayTest.read(serversDat);
               byte[] oldBefore = DevOrePlayTest.read(serversOld);
               Window window = test.minecraft.getWindow();
               int guiScale = DevOrePlayTest.onClient(test.minecraft, () -> (Integer)test.minecraft.options.guiScale().get());
               int width = DevOrePlayTest.<Integer>onClient(test.minecraft, window::getScreenWidth);
               int height = DevOrePlayTest.<Integer>onClient(test.minecraft, window::getScreenHeight);
               Thread watcher = Thread.ofVirtual().name("Dev oredisconnect watcher").start(test::watchJavaScreen);

               try {
                  test.run();
               } catch (Throwable var14) {
                  BedrockNative.LOGGER.error("Dev oredisconnect test: FAIL crashed", var14);
                  test.failed++;
               } finally {
                  watcher.interrupt();
                  test.check("Java's disconnect screen never showed", !test.javaScreenSeen);
                  DevOrePlayTest.sleep(1000L);
                  DevOrePlayTest.restore(serversDat, datBefore);
                  DevOrePlayTest.restore(serversOld, oldBefore);
                  test.check(
                     "servers.dat and servers.dat_old are back as they were, byte for byte",
                     Arrays.equals(datBefore, DevOrePlayTest.read(serversDat)) && Arrays.equals(oldBefore, DevOrePlayTest.read(serversOld))
                  );
                  test.run(() -> {
                     test.minecraft.options.guiScale().set(guiScale);
                     DevOrePlayTest.frameWindow(window);
                     window.setWindowed(width, height);
                  });
                  BedrockNative.LOGGER.info("Dev oredisconnect test: SUMMARY {} passed, {} failed", test.passed, test.failed);
                  BedrockNative.LOGGER.info("Dev oredisconnect test: done");
                  DevOrePlayTest.sleep(500L);
                  test.run(test.minecraft::stop);
               }
            }
         );
   }

   private DevOrePlayTest.Reporter reporter() {
      return new DevOrePlayTest.Reporter() {
         {
            Objects.requireNonNull(DevOreDisconnectTest.this);
         }

         @Override
         public void check(String what, boolean ok) {
            DevOreDisconnectTest.this.check(what, ok);
         }

         @Override
         public void screenshot(String name) {
            DevOrePlayTest.screenshot(DevOreDisconnectTest.this.minecraft, name);
         }

         @Override
         public void log(String message, Object... args) {
            BedrockNative.LOGGER.info("Dev oredisconnect test: " + message, args);
         }
      };
   }

   private void watchJavaScreen() {
      while (!Thread.currentThread().isInterrupted()) {
         Screen screen = this.screenNow();
         if (screen instanceof DisconnectedScreen && !this.javaScreenSeen) {
            this.javaScreenSeen = true;
            this.reporter().log("Java's DisconnectedScreen showed: {}", screen.getTitle().getString());
         }

         try {
            Thread.sleep(50L);
         } catch (InterruptedException var3) {
            return;
         }
      }
   }

   private void run() {
      DevOrePlayTest.waitFor(
         this.minecraft,
         "the title screen",
         () -> DevOrePlayTest.onClient(
            this.minecraft,
            () -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() instanceof TitleScreen
         ),
         180000L,
         this.reporter()
      );
      this.check("the install has Ore UI (" + OreInstall.defaultFolder() + ")", OreUi.available());
      if (OreUi.available()) {
         DevOrePlayTest.referenceWindow(this.minecraft, 1920, 1080);
         this.run(() -> this.minecraft.options.guiScale().set(2));
         DevOrePlayTest.sleep(1500L);
         int windowWidth = DevOrePlayTest.onClient(this.minecraft, () -> this.minecraft.getWindow().getWidth());
         int windowHeight = DevOrePlayTest.onClient(this.minecraft, () -> this.minecraft.getWindow().getHeight());
         boolean reference = windowWidth == 1920 && windowHeight == 1080;
         this.reporter().log("window {}x{}, GUI scale option 2", windowWidth, windowHeight);
         OrePlayScreen play = DevOrePlayTest.open(this.minecraft, 2, this.reporter());
         if (play != null) {
            this.unreachable(play, reference);
            if (DevOrePlayTest.join(this.minecraft, play, this.reporter(), "oredisconnect")) {
               this.kicked(play, reference);
               if (DevOrePlayTest.join(this.minecraft, play, this.reporter(), "oredisconnect-again")) {
                  this.kickedWithoutWords(play);
               }
            }
         }
      }
   }

   private void unreachable(OrePlayScreen play, boolean reference) {
      ServerData nowhere = new ServerData("BN nowhere", "bedrocknative-test.invalid", Type.OTHER);
      this.run(() -> ConnectScreen.startConnecting(play, this.minecraft, ServerAddress.parseString(nowhere.ip), nowhere, false, null));
      OreDisconnectScreen first = this.awaitScreen(null, "the unreachable server's disconnect screen");
      if (first != null) {
         OreDisconnect data = first.ui().data();
         this.check(
            "an unreachable server opens Bedrock's standard layout ("
               + data.variant()
               + ", reason "
               + data.reason()
               + " "
               + data.reasonName()
               + ": "
               + data.title()
               + " / "
               + data.message()
               + ")",
            data.variant() == OreDisconnect.Variant.STANDARD
               && (data.reason() == DisconnectFailReason.SERVER_NOT_FOUND.ordinal() || data.reason() == DisconnectFailReason.CANT_CONNECT.ordinal())
         );
         OreDisconnectUi.Layout layout = DevOrePlayTest.onClient(this.minecraft, () -> first.ui().layout());
         List<String> ids = layout == null ? List.of() : layout.buttons().stream().map(OreDisconnectUi.Button::id).toList();
         this.check("it offers Retour au menu, Reconnecter, Afficher les détails (" + ids + ")", ids.equals(List.of("back", "reconnect", "details")));
         this.check("Retour au menu leads back to the Play screen it was joined from", first.parent() == play);
         if (reference && layout != null) {
            this.rects(layout);
         }

         DevOrePlayTest.screenshot(this.minecraft, "oredisconnect-unreachable");
         this.compare(first, "oredisconnect-unreachable");
         this.details(first, "oredisconnect-unreachable-details", false);
         DevOrePlayTest.press(this.minecraft, first, () -> widgets(first), "reconnect", this.reporter());
         OreDisconnectScreen second = this.awaitScreen(first, "the disconnect screen after Reconnecter");
         BedrockDisconnects.Ended again = BedrockDisconnects.last();
         this.check(
            "Reconnecter connects again to bedrocknative-test.invalid (" + (again == null ? "nothing" : again.host() + ":" + again.port()) + ")",
            second != null && again != null && "bedrocknative-test.invalid".equals(again.host())
         );
         if (second != null) {
            DevOrePlayTest.press(this.minecraft, second, () -> widgets(second), "back", this.reporter());
            DevOrePlayTest.waitFor(
               this.minecraft, "the Play screen after Retour au menu", () -> DevOrePlayTest.screen(this.minecraft) == play, 5000L, this.reporter()
            );
            DevOrePlayTest.sleep(600L);
            this.check(
               "Retour au menu goes back to the Play screen (" + DevOrePlayTest.name(DevOrePlayTest.screen(this.minecraft)) + ")",
               DevOrePlayTest.screen(this.minecraft) == play
            );
         }
      }
   }

   private void kicked(OrePlayScreen play, boolean reference) {
      this.settle();
      this.command("scriptevent bedrocknative:kick BedrockNative test kick");
      OreDisconnectScreen kicked = this.awaitScreen(null, "the kick's disconnect screen (needs the test pack's kick.js: testing/bds/install.sh, BDS restarted)");
      if (kicked != null) {
         BedrockDisconnects.Ended end = BedrockDisconnects.last();
         this.logEnd(end);
         OreDisconnect data = kicked.ui().data();
         String name = end == null ? "" : end.server().name;
         this.check(
            "a kick with words opens Bedrock's server layout (" + data.variant() + "): \"" + data.title() + "\" / \"" + data.serverMessage() + "\"",
            data.variant() == OreDisconnect.Variant.SERVER
               && data.serverMessage().contains("BedrockNative test kick")
               && !name.isEmpty()
               && data.title().contains(name)
         );
         OreDisconnectUi.Layout layout = DevOrePlayTest.onClient(this.minecraft, () -> kicked.ui().layout());
         List<String> ids = layout == null ? List.of() : layout.buttons().stream().map(OreDisconnectUi.Button::id).toList();
         this.check("the server layout offers Retour au menu and Afficher les détails, no Reconnecter (" + ids + ")", ids.equals(List.of("back", "details")));
         this.check("the server's words sit in their box", layout != null && layout.messageBox() != null && layout.image() == null);
         if (reference && layout != null && layout.card() != null) {
            this.cardRects(layout);
         }

         DevOrePlayTest.screenshot(this.minecraft, "oredisconnect-kick");
         this.compare(kicked, "oredisconnect-kick");
         this.details(kicked, "oredisconnect-kick-details", true);
         DevOrePlayTest.press(this.minecraft, kicked, () -> widgets(kicked), "back", this.reporter());
         DevOrePlayTest.waitFor(this.minecraft, "the Play screen after the kick", () -> DevOrePlayTest.screen(this.minecraft) == play, 5000L, this.reporter());
         DevOrePlayTest.sleep(800L);
         this.check("Retour au menu after the kick goes back to the Play screen", DevOrePlayTest.screen(this.minecraft) == play);
      }
   }

   private void kickedWithoutWords(OrePlayScreen play) {
      this.settle();
      this.command("scriptevent bedrocknative:kick");
      OreDisconnectScreen bare = this.awaitScreen(null, "the bare kick's disconnect screen");
      if (bare != null) {
         this.logEnd(BedrockDisconnects.last());
         OreDisconnect data = bare.ui().data();
         this.reporter().log("a kick without words: {} layout, \"{}\" / \"{}{}\"", data.variant(), data.title(), data.message(), data.serverMessage());
         DevOrePlayTest.screenshot(this.minecraft, "oredisconnect-kick-bare");
         OreDisconnectUi.Layout layout = DevOrePlayTest.onClient(this.minecraft, () -> bare.ui().layout());
         boolean reconnect = layout != null && layout.button("reconnect") != null;
         this.check(
            "a kick without words opens Bedrock's disconnect screen (" + data.variant() + ", Reconnecter " + (reconnect ? "shown" : "not shown") + ")",
            layout != null && layout.button("back") != null
         );
         if (!reconnect) {
            DevOrePlayTest.press(this.minecraft, bare, () -> widgets(bare), "back", this.reporter());
            DevOrePlayTest.waitFor(this.minecraft, "the Play screen", () -> DevOrePlayTest.screen(this.minecraft) == play, 5000L, this.reporter());
         } else {
            DevOrePlayTest.press(this.minecraft, bare, () -> widgets(bare), "reconnect", this.reporter());
            DevOrePlayTest.waitFor(
               this.minecraft,
               "the world after Reconnecter",
               () -> DevOrePlayTest.onClient(this.minecraft, () -> this.minecraft.level != null && this.minecraft.player != null),
               120000L,
               this.reporter()
            );
            this.check(
               "Reconnecter joins the BDS again with the same account",
               DevOrePlayTest.onClient(this.minecraft, () -> this.minecraft.level != null && this.minecraft.player != null)
            );
            DevOrePlayTest.screenshot(this.minecraft, "oredisconnect-reconnected");
         }
      }
   }

   private void details(OreDisconnectScreen screen, String picture, boolean tab) {
      DevOrePlayTest.press(this.minecraft, screen, () -> widgets(screen), "details", this.reporter());
      DevOrePlayTest.sleep(600L);
      OreDisconnectUi.Layout details = DevOrePlayTest.onClient(this.minecraft, () -> screen.ui().layout());
      boolean open = details != null && details.route() == OreDisconnectUi.Route.DETAILS && details.right() != null;
      String text = open && !details.right().content().isEmpty() ? String.join(" | ", details.right().content().getFirst().lines()) : "";
      this.check("Afficher les détails opens the details (" + text + ")", open && text.contains(String.valueOf(screen.ui().data().reason())));
      this.check("the details' \"De\" tab shows " + (tab ? "with" : "without") + " the server's words", open && details.tab() != null == tab);
      DevOrePlayTest.screenshot(this.minecraft, picture);
      DevOrePlayTest.press(this.minecraft, screen, () -> widgets(screen), "details-back", this.reporter());
      DevOrePlayTest.sleep(500L);
      this.check(
         "the details' back arrow returns to the disconnect screen",
         DevOrePlayTest.onClient(this.minecraft, () -> screen.ui().route()) == OreDisconnectUi.Route.MAIN
      );
   }

   private void rects(OreDisconnectUi.Layout layout) {
      this.cardRects(layout);
      OreBox image = layout.image();
      this.check(
         "the connection_error picture is 520 x 180 (" + image + ")",
         image != null && Math.abs(image.w() - 520.0F) <= 1.0F && Math.abs(image.h() - 180.0F) <= 1.0F
      );
      if (layout.buttons().size() == 3) {
         OreBox first = layout.buttons().getFirst().box();
         OreBox last = layout.buttons().getLast().box();
         this.check(
            "the three buttons' row is 87.2rem wide, centred (" + first + " .. " + last + ")",
            Math.abs(last.right() - first.x() - 872.0F) <= 1.0F
               && Math.abs((first.x() + last.right()) / 2.0F - 960.0F) <= 1.0F
               && Math.abs(first.h() - 48.0F) <= 1.0F
         );
      }
   }

   private void cardRects(OreDisconnectUi.Layout layout) {
      OreBox card = layout.card();
      this.check(
         "the card is the grid's 10/12 column, centred on the window (" + card + ")",
         card != null && Math.abs(card.px0() - 435) <= 1 && Math.abs(card.px1() - 1 - 1485) <= 1 && Math.abs(card.centerY() - 540.0F) <= 1.0F
      );
   }

   private void compare(OreDisconnectScreen screen, String picture) {
      DevOrePlayTest.sleep(700L);
      Path file = this.minecraft.gameDirectory.toPath().resolve("screenshots").resolve(picture + ".png");

      BufferedImage shot;
      try {
         shot = Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
      } catch (IOException var18) {
         shot = null;
      }

      OreInstall install = OreUi.install();
      if (shot != null && install != null) {
         int width = shot.getWidth();
         int height = shot.getHeight();
         BufferedImage offline = DevOrePlayTest.onClient(this.minecraft, () -> {
            OreImagePainter painter = new OreImagePainter(width, height, install, 0);
            screen.ui().render(painter, 0L);
            return painter.image();
         });
         long compared = 0L;
         long close = 0L;

         for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
               int expected = offline.getRGB(x, y);
               if (expected >>> 24 == 255) {
                  int actual = shot.getRGB(x, y);
                  compared++;
                  if (Math.abs((expected >> 16 & 0xFF) - (actual >> 16 & 0xFF)) <= 24
                     && Math.abs((expected >> 8 & 0xFF) - (actual >> 8 & 0xFF)) <= 24
                     && Math.abs((expected & 0xFF) - (actual & 0xFF)) <= 24) {
                     close++;
                  }
               }
            }
         }

         double share = compared == 0L ? 0.0 : 100.0 * close / compared;
         this.check(String.format("%s: %.1f%% of %d opaque Ore UI pixels match the offline render", picture, share, compared), share >= 97.0);

         try {
            ImageIO.write(offline, "png", file.resolveSibling(picture + "-offline.png").toFile());
         } catch (IOException var17) {
            this.reporter().log("couldn't save the offline render: {}", var17.getMessage());
         }
      } else {
         this.check("the screenshot " + picture + " can be read for the comparison", false);
      }
   }

   @Nullable
   private OreDisconnectScreen awaitScreen(@Nullable OreDisconnectScreen previous, String what) {
      long started = System.currentTimeMillis();

      while (
         !(
               this.screenNow() instanceof OreDisconnectScreen screen
                  && screen != previous
                  && Boolean.TRUE.equals(this.ask(() -> this.minecraft.gui.overlay() == null))
            )
            && System.currentTimeMillis() - started < 60000L
      ) {
         DevOrePlayTest.sleep(100L);
      }

      DevOrePlayTest.sleep(1500L);
      Screen screen = this.screenNow();
      OreDisconnectScreen found = screen instanceof OreDisconnectScreen ore && ore != previous ? ore : null;
      this.check(what + " shows (" + DevOrePlayTest.name(screen) + ")", found != null);
      return found;
   }

   @Nullable
   private Screen screenNow() {
      return this.ask(() -> this.minecraft.gui.screen());
   }

   @Nullable
   private <T> T ask(Supplier<T> question) {
      if (this.minecraft.isSameThread()) {
         return question.get();
      } else {
         CompletableFuture<T> answer = CompletableFuture.supplyAsync(question, this.minecraft::execute);

         try {
            return answer.get(2L, TimeUnit.SECONDS);
         } catch (ExecutionException | TimeoutException var4) {
            return null;
         } catch (InterruptedException var5) {
            Thread.currentThread().interrupt();
            return null;
         }
      }
   }

   private void settle() {
      DevOrePlayTest.waitFor(
         this.minecraft,
         "the loading screen to go",
         () -> DevOrePlayTest.onClient(this.minecraft, () -> this.minecraft.level != null && this.minecraft.gui.screen() == null),
         60000L,
         this.reporter()
      );
      DevOrePlayTest.sleep(3000L);
   }

   private void command(String command) {
      this.reporter().log("/{}", command);
      this.run(() -> {
         if (this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
         }
      });
   }

   private void logEnd(@Nullable BedrockDisconnects.Ended end) {
      if (end == null) {
         this.reporter().log("no session end on record");
      } else {
         SessionEnd how = end.end();
         this.reporter()
            .log(
               "the server sent {} (reason {} {}, skip {}, words {}), detail {}",
               how.source(),
               how.reasonCode(),
               how.reason(),
               how.skipScreen(),
               "\"" + how.message().replace("\n", "\\n") + "\"",
               how.detail()
            );
      }
   }

   private static OreWidgets widgets(OreDisconnectScreen screen) {
      OreDisconnectUi.Layout layout = screen.ui().layout();
      return layout == null ? new OreWidgets() : layout.widgets();
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev oredisconnect test: {} {}", ok ? "PASS" : "FAIL", what);
   }

   private void run(Runnable task) {
      DevOrePlayTest.onClient(this.minecraft, () -> {
         task.run();
         return null;
      });
   }
}
