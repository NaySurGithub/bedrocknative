package net.bedrocknative.client.content;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.content.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class AtlasReload {
   private static final Set<Identifier> ATLASES = Set.of(AtlasIds.BLOCKS, AtlasIds.ITEMS);
   public static final Preparations KEPT = new Preparations(0, 0, 0, null, Map.of(), CompletableFuture.completedFuture(null));
   private static boolean choosing;
   @Nullable
   private static TextureAtlas keptAtlas;
   private static final long FALLBACK_TIMEOUT_SECONDS = 180L;
   private static final ReloadChain CHAIN = new ReloadChain();
   private static volatile boolean running;
   private static boolean blockUploadPending;
   private static volatile int runs;
   @Nullable
   private static volatile AtlasReload.Timings lastTimings;

   private AtlasReload() {
   }

   public static CompletableFuture<Void> run(Minecraft minecraft, String why) {
      Executor client = ReloadTasks.wrap(minecraft);
      return CHAIN.enqueue(client, result -> start(minecraft, why, result, client), (result, error) -> {
         running = false;
         choosing = false;
         BedrockNative.LOGGER.warn("Atlas reload ({}) threw; the next request goes on", why, error);
      });
   }

   public static boolean running() {
      return running;
   }

   public static int runs() {
      return runs;
   }

   public static boolean blockAtlasPending() {
      return blockUploadPending;
   }

   public static void blockAtlasUploaded() {
      blockUploadPending = false;
   }

   public static void phase(String name) {
      ReloadPhases.mark(name);
   }

   @Nullable
   public static AtlasReload.Timings lastTimings() {
      return lastTimings;
   }

   public static boolean keeps(Identifier definition) {
      return choosing && !ATLASES.contains(definition);
   }

   public static boolean restitches(Identifier definition) {
      return choosing && ATLASES.contains(definition);
   }

   public static boolean kept(TextureAtlas atlas, Preparations preparations) {
      if (preparations != KEPT) {
         return false;
      } else {
         keptAtlas = atlas;
         return true;
      }
   }

   public static Map<Identifier, TextureAtlasSprite> regions(Preparations preparations) {
      TextureAtlas atlas = keptAtlas;
      if (preparations == KEPT && atlas != null) {
         keptAtlas = null;
         return ((TextureAtlasAccessor)atlas).bedrocknative$texturesByName();
      } else {
         return preparations.regions();
      }
   }

   private static void start(Minecraft minecraft, String why, CompletableFuture<Void> result, Executor client) {
      if (!minecraft.isRunning()) {
         result.complete(null);
      } else if (minecraft.gui.overlay() instanceof LoadingOverlay) {
         ReloadTasks.later(minecraft, () -> start(minecraft, why, result, client));
      } else {
         long started = System.nanoTime();
         long clientBefore = ReloadTasks.clientNanos();
         running = true;
         choosing = true;
         ReloadPhases.start();

         ReloadInstance reload;
         label40: {
            try {
               List<PreparableReloadListener> listeners = List.of(minecraft.getAtlasManager(), minecraft.getModelManager());
               reload = SimpleReloadInstance.of(
                  minecraft.getResourceManager(),
                  listeners,
                  Util.backgroundExecutor().forName("resourceLoad"),
                  client,
                  CompletableFuture.completedFuture(Unit.INSTANCE)
               );
               break label40;
            } catch (RuntimeException var13) {
               choosing = false;
               fallback(minecraft, why, var13, result, client);
            } finally {
               choosing = false;
            }

            return;
         }

         blockUploadPending = true;
         reload.done().whenCompleteAsync((ignored, error) -> {
            if (error != null) {
               fallback(minecraft, why, error, result, client);
            } else {
               try {
                  finish(minecraft, why, started, clientBefore);
               } finally {
                  result.complete(null);
               }
            }
         }, client);
      }
   }

   private static void finish(Minecraft minecraft, String why, long started, long clientBefore) {
      running = false;
      blockUploadPending = false;
      runs++;
      ComposedAtlasUpload.discardUnused();
      long total = (System.nanoTime() - started) / 1000000L;
      long clientMillis = (ReloadTasks.clientNanos() - clientBefore) / 1000000L;
      lastTimings = new AtlasReload.Timings(why, total, clientMillis, false);
      if (minecraft.level != null) {
         minecraft.levelExtractor.allChanged();
      }

      BedrockNative.LOGGER
         .info(
            "Atlas reload ({}): block and item atlases restitched and the models rebaked in {} ms, {} ms of it on the client thread{}; phases {}",
            new Object[]{why, total, clientMillis, minecraft.level != null ? "; the world's geometry is rebuilt" : "", ReloadPhases.finish()}
         );
   }

   private static void fallback(Minecraft minecraft, String why, Throwable error, CompletableFuture<Void> result, Executor client) {
      running = false;
      blockUploadPending = false;
      ComposedAtlasUpload.discardUnused();
      BedrockNative.LOGGER.warn("Atlas reload ({}) failed; reloading every resource instead", why, error);
      lastTimings = new AtlasReload.Timings(why, -1L, -1L, true);
      if (!minecraft.isRunning()) {
         result.complete(null);
      } else {
         try {
            minecraft.reloadResourcePacks().orTimeout(180L, TimeUnit.SECONDS).whenCompleteAsync((ignored, fullError) -> result.complete(null), client);
         } catch (RuntimeException var6) {
            BedrockNative.LOGGER.warn("Atlas reload ({}): the full reload could not start either", why, var6);
            result.complete(null);
         }
      }
   }

   public record Timings(String why, long totalMillis, long clientMillis, boolean fallback) {
   }
}
