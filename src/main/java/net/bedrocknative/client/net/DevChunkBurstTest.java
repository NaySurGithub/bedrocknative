package net.bedrocknative.client.net;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.perf.StallSampler;
import net.bedrocknative.client.world.ChunkStream;
import net.bedrocknative.client.world.light.ThreadedLight;
import net.bedrocknative.client.world.light.ThreadedLightEngine;
import net.bedrocknative.perf.FrameStats;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.perf.MeshStats;
import net.bedrocknative.world.chunk.stream.ArrivalWatch;
import net.bedrocknative.world.chunk.stream.ColumnQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

final class DevChunkBurstTest {
   private static final double WORST_LIMIT_MS = 60.0;
   private static final double P99_LIMIT_MS = 25.0;
   private static final int FAR = 2000;
   private static final long SETTLE_TIMEOUT_MS = 60000L;
   private static final long QUIET_MS = 2000L;
   private static final int DIGEST_RADIUS = 6;
   private final Minecraft minecraft;
   private final MovementStats movement;
   private final List<String> failures = new ArrayList<>();
   private final Map<String, String> table = new LinkedHashMap<>();
   private int checks;
   private final List<Long> gcPauses = new ArrayList<>();
   private volatile boolean watchingGc;

   DevChunkBurstTest(Minecraft minecraft, MovementStats movement) {
      this.minecraft = minecraft;
      this.movement = movement;
   }

   void run() {
      int initialDistance = this.onClient(() -> (Integer)this.minecraft.options.renderDistance().get());
      GameType initialMode = this.onClient(() -> this.minecraft.gameMode.getPlayerMode());
      List<Runnable> unhook = this.watchGc();

      try {
         this.test();
      } catch (RuntimeException var8) {
         this.failures.add("exception: " + var8);
         BedrockNative.LOGGER.error("Dev chunk-burst test failed with an exception", var8);
      } finally {
         this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(initialDistance));
         this.command("gamemode " + initialMode.getName());
         unhook.forEach(Runnable::run);
      }

      ChunkStream stream = ChunkStream.active();
      BedrockNative.LOGGER
         .info(
            "Dev chunk-burst test: chunk streaming {}, budget {} ms a frame in game, {} decode workers",
            new Object[]{
               ChunkStream.ENABLED ? "on" : "off (-PchunkStream=off: the old path)",
               stream == null ? "?" : stream.stats().budgetMillis(),
               stream == null ? "?" : stream.stats().workers()
            }
         );
      this.table.forEach((name, line) -> BedrockNative.LOGGER.info("Dev chunk-burst test summary {}: {}", name, line));
      BedrockNative.LOGGER
         .info(
            "Dev chunk-burst test: {} of {} checks passed{}",
            new Object[]{this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures}
         );
      BedrockNative.LOGGER.info("Dev chunk-burst test: done");
   }

   private void test() {
      this.waitFor(
         () -> this.minecraft.player != null
            && this.minecraft.getConnection() != null
            && this.minecraft.getConnection().hasClientLoaded()
            && this.minecraft.gui.screen() == null,
         60000L
      );
      Vec3 home = this.onClient(() -> this.minecraft.player.position());
      this.command("gamemode creative");
      this.command("time set noon");
      this.settle(null);
      this.scenario("radius-4", () -> this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(4)), null);
      this.scenario("radius-24", () -> this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(24)), null);
      this.fly(true);
      int farX = (int)Math.floor(home.x) + 2000;
      int farZ = (int)Math.floor(home.z) + 2000 + (int)(System.currentTimeMillis() / 1000L % 997L) * 1024;
      this.scenario("teleport-fresh", () -> this.command(String.format(Locale.ROOT, "tp @s %d 200 %d", farX, farZ)), null);
      this.screenshot("chunk-burst-teleport");
      Vec3 landed = this.land("teleport-fresh");
      this.scenario("teleport-back", () -> this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", home.x, home.y, home.z)), null);
      sleep(2000L);
      this.checkStanding("teleport-back", home.y);
      this.screenshot("chunk-burst-back");
      this.scenario("teleport-generated", () -> this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", landed.x, landed.y, landed.z)), null);
      sleep(2000L);
      this.checkStanding("teleport-generated", landed.y);
      this.scenario("radius-8", () -> this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(8)), null);
      this.scenario(
         "nether",
         () -> this.command("execute in nether run tp @s 0 100 0"),
         () -> this.minecraft.level != null && this.minecraft.level.dimension() == Level.NETHER && this.minecraft.getConnection().hasClientLoaded()
      );
      this.screenshot("chunk-burst-nether");
      this.scenario(
         "overworld",
         () -> this.command(String.format(Locale.ROOT, "execute in overworld run tp @s %.2f %.2f %.2f", home.x, home.y, home.z)),
         () -> this.minecraft.level != null && this.minecraft.level.dimension() == Level.OVERWORLD && this.minecraft.getConnection().hasClientLoaded()
      );
      sleep(1000L);
      this.screenshot("chunk-burst-home");
      this.finalChecks();
   }

   private void scenario(String name, Runnable action, BooleanSupplier arrived) {
      int corrections = this.movement.count();
      int gcBefore;
      synchronized (this.gcPauses) {
         gcBefore = this.gcPauses.size();
      }

      MeshStats.resetMax();
      MeshStats.Snapshot meshesBefore = MeshStats.snapshot();
      ThreadedLightEngine lightEngine = this.onClient(() -> ThreadedLight.of(this.minecraft.level));
      ThreadedLightEngine.Stats lightBefore = lightEngine == null ? null : lightEngine.stats();
      this.onClient(() -> {
         this.minecraft.gui.hud.getChat().clearMessages(false);
         FrameTimes.watch().startRecording(name);
         StallSampler.start();
         return null;
      });
      BedrockNative.LOGGER.info("Dev chunk-burst test: scenario {}", name);
      long started = System.currentTimeMillis();
      ChunkStream.arrivals().reset();
      action.run();
      long settled = this.settle(arrived) - started;
      String[] stalls = new String[1];
      FrameWatch.Recording recording = this.onClient(() -> {
         stalls[0] = StallSampler.stop();
         return FrameTimes.watch().stopRecording();
      });
      if (!stalls[0].isEmpty()) {
         BedrockNative.LOGGER.info("Dev chunk-burst test: {} stall samples: {}", name, stalls[0]);
      }

      MeshStats.Snapshot meshes = MeshStats.snapshot().since(meshesBefore);
      String offThread = meshes.describe();
      if (lightEngine != null && lightBefore != null) {
         ThreadedLightEngine.Stats lightNow = lightEngine.stats();
         offThread = offThread
            + String.format(
               Locale.ROOT,
               "; light thread %.0f ms for %d columns",
               (lightNow.engineNanos() - lightBefore.engineNanos()) / 1000000.0,
               lightNow.columnsLit() - lightBefore.columnsLit()
            );
      }

      BedrockNative.LOGGER
         .info(
            "Dev chunk-burst test: {} off the client thread: {}; client thread parts: {}; apply parts: {}",
            new Object[]{name, offThread, recording.bucketsLargestFirst(8), recording.applyParts().isEmpty() ? "none" : recording.applyParts()}
         );
      ArrivalWatch.Arrivals arrivals = ChunkStream.arrivals().arrivals();
      List<Long> pauses;
      synchronized (this.gcPauses) {
         pauses = new ArrayList<>(this.gcPauses.subList(gcBefore, this.gcPauses.size()));
      }

      int[] view = this.onClient(() -> this.minecraft.player == null ? new int[]{0, 0, 0} : this.coverage());
      BedrockNative.LOGGER
         .info(
            "Dev chunk-burst test: {} server: {}; view {} of {} chunks within {} when settled",
            new Object[]{name, arrivals.describe(), view[0], view[1], Math.max(1, view[2] - 2)}
         );
      this.report(name, recording, settled, this.movement.count() - corrections, pauses);
   }

   private void report(String name, FrameWatch.Recording recording, long settledMillis, int corrections, List<Long> pauses) {
      FrameStats inGame = recording.inGame();
      String limiter = this.onClient(
         () -> this.minecraft.getFramerateLimitTracker().getFramerateLimit() + " fps (" + this.minecraft.getFramerateLimitTracker().getThrottleReason() + ")"
      );
      int limit = Integer.parseInt(limiter.substring(0, limiter.indexOf(32)));
      double p99Limit = limit > 0 && limit < 45 ? 1000.0 / limit + 8.0 : 25.0;
      boolean frames = inGame.count() == 0 || inGame.maxMillis() < Math.max(60.0, p99Limit) && inGame.percentileMillis(99.0) < p99Limit;
      boolean player = recording.playerColumnWaits() == 0;
      String line = String.format(
         Locale.ROOT,
         "in game frames %s; tick gaps p99 %.1f, max %.1f ms; longest client tick %.1f ms; %d columns applied, %d dropped, peak %d waiting, settled after %.1f s; apply %.0f ms, light %.0f ms; loading screen frames %s; view rebuild frames %d (max %.1f ms); player column waits %d; corrections %d; GC pauses %d (max %d ms); frame limit %s",
         inGame.summary(),
         recording.tickGaps().percentileMillis(99.0),
         recording.tickGaps().maxMillis(),
         recording.ticks().maxMillis(),
         recording.columns(),
         recording.dropped(),
         recording.maxWaiting(),
         settledMillis / 1000.0,
         recording.applyMillis(),
         recording.lightMillis(),
         recording.loading().count() == 0
            ? "0"
            : recording.loading().count() + " (max " + String.format(Locale.ROOT, "%.1f", recording.loading().maxMillis()) + " ms)",
         recording.viewRebuilds().count(),
         recording.viewRebuilds().maxMillis(),
         recording.playerColumnWaits(),
         corrections,
         pauses.size(),
         pauses.stream().mapToLong(Long::longValue).max().orElse(0L),
         limiter
      );
      this.check(name + ": in game worst frame under 60 ms and p99 under " + (int)p99Limit + " ms", frames, line);
      this.check(name + ": the player never ticked while its own column waited decoded", player, recording.playerColumnWaits() + " ticks");
      this.table
         .put(
            name,
            String.format(
               Locale.ROOT,
               "%s, worst %.1f ms, p99 %.1f ms, columns %d, settled %.1f s",
               frames && player ? "PASS" : "FAIL",
               inGame.maxMillis(),
               inGame.percentileMillis(99.0),
               recording.columns(),
               settledMillis / 1000.0
            )
         );
      BedrockNative.LOGGER.info("Dev chunk-burst test: {} slow frames: {}", name, recording.slowFrames(8));
      int shown = 0;

      for (FrameWatch.Hitch hitch : recording.worst()) {
         if (shown++ == 4) {
            break;
         }

         BedrockNative.LOGGER
            .info(
               String.format(
                  Locale.ROOT,
                  "Dev chunk-burst test: %s hitch %.0f ms%s: %s",
                  name,
                  hitch.frameMillis(),
                  hitch.loading() ? " (loading screen)" : (hitch.viewRebuild() ? " (Java's view rebuild)" : ""),
                  hitch.causes()
               )
            );
      }
   }

   private long settle(BooleanSupplier arrived) {
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
               boolean there = arrived == null || arrived.getAsBoolean();
               int[] coverage = there && this.minecraft.player != null ? this.coverage() : new int[]{0, 1};
               return new long[]{
                  totals == null ? 0L : totals.reserved(),
                  totals == null ? 0L : totals.waiting() + totals.decoding(),
                  loaded,
                  there ? 1L : 0L,
                  coverage[0],
                  coverage[1]
               };
            }
         );
         long time = System.currentTimeMillis();
         boolean busy = now[1] > 0L || now[3] == 0L;
         if (!busy && now[0] == lastReserved && now[2] == lastLoaded) {
            long quiet = time - lastChange;
            boolean whole = now[4] >= now[5] * 0.95;
            if (quiet >= 2000L && whole || quiet >= 6000L) {
               return lastChange;
            }
         } else {
            lastChange = time;
            lastReserved = now[0];
            lastLoaded = now[2];
         }
      }

      BedrockNative.LOGGER.warn("Dev chunk-burst test: the chunks did not settle in {} s", 60L);
      return lastChange;
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

      return new int[]{present, expected, radius};
   }

   private String missingOffsets() {
      int inner = Math.max(1, this.minecraft.options.getEffectiveRenderDistance() - 2);
      int centerX = this.minecraft.player.getBlockX() >> 4;
      int centerZ = this.minecraft.player.getBlockZ() >> 4;
      StringBuilder missing = new StringBuilder("player chunk " + centerX + "," + centerZ + ":");

      for (int dx = -inner; dx <= inner; dx++) {
         for (int dz = -inner; dz <= inner; dz++) {
            if (dx * dx + dz * dz <= inner * inner && this.minecraft.level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false) == null) {
               missing.append(' ').append(dx).append(',').append(dz);
            }
         }
      }

      return missing.toString();
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

      BedrockNative.LOGGER.info("Dev chunk-burst test: flying {}", this.onClient(() -> this.minecraft.player.getAbilities().flying));
   }

   private Vec3 land(String name) {
      this.fly(false);
      this.waitFor(() -> this.minecraft.player.onGround() || this.minecraft.player.isInWater(), 15000L);
      sleep(500L);
      String state = this.onClient(() -> {
         LocalPlayer player = this.minecraft.player;
         int topx = this.minecraft.level.getHeight(Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
         return String.format(Locale.ROOT, "%.2f %.2f %.2f %s %s %d", player.getX(), player.getY(), player.getZ(), player.onGround(), player.isInWater(), topx);
      });
      String[] parts = state.split(" ");
      double y = Double.parseDouble(parts[1]);
      boolean supported = Boolean.parseBoolean(parts[3]) || Boolean.parseBoolean(parts[4]);
      int top = Integer.parseInt(parts[5]);
      boolean inWater = Boolean.parseBoolean(parts[4]);
      boolean placed = inWater ? y <= top + 2.0 : Math.abs(y - top) <= 2.0;
      this.check(
         name + ": the player landed on the streamed terrain",
         supported && placed && y > -64.0,
         "at " + parts[0] + " " + parts[1] + " " + parts[2] + ", on ground " + parts[3] + ", in water " + parts[4] + ", terrain top " + top
      );
      return new Vec3(Double.parseDouble(parts[0]), y, Double.parseDouble(parts[2]));
   }

   private void checkStanding(String name, double expectedY) {
      String state = this.onClient(
         () -> String.format(Locale.ROOT, "%.2f %s %s", this.minecraft.player.getY(), this.minecraft.player.onGround(), this.minecraft.player.isInWater())
      );
      String[] parts = state.split(" ");
      double y = Double.parseDouble(parts[0]);
      boolean inWater = Boolean.parseBoolean(parts[2]);
      this.check(
         name + ": the player stands where it was",
         Math.abs(y - expectedY) <= 1.0 || inWater && Math.abs(y - expectedY) <= 3.0,
         "y " + parts[0] + " (was " + String.format(Locale.ROOT, "%.2f", expectedY) + "), on ground " + parts[1] + ", in water " + parts[2]
      );
   }

   private void finalChecks() {
      this.settle(null);
      int[] coverage = this.onClient(this::coverage);
      int missing = coverage[1] - coverage[0];
      this.check(
         "every chunk within the radius is loaded",
         missing == 0,
         missing
            + " of "
            + coverage[1]
            + " missing within "
            + Math.max(1, coverage[2] - 2)
            + " chunks (radius "
            + coverage[2]
            + ")"
            + (missing == 0 ? "" : "; missing offsets from the player's chunk " + this.onClient(this::missingOffsets))
      );
      ChunkStream stream = ChunkStream.active();
      if (stream != null) {
         ChunkStream.Stats stats = this.onClient(stream::stats);
         BedrockNative.LOGGER
            .info(
               String.format(
                  Locale.ROOT,
                  "Dev chunk-burst test: stream totals %s; player column still decoding at %d tick starts; estimates per column: insert %.0f us, light %.0f us, Java's render work %.0f us",
                  stats.queue(),
                  stats.playerColumnDecodingAtTick(),
                  stats.applyMicros(),
                  stats.lightMicros(),
                  stats.renderMicros()
               )
            );
         this.check(
            "the stream is empty and no column failed",
            stats.queue().waiting() == 0 && stats.queue().decoding() == 0 && stats.queue().failed() == 0L,
            stats.queue().toString()
         );
      }

      this.digests();
   }

   private void digests() {
      int[] center = this.onClient(() -> new int[]{this.minecraft.player.getBlockX() >> 4, this.minecraft.player.getBlockZ() >> 4});
      Map<String, Long> digests = this.onClient(() -> {
         Map<String, Long> result = new LinkedHashMap<>();

         for (int x = center[0] - 6; x <= center[0] + 6; x++) {
            for (int z = center[1] - 6; z <= center[1] + 6; z++) {
               LevelChunk chunk = this.minecraft.level.getChunkSource().getChunk(x, z, false);
               if (chunk != null) {
                  result.put(x + "," + z, digest(chunk));
               }
            }
         }

         return result;
      });
      String mode = ChunkStream.ENABLED ? "on" : "off";
      String other = ChunkStream.ENABLED ? "off" : "on";
      Path folder = this.minecraft.gameDirectory.toPath().resolve("chunk-burst");

      try {
         Files.createDirectories(folder);
         List<String> lines = new ArrayList<>();
         lines.add("center " + center[0] + " " + center[1]);
         digests.forEach((chunk, value) -> lines.add(chunk + " " + Long.toHexString(value)));
         Files.write(folder.resolve("digest-" + mode + ".txt"), lines, StandardCharsets.UTF_8);
         Path otherFile = folder.resolve("digest-" + other + ".txt");
         if (Files.isRegularFile(otherFile)) {
            try (Stream<String> stream = Files.lines(otherFile, StandardCharsets.UTF_8)) {
               List<String> theirs = stream.toList();
               if (!theirs.getFirst().equals(lines.getFirst())) {
                  BedrockNative.LOGGER.info("Dev chunk-burst test: the {} run ended in another chunk ({}); not compared", other, theirs.getFirst());
               } else {
                  int differing = 0;

                  for (String line : theirs.subList(1, theirs.size())) {
                     int space = line.lastIndexOf(32);
                     Long mine = digests.get(line.substring(0, space));
                     if (mine == null || !Long.toHexString(mine).equals(line.substring(space + 1))) {
                        differing++;
                     }
                  }

                  BedrockNative.LOGGER
                     .info(
                        "Dev chunk-burst test: chunks around the end point against the {} run: {} of {} differ (the BDS world drifts between runs)",
                        new Object[]{other, differing, digests.size()}
                     );
               }
            }
         }
      } catch (IOException var17) {
         BedrockNative.LOGGER.warn("Dev chunk-burst test: cannot write the digests", var17);
      }
   }

   private static long digest(LevelChunk chunk) {
      long hash = 1125899906842597L;

      for (LevelChunkSection section : chunk.getSections()) {
         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  hash = 31L * hash + Block.getId(section.getBlockState(x, y, z));
               }
            }
         }
      }

      return hash;
   }

   private void check(String what, boolean ok, String detail) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev chunk-burst test PASS {} ({})", what, detail);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev chunk-burst test FAIL {} ({})", what, detail);
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
            BedrockNative.LOGGER.info("Dev chunk-burst test command: /{}", command);
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
         } catch (RuntimeException var3) {
            future.completeExceptionally(var3);
         }
      });

      try {
         return future.get(30L, TimeUnit.SECONDS);
      } catch (Exception var4) {
         throw new IllegalStateException(var4);
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
