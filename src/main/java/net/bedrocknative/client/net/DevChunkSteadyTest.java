package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.perf.StallSampler;
import net.bedrocknative.client.world.ChunkStream;
import net.bedrocknative.client.world.LightReference;
import net.bedrocknative.client.world.light.ThreadedLight;
import net.bedrocknative.client.world.light.ThreadedLightEngine;
import net.bedrocknative.perf.FrameStats;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.perf.MeshStats;
import net.bedrocknative.world.chunk.stream.ColumnQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class DevChunkSteadyTest {
   private static final double WORST_LIMIT_MS = 50.0;
   private static final double P99_LIMIT_MS = 20.0;
   private static final double FIGHT_P99_LIMIT_MS = 25.0;
   private static final double OTHERS_CPU_LIMIT = 0.25;
   private static final int RENDER_DISTANCE = 12;
   private static final int FAR = 3000;
   private static final int CRUISE_SECONDS = Integer.getInteger("bedrocknative.chunkSteadySeconds", 25);
   private static final boolean FRESH = Boolean.getBoolean("bedrocknative.chunkSteadyFresh");
   private static final double CRUISE_MIN_BLOCKS = 150.0;
   private static final int FIGHT_SECONDS = 20;
   private static final int STAND_SECONDS = 10;
   private static final long SETTLE_TIMEOUT_MS = 60000L;
   private static final long QUIET_MS = 2000L;
   private static final String[] MOBS = new String[]{"zombie", "skeleton", "spider", "husk"};
   private final Minecraft minecraft;
   private final MovementStats movement;
   private final List<String> failures = new ArrayList<>();
   private final Map<String, String> table = new LinkedHashMap<>();
   private int checks;
   private final List<Long> gcPauses = new ArrayList<>();
   private volatile boolean watchingGc;
   private volatile Consumer<Minecraft> perTick;
   private boolean tickHooked;
   private volatile Vec3 home;

   DevChunkSteadyTest(Minecraft minecraft, MovementStats movement) {
      this.minecraft = minecraft;
      this.movement = movement;
   }

   void run() {
      int initialDistance = this.onClient(() -> this.minecraft.options.renderDistance().get());
      GameType initialMode = this.onClient(() -> this.minecraft.gameMode.getPlayerMode());
      List<Runnable> unhook = this.watchGc();

      try {
         this.test();
      } catch (RuntimeException e) {
         this.failures.add("exception: " + e);
         BedrockNative.LOGGER.error("Dev chunk-steady test failed with an exception", e);
      } finally {
         this.perTick = null;
         this.minecraft.execute(() -> {
            this.minecraft.options.renderDistance().set(initialDistance);
            this.minecraft.options.keyUp.setDown(false);
            this.minecraft.options.keyLeft.setDown(false);
            this.minecraft.options.keyRight.setDown(false);
         });
         this.command("kill @e[tag=bn_steady]");
         Vec3 start = this.home;
         if (start != null) {
            this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", start.x, start.y, start.z));
         }

         this.command("gamemode " + initialMode.getName());
         unhook.forEach(Runnable::run);
      }

      ChunkStream stream = ChunkStream.active();
      ChunkStream.Stats stats = stream == null ? null : this.onClient(stream::stats);
      BedrockNative.LOGGER
         .info(
            "Dev chunk-steady test: chunk streaming {}, budget {} ms a frame in game ({}, {}, now {} ms with {} ms of other work a frame, frames {} ms apart), {} decode workers, light {}; estimates per column: insert {} us, light {} us, Java's render work {} us",
            new Object[]{
               ChunkStream.ENABLED ? "on" : "off",
               stats == null ? "?" : stats.budgetMillis(),
               ChunkStream.ADAPTIVE_BUDGET ? "adaptive" : "fixed",
               ChunkStream.PACED ? "paced" : "not paced",
               stats == null ? "?" : String.format(Locale.ROOT, "%.1f", stats.budgetNowMillis()),
               stats == null ? "?" : String.format(Locale.ROOT, "%.1f", stats.otherWorkMillis()),
               stats == null ? "?" : String.format(Locale.ROOT, "%.1f", stats.framePeriodMillis()),
               stats == null ? "?" : stats.workers(),
               ThreadedLight.current() != null ? "on its own thread" : "on the client thread",
               stats == null ? "?" : Math.round(stats.applyMicros()),
               stats == null ? "?" : Math.round(stats.lightMicros()),
               stats == null ? "?" : Math.round(stats.renderMicros())
            }
         );
      this.table.forEach((name, line) -> BedrockNative.LOGGER.info("Dev chunk-steady test summary {}: {}", name, line));
      BedrockNative.LOGGER
         .info(
            "Dev chunk-steady test: {} of {} checks passed{}",
            new Object[]{this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures}
         );
      BedrockNative.LOGGER.info("Dev chunk-steady test: done");
   }

   private void test() {
      this.waitFor(
         () -> this.minecraft.player != null
            && this.minecraft.getConnection() != null
            && this.minecraft.getConnection().hasClientLoaded()
            && this.minecraft.gui.screen() == null,
         60000L
      );
      this.hookTicks();
      Vec3 home = this.onClient(() -> this.minecraft.player.position());
      this.home = home;
      this.command("gamemode creative");
      this.command("time set noon");
      this.command("weather clear");
      this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(12));
      this.settle();
      this.fly(true);
      int baseX = (int)Math.floor(home.x) + 3000 + (FRESH ? (int)(System.currentTimeMillis() / 1000L % 983L) * 512 : 0);
      int baseZ = (int)Math.floor(home.z) + 3000;
      this.command(String.format(Locale.ROOT, "tp @s %d 110 %d", baseX, baseZ));
      this.settle();
      this.fly(true);
      this.scenario("cruise-out", CRUISE_SECONDS, () -> this.cruise(-90.0F));
      this.screenshot("chunk-steady-cruise");
      this.scenario("cruise-back", CRUISE_SECONDS, () -> this.cruise(90.0F));
      this.onClient(() -> {
         this.minecraft.options.keyUp.setDown(false);
         return null;
      });
      this.command(String.format(Locale.ROOT, "tp @s %d 110 %d", baseX, baseZ));
      sleep(1000L);
      this.land();
      this.settle();
      this.summonMobs();
      this.scenario("fight", 20, this::fight);
      this.screenshot("chunk-steady-fight");
      this.command("kill @e[tag=bn_steady]");
      this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(12));
      this.settle();
      this.scenario("stand", 10, () -> {});
      this.lightCheck();
      this.finalChecks();
   }

   private void cruise(float yaw) {
      this.onClient(() -> {
         LocalPlayer player = this.minecraft.player;
         player.setYRot(yaw);
         player.setXRot(0.0F);
         this.minecraft.options.keyUp.setDown(true);
         this.minecraft.options.keySprint.setDown(true);
         return null;
      });
   }

   private void fight() {
      int[] tick = new int[]{0};
      this.perTick = client -> {
         LocalPlayer player = client.player;
         if (player != null) {
            int t = (int)(tick[0]++);
            boolean left = t / 20 % 2 == 0;
            client.options.keyLeft.setDown(left);
            client.options.keyRight.setDown(!left);
            player.setYRot(player.getYRot() + 7.0F);
            if (t % 8 == 0) {
               KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()));
            }

            if (t == 100) {
               client.options.renderDistance().set(16);
            } else if (t == 240) {
               client.options.renderDistance().set(8);
            }
         }
      };
   }

   private void scenario(String name, int seconds, Runnable action) {
      int corrections = this.movement.count();
      int gcBefore;
      synchronized (this.gcPauses) {
         gcBefore = this.gcPauses.size();
      }

      DevLoadWatch.reset();
      MeshStats.resetMax();
      MeshStats.Snapshot meshesBefore = MeshStats.snapshot();
      DevCpuSampler cpu = new DevCpuSampler();
      Vec3 from = this.onClient(() -> {
         this.minecraft.gui.hud.getChat().clearMessages(false);
         FrameTimes.watch().startRecording(name);
         StallSampler.start();
         return this.minecraft.player.position();
      });
      BedrockNative.LOGGER.info("Dev chunk-steady test: scenario {}", name);
      ChunkStream.arrivals().reset();
      action.run();
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < seconds * 1000L) {
         sleep(500L);
         cpu.sample();
      }

      cpu.stop();
      this.perTick = null;
      String[] stalls = new String[1];
      Vec3[] to = new Vec3[1];
      FrameWatch.Recording recording = this.onClient(() -> {
         this.minecraft.options.keyUp.setDown(false);
         this.minecraft.options.keyLeft.setDown(false);
         this.minecraft.options.keyRight.setDown(false);
         this.minecraft.options.keySprint.setDown(false);
         stalls[0] = StallSampler.stop();
         to[0] = this.minecraft.player.position();
         return FrameTimes.watch().stopRecording();
      });
      MeshStats.Snapshot meshes = MeshStats.snapshot().since(meshesBefore);
      List<Long> pauses;
      synchronized (this.gcPauses) {
         pauses = new ArrayList<>(this.gcPauses.subList(gcBefore, this.gcPauses.size()));
      }

      double moved = Math.sqrt(Math.pow(to[0].x - from.x, 2.0) + Math.pow(to[0].z - from.z, 2.0));
      this.report(name, recording, meshes, pauses, cpu, this.movement.count() - corrections, moved);
      if (!stalls[0].isEmpty()) {
         BedrockNative.LOGGER.info("Dev chunk-steady test: {} stall samples: {}", name, stalls[0]);
      }
   }

   private void report(
      String name, FrameWatch.Recording recording, MeshStats.Snapshot meshes, List<Long> pauses, DevCpuSampler cpu, int corrections, double moved
   ) {
      FrameStats inGame = recording.inGame();
      String limiter = this.onClient(
         () -> this.minecraft.getFramerateLimitTracker().getFramerateLimit() + " fps (" + this.minecraft.getFramerateLimitTracker().getThrottleReason() + ")"
      );
      int limit = Integer.parseInt(limiter.substring(0, limiter.indexOf(32)));
      double p99Limit = name.equals("fight") ? 25.0 : 20.0;
      if (limit > 0 && limit < 45) {
         p99Limit = 1000.0 / limit + 8.0;
      }

      boolean frames = inGame.count() == 0 || inGame.maxMillis() < Math.max(50.0, p99Limit) && inGame.percentileMillis(99.0) < p99Limit;
      boolean player = recording.playerColumnWaits() == 0;
      double unexplained = 0.0;
      double windowEvents = 0.0;

      for (FrameWatch.Hitch hitch : recording.worst()) {
         if (!hitch.loading() && hitch.frameMillis() > 80.0 && hitch.causes().matches(".*other (\\d+) ms.*")) {
            double other = Double.parseDouble(hitch.causes().replaceAll(".*other (\\d+) ms.*", "$1"));
            if (other > hitch.frameMillis() / 2.0) {
               unexplained = Math.max(unexplained, other);
            }
         }

         if (!hitch.loading() && hitch.causes().matches(".*window events (\\d+) ms.*")) {
            double waited = Double.parseDouble(hitch.causes().replaceAll(".*window events (\\d+) ms.*", "$1"));
            if (waited > hitch.frameMillis() / 2.0) {
               windowEvents = Math.max(windowEvents, waited);
            }
         }
      }

      boolean loaded = cpu.meanOthers() > 0.25;
      boolean stuck = name.startsWith("cruise") && moved < 150.0;
      String validity = stuck
         ? "INVALID-MOVE (the player covered " + Math.round(moved) + " blocks)"
         : (
            loaded
               ? "INVALID-LOAD (other processes used " + Math.round(cpu.meanOthers() * 100.0) + "% of the CPU on average)"
               : (
                  windowEvents > 0.0
                     ? "SUSPECT-OS (a frame of which " + Math.round(windowEvents) + " ms waited in the window system's events)"
                     : (unexplained > 0.0 ? "SUSPECT-LOAD (a frame of which " + Math.round(unexplained) + " ms is unexplained time)" : "valid")
               )
         );
      String line = String.format(
         Locale.ROOT,
         "in game frames %s; over 10/16/33/50 ms: %d/%d/%d/%d of %d; tick gaps p99 %.1f, max %.1f ms; longest client tick %.1f ms; %d columns applied, %d dropped, peak %d waiting; apply %.0f ms (%s), light %.0f ms, light updates %.0f ms; client thread parts: %s; %s; GC pauses %d (%.0f ms in all, max %d ms); loading screen frames %d; view rebuild frames %d (max %.1f ms); player column waits %d; corrections %d; moved %.0f blocks; CPU other processes %.0f%%, BDS %.0f%%, ours %.0f%% (machine less ours %.0f%% mean, %.0f%% max); frame limit %s; %s",
         inGame.summary(),
         inGame.over(10000000L),
         inGame.over(16700000L),
         inGame.over(33300000L),
         inGame.over(50000000L),
         inGame.count(),
         recording.tickGaps().percentileMillis(99.0),
         recording.tickGaps().maxMillis(),
         recording.ticks().maxMillis(),
         recording.columns(),
         recording.dropped(),
         recording.maxWaiting(),
         recording.applyMillis(),
         recording.applyParts().isEmpty() ? "no parts" : recording.applyParts(),
         recording.lightMillis(),
         recording.bucketMillis(FrameWatch.Bucket.LIGHT_UPDATES),
         recording.bucketsLargestFirst(10),
         meshes.describe(),
         pauses.size(),
         recording.gcMillis(),
         pauses.stream().mapToLong(Long::longValue).max().orElse(0L),
         recording.loading().count(),
         recording.viewRebuilds().count(),
         recording.viewRebuilds().maxMillis(),
         recording.playerColumnWaits(),
         corrections,
         moved,
         cpu.meanOthers() * 100.0,
         cpu.meanServer() * 100.0,
         cpu.meanOurs() * 100.0,
         cpu.meanSystem() * 100.0,
         cpu.maxOthers() * 100.0,
         limiter,
         validity
      );
      this.check(name + ": in game worst frame under 50 ms and p99 under " + (int)p99Limit + " ms", frames, line);
      this.check(name + ": the player never ticked while its own column waited decoded", player, recording.playerColumnWaits() + " ticks");
      this.table
         .put(
            name,
            String.format(
               Locale.ROOT,
               "%s%s, worst %.1f ms, p99 %.1f ms, over 16/33/50 ms %d/%d/%d of %d, columns %d, apply %.0f + light %.0f ms, meshes %d (%.0f ms)",
               frames && player ? "PASS" : "FAIL",
               stuck ? " INVALID-MOVE" : (loaded ? " INVALID-LOAD" : (windowEvents > 0.0 ? " SUSPECT-OS" : (unexplained > 0.0 ? " SUSPECT-LOAD" : ""))),
               inGame.maxMillis(),
               inGame.percentileMillis(99.0),
               inGame.over(16700000L),
               inGame.over(33300000L),
               inGame.over(50000000L),
               inGame.count(),
               recording.columns(),
               recording.applyMillis(),
               recording.lightMillis(),
               meshes.count(),
               meshes.millis()
            )
         );
      BedrockNative.LOGGER.info("Dev chunk-steady test: {} slow frames: {}", name, recording.slowFrames(8));
      int shown = 0;

      for (FrameWatch.Hitch hitch : recording.worst()) {
         if (shown++ == 4) {
            break;
         }

         BedrockNative.LOGGER
            .info(
               String.format(
                  Locale.ROOT,
                  "Dev chunk-steady test: %s hitch %.0f ms%s: %s",
                  name,
                  hitch.frameMillis(),
                  hitch.loading() ? " (loading screen)" : (hitch.viewRebuild() ? " (Java's view rebuild)" : ""),
                  hitch.causes()
               )
            );
      }
   }

   private void lightCheck() {
      this.settle();
      ThreadedLightEngine threaded = this.onClient(() -> ThreadedLight.of(this.minecraft.level));
      if (threaded != null) {
         this.waitFor(threaded::idle, 20000L);
         this.onClient(() -> {
            threaded.drain(column -> {});
            return null;
         });
         BedrockNative.LOGGER.info("Dev chunk-steady test: {}", threaded.stats().describe());
      }

      String result = this.onClient(() -> {
         if (this.minecraft.level != null && this.minecraft.player != null) {
            if (threaded == null) {
               this.minecraft.level.getChunkSource().getLightEngine().runLightUpdates();
            }

            return LightReference.compare(this.minecraft.level, this.minecraft.player.getBlockX() >> 4, this.minecraft.player.getBlockZ() >> 4, 6).describe();
         } else {
            return "no level";
         }
      });
      BedrockNative.LOGGER
         .info("Dev chunk-steady test: light check ({}): {}", threaded != null ? "light thread" : "Java's engine on the client thread", result);
   }

   private void summonMobs() {
      for (int i = 0; i < 24; i++) {
         double angle = i * 2.399963;
         int radius = 3 + i % 7;
         this.command(
            String.format(
               Locale.ROOT, "summon %s ~%d ~ ~%d", MOBS[i % MOBS.length], (int)Math.round(Math.cos(angle) * radius), (int)Math.round(Math.sin(angle) * radius)
            )
         );
      }

      sleep(1000L);
      this.command("tag @e[type=!player,r=14] add bn_steady");
   }

   private void settle() {
      long deadline = System.currentTimeMillis() + 60000L;
      long lastChange = System.currentTimeMillis();
      long lastReserved = -1L;
      long lastLoaded = -1L;

      while (System.currentTimeMillis() < deadline) {
         sleep(250L);
         long[] now = this.onClient(
            () -> {
               ChunkStream stream = ChunkStream.active();
               ColumnQueue.Totals totals = stream == null ? null : stream.stats().queue();
               int loaded = this.minecraft.level == null ? 0 : this.minecraft.level.getChunkSource().getLoadedChunksCount();
               int[] coverage = this.minecraft.player != null ? this.coverage() : new int[]{0, 1};
               return new long[]{
                  totals == null ? 0L : totals.reserved(), totals == null ? 0L : totals.waiting() + totals.decoding(), loaded, coverage[0], coverage[1]
               };
            }
         );
         long time = System.currentTimeMillis();
         if (now[1] <= 0L && now[0] == lastReserved && now[2] == lastLoaded) {
            long quiet = time - lastChange;
            if (quiet >= 2000L && now[3] >= now[4] * 0.95 || quiet >= 6000L) {
               return;
            }
         } else {
            lastChange = time;
            lastReserved = now[0];
            lastLoaded = now[2];
         }
      }

      BedrockNative.LOGGER.warn("Dev chunk-steady test: the chunks did not settle in {} s", 60L);
   }

   private int[] coverage() {
      int radius = this.minecraft.options.getEffectiveRenderDistance();
      int centerX = this.minecraft.player.getBlockX() >> 4;
      int centerZ = this.minecraft.player.getBlockZ() >> 4;
      int inner = Math.max(1, radius - 2);
      int expected = 0;
      int present = 0;

      for (int dx = -inner; dx <= inner; dx++) {
         for (int dz = -inner; dz <= inner; dz++) {
            if (dx * dx + dz * dz <= inner * inner) {
               expected++;
               if (this.minecraft.level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false) != null) {
                  present++;
               }
            }
         }
      }

      return new int[]{present, expected};
   }

   private void fly(boolean on) {
      for (int attempt = 0; attempt < 2 && this.onClient(() -> this.minecraft.player.getAbilities().flying) != on; attempt++) {
         for (int press = 0; press < 2; press++) {
            this.minecraft.execute(() -> this.minecraft.options.keyJump.setDown(true));
            sleep(120L);
            this.minecraft.execute(() -> this.minecraft.options.keyJump.setDown(false));
            sleep(120L);
         }

         sleep(800L);
      }
   }

   private void land() {
      this.fly(false);
      this.waitFor(() -> this.minecraft.player.onGround() || this.minecraft.player.isInWater(), 15000L);
      sleep(500L);
   }

   private void finalChecks() {
      ChunkStream stream = ChunkStream.active();
      if (stream != null) {
         ChunkStream.Stats stats = this.onClient(stream::stats);
         this.check(
            "the stream is empty and no column failed",
            stats.queue().waiting() == 0 && stats.queue().decoding() == 0 && stats.queue().failed() == 0L,
            stats.queue().toString()
         );
      }
   }

   private void hookTicks() {
      if (!this.tickHooked) {
         this.tickHooked = true;
         ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
            DevLoadWatch.tick(this.perTick != null);
            Consumer<Minecraft> step = this.perTick;
            if (step != null && client.player != null && client.level != null) {
               step.accept(client);
            }
         });
      }
   }

   private void check(String what, boolean ok, String detail) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev chunk-steady test PASS {} ({})", what, detail);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev chunk-steady test FAIL {} ({})", what, detail);
      }
   }

   private List<Runnable> watchGc() {
      List<Runnable> unhook = new ArrayList<>();
      this.watchingGc = true;

      for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
         if (bean instanceof NotificationEmitter emitter && !bean.getName().contains("Cycles")) {
            NotificationListener listener = (notification, handback) -> {
               if (this.watchingGc && "com.sun.management.gc.notification".equals(notification.getType())) {
                  GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData)notification.getUserData());
                  synchronized (this.gcPauses) {
                     this.gcPauses.add(info.getGcInfo().getDuration());
                  }
               }
            };
            emitter.addNotificationListener(listener, null, null);
            unhook.add(() -> {
               try {
                  emitter.removeNotificationListener(listener);
               } catch (ListenerNotFoundException var3x) {
               }
            });
         }
      }

      unhook.add(() -> this.watchingGc = false);
      return unhook;
   }

   private void command(String command) {
      this.minecraft.execute(() -> {
         if (this.minecraft.getConnection() != null) {
            BedrockNative.LOGGER.info("Dev chunk-steady test command: /{}", command);
            this.minecraft.getConnection().sendCommand(command);
         }
      });
      sleep(400L);
   }

   private void screenshot(String name) {
      this.minecraft
         .execute(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(600L);
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.onClient(condition::getAsBoolean)) {
         sleep(250L);
      }
   }

   private <T> T onClient(Supplier<T> supplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      this.minecraft.execute(() -> {
         try {
            future.complete(supplier.get());
         } catch (RuntimeException e) {
            future.completeExceptionally(e);
         }
      });

      try {
         return future.get(30L, TimeUnit.SECONDS);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
