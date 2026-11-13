package net.bedrocknative.client.world;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.client.net.SessionTimeline;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.perf.StallSampler;
import net.bedrocknative.client.world.light.ThreadedLight;
import net.bedrocknative.client.world.light.ThreadedLightEngine;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.perf.MeshStats;
import net.bedrocknative.world.chunk.stream.ApplyBudget;
import net.bedrocknative.world.chunk.stream.ArrivalWatch;
import net.bedrocknative.world.chunk.stream.ColumnQueue;
import net.bedrocknative.world.chunk.stream.FrameBudget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ChunkStream {
   public static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.chunkStream", "on"));
   static final long BUDGET_NANOS = millis("bedrocknative.chunkBudgetMs", 6);
   static final long LOADING_BUDGET_NANOS = millis("bedrocknative.chunkLoadingBudgetMs", 30);
   public static final boolean ADAPTIVE_BUDGET = !"fixed".equalsIgnoreCase(System.getProperty("bedrocknative.chunkBudget", "adaptive"));
   public static final boolean PRIORITY_AHEAD = !"distance".equalsIgnoreCase(System.getProperty("bedrocknative.chunkPriority", "ahead"));
   public static final boolean PACED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.chunkPace", "on"));
   private static final double AHEAD_TICKS = 20.0;
   private static final double AHEAD_MAX_CHUNKS = 6.0;
   private static final long BURST_QUIET_NANOS = 1000000000L;
   private static final int BURST_LOG_COLUMNS = 32;
   private static final long STALE_DRAIN_NANOS = 100000000L;
   private static final int WORKERS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 3);
   private static final AtomicInteger WORKER_NUMBER = new AtomicInteger();
   private static final AtomicLong SUBMITTED = new AtomicLong();
   private static final ThreadPoolExecutor DECODE = new ThreadPoolExecutor(
      WORKERS,
      WORKERS,
      30L,
      TimeUnit.SECONDS,
      new PriorityBlockingQueue<>(),
      runnable -> Thread.ofPlatform().daemon().name("Bedrock chunk decode #" + WORKER_NUMBER.incrementAndGet()).unstarted(runnable)
   );
   private static final Set<Class<?>> FAILURES_LOGGED = ConcurrentHashMap.newKeySet();
   private static final ArrivalWatch ARRIVALS = new ArrivalWatch(System::nanoTime);
   private static volatile long playerChunk = ColumnQueue.key(0, 0);
   private static volatile @Nullable ChunkStream active;
   private final Minecraft minecraft;
   private final Runnable onFullColumn;
   private final ApplyBudget budget = new ApplyBudget();
   private final FrameBudget frameBudget = new FrameBudget(BUDGET_NANOS);
   private final ColumnQueue<PreparedColumn> queue = new ColumnQueue<>(System::nanoTime, this.budget);
   private final ChunkStream.LevelTarget target = new ChunkStream.LevelTarget();
   private long lastDrain;
   private int playerColumnDecodingAtTick;
   private int playerColumnReadyAtTick;
   private ColumnQueue.Totals before;
   private ColumnQueue.Totals burstBase;
   private long burstStarted;
   private long lastArrival;
   private long emptiedAt;
   private ThreadedLightEngine.@Nullable Stats burstLight;
   private MeshStats.@Nullable Snapshot burstMeshes;

   public ChunkStream(Minecraft minecraft, Runnable onFullColumn) {
      this.minecraft = minecraft;
      this.onFullColumn = onFullColumn;
      this.before = this.queue.totals();
   }

   public static void register() {
      FrameTimes.register();
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         FrameTimes.watch().tickStart(System.nanoTime());
         ChunkStream stream = active;
         if (stream != null) {
            stream.tickStarted();
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> FrameTimes.watch().tickEnd(System.nanoTime()));
   }

   public static void frameStarted() {
      Minecraft minecraft = Minecraft.getInstance();
      FrameWatch watch = FrameTimes.watch();
      ChunkStream stream = active;
      long downstream = watch.bucketNanos(FrameWatch.Bucket.SECTION_COPIES)
         + watch.bucketNanos(FrameWatch.Bucket.SECTION_COMPILE)
         + watch.bucketNanos(FrameWatch.Bucket.TERRAIN_UPLOADS);
      long chunkWork = watch.bucketNanos(FrameWatch.Bucket.CHUNK_APPLY)
         + watch.bucketNanos(FrameWatch.Bucket.LIGHT)
         + (watch.frameColumns() > 0 ? downstream : 0L);
      long idle = watch.bucketNanos(FrameWatch.Bucket.PRESENT);
      if (stream != null && watch.frameColumns() > 0 && watch.bucketNanos(FrameWatch.Bucket.VIEW_REBUILD) == 0L) {
         stream.budget.recordDownstream(downstream, watch.frameColumns());
      }

      long now = System.nanoTime();
      long ended = watch.frameStart(now, minecraft.gui != null && minecraft.gui.screen() instanceof LevelLoadingScreen);
      StallSampler.frameStarted(now);
      if (stream != null && ended > 0L) {
         stream.frameBudget.frameEnded(ended, idle, chunkWork);
      }
   }

   public static void drainActive() {
      Minecraft minecraft = Minecraft.getInstance();
      ThreadedLightEngine light = ThreadedLight.of(minecraft.level);
      if (light != null) {
         long started = System.nanoTime();
         ClientLevel level = minecraft.level;
         light.drain(column -> SodiumChunks.lit(level, ChunkPos.getX(column), ChunkPos.getZ(column)));
         FrameTimes.watch().add(FrameWatch.Bucket.LIGHT, System.nanoTime() - started);
      }

      ChunkStream stream = active;
      if (stream != null) {
         stream.drain(stream.frameBudget(), stream.frameColumns());
      }
   }

   private long frameBudget() {
      if (this.minecraft.gui.screen() instanceof LevelLoadingScreen) {
         return LOADING_BUDGET_NANOS;
      }

      long base = ADAPTIVE_BUDGET ? this.frameBudget.nanos(this.queue.waiting()) : BUDGET_NANOS;
      int limit = this.minecraft.getFramerateLimitTracker().getFramerateLimit();
      return limit > 0 && limit < 260 ? Math.max(base, 1000000000L / limit / 3L) : base;
   }

   private int frameColumns() {
      return PACED && !(this.minecraft.gui.screen() instanceof LevelLoadingScreen) ? this.frameBudget.columns(this.queue.waiting()) : Integer.MAX_VALUE;
   }

   private static ColumnQueue.Priority priority(LocalPlayer player, int chunkX, int chunkZ) {
      if (!PRIORITY_AHEAD) {
         return ColumnQueue.Priority.around(chunkX, chunkZ);
      }

      Vec3 velocity = player.getDeltaMovement();
      double aheadX = velocity.x * 20.0 / 16.0;
      double aheadZ = velocity.z * 20.0 / 16.0;
      double ahead = Math.sqrt(aheadX * aheadX + aheadZ * aheadZ);
      if (ahead > 6.0) {
         aheadX *= 6.0 / ahead;
         aheadZ *= 6.0 / ahead;
      }

      Vec3 look = player.getViewVector(1.0F);
      double flat = Math.sqrt(look.x * look.x + look.z * look.z);
      double forwardX = flat > 1.0E-6 ? look.x / flat : 0.0;
      double forwardZ = flat > 1.0E-6 ? look.z / flat : 0.0;
      return new ColumnQueue.Priority(chunkX, chunkZ, player.getX() / 16.0 + aheadX, player.getZ() / 16.0 + aheadZ, forwardX, forwardZ);
   }

   public static @Nullable ChunkStream active() {
      return active;
   }

   public static ArrivalWatch arrivals() {
      return ARRIVALS;
   }

   public ColumnQueue<PreparedColumn> queue() {
      return this.queue;
   }

   public ChunkStream.Stats stats() {
      return new ChunkStream.Stats(
         this.queue.totals(),
         this.playerColumnDecodingAtTick,
         this.playerColumnReadyAtTick,
         WORKERS,
         BUDGET_NANOS / 1000000L,
         this.budget.applyNanos() / 1000.0,
         this.budget.finishNanos() / 1000.0,
         this.budget.downstreamNanos() / 1000.0,
         this.frameBudget.nanos(this.queue.waiting()) / 1000000.0,
         this.frameBudget.otherNanos() / 1000000.0,
         this.frameBudget.periodNanos() / 1000000.0
      );
   }

   public void decode(int chunkX, int chunkZ, Runnable work) {
      long player = playerChunk;
      long dx = chunkX - (int)(player >> 32);
      long dz = chunkZ - (int)player;
      DECODE.execute(new ChunkStream.DecodeTask(dx * dx + dz * dz, SUBMITTED.getAndIncrement(), work));
   }

   public int newGeneration() {
      return this.queue.newGeneration();
   }

   public void activate(int generation) {
      this.queue.activate(generation);
      active = this;
   }

   public void close() {
      this.queue.newGeneration();
      if (active == this) {
         active = null;
      }
   }

   private void drain(long budget, int maxColumns) {
      this.lastDrain = System.nanoTime();
      ClientLevel level = this.minecraft.level;
      LocalPlayer player = this.minecraft.player;
      if (level != null && player != null) {
         int chunkX = SectionPos.blockToSectionCoord(player.getBlockX());
         int chunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
         playerChunk = ColumnQueue.key(chunkX, chunkZ);
         this.target.level = level;
         long perf = PerfProbe.start();

         ColumnQueue.Drained drained;
         try {
            drained = this.queue.drain(priority(player, chunkX, chunkZ), budget, maxColumns, this.target);
         } finally {
            this.target.level = null;
         }

         if (drained.applied() > 0) {
            PerfProbe.end(PerfProbe.Part.CHUNKS_APPLY, perf);
         }

         FrameWatch watch = FrameTimes.watch();
         watch.add(FrameWatch.Bucket.CHUNK_APPLY, drained.applyNanos());
         watch.add(FrameWatch.Bucket.LIGHT, drained.finishNanos());
         watch.columns(drained.applied(), drained.dropped(), drained.waiting());
         watch.waiting(drained.waiting());
         this.bursts(watch, drained.waiting());
      }
   }

   private void tickStarted() {
      if (System.nanoTime() - this.lastDrain > 100000000L && this.queue.waiting() > 0) {
         this.drain(this.frameBudget(), this.frameColumns());
      }

      LocalPlayer player = this.minecraft.player;
      if (player != null) {
         int chunkX = SectionPos.blockToSectionCoord(player.getBlockX());
         int chunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
         ColumnQueue.State state = this.queue.state(chunkX, chunkZ);
         if (state == ColumnQueue.State.READY) {
            this.drain(0L, 0);
            state = this.queue.state(chunkX, chunkZ);
            if (state == ColumnQueue.State.READY) {
               this.playerColumnReadyAtTick++;
               FrameTimes.watch().playerColumnWaited();
            }
         }

         if (state == ColumnQueue.State.DECODING) {
            this.playerColumnDecodingAtTick++;
         }
      }
   }

   private void bursts(FrameWatch watch, int waiting) {
      long now = System.nanoTime();
      ColumnQueue.Totals totals = this.queue.totals();
      if (totals.reserved() != this.before.reserved()) {
         if (this.burstBase == null || !watch.inBurst()) {
            this.burstBase = this.before;
            this.burstStarted = now;
            this.queue.resetMaxWaiting();
            ThreadedLightEngine light = ThreadedLight.of(this.minecraft.level);
            this.burstLight = light == null ? null : light.stats();
            this.burstMeshes = MeshStats.snapshot();
            if (!watch.inBurst()) {
               watch.beginBurst();
            }
         }

         this.lastArrival = now;
      }

      if (waiting > 0 || totals.decoding() > 0) {
         this.emptiedAt = 0L;
      } else if (this.emptiedAt == 0L) {
         this.emptiedAt = now;
      }

      if (watch.inBurst() && this.burstBase == null) {
         watch.endBurst();
      } else if (watch.inBurst() && waiting == 0 && totals.decoding() == 0 && now - this.lastArrival >= 1000000000L) {
         long arrived = totals.reserved() - this.burstBase.reserved();
         ThreadedLightEngine light = ThreadedLight.of(this.minecraft.level);
         String offThread = "";
         if (light != null && this.burstLight != null) {
            ThreadedLightEngine.Stats lightNow = light.stats();
            offThread = String.format(
               Locale.ROOT,
               "; light thread %.0f ms for %d columns",
               (lightNow.engineNanos() - this.burstLight.engineNanos()) / 1000000.0,
               lightNow.columnsLit() - this.burstLight.columnsLit()
            );
         }

         if (this.burstMeshes != null) {
            MeshStats.Snapshot meshes = MeshStats.snapshot().since(this.burstMeshes);
            offThread = offThread + String.format(Locale.ROOT, "; %d meshes in %.0f ms on the builders", meshes.count(), meshes.millis());
         }

         String line = String.format(
            Locale.ROOT,
            "Chunk burst: %d column updates arrived over %.1f s, %d applied, %d dropped, %d failed, done after %.1f s, peak %d waiting; %s%s",
            arrived,
            (this.lastArrival - this.burstStarted) / 1.0E9,
            totals.applied() - this.burstBase.applied(),
            totals.dropped() - this.burstBase.dropped(),
            totals.failed() - this.burstBase.failed(),
            ((this.emptiedAt != 0L ? this.emptiedAt : now) - this.burstStarted) / 1.0E9,
            totals.maxWaiting(),
            watch.endBurst(),
            offThread
         );
         if (arrived >= 32L) {
            BedrockNative.LOGGER.info(line);
         } else {
            BedrockNative.LOGGER.debug(line);
         }
      }

      this.before = totals;
   }

   private static long millis(String property, int fallback) {
      return Math.max(1, Integer.getInteger(property, fallback)) * 1000000L;
   }

   static {
      DECODE.allowCoreThreadTimeOut(true);
   }

   private record DecodeTask(long distance, long sequence, Runnable work) implements Runnable, Comparable<ChunkStream.DecodeTask> {
      @Override
      public void run() {
         long perf = PerfProbe.start();

         try {
            this.work.run();
            PerfProbe.end(PerfProbe.Part.CHUNKS_DECODE, perf);
         } catch (RuntimeException e) {
            if (ChunkStream.FAILURES_LOGGED.add(e.getClass())) {
               BedrockNative.LOGGER.error("Chunk decode failed; later failures of this kind are not logged", e);
            }
         }
      }

      public int compareTo(ChunkStream.DecodeTask other) {
         int byDistance = Long.compare(this.distance, other.distance);
         return byDistance != 0 ? byDistance : Long.compare(this.sequence, other.sequence);
      }
   }

   private final class LevelTarget implements ColumnQueue.Target<PreparedColumn> {
      @Nullable ClientLevel level;

      @Override
      public boolean inRange(int x, int z) {
         return BedrockChunkLoader.inRange(this.level, x, z);
      }

      public void apply(int x, int z, PreparedColumn column) {
         if (!PreparedColumn.empty(column)) {
            BedrockChunkLoader.apply(this.level, x, z, column);
            SessionTimeline.mark("first chunk");
            if (column.fullColumn()) {
               ChunkStream.this.onFullColumn.run();
            }
         }
      }

      @Override
      public void run(Runnable task) {
         long started = System.nanoTime();

         try {
            task.run();
         } finally {
            FrameTimes.watch().applyPart(FrameWatch.ApplyPart.PARKED, System.nanoTime() - started);
         }
      }

      @Override
      public void finish() {
         BedrockChunkLoader.runLight(this.level);
      }

      @Override
      public void failed(int x, int z, RuntimeException error) {
         if (ChunkStream.FAILURES_LOGGED.add(error.getClass())) {
            BedrockNative.LOGGER.error("Could not apply an update of chunk {} {}; later failures of this kind are not logged", new Object[]{x, z, error});
         }
      }
   }

   public record Stats(
      ColumnQueue.Totals queue,
      int playerColumnDecodingAtTick,
      int playerColumnReadyAtTick,
      int workers,
      long budgetMillis,
      double applyMicros,
      double lightMicros,
      double renderMicros,
      double budgetNowMillis,
      double otherWorkMillis,
      double framePeriodMillis
   ) {
   }
}
