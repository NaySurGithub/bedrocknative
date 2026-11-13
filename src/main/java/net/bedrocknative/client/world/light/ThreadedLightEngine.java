package net.bedrocknative.client.world.light;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import net.bedrocknative.BedrockNative;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage.SectionType;
import org.jspecify.annotations.Nullable;

public final class ThreadedLightEngine extends LevelLightEngine {
   private static final int BATCH = 64;
   private static final long IDLE_PARK_NANOS = 2000000L;
   private static final AtomicInteger NUMBER = new AtomicInteger();
   private final ThreadedLightEngine.Collector collector;
   private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
   private final AtomicInteger pending = new AtomicInteger();
   private final Object engineLock = new Object();
   private final Thread thread;
   private volatile boolean closed;
   private final LongOpenHashSet litColumns = new LongOpenHashSet();
   private final Long2LongOpenHashMap expected = new Long2LongOpenHashMap();
   private long ticket;
   private final Object handover = new Object();
   private LongArrayList litPending = new LongArrayList();
   private LongArrayList dirtySkyPending = new LongArrayList();
   private LongArrayList dirtyBlockPending = new LongArrayList();
   private final LongArrayList litThisBatch = new LongArrayList();
   private final AtomicLong taskCount = new AtomicLong();
   private final AtomicLong passCount = new AtomicLong();
   private final AtomicLong engineNanos = new AtomicLong();
   private final AtomicLong columnsLit = new AtomicLong();
   private final AtomicLong longestPass = new AtomicLong();

   public ThreadedLightEngine(LightChunkGetter source, boolean hasBlockLight, boolean hasSkyLight) {
      this(new ThreadedLightEngine.Collector(source), hasBlockLight, hasSkyLight);
   }

   private ThreadedLightEngine(ThreadedLightEngine.Collector collector, boolean hasBlockLight, boolean hasSkyLight) {
      super(collector, hasBlockLight, hasSkyLight);
      this.collector = collector;
      this.thread = Thread.ofPlatform().daemon().name("Bedrock light #" + NUMBER.incrementAndGet()).unstarted(this::loop);
      this.thread.start();
   }

   public LightChunkGetter source() {
      return this.collector.source;
   }

   public void lightColumn(ChunkPos pos, boolean[] emptySections, int minSectionY) {
      long key = pos.pack();
      long mine = this.expectLit(pos);
      boolean[] empty = (boolean[])emptySections.clone();
      this.post(() -> {
         try {
            for (int i = 0; i < empty.length; i++) {
               super.updateSectionStatus(SectionPos.of(pos, minSectionY + i), empty[i]);
            }

            super.setLightEnabled(pos, true);
            super.propagateLightSources(pos);
         } finally {
            this.litThisBatch.add(key);
            this.litThisBatch.add(mine);
         }
      });
   }

   public void forgetColumn(ChunkPos pos, int minLightSection, int maxLightSection, int minSectionY, int maxSectionY) {
      this.litColumns.remove(SectionPos.getZeroNode(pos.x(), pos.z()));
      this.expected.remove(pos.pack());
      this.post(() -> {
         super.setLightEnabled(pos, false);

         for (int sectionY = minLightSection; sectionY < maxLightSection; sectionY++) {
            SectionPos section = SectionPos.of(pos, sectionY);
            super.queueSectionData(LightLayer.BLOCK, section, null);
            super.queueSectionData(LightLayer.SKY, section, null);
         }

         for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            super.updateSectionStatus(SectionPos.of(pos, sectionY), true);
         }
      });
   }

   public void drain(LongConsumer lit) {
      LongArrayList litNow;
      LongArrayList sky;
      LongArrayList block;
      synchronized (this.handover) {
         if (this.litPending.isEmpty() && this.dirtySkyPending.isEmpty() && this.dirtyBlockPending.isEmpty()) {
            return;
         }

         litNow = this.litPending;
         sky = this.dirtySkyPending;
         block = this.dirtyBlockPending;
         this.litPending = new LongArrayList();
         this.dirtySkyPending = new LongArrayList();
         this.dirtyBlockPending = new LongArrayList();
      }

      LongArrayList accepted = new LongArrayList();

      for (int i = 0; i + 1 < litNow.size(); i += 2) {
         long key = litNow.getLong(i);
         if (this.expected.get(key) == litNow.getLong(i + 1)) {
            this.expected.remove(key);
            this.litColumns.add(SectionPos.getZeroNode(ChunkPos.getX(key), ChunkPos.getZ(key)));
            accepted.add(key);
         }
      }

      for (int ix = 0; ix < sky.size(); ix++) {
         this.collector.source.onLightUpdate(LightLayer.SKY, SectionPos.of(sky.getLong(ix)));
      }

      for (int ix = 0; ix < block.size(); ix++) {
         this.collector.source.onLightUpdate(LightLayer.BLOCK, SectionPos.of(block.getLong(ix)));
      }

      for (int ix = 0; ix < accepted.size(); ix++) {
         lit.accept(accepted.getLong(ix));
      }
   }

   public boolean idle() {
      return this.pending.get() <= 0 && !super.hasLightWork();
   }

   public int litColumnCount() {
      return this.litColumns.size();
   }

   public void close() {
      this.closed = true;
      LockSupport.unpark(this.thread);
   }

   public boolean isClosed() {
      return this.closed;
   }

   public ThreadedLightEngine.Stats stats() {
      return new ThreadedLightEngine.Stats(
         this.taskCount.get(), this.passCount.get(), this.engineNanos.get(), this.columnsLit.get(), this.longestPass.get(), this.pending.get()
      );
   }

   public void checkBlock(BlockPos pos) {
      BlockPos copy = pos.immutable();
      this.post(() -> super.checkBlock(copy));
   }

   public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
      this.post(() -> super.updateSectionStatus(pos, sectionEmpty));
   }

   public void setLightEnabled(ChunkPos pos, boolean enable) {
      if (!enable) {
         this.litColumns.remove(SectionPos.getZeroNode(pos.x(), pos.z()));
         this.expected.remove(pos.pack());
      }

      this.post(() -> super.setLightEnabled(pos, enable));
   }

   public void propagateLightSources(ChunkPos pos) {
      long key = pos.pack();
      long mine = this.expectLit(pos);
      this.post(() -> {
         try {
            super.propagateLightSources(pos);
         } finally {
            this.litThisBatch.add(key);
            this.litThisBatch.add(mine);
         }
      });
   }

   public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
      this.post(() -> super.queueSectionData(layer, pos, data));
   }

   public void retainData(ChunkPos pos, boolean retain) {
      this.post(() -> super.retainData(pos, retain));
   }

   private long expectLit(ChunkPos pos) {
      this.litColumns.remove(SectionPos.getZeroNode(pos.x(), pos.z()));
      long mine = ++this.ticket;
      this.expected.put(pos.pack(), mine);
      return mine;
   }

   public int runLightUpdates() {
      if (this.closed) {
         synchronized (this.engineLock) {
            int left = super.runLightUpdates();
            this.publish();
            return left;
         }
      } else {
         LockSupport.unpark(this.thread);
         return 0;
      }
   }

   public boolean hasLightWork() {
      return this.pending.get() > 0 || super.hasLightWork();
   }

   public boolean lightOnInColumn(long sectionZeroNode) {
      return this.litColumns.contains(sectionZeroNode);
   }

   public String getDebugData(LightLayer layer, SectionPos pos) {
      synchronized (this.engineLock) {
         return super.getDebugData(layer, pos);
      }
   }

   public SectionType getDebugSectionType(LightLayer layer, SectionPos pos) {
      synchronized (this.engineLock) {
         return super.getDebugSectionType(layer, pos);
      }
   }

   private void post(Runnable task) {
      if (this.closed) {
         synchronized (this.engineLock) {
            task.run();
         }
      } else {
         boolean wake = this.pending.getAndIncrement() == 0;
         this.tasks.add(task);
         if (this.closed) {
            this.finishQueue(true);
         } else if (wake) {
            LockSupport.unpark(this.thread);
         }
      }
   }

   private void finishQueue(boolean run) {
      synchronized (this.engineLock) {
         int n = 0;

         Runnable task;
         while ((task = this.tasks.poll()) != null) {
            n++;
            if (run) {
               try {
                  task.run();
               } catch (RuntimeException var8) {
                  BedrockNative.LOGGER.error("Light engine: a queued task failed; the light may be wrong around it", var8);
               }
            }
         }

         if (n > 0) {
            this.pending.addAndGet(-n);
         }

         if (run && n > 0) {
            try {
               super.runLightUpdates();
            } catch (RuntimeException var7) {
               BedrockNative.LOGGER.error("Light engine: the final pass failed", var7);
            }
         }

         this.publish();
      }
   }

   private void loop() {
      int ran = 0;

      try {
         while (!this.closed) {
            Runnable task = this.tasks.poll();
            if (task == null) {
               LockSupport.parkNanos(2000000L);
            } else {
               long started = System.nanoTime();
               ran = 0;
               synchronized (this.engineLock) {
                  for (; task != null; task = ran < 64 ? this.tasks.poll() : null) {
                     ran++;

                     try {
                        task.run();
                     } catch (RuntimeException var21) {
                        BedrockNative.LOGGER.error("Light thread: a task failed; the light may be wrong around it", var21);
                     }
                  }

                  try {
                     super.runLightUpdates();
                  } catch (RuntimeException var20) {
                     BedrockNative.LOGGER.error("Light thread: a pass failed; the light may be wrong around it", var20);
                  }

                  this.publish();
               }

               this.pending.addAndGet(-ran);
               int counted = ran;
               ran = 0;
               long took = System.nanoTime() - started;
               this.taskCount.addAndGet(counted);
               this.passCount.incrementAndGet();
               this.engineNanos.addAndGet(took);
               this.longestPass.accumulateAndGet(took, Math::max);
            }
         }
      } catch (Throwable var23) {
         BedrockNative.LOGGER.error("Light thread died; the queued light work runs on the client thread from now on", var23);
      } finally {
         boolean died = !this.closed;
         this.closed = true;
         if (ran > 0) {
            this.pending.addAndGet(-ran);
         }

         try {
            this.finishQueue(died);
         } catch (Throwable var19) {
            BedrockNative.LOGGER.error("Light thread: the queue could not be finished", var19);
         }
      }
   }

   private void publish() {
      synchronized (this.handover) {
         if (!this.litThisBatch.isEmpty()) {
            this.litPending.addAll(this.litThisBatch);
            this.columnsLit.addAndGet(this.litThisBatch.size() / 2);
            this.litThisBatch.clear();
         }

         if (!this.collector.dirtySky.isEmpty()) {
            this.dirtySkyPending.addAll(this.collector.dirtySky);
            this.collector.dirtySky.clear();
         }

         if (!this.collector.dirtyBlock.isEmpty()) {
            this.dirtyBlockPending.addAll(this.collector.dirtyBlock);
            this.collector.dirtyBlock.clear();
         }
      }
   }

   private static final class Collector implements LightChunkGetter {
      final LightChunkGetter source;
      final LongArrayList dirtySky = new LongArrayList();
      final LongArrayList dirtyBlock = new LongArrayList();

      Collector(LightChunkGetter source) {
         this.source = source;
      }

      @Nullable
      public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
         return this.source.getChunkForLighting(chunkX, chunkZ);
      }

      public BlockGetter getLevel() {
         return this.source.getLevel();
      }

      public void onLightUpdate(LightLayer layer, SectionPos pos) {
         (layer == LightLayer.SKY ? this.dirtySky : this.dirtyBlock).add(pos.asLong());
      }
   }

   public record Stats(long tasks, long passes, long engineNanos, long columnsLit, long longestPassNanos, int pending) {
      public String describe() {
         return String.format(
            Locale.ROOT,
            "light thread: %d tasks in %d passes, %.0f ms in the engine (longest pass %.1f ms), %d columns lit, %d tasks pending",
            this.tasks,
            this.passes,
            this.engineNanos / 1000000.0,
            this.longestPassNanos / 1000000.0,
            this.columnsLit,
            this.pending
         );
      }
   }
}
