package net.bedrocknative.movement;

import java.util.Arrays;
import java.util.Locale;

public final class InputPacing {
   public static final int HISTORY = 256;
   public static final long HITCH_MILLIS = 80L;
   public static final long BURST_MILLIS = 20L;
   private final long[] ticks = new long[256];
   private final long[] nanos = new long[256];
   private int inputs;
   private int hitches;
   private int bursts;
   private long longestNanos;
   private long previousTick = -1L;
   private long previousNanos;

   public InputPacing() {
      Arrays.fill(this.ticks, -1L);
   }

   public void record(long tick, long nowNanos) {
      int index = index(tick);
      this.ticks[index] = tick;
      this.nanos[index] = nowNanos;
      this.inputs++;
      if (this.previousTick >= 0L && tick == this.previousTick + 1L) {
         long gap = nowNanos - this.previousNanos;
         this.longestNanos = Math.max(this.longestNanos, gap);
         if (gap > 80000000L) {
            this.hitches++;
         } else if (gap < 20000000L) {
            this.bursts++;
         }
      }

      this.previousTick = tick;
      this.previousNanos = nowNanos;
   }

   public void clear() {
      Arrays.fill(this.ticks, -1L);
      this.inputs = 0;
      this.hitches = 0;
      this.bursts = 0;
      this.longestNanos = 0L;
      this.previousTick = -1L;
   }

   public long gapMillis(long tick) {
      int now = index(tick);
      int before = index(tick - 1L);
      return tick >= 1L && this.ticks[now] == tick && this.ticks[before] == tick - 1L ? Math.round((this.nanos[now] - this.nanos[before]) / 1000000.0) : -1L;
   }

   public String spacing(long from, long to) {
      StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "send gaps ms %d-%d:", from, to));

      for (long tick = from; tick <= to; tick++) {
         long gap = this.gapMillis(tick);
         text.append(' ');
         if (gap < 0L) {
            text.append('?');
         } else {
            text.append(gap);
            if (gap > 80L) {
               text.append('!');
            } else if (gap < 20L) {
               text.append('*');
            }
         }
      }

      return text.toString();
   }

   public String summary() {
      return String.format(
         Locale.ROOT,
         "%d inputs, %d gaps over %d ms, %d inputs closer than %d ms to the one before, longest gap %d ms",
         this.inputs,
         this.hitches,
         80L,
         this.bursts,
         20L,
         Math.round(this.longestNanos / 1000000.0)
      );
   }

   public int inputs() {
      return this.inputs;
   }

   public int hitches() {
      return this.hitches;
   }

   public int bursts() {
      return this.bursts;
   }

   private static int index(long tick) {
      return (int)Math.floorMod(tick, 256L);
   }
}
