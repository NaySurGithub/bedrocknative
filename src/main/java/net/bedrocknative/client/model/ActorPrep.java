package net.bedrocknative.client.model;

import java.util.Locale;
import java.util.concurrent.Executor;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.prep.PrepPool;
import net.bedrocknative.model.prep.RegistryWarmer;
import net.bedrocknative.perf.FrameWatch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class ActorPrep {
   public static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.actorPrep", "on"));
   static final long BUDGET_NANOS = 2000000L;
   static final long LOADING_BUDGET_NANOS = 8000000L;
   static final int MAX_ITEMS = 8;
   static final int MAX_QUEUED = 512;
   static final long MAX_PREPARED_BYTES = 67108864L;
   static final long MAX_WARM_PIXELS = 16777216L;
   private static final long STALE_DRAIN_NANOS = 100000000L;
   static final int WORKERS = Math.clamp((long)(Runtime.getRuntime().availableProcessors() / 4), 1, 2);
   @Nullable
   private static final PrepPool POOL = ENABLED
      ? new PrepPool("Bedrock actor prep", WORKERS, 5, 512, 67108864L, (message, error) -> BedrockNative.LOGGER.warn(message, error))
      : null;
   @Nullable
   private static volatile RegistryWarmer warmer;
   private static long lastDrain;
   private static volatile double[] viewPosition = new double[0];

   private ActorPrep() {
   }

   @Nullable
   public static PrepPool pool() {
      return POOL;
   }

   public static int workers() {
      return POOL != null ? POOL.workers() : 0;
   }

   static Executor modelExecutor() {
      return (Executor)(POOL != null ? POOL.executor(PrepPool.Tier.NEAR) : Util.backgroundExecutor());
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (POOL != null && System.nanoTime() - lastDrain > 100000000L) {
            drainActive();
         }
      });
   }

   public static void drainActive() {
      PrepPool pool = POOL;
      if (pool != null) {
         lastDrain = System.nanoTime();
         Entity local = Minecraft.getInstance().player;
         if (local != null) {
            viewPosition = new double[]{local.getX(), local.getY(), local.getZ()};
         }

         if (pool.hasWaiting()) {
            long started = FrameTimes.start();
            Minecraft minecraft = Minecraft.getInstance();
            boolean loading = minecraft.gui != null && minecraft.gui.screen() instanceof LevelLoadingScreen;
            pool.drain(loading ? 8000000L : 2000000L, 8, System::nanoTime);
            FrameTimes.end(FrameWatch.Bucket.ACTOR_PREP, started);
         }
      }
   }

   static void registryLoaded(ModelRegistry registry) {
      PrepPool pool = POOL;
      RegistryWarmer previous = warmer;
      if (previous != null) {
         previous.cancel();
      }

      if (pool != null) {
         RegistryWarmer next = new RegistryWarmer(
            registry,
            pool,
            16777216L,
            summary -> BedrockNative.LOGGER
               .info(
                  String.format(
                     Locale.ROOT,
                     "Actor prep: the player, base game actors and attachables (%d definitions) prepared off the client thread in %.0f ms: %d textures decoded (%.1f M pixels), %d geometries meshed (what draws made first is not counted); %d workers",
                     summary.planned(),
                     summary.nanos() / 1000000.0,
                     summary.images(),
                     summary.pixels() / 1000000.0,
                     summary.meshes(),
                     WORKERS
                  )
               ),
            System::nanoTime
         );
         warmer = next;
         next.start();
      }
   }

   static void spawned(ModelRegistry registry, @Nullable ClientEntityDef definition, Entity entity) {
      RegistryWarmer current = warmer;
      if (current != null && definition != null && current.registry() == registry) {
         Entity local = Minecraft.getInstance().player;
         long distanceSq = local == null ? 0L : (long)Math.min(entity.distanceToSqr(local), 9.223372E18F);
         current.spawn(definition, distanceSq);
      }
   }

   public static void announced(String identifier, double x, double y, double z) {
      RegistryWarmer current = warmer;
      if (current != null) {
         ModelRegistry registry = current.registry();
         ClientEntityDef definition = registry.entity(identifier);
         if (definition != null && registry.serverDefines(identifier)) {
            double[] view = viewPosition;
            long distanceSq = 0L;
            if (view.length == 3) {
               double dx = x - view[0];
               double dy = y - view[1];
               double dz = z - view[2];
               distanceSq = (long)Math.min(dx * dx + dy * dy + dz * dz, 9.223372E18F);
            }

            current.spawn(definition, distanceSq);
         }
      }
   }

   static void sessionEnded() {
      RegistryWarmer current = warmer;
      warmer = null;
      if (current != null) {
         current.cancel();
      }
   }

   @Nullable
   public static RegistryWarmer.Summary warmed() {
      RegistryWarmer current = warmer;
      return current != null ? current.summary() : null;
   }

   @Nullable
   public static PrepPool.Stats stats() {
      PrepPool pool = POOL;
      return pool != null ? pool.stats() : null;
   }
}
