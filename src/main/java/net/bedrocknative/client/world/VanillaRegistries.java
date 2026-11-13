package net.bedrocknative.client.world;

import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldLoader.DataLoadOutput;
import net.minecraft.server.WorldLoader.InitConfig;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldDataConfiguration;

public final class VanillaRegistries {
   private static final boolean OWN_THREAD = !"client".equalsIgnoreCase(System.getProperty("bedrocknative.registrySteps", "").trim());
   private static final Executor STEP_THREAD = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
      Thread thread = new Thread(task, "BedrockNative vanilla registries");
      thread.setDaemon(true);
      return thread;
   });
   private static final long SETTLE_MS = 5000L;
   private static CompletableFuture<Frozen> loading;

   private VanillaRegistries() {
   }

   public static synchronized CompletableFuture<Frozen> get(Minecraft minecraft) {
      if (loading == null || loading.isCompletedExceptionally()) {
         long started = System.nanoTime();
         VanillaRegistries.TimedSteps steps = OWN_THREAD
            ? new VanillaRegistries.TimedSteps(STEP_THREAD, "their own thread")
            : new VanillaRegistries.TimedSteps(minecraft, "the client thread");
         PackRepository vanillaOnly = new PackRepository(new ServerPacksSource(minecraft.directoryValidator()));
         InitConfig config = new InitConfig(
            new PackConfig(vanillaOnly, WorldDataConfiguration.DEFAULT, false, true), CommandSelection.INTEGRATED, LevelBasedPermissionSet.GAMEMASTER
         );
         loading = WorldLoader.load(config, context -> new DataLoadOutput<>(null, context.datapackDimensions()), (resources, managers, registries, cookie) -> {
            resources.close();
            return registries.compositeAccess();
         }, Util.backgroundExecutor(), steps);
         loading.whenComplete(
            (access, error) -> BedrockNative.LOGGER
               .info(
                  "Vanilla registries for Bedrock joins {} in {} ms; {}",
                  new Object[]{error == null ? "ready" : "failed (" + error + ")", (System.nanoTime() - started) / 1000000L, steps.summary()}
               )
         );
      }

      return loading;
   }

   public static void settle() {
      CompletableFuture<Frozen> pending;
      synchronized (VanillaRegistries.class) {
         pending = loading;
      }

      if (OWN_THREAD && pending != null && !pending.isDone()) {
         long started = System.nanoTime();

         try {
            pending.get(5000L, TimeUnit.MILLISECONDS);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         } catch (Exception var5) {
         }

         BedrockNative.LOGGER.info("Vanilla registries still loading when a world was opened: waited {} ms", (System.nanoTime() - started) / 1000000L);
      }
   }

   private static final class TimedSteps implements Executor {
      private final Executor target;
      private final String where;
      private final AtomicInteger count = new AtomicInteger();
      private final AtomicLong nanos = new AtomicLong();
      private final Queue<Long> each = new ConcurrentLinkedQueue<>();

      TimedSteps(Executor target, String where) {
         this.target = target;
         this.where = where;
      }

      @Override
      public void execute(Runnable task) {
         this.target.execute(() -> {
            long started = System.nanoTime();

            try {
               task.run();
            } finally {
               long took = System.nanoTime() - started;
               this.count.incrementAndGet();
               this.nanos.addAndGet(took);
               this.each.add(took);
            }
         });
      }

      String summary() {
         StringBuilder steps = new StringBuilder();
         this.each.forEach(took -> steps.append(steps.isEmpty() ? "" : ", ").append(String.format(Locale.ROOT, "%.1f", took.longValue() / 1000000.0)));
         return String.format(
            Locale.ROOT,
            "world creation's main-thread steps on %s: %d ms in %d steps (%s ms in order)",
            this.where,
            this.nanos.get() / 1000000L,
            this.count.get(),
            steps
         );
      }
   }
}
