package net.bedrocknative.client.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.AtlasReload;
import net.bedrocknative.client.content.BedrockSpriteSource;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.content.BedrockTextureMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelSettings.DifficultySettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class DevRestoreTest {
   private static final String WORLD = "BN restore test";
   private final Minecraft minecraft;
   private final String join = System.getProperty("bedrocknative.startupJoin", "127.0.0.1");
   private int passed;
   private int failed;

   private DevRestoreTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start() {
      Thread.ofVirtual().name("Dev restore test").start(() -> {
         Minecraft instance;
         while ((instance = Minecraft.getInstance()) == null || instance.getWindow() == null) {
            sleep(200L);
         }

         DevRestoreTest test = new DevRestoreTest(instance);

         try {
            test.run();
         } catch (Throwable var6) {
            BedrockNative.LOGGER.error("Dev restore test: FAIL crashed", var6);
            test.failed++;
         } finally {
            test.log("SUMMARY {} passed, {} failed", test.passed, test.failed);
            test.log("done");
            sleep(500L);
            test.run(instance::stop);
         }
      });
   }

   private void run() {
      this.waitFor(
         "the title screen",
         () -> this.onClient(() -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() != null),
         180000L
      );
      sleep(2000L);
      int joins = SessionTimeline.joins();
      this.run(() -> {
         ServerData server = new ServerData("BN restore test", this.join, Type.OTHER);
         server.setResourcePackStatus(ServerPackStatus.ENABLED);
         ConnectScreen.startConnecting(this.minecraft.gui.screen(), this.minecraft, ServerAddress.parseString(this.join), server, false, null);
      });
      this.waitFor("the Bedrock join", () -> SessionTimeline.joins() > joins, 120000L);
      sleep(3000L);
      this.check(
         "Bedrock session: textures armed and stitched from Bedrock",
         BedrockVanillaTextures.isArmed() && BedrockSpriteSource.last(BedrockTextureMap.Atlas.BLOCKS) != null
      );
      this.screenshot("restore-1-bedrock");
      int leaves = SessionTimeline.leaves();
      int reloads = AtlasReload.runs();
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
      this.waitFor("the leave", () -> SessionTimeline.leaves() > leaves, 60000L);
      sleep(1500L);
      this.check(
         "leave: nothing reloaded, the Bedrock textures wait for a Java world", AtlasReload.runs() == reloads && BedrockVanillaTextures.restorePending()
      );
      this.log(
         "after the leave: glyph pages active {}, Bedrock glyphs baked in Java's font sets until a Java world {}",
         BedrockGlyphs.active(),
         BedrockGlyphs.bakedPagesLeft()
      );
      long started = System.nanoTime();
      Path folder = this.minecraft.getLevelSource().getBaseDir();
      String[] levelId = new String[1];
      this.run(
         () -> {
            try {
               levelId[0] = FileUtil.findAvailableName(folder, "BN restore test", "");
            } catch (IOException var4x) {
               throw new IllegalStateException(var4x);
            }

            LevelSettings settings = new LevelSettings(levelId[0], GameType.CREATIVE, DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
            this.minecraft
               .createWorldOpenFlows()
               .createFreshLevel(
                  levelId[0], settings, new WorldOptions(20261014L, false, false), WorldPresets::createNormalWorldDimensions, this.minecraft.gui.screen()
               );
         }
      );
      this.waitFor(
         "the singleplayer world",
         () -> this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null && this.minecraft.hasSingleplayerServer()),
         180000L
      );
      this.waitFor(
         "Java's textures back", () -> !BedrockVanillaTextures.isArmed() && !BedrockVanillaTextures.busy() && !BedrockVanillaTextures.restorePending(), 60000L
      );
      long millis = (System.nanoTime() - started) / 1000000L;
      AtlasReload.Timings restore = AtlasReload.lastTimings();
      this.log(
         "singleplayer world up and Java's textures back {} ms after the world was asked for; restore reload {}",
         millis,
         restore == null
            ? "none"
            : restore.why()
               + ": "
               + restore.totalMillis()
               + " ms, "
               + restore.clientMillis()
               + " ms on the client thread"
               + (restore.fallback() ? " (FULL RELOAD FALLBACK)" : "")
      );
      this.check(
         "singleplayer: Java's textures back by one targeted restitch (no full reload, no overlay)",
         !BedrockVanillaTextures.isArmed()
            && BedrockSpriteSource.last(BedrockTextureMap.Atlas.BLOCKS) == null
            && AtlasReload.runs() == reloads + 1
            && restore != null
            && !restore.fallback()
            && this.onClient(() -> this.minecraft.gui.overlay() == null)
      );
      this.run(() -> {
         TextureAtlasSprite stone = this.minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Identifier.withDefaultNamespace("block/stone"));
         this.log("singleplayer: block/stone sprite {}x{} (Java's)", stone.contents().width(), stone.contents().height());
      });
      this.check("singleplayer: Java's font sets no longer hold the last server's glyphs", !BedrockGlyphs.active() && !BedrockGlyphs.bakedPagesLeft());
      sleep(4000L);
      this.screenshot("restore-2-singleplayer");
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
      this.waitFor("back to the menus", () -> this.onClient(() -> this.minecraft.level == null && !this.minecraft.hasSingleplayerServer()), 60000L);
      sleep(2000L);
      this.deleteWorld(folder.resolve(levelId[0]));
   }

   private void deleteWorld(Path world) {
      if (world.getFileName().toString().startsWith("BN restore test")) {
         try (Stream<Path> files = Files.walk(world)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
               try {
                  Files.deleteIfExists(path);
               } catch (IOException var3) {
                  this.log("couldn't delete {}: {}", path, var3.toString());
               }
            });
            this.log("test world deleted");
         } catch (IOException var7) {
            this.log("couldn't delete the test world: {}", var7.toString());
         }
      }
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(800L);
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      this.log("{} {}", ok ? "PASS" : "FAIL", what);
   }

   private void waitFor(String what, BooleanSupplier done, long millis) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > millis) {
            this.log("FAIL waited {} s for {}", millis / 1000L, what);
            this.failed++;
            return;
         }

         sleep(100L);
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

   private void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev restore test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
