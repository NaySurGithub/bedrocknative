package net.bedrocknative.client.world.light;

import net.bedrocknative.BedrockNative;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public final class ThreadedLight {
   public static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.lightThread", "on"));
   private static volatile boolean armed;
   @Nullable
   private static volatile ThreadedLightEngine current;

   private ThreadedLight() {
   }

   public static void arm() {
      armed = ENABLED;
   }

   public static synchronized LevelLightEngine create(LightChunkGetter source, boolean hasBlockLight, boolean hasSkyLight) {
      if (!armed) {
         return new LevelLightEngine(source, hasBlockLight, hasSkyLight);
      } else {
         armed = false;
         closeCurrent();
         ThreadedLightEngine engine = new ThreadedLightEngine(source, hasBlockLight, hasSkyLight);
         current = engine;
         BedrockNative.LOGGER.info("Light: the level's light engine runs on its own thread (-PlightThread=off keeps it on the client thread)");
         return engine;
      }
   }

   public static synchronized void closeCurrent() {
      armed = false;
      ThreadedLightEngine engine = current;
      if (engine != null) {
         engine.close();
         current = null;
         ThreadedLightEngine.Stats stats = engine.stats();
         if (stats.columnsLit() > 0L) {
            BedrockNative.LOGGER.info("Light: the level's {}", stats.describe());
         }
      }
   }

   @Nullable
   public static ThreadedLightEngine of(@Nullable ClientLevel level) {
      return level != null && level.getChunkSource().getLightEngine() instanceof ThreadedLightEngine engine ? engine : null;
   }

   @Nullable
   public static ThreadedLightEngine current() {
      return current;
   }
}
