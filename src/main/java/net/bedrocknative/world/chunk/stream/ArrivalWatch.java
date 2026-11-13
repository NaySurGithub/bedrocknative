package net.bedrocknative.world.chunk.stream;

import java.util.Locale;
import java.util.function.LongSupplier;

public final class ArrivalWatch {
   private final LongSupplier nanoTime;
   private long since;
   private boolean any;
   private long first;
   private long last;
   private long longestGap;
   private long longestGapEnd;
   private int levelChunks;
   private int subChunkPackets;
   private int subChunks;

   public ArrivalWatch(LongSupplier nanoTime) {
      this.nanoTime = nanoTime;
      this.since = nanoTime.getAsLong();
   }

   public synchronized void reset() {
      this.since = this.nanoTime.getAsLong();
      this.any = false;
      this.longestGap = 0L;
      this.levelChunks = 0;
      this.subChunkPackets = 0;
      this.subChunks = 0;
   }

   public synchronized void levelChunk() {
      this.arrived();
      this.levelChunks++;
   }

   public synchronized void subChunks(int count) {
      this.arrived();
      this.subChunkPackets++;
      this.subChunks += count;
   }

   private void arrived() {
      long now = this.nanoTime.getAsLong();
      if (!this.any) {
         this.any = true;
         this.first = now;
         this.longestGapEnd = now;
      } else if (now - this.last > this.longestGap) {
         this.longestGap = now - this.last;
         this.longestGapEnd = now;
      }

      this.last = now;
   }

   public synchronized ArrivalWatch.Arrivals arrivals() {
      return !this.any
         ? new ArrivalWatch.Arrivals(0, 0, 0, -1.0, -1.0, 0.0, -1.0)
         : new ArrivalWatch.Arrivals(
            this.levelChunks,
            this.subChunkPackets,
            this.subChunks,
            (this.first - this.since) / 1000000.0,
            (this.last - this.since) / 1000000.0,
            this.longestGap / 1000000.0,
            (this.longestGapEnd - this.since) / 1000000.0
         );
   }

   public record Arrivals(
      int levelChunks, int subChunkPackets, int subChunks, double firstMillis, double lastMillis, double longestGapMillis, double longestGapEndMillis
   ) {
      public String describe() {
         return this.levelChunks + this.subChunkPackets == 0
            ? "no chunk packet"
            : String.format(
               Locale.ROOT,
               "first chunk packet after %.0f ms, %d LevelChunk and %d SubChunk packets (%d sub-chunks) until %.0f ms, longest silence between two %.0f ms (ending at %.0f ms)",
               this.firstMillis,
               this.levelChunks,
               this.subChunkPackets,
               this.subChunks,
               this.lastMillis,
               this.longestGapMillis,
               this.longestGapEndMillis
            );
      }
   }
}
