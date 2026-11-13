package net.bedrocknative.perf;

import java.util.Arrays;
import java.util.Locale;

public final class FrameStats {
   private static final long BIN_NANOS = 250000L;
   private static final int BINS = 2000;
   private final int[] bins = new int[2001];
   private int count;
   private long sum;
   private long max;

   public void add(long nanos) {
      if (nanos >= 0L) {
         int bin = (int)Math.min(2000L, nanos / 250000L);
         this.bins[bin]++;
         this.count++;
         this.sum += nanos;
         this.max = Math.max(this.max, nanos);
      }
   }

   public void addAll(FrameStats other) {
      for (int i = 0; i <= 2000; i++) {
         this.bins[i] = this.bins[i] + other.bins[i];
      }

      this.count = this.count + other.count;
      this.sum = this.sum + other.sum;
      this.max = Math.max(this.max, other.max);
   }

   public void clear() {
      Arrays.fill(this.bins, 0);
      this.count = 0;
      this.sum = 0L;
      this.max = 0L;
   }

   public int count() {
      return this.count;
   }

   public long maxNanos() {
      return this.max;
   }

   public double maxMillis() {
      return this.max / 1000000.0;
   }

   public double meanMillis() {
      return this.count == 0 ? 0.0 : (double)this.sum / this.count / 1000000.0;
   }

   public int over(long nanos) {
      int bin = (int)Math.min(2000L, nanos / 250000L);
      int result = 0;

      for (int i = bin + 1; i <= 2000; i++) {
         result += this.bins[i];
      }

      return result;
   }

   public double percentileMillis(double percent) {
      if (this.count == 0) {
         return 0.0;
      }

      long rank = Math.max(1L, (long)Math.ceil(percent / 100.0 * this.count));
      if (rank >= this.count) {
         return this.maxMillis();
      }

      long seen = 0L;

      for (int i = 0; i <= 2000; i++) {
         seen += this.bins[i];
         if (seen >= rank) {
            return i == 2000 ? this.maxMillis() : Math.min(this.maxMillis(), (i + 1) * 250000L / 1000000.0);
         }
      }

      return this.maxMillis();
   }

   public String summary() {
      return String.format(
         Locale.ROOT,
         "%d, p50 %.1f, p95 %.1f, p99 %.1f, max %.1f ms",
         this.count,
         this.percentileMillis(50.0),
         this.percentileMillis(95.0),
         this.percentileMillis(99.0),
         this.maxMillis()
      );
   }
}
