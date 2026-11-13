package net.bedrocknative.client.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.content.PackDownloadScreen;
import net.bedrocknative.client.form.FormScreen;
import net.bedrocknative.client.form.PackPromptScreen;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.text.BedrockText;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;

public final class DevPacksTest {
   private static final UUID PACK = UUID.fromString("bd1ccd95-4246-4ee9-b0d7-f27b2fcae7de");
   private static final String IMAGE = "textures/ui/bedrocknative_test";
   private static final KeyEvent ENTER = new KeyEvent(40, 0, 0);
   private static final KeyEvent ESCAPE = new KeyEvent(41, 0, 0);
   private static final int PROMPT_WAIT = Integer.getInteger("bedrocknative.devPromptWait", 3);
   private final Minecraft minecraft;
   private final Path cache = FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("packs");
   private final ServerData server = new ServerData("Bedrock test server", "127.0.0.1", Type.OTHER);
   private boolean promptSeen;
   private boolean downloadSeen;

   private DevPacksTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start(boolean required) {
      Thread.ofVirtual().name("Dev packs test").start(() -> {
         Minecraft minecraft;
         while ((minecraft = Minecraft.getInstance()) == null) {
            sleep(200L);
         }

         DevPacksTest test = new DevPacksTest(minecraft);

         try {
            test.deleteCache();
            if (required) {
               test.required();
            } else {
               test.optional();
            }

            log("done");
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.error("Dev packs test failed", e);
            log("done (failed: {})", e.getMessage());
         }
      });
   }

   private void quickPlay() {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < 120000L) {
         if (this.onClient(this::screen) instanceof PackPromptScreen prompt) {
            this.press(prompt.buttons().getFirst());
         }

         if (this.onClient(() -> this.minecraft.player != null && this.minecraft.level != null && this.screen() == null)) {
            break;
         }

         sleep(200L);
      }

      sleep(3000L);
      this.check("0 quick play");
      this.leave();
      this.deleteCache();
   }

   private void optional() {
      this.quickPlay();
      this.server.setResourcePackStatus(ServerPackStatus.PROMPT);
      this.join();
      PackPromptScreen prompt = this.awaitPrompt();
      this.screenshot("prompt");
      log("1 first join: prompt buttons {}", this.buttons(prompt));
      sleep(PROMPT_WAIT * 1000L);
      this.press(prompt.buttons().getFirst());
      this.awaitSpawn();
      this.check("1 first join (download)");
      this.form("1-form");
      this.leave();
      Path stale = this.staleDownload();
      this.server.setResourcePackStatus(ServerPackStatus.PROMPT);
      this.join();
      this.awaitSpawn();
      this.check("2 second join (cache)");
      log("2 download left by a crash removed: {}", !Files.exists(stale));
      this.leave();
      this.corruptCache();
      this.server.setResourcePackStatus(ServerPackStatus.PROMPT);
      this.join();
      this.press(this.awaitPrompt().buttons().getFirst());
      this.awaitSpawn();
      this.check("3 corrupted cache (download again)");
      this.leave();
      this.deleteCache();
      this.server.setResourcePackStatus(ServerPackStatus.PROMPT);
      this.join();
      prompt = this.awaitPrompt();
      log("4 decline: pressing '{}'", prompt.buttons().get(1).getMessage().getString());
      this.press(prompt.buttons().get(1));
      this.awaitSpawn();
      this.check("4 declined (join without)");
      this.form("4-form");
      log("4 remembered setting: {}", this.server.getResourcePackStatus());
      this.leave();
      this.join();
      this.awaitSpawn();
      this.check("5 declined remembered (no prompt)");
      this.leave();
      this.server.setResourcePackStatus(ServerPackStatus.ENABLED);
      this.join();
      this.awaitSpawn();
      this.check("6 enabled remembered (download without prompt)");
      this.leave();
      this.server.setResourcePackStatus(ServerPackStatus.DISABLED);
      this.join();
      this.awaitSpawn();
      this.check("7 disabled with the pack cached (left out)");
   }

   private void required() {
      this.quickPlay();
      this.server.setResourcePackStatus(ServerPackStatus.PROMPT);
      this.join();
      PackPromptScreen prompt = this.awaitPrompt();
      this.screenshot("required-prompt");
      log("R1 required prompt buttons {}; keeping it open {} s", this.buttons(prompt), PROMPT_WAIT);
      sleep(PROMPT_WAIT * 1000L);
      AbstractButton leave = prompt.buttons().getLast();
      log("R1 pressing '{}'", leave.getMessage().getString());
      this.press(leave);
      sleep(3000L);
      log("R1 after leaving: screen {}, still connected {}", this.screenName(), this.onClient(() -> this.minecraft.getConnection() != null));
      this.server.setResourcePackStatus(ServerPackStatus.DISABLED);
      this.join();
      prompt = this.awaitPrompt();
      log("R2 required with the server set to Disabled still prompts: {}", this.buttons(prompt));
      sleep(PROMPT_WAIT * 1000L);
      this.press(prompt.buttons().getFirst());
      this.awaitSpawn();
      this.check("R2 required (download)");
      this.form("R2-form");
   }

   private void check(String label) {
      record State(String done, boolean image, boolean serverPacks, String screen) {
      }

      State state = this.onClient(
         () -> new State(
            BedrockText.translate("gui.done", List.of()),
            BedrockTextures.pack("textures/ui/bedrocknative_test").isPresent(),
            PackStack.hasServerPacks(),
            this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName()
         )
      );
      log(
         "{}: gui.done '{}', pack image {}, server packs {}, prompt seen {}, download screen seen {}, cache {}",
         label,
         state.done(),
         state.image(),
         state.serverPacks(),
         this.promptSeen,
         this.downloadSeen,
         this.cacheFiles()
      );
      this.screenshot(label.substring(0, label.indexOf(32)));
   }

   private void form(String name) {
      this.run(() -> this.minecraft.getConnection().sendCommand("scriptevent bedrocknative:form packs"));
      FormScreen form = this.await(FormScreen.class, 8000L);
      sleep(1500L);
      this.screenshot(name);
      log("form title '{}'", this.onClient(() -> form.getTitle().getString()));
      this.run(() -> form.keyPressed(ESCAPE));
      sleep(1000L);
   }

   private PackPromptScreen awaitPrompt() {
      this.promptSeen = true;
      return this.await(PackPromptScreen.class, 90000L);
   }

   private void awaitSpawn() {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < 120000L) {
         Screen screen = this.onClient(this::screen);
         this.promptSeen |= screen instanceof PackPromptScreen;
         this.downloadSeen |= screen instanceof PackDownloadScreen;
         if (BedrockDisconnects.isDisconnectScreen(screen)) {
            throw new IllegalStateException("Disconnected: " + this.onClient(() -> BedrockDisconnects.describe(screen)));
         }

         if (this.onClient(() -> this.minecraft.player != null && this.minecraft.level != null && this.minecraft.gui.screen() == null)) {
            sleep(4000L);
            return;
         }

         sleep(100L);
      }

      throw new IllegalStateException("Not in the world after 120 s, screen " + this.screenName());
   }

   private void join() {
      this.promptSeen = false;
      this.downloadSeen = false;
      this.run(() -> ConnectScreen.startConnecting(new TitleScreen(), this.minecraft, ServerAddress.parseString(this.server.ip), this.server, false, null));
   }

   private void leave() {
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
      sleep(3000L);
   }

   private void deleteCache() {
      try (Stream<Path> files = Files.exists(this.cache) ? Files.list(this.cache) : Stream.empty()) {
         for (Path file : files.filter(filex -> filex.getFileName().toString().startsWith(PACK.toString())).toList()) {
            Files.delete(file);
         }
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private Path staleDownload() {
      try {
         Path part = Files.writeString(this.cache.resolve(PACK + "crashed.part"), "partial");
         Files.setLastModifiedTime(part, FileTime.fromMillis(System.currentTimeMillis() - 3600000L));
         return part;
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private void corruptCache() {
      Path zip = this.cache.resolve(PACK + "_1.0.0.zip");

      try {
         byte[] bytes = Files.readAllBytes(zip);
         bytes[bytes.length / 2] = (byte)(bytes[bytes.length / 2] ^ 85);
         Files.write(zip, bytes);
         log("3 corrupted one byte of {}", zip.getFileName());
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private List<String> cacheFiles() {
      try (Stream<Path> files = Files.exists(this.cache) ? Files.list(this.cache) : Stream.empty()) {
         return files.<String>map(file -> file.getFileName().toString()).filter(name -> name.startsWith(PACK.toString())).sorted().toList();
      } catch (IOException e) {
         return List.of(e.getMessage());
      }
   }

   private List<String> buttons(PackPromptScreen prompt) {
      return this.onClient(() -> prompt.buttons().stream().map(button -> button.getMessage().getString()).toList());
   }

   private <T extends Screen> T await(Class<T> type, long timeout) {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < timeout) {
         Screen screen = this.onClient(this::screen);
         if (type.isInstance(screen)) {
            return type.cast(screen);
         }

         if (BedrockDisconnects.isDisconnectScreen(screen)) {
            throw new IllegalStateException("Disconnected: " + this.onClient(() -> BedrockDisconnects.describe(screen)));
         }

         sleep(100L);
      }

      throw new IllegalStateException("No " + type.getSimpleName() + " after " + timeout + " ms, screen is " + this.screenName());
   }

   private void press(AbstractButton button) {
      this.run(() -> button.keyPressed(ENTER));
   }

   private String screenName() {
      return this.onClient(() -> this.screen() == null ? "none" : this.screen().getClass().getSimpleName());
   }

   private @Nullable Screen screen() {
      return this.minecraft.gui == null ? null : this.minecraft.gui.screen();
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, "packs-" + name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
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
      BedrockNative.LOGGER.info("Dev packs test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
