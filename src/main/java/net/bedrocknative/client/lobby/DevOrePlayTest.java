package net.bedrocknative.client.lobby;

import com.mojang.blaze3d.platform.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.oreui.OreSession;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.oreui.OreBox;
import net.bedrocknative.oreui.OreImagePainter;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OreKey;
import net.bedrocknative.oreui.OreModal;
import net.bedrocknative.oreui.OrePlayLayout;
import net.bedrocknative.oreui.OrePlayUi;
import net.bedrocknative.oreui.OreServerFormUi;
import net.bedrocknative.oreui.OreWidget;
import net.bedrocknative.oreui.OreWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLVideo;

public final class DevOrePlayTest {
   static final String NAME = "BN test";
   static final String ADDRESS = "127.0.0.1";
   static final String ADD_ADDRESS = "127.0.0.3";
   private static final int WIDTH = 1920;
   private static final int HEIGHT = 1080;
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   private DevOrePlayTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   static Minecraft awaitInstance() {
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
         .name("Dev oreplay test")
         .start(
            () -> {
               DevOrePlayTest test = new DevOrePlayTest(awaitInstance());
               Path serversDat = test.minecraft.gameDirectory.toPath().resolve("servers.dat");
               Path serversOld = test.minecraft.gameDirectory.toPath().resolve("servers.dat_old");
               byte[] datBefore = read(serversDat);
               byte[] oldBefore = read(serversOld);
               Window window = test.minecraft.getWindow();
               int guiScale = onClient(test.minecraft, () -> test.minecraft.options.guiScale().get());
               int width = onClient(test.minecraft, window::getScreenWidth);
               int height = onClient(test.minecraft, window::getScreenHeight);

               try {
                  test.run(serversDat);
               } catch (Throwable e) {
                  BedrockNative.LOGGER.error("Dev oreplay test: FAIL crashed", e);
                  test.failed++;
               } finally {
                  sleep(1500L);
                  restore(serversDat, datBefore);
                  restore(serversOld, oldBefore);
                  test.check(
                     "servers.dat and servers.dat_old are back as they were, byte for byte",
                     Arrays.equals(datBefore, read(serversDat)) && Arrays.equals(oldBefore, read(serversOld))
                  );
                  test.run(() -> {
                     test.minecraft.options.guiScale().set(guiScale);
                     frameWindow(window);
                     window.setWindowed(width, height);
                  });
                  BedrockNative.LOGGER.info("Dev oreplay test: SUMMARY {} passed, {} failed", test.passed, test.failed);
                  BedrockNative.LOGGER.info("Dev oreplay test: done");
                  sleep(500L);
                  test.run(test.minecraft::stop);
               }
            }
         );
   }

   private DevOrePlayTest.Reporter reporter() {
      return new DevOrePlayTest.Reporter() {
         @Override
         public void check(String what, boolean ok) {
            DevOrePlayTest.this.check(what, ok);
         }

         @Override
         public void screenshot(String name) {
            DevOrePlayTest.screenshot(DevOrePlayTest.this.minecraft, name);
         }

         @Override
         public void log(String message, Object... args) {
            BedrockNative.LOGGER.info("Dev oreplay test: " + message, args);
         }
      };
   }

   private void run(Path serversDat) {
      waitFor(
         this.minecraft,
         "the title screen",
         () -> onClient(
            this.minecraft,
            () -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() instanceof TitleScreen
         ),
         180000L,
         this.reporter()
      );
      this.check("the install has Ore UI (" + OreInstall.defaultFolder() + ")", OreUi.available());
      if (OreUi.available()) {
         referenceWindow(this.minecraft, 1920, 1080);
         this.run(() -> this.minecraft.options.guiScale().set(2));
         sleep(1500L);
         int windowWidth = onClient(this.minecraft, () -> this.minecraft.getWindow().getWidth());
         int windowHeight = onClient(this.minecraft, () -> this.minecraft.getWindow().getHeight());
         this.reporter().log("window {}x{}, GUI scale option 2", windowWidth, windowHeight);
         OrePlayScreen play = open(this.minecraft, 2, this.reporter());
         if (play != null) {
            boolean reference = windowWidth == 1920 && windowHeight == 1080;
            if (!reference) {
               this.reporter().log("SKIP the spec's rects: the window is {}x{}, not {}x{}", windowWidth, windowHeight, 1920, 1080);
            }

            scenario(this.minecraft, play, this.reporter(), serversDat, "oreplay", reference);
            this.keys(play);
            press(this.minecraft, play, () -> play.ui().frame().widgets(), "back", this.reporter());
            sleep(600L);
            this.check("the back arrow returns to the start screen (" + name(screen(this.minecraft)) + ")", screen(this.minecraft) instanceof TitleScreen);
         }
      }
   }

   private void keys(OrePlayScreen play) {
      int tab = onClient(this.minecraft, () -> play.ui().tab());
      this.key(play, 48);
      int next = onClient(this.minecraft, () -> play.ui().tab());
      this.key(play, 47);
      int back = onClient(this.minecraft, () -> play.ui().tab());
      this.check(
         "\"]\" opens the next tab and \"[\" the previous one (" + tab + " -> " + next + " -> " + back + ")",
         (tab == 2 ? next == tab : next == tab + 1) && back == (tab == 2 ? tab - 1 : tab)
      );
      this.key(play, 43);
      String focused = onClient(this.minecraft, () -> play.ui().interaction().focused());
      this.check(
         "Tab focuses a control with its ring (" + focused + ")", focused != null && onClient(this.minecraft, () -> play.ui().interaction().focusVisible())
      );
      screenshot(this.minecraft, "oreplay-focus");
   }

   private void key(Screen screen, int key) {
      this.run(() -> screen.keyPressed(new KeyEvent(key, 0, 0)));
      sleep(400L);
   }

   static @Nullable OrePlayScreen open(Minecraft minecraft, int tab, DevOrePlayTest.Reporter reporter) {
      onClient(minecraft, () -> {
         BedrockLobby.lastTab(tab);
         minecraft.gui.setScreen(new JoinMultiplayerScreen(minecraft.gui.screen()));
         return null;
      });
      waitFor(minecraft, "the Ore UI play screen", () -> screen(minecraft) instanceof OrePlayScreen, 10000L, reporter);
      sleep(1500L);
      OrePlayScreen play = onClient(minecraft, () -> minecraft.gui.screen() instanceof OrePlayScreen screen ? screen : null);
      reporter.check("Java's multiplayer screen opens Bedrock's Play screen (" + name(screen(minecraft)) + ")", play != null);
      return play;
   }

   static void scenario(Minecraft minecraft, OrePlayScreen play, DevOrePlayTest.Reporter reporter, Path serversDat, String prefix, boolean reference) {
      OrePlayUi ui = play.ui();
      tab(minecraft, play, 0, reporter);
      OrePlayLayout.Frame worlds = onClient(minecraft, ui::frame);
      int javaWorlds = onClient(minecraft, () -> ui.model().worlds().size());
      int lan = onClient(minecraft, () -> ui.model().lan().size());
      int onPage = Math.min(javaWorlds + lan, 12);
      reporter.check(
         "Mondes shows Java's " + javaWorlds + " worlds and " + lan + " LAN games as cards, " + onPage + " on the first page",
         worlds != null && worlds.worldCards().size() + worlds.worldRows().size() == onPage && worlds.headerButtons().size() == 2
      );
      int pages = Math.max(1, (javaWorlds + lan + 12 - 1) / 12);
      reporter.check(
         "the page control shows only with more than 12 worlds (" + pages + " pages)",
         worlds != null && pages > 1 == (worlds.pagination() != null) && worlds.worldPages() == pages
      );
      if (pages > 1 && worlds != null && worlds.pagination() != null) {
         toWorldsEnd(minecraft, ui);
         press(minecraft, play, () -> ui.frame().widgets(), "page-next", reporter);
         sleep(500L);
         OrePlayLayout.Frame second = onClient(minecraft, ui::frame);
         int first = second == null
            ? -1
            : (
               !second.worldCards().isEmpty()
                  ? second.worldCards().getFirst().index()
                  : (!second.worldRows().isEmpty() ? second.worldRows().getFirst().index() : -1)
            );
         reporter.check("next page: worlds 13 on, page 2 (first " + first + ")", second != null && second.worldPage() == 2 && first == 12);
         reporter.screenshot(prefix + "-worlds-page2");
         toWorldsEnd(minecraft, ui);
         press(minecraft, play, () -> ui.frame().widgets(), "page-prev", reporter);
         sleep(500L);
         OrePlayLayout.Frame back = onClient(minecraft, ui::frame);
         reporter.check("previous page: back on page 1", back != null && back.worldPage() == 1);
         worlds = back;
      }

      reporter.check(
         "Mondes's tab reads the count (" + (worlds == null ? "" : worlds.tabs().getFirst().text()) + ")",
         worlds != null && worlds.tabs().getFirst().text().contains(String.valueOf(javaWorlds + lan))
      );
      if (reference && worlds != null) {
         rects(worlds, reporter);
      }

      reporter.screenshot(prefix + "-worlds");
      compare(minecraft, play, prefix + "-worlds", reporter);
      tab(minecraft, play, 1, reporter);
      OrePlayLayout.Frame realms = onClient(minecraft, ui::frame);
      reporter.check("Realms shows the not-available card", realms != null && realms.realmsNotice() != null);
      reporter.screenshot(prefix + "-realms");
      tab(minecraft, play, 2, reporter);
      OrePlayLayout.Frame servers = onClient(minecraft, ui::frame);
      boolean partners = onClient(minecraft, () -> !ui.model().featured().isEmpty());
      reporter.check(
         "the featured section shows only with partner servers (" + (partners ? "configured" : "none") + ")",
         servers != null
            && (partners || servers.sections().stream().noneMatch(section -> section.items().stream().anyMatch(item -> item.id().startsWith("featured:"))))
      );
      reporter.check(
         "Autre serveur lists servers.dat (" + play.serverCount() + ")",
         servers != null && (play.serverCount() == 0 || servers.sections().getLast().items().size() == play.serverCount())
      );
      if (reference && servers != null) {
         serverRects(servers, reporter);
      }

      reporter.screenshot(prefix + "-servers");
      compare(minecraft, play, prefix + "-servers", reporter);
      int before = onClient(minecraft, play::serverCount);
      CompoundTag nbtBefore = nbt(serversDat);
      OreServerFormScreen empty = openAdd(minecraft, play, reporter);
      if (empty != null) {
         reporter.screenshot(prefix + "-add-empty");
         reporter.check("the port starts at 19132", "19132".equals(onClient(minecraft, () -> empty.ui().field(2).text())));
         press(minecraft, empty, () -> form(empty), "primary", reporter);
         sleep(500L);
         reporter.check("an empty name opens Bedrock's error (" + modalTitle(minecraft, empty) + ")", onClient(minecraft, () -> empty.ui().modalOpen()));
         reporter.screenshot(prefix + "-add-error");
         pressModal(minecraft, empty, 0, reporter);
         type(minecraft, empty, 0, "BN test", reporter);
         type(minecraft, empty, 1, "127.0.0.3", reporter);
         reporter.screenshot(prefix + "-add-typed");
         press(minecraft, empty, () -> form(empty), "secondary", reporter);
         waitFor(minecraft, "the play screen again", () -> screen(minecraft) == play, 5000L, reporter);
         sleep(800L);
         int index = onClient(minecraft, play::serverCount) - 1;
         reporter.check(
            "\"Nouveau serveur\" adds BN test at 127.0.0.1 to the list and selects it",
            onClient(
               minecraft,
               () -> play.serverCount() == before + 1
                  && "BN test".equals(play.server(index).name)
                  && "127.0.0.3".equals(play.server(index).ip)
                  && ("server:" + index).equals(ui.selected())
            )
         );
         reporter.check("servers.dat has it", savedNames(serversDat).contains("BN test"));
         reporter.screenshot(prefix + "-servers-added");
         OreServerFormScreen again = openAdd(minecraft, play, reporter);
         if (again != null) {
            type(minecraft, again, 0, "BN test 2", reporter);
            type(minecraft, again, 1, "127.0.0.3", reporter);
            press(minecraft, again, () -> form(again), "secondary", reporter);
            sleep(500L);
            reporter.check(
               "the same address and port twice opens Bedrock's error (" + modalTitle(minecraft, again) + ")",
               onClient(minecraft, () -> again.ui().modalOpen()) && onClient(minecraft, () -> play.serverCount() == before + 1)
            );
            pressModal(minecraft, again, 0, reporter);
            press(minecraft, again, () -> form(again), "back", reporter);
            sleep(400L);
            reporter.check("back after typing asks first (" + modalTitle(minecraft, again) + ")", onClient(minecraft, () -> again.ui().modalOpen()));
            pressModal(minecraft, again, 0, reporter);
            waitFor(minecraft, "the play screen after leaving the form", () -> screen(minecraft) == play, 5000L, reporter);
         }

         typing(minecraft, play, reporter, prefix);
         press(minecraft, play, () -> ui.frame().widgets(), "server:" + index, reporter);
         sleep(400L);
         press(minecraft, play, () -> ui.frame().widgets(), "toggle-address", reporter);
         sleep(400L);
         reporter.check(
            "\"Afficher\" warns first (" + onClient(minecraft, () -> ui.modal() == null ? "" : ui.modal().title()) + ")", onClient(minecraft, ui::modalOpen)
         );
         reporter.screenshot(prefix + "-address-warning");
         pressModalPlay(minecraft, play, 0, reporter);
         reporter.check("the warning's first button shows the address", onClient(minecraft, ui::addressShown));
         reporter.screenshot(prefix + "-server-details");
         press(minecraft, play, () -> ui.frame().widgets(), "edit-server", reporter);
         waitFor(minecraft, "the edit form", () -> screen(minecraft) instanceof OreServerFormScreen, 5000L, reporter);
         sleep(600L);
         OreServerFormScreen edit = onClient(minecraft, () -> minecraft.gui.screen() instanceof OreServerFormScreen screen ? screen : null);
         reporter.check(
            "\"Modifier le serveur\" opens Bedrock's edit form with BN test",
            edit != null && onClient(minecraft, () -> edit.ui().editMode() && "BN test".equals(edit.ui().field(0).text()))
         );
         if (edit != null) {
            reporter.screenshot(prefix + "-edit");
            OreServerFormScreen deleting = rename(minecraft, play, edit, index, serversDat, reporter, prefix);
            if (deleting != null) {
               press(minecraft, deleting, () -> form(deleting), "secondary", reporter);
               sleep(500L);
               reporter.check(
                  "\"Supprimer le serveur\" asks first (" + modalTitle(minecraft, deleting) + ")", onClient(minecraft, () -> deleting.ui().modalOpen())
               );
               reporter.check("the delete question stops the window's text input", !textInputActive(minecraft));
               reporter.screenshot(prefix + "-delete");
               pressModal(minecraft, deleting, 1, reporter);
               waitFor(minecraft, "the play screen after deleting", () -> screen(minecraft) == play, 5000L, reporter);
               sleep(600L);
               reporter.check(
                  "the confirmation's \"Supprimer le serveur\" takes BN test out",
                  onClient(minecraft, () -> play.serverCount() == before) && !savedNames(serversDat).contains("BN test")
               );
               reporter.check("servers.dat holds what it held before", Objects.equals(nbtBefore, nbt(serversDat)));
               reporter.check("leaving the form leaves the window's text input off", !textInputActive(minecraft));
               reporter.screenshot(prefix + "-servers-removed");
            }
         }
      }
   }

   private static void typing(Minecraft minecraft, OrePlayScreen play, DevOrePlayTest.Reporter reporter, String prefix) {
      int before = onClient(minecraft, play::serverCount);
      OreServerFormScreen form = openAdd(minecraft, play, reporter);
      if (form != null) {
         reporter.check("the form opens with the window's text input off", !textInputActive(minecraft));
         String name = "Mon Serveur é";
         type(minecraft, form, 0, name, reporter);
         reporter.check(
            "the name field takes letters, spaces and an accent (" + fieldText(minecraft, form, 0) + ")", name.equals(fieldText(minecraft, form, 0))
         );
         textInput(minecraft, "x".repeat(80));
         reporter.check(
            "the name field is not capped at 64 characters (" + fieldText(minecraft, form, 0).length() + ")",
            fieldText(minecraft, form, 0).length() == name.length() + 80
         );
         onClient(minecraft, () -> form.ui().key(OreKey.SELECT_ALL, false, true, () -> "", text -> {}));
         onClient(minecraft, () -> form.ui().key(OreKey.PASTE, false, true, () -> "Collé à la main", text -> {}));
         reporter.check(
            "select all then paste replaces the name (" + fieldText(minecraft, form, 0) + ")", "Collé à la main".equals(fieldText(minecraft, form, 0))
         );
         pressKey(form, 43);
         reporter.check(
            "Tab moves to the address field and keeps the text input on (focused field " + onClient(minecraft, () -> form.ui().focusedField()) + ")",
            onClient(minecraft, () -> form.ui().focusedField()) == 1 && textInputActive(minecraft)
         );
         textInput(minecraft, "127.0.0.3");
         reporter.check(
            "the address field takes its digits and dots (" + fieldText(minecraft, form, 1) + ")", "127.0.0.3".equals(fieldText(minecraft, form, 1))
         );
         reporter.screenshot(prefix + "-typing");
         pressKey(form, 41);
         reporter.check(
            "Esc blurs the field and stops the window's text input", onClient(minecraft, () -> form.ui().focusedField()) < 0 && !textInputActive(minecraft)
         );
         press(minecraft, form, () -> form(form), "field:0", reporter);
         sleep(200L);
         reporter.check("a click on the name field starts it again", textInputActive(minecraft));
         press(minecraft, form, () -> form(form), "back", reporter);
         sleep(400L);
         reporter.check(
            "back after typing asks first, and its question stops the text input (" + modalTitle(minecraft, form) + ")",
            onClient(minecraft, () -> form.ui().modalOpen()) && !textInputActive(minecraft)
         );
         pressModal(minecraft, form, 0, reporter);
         waitFor(minecraft, "the play screen after leaving the typing form", () -> screen(minecraft) == play, 5000L, reporter);
         sleep(400L);
         reporter.check(
            "leaving without saving adds nothing (" + onClient(minecraft, play::serverCount) + " servers)", onClient(minecraft, play::serverCount) == before
         );
      }
   }

   private static @Nullable OreServerFormScreen rename(
      Minecraft minecraft, OrePlayScreen play, OreServerFormScreen edit, int index, Path serversDat, DevOrePlayTest.Reporter reporter, String prefix
   ) {
      OrePlayUi ui = play.ui();
      String renamed = "Mon Serveur é";
      press(minecraft, edit, () -> form(edit), "field:0", reporter);
      sleep(200L);
      reporter.check("a click on the edit form's name field starts the window's text input", textInputActive(minecraft));
      onClient(minecraft, () -> edit.ui().key(OreKey.SELECT_ALL, false, true, () -> "", text -> {}));
      textInput(minecraft, renamed);
      reporter.check(
         "typing over the selected name renames it in the form (" + fieldText(minecraft, edit, 0) + ")", renamed.equals(fieldText(minecraft, edit, 0))
      );
      reporter.screenshot(prefix + "-edit-renamed");
      press(minecraft, edit, () -> form(edit), "primary", reporter);
      waitFor(minecraft, "the play screen after saving the rename", () -> screen(minecraft) == play, 5000L, reporter);
      sleep(600L);
      reporter.check(
         "\"Enregistrer les modifications\" writes the new name to the entry and to servers.dat",
         onClient(minecraft, () -> renamed.equals(play.server(index).name))
            && savedNames(serversDat).contains(renamed)
            && !savedNames(serversDat).contains("BN test")
      );
      press(minecraft, play, () -> ui.frame().widgets(), "server:" + index, reporter);
      sleep(400L);
      press(minecraft, play, () -> ui.frame().widgets(), "edit-server", reporter);
      waitFor(minecraft, "the edit form again", () -> screen(minecraft) instanceof OreServerFormScreen, 5000L, reporter);
      sleep(600L);
      OreServerFormScreen again = onClient(minecraft, () -> minecraft.gui.screen() instanceof OreServerFormScreen screen ? screen : null);
      reporter.check("the edit form shows the renamed entry", again != null && renamed.equals(fieldText(minecraft, again, 0)));
      return again;
   }

   private static void pressKey(Screen screen, int key) {
      Minecraft minecraft = Minecraft.getInstance();
      onClient(minecraft, () -> screen.keyPressed(new KeyEvent(key, 0, 0)));
      sleep(300L);
   }

   static boolean join(Minecraft minecraft, OrePlayScreen play, DevOrePlayTest.Reporter reporter, String prefix) {
      tab(minecraft, play, 2, reporter);
      int index = onClient(minecraft, () -> {
         for (int i = 0; i < play.serverCount(); i++) {
            BedrockAddress address = BedrockAddress.parse(play.server(i).ip);
            if (address.port() == 19132 && (address.host().equals("127.0.0.1") || address.host().equalsIgnoreCase("localhost"))) {
               return i;
            }
         }

         return -1;
      });
      if (index < 0) {
         OreServerFormScreen add = openAdd(minecraft, play, reporter);
         if (add == null) {
            return false;
         }

         type(minecraft, add, 0, "BN test", reporter);
         type(minecraft, add, 1, "127.0.0.1", reporter);
         press(minecraft, add, () -> form(add), "secondary", reporter);
         waitFor(minecraft, "the play screen again", () -> screen(minecraft) == play, 5000L, reporter);
         sleep(800L);
         index = onClient(minecraft, play::serverCount) - 1;
      }

      int target = index;
      onClient(minecraft, () -> {
         ServerData server = play.server(target);
         if (server.getResourcePackStatus() == ServerPackStatus.PROMPT) {
            server.setResourcePackStatus(ServerPackStatus.ENABLED);
         }

         return null;
      });
      press(minecraft, play, () -> play.ui().frame().widgets(), "server:" + target, reporter);
      sleep(500L);
      reporter.check(
         "the entry is selected and its details show",
         onClient(minecraft, () -> ("server:" + target).equals(play.ui().selected()) && play.ui().frame().saved() != null)
      );
      reporter.screenshot(prefix + "-server-selected");
      press(minecraft, play, () -> play.ui().frame().widgets(), "play", reporter);
      waitFor(
         minecraft,
         "the world after joining 127.0.0.1",
         () -> onClient(minecraft, () -> minecraft.level != null && minecraft.player != null),
         120000L,
         reporter
      );
      boolean joined = onClient(minecraft, () -> minecraft.level != null && minecraft.player != null);
      reporter.check("JOUER joins 127.0.0.1 from the Serveurs tab (" + name(screen(minecraft)) + ")", joined);
      return joined;
   }

   private static void rects(OrePlayLayout.Frame frame, DevOrePlayTest.Reporter reporter) {
      rect(reporter, "navigation bar", frame.header().bar(), 0, 0, 1919, 43);
      rect(reporter, "its shadow strip", frame.header().strip(), 0, 44, 1919, 47);
      rect(reporter, "unselected tab Realms", frame.tabs().get(1).box(), 749, 56, 1170, 103);
      rect(reporter, "selected tab Mondes (top and bottom)", frame.tabs().getFirst().box(), 328, 60, 750, 103);
      rect(reporter, "the layout toggle", frame.layoutToggle(), 328, 112, 375, 159);
      rect(reporter, "Créer un nouveau monde", frame.headerButtons().get(0).box(), 1064, 112, 1319, 159);
      rect(reporter, "Importer le monde", frame.headerButtons().get(1).box(), 1336, 112, 1591, 159);
      if (!frame.worldCards().isEmpty()) {
         OrePlayLayout.WorldCard card = frame.worldCards().getFirst();
         rect(reporter, "the first world card", card.card(), 328, 168, 631, 406);
      }
   }

   private static void serverRects(OrePlayLayout.Frame frame, DevOrePlayTest.Reporter reporter) {
      boolean scrolls = frame.menuScroll() != null && frame.menuScroll().scrollable();
      rect(
         reporter,
         "+ Nouveau serveur" + (scrolls ? "" : " (the list fits: no scrollbar)"),
         frame.addServer() == null ? null : frame.addServer().box(),
         346,
         130,
         scrolls ? 698 : 714,
         177
      );
      rect(reporter, "selected tab Serveurs", frame.tabs().get(2).box(), 1169, 60, 1591, 103);
   }

   private static void rect(DevOrePlayTest.Reporter reporter, String what, @Nullable OreBox box, int x0, int y0, int x1, int y1) {
      boolean ok = box != null
         && Math.abs(box.px0() - x0) <= 1
         && Math.abs(box.py0() - y0) <= 1
         && Math.abs(box.px1() - 1 - x1) <= 1
         && Math.abs(box.py1() - 1 - y1) <= 1;
      reporter.check(what + " at " + (box == null ? "none" : box) + " (spec x " + x0 + ".." + x1 + ", y " + y0 + ".." + y1 + ")", ok);
   }

   private static void compare(Minecraft minecraft, OrePlayScreen play, String picture, DevOrePlayTest.Reporter reporter) {
      sleep(700L);
      Path file = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(picture + ".png");

      BufferedImage shot;
      try {
         shot = Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
      } catch (IOException e) {
         shot = null;
      }

      OreInstall install = OreUi.install();
      if (shot != null && install != null) {
         int width = shot.getWidth();
         int height = shot.getHeight();
         BufferedImage offline = onClient(minecraft, () -> {
            OreImagePainter painter = new OreImagePainter(width, height, install, 0);
            play.ui().render(painter, 0L);
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
         reporter.check(String.format("%s: %.1f%% of %d opaque Ore UI pixels match the offline render", picture, share, compared), share >= 97.0);

         try {
            ImageIO.write(offline, "png", file.resolveSibling(picture + "-offline.png").toFile());
         } catch (IOException e) {
            reporter.log("couldn't save the offline render: {}", e.getMessage());
         }
      } else {
         reporter.check("the screenshot " + picture + " can be read for the comparison", false);
      }
   }

   static void tab(Minecraft minecraft, OrePlayScreen play, int tab, DevOrePlayTest.Reporter reporter) {
      press(minecraft, play, () -> play.ui().frame().widgets(), "tab:" + tab, reporter);
      sleep(700L);
      reporter.check("tab " + tab + " opens on a click", onClient(minecraft, () -> play.ui().tab() == tab));
   }

   static @Nullable OreServerFormScreen openAdd(Minecraft minecraft, OrePlayScreen play, DevOrePlayTest.Reporter reporter) {
      if (play.ui().tab() != 2) {
         tab(minecraft, play, 2, reporter);
      }

      press(minecraft, play, () -> play.ui().frame().widgets(), "add-server", reporter);
      waitFor(minecraft, "the server form", () -> screen(minecraft) instanceof OreServerFormScreen, 5000L, reporter);
      sleep(600L);
      OreServerFormScreen form = onClient(minecraft, () -> minecraft.gui.screen() instanceof OreServerFormScreen screen ? screen : null);
      reporter.check("\"Nouveau serveur\" opens Bedrock's server form (" + name(screen(minecraft)) + ")", form != null && !form.ui().editMode());
      return form;
   }

   private static OreWidgets form(OreServerFormScreen form) {
      OreServerFormUi.Layout layout = form.ui().layout();
      return layout == null ? new OreWidgets() : layout.widgets();
   }

   static void type(Minecraft minecraft, OreServerFormScreen form, int field, String text, DevOrePlayTest.Reporter reporter) {
      press(minecraft, form, () -> form(form), "field:" + field, reporter);
      sleep(200L);
      reporter.check("a click on field " + field + " starts the window's text input (SDL delivers typed text only then)", textInputActive(minecraft));
      textInput(minecraft, text);
   }

   static void textInput(Minecraft minecraft, String text) {
      onClient(minecraft, () -> {
         minecraft.keyboardHandler.textInput(minecraft.getWindow().handle(), text);
         return null;
      });
      sleep(200L);
   }

   static boolean textInputActive(Minecraft minecraft) {
      return onClient(minecraft, () -> SDLKeyboard.SDL_TextInputActive(minecraft.getWindow().handle()));
   }

   private static String fieldText(Minecraft minecraft, OreServerFormScreen form, int field) {
      return onClient(minecraft, () -> form.ui().field(field).text());
   }

   private static String modalTitle(Minecraft minecraft, OreServerFormScreen form) {
      return onClient(minecraft, () -> form.ui().modal() == null ? "no modal" : form.ui().modal().title());
   }

   private static void pressModal(Minecraft minecraft, OreServerFormScreen form, int choice, DevOrePlayTest.Reporter reporter) {
      OreModal.Layout layout = onClient(minecraft, () -> form.ui().modalLayout());
      if (layout != null && choice < layout.buttons().size()) {
         pressBox(minecraft, form, layout.buttons().get(choice).box());
         sleep(500L);
      } else {
         reporter.check("the modal's button " + choice + " exists", false);
      }
   }

   static void pressModalPlay(Minecraft minecraft, OrePlayScreen play, int choice, DevOrePlayTest.Reporter reporter) {
      OreModal.Layout layout = onClient(minecraft, () -> play.ui().modalLayout());
      if (layout != null && choice < layout.buttons().size()) {
         pressBox(minecraft, play, layout.buttons().get(choice).box());
         sleep(500L);
      } else {
         reporter.check("the modal's button " + choice + " exists", false);
      }
   }

   private static void toWorldsEnd(Minecraft minecraft, OrePlayUi ui) {
      onClient(minecraft, () -> {
         OrePlayLayout.Frame frame = ui.frame();
         if (frame != null && frame.worldScroll() != null) {
            OreBox view = frame.worldScroll().viewport();
            ui.scrolled(view.centerX(), view.centerY(), -1000.0);
         }

         return null;
      });
      sleep(300L);
   }

   static void press(Minecraft minecraft, Screen screen, Supplier<OreWidgets> widgets, String id, DevOrePlayTest.Reporter reporter) {
      OreWidget widget = onClient(minecraft, () -> widgets.get().get(id));
      if (widget != null && !widget.hit().empty()) {
         pressBox(minecraft, screen, widget.hit());
      } else {
         reporter.check("the control " + id + " is on screen", false);
      }
   }

   private static void pressBox(Minecraft minecraft, Screen screen, OreBox box) {
      onClient(minecraft, () -> {
         double scale = OreSession.guiScale();
         double x = box.centerX() / scale;
         double y = box.centerY() / scale;
         MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
         screen.mouseMoved(x, y);
         screen.mouseClicked(event, false);
         screen.mouseReleased(event);
         return null;
      });
      sleep(200L);
   }

   static byte @Nullable [] read(Path file) {
      try {
         return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
      } catch (IOException e) {
         return null;
      }
   }

   static void restore(Path file, byte @Nullable [] bytes) {
      try {
         if (bytes == null) {
            Files.deleteIfExists(file);
         } else {
            Files.write(file, bytes);
         }
      } catch (IOException e) {
         BedrockNative.LOGGER.error("Dev oreplay test: FAIL couldn't put {} back", file, e);
      }
   }

   static @Nullable CompoundTag nbt(Path file) {
      try {
         return Files.isRegularFile(file) ? NbtIo.read(file) : null;
      } catch (IOException e) {
         return null;
      }
   }

   static List<String> savedNames(Path serversDat) {
      CompoundTag tag = nbt(serversDat);
      List<String> names = new ArrayList<>();
      if (tag != null) {
         tag.getListOrEmpty("servers").compoundStream().forEach(server -> names.add(server.getStringOr("name", "")));
      }

      return names;
   }

   static String name(@Nullable Screen screen) {
      return screen == null ? "nothing" : screen.getClass().getSimpleName();
   }

   static @Nullable Screen screen(Minecraft minecraft) {
      return onClient(minecraft, () -> minecraft.gui.screen());
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev oreplay test: {} {}", ok ? "PASS" : "FAIL", what);
   }

   static void screenshot(Minecraft minecraft, String name) {
      onClient(minecraft, () -> {
         Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
         return null;
      });
      sleep(500L);
   }

   static void waitFor(Minecraft minecraft, String what, BooleanSupplier done, long millis, DevOrePlayTest.Reporter reporter) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > millis) {
            reporter.check("waited too long for " + what + " (" + name(screen(minecraft)) + ")", false);
            return;
         }

         sleep(100L);
      }
   }

   static void referenceWindow(Minecraft minecraft, int width, int height) {
      Window window = minecraft.getWindow();
      onClient(minecraft, () -> {
         window.setWindowed(width, height);
         SDLVideo.SDL_SetWindowBordered(window.handle(), false);
         SDLVideo.SDL_SetWindowPosition(window.handle(), 0, 0);
         SDLVideo.SDL_SetWindowSize(window.handle(), width, height);
         SDLVideo.SDL_SyncWindow(window.handle());
         return null;
      });
      sleep(2000L);
   }

   static void frameWindow(Window window) {
      SDLVideo.SDL_SetWindowBordered(window.handle(), true);
   }

   static <T> T onClient(Minecraft minecraft, Supplier<T> task) {
      return minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, minecraft::execute).join();
   }

   private void run(Runnable task) {
      onClient(this.minecraft, () -> {
         task.run();
         return null;
      });
   }

   static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   interface Reporter {
      void check(String var1, boolean var2);

      void screenshot(String var1);

      void log(String var1, Object... var2);
   }
}
