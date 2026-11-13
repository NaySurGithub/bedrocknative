package net.bedrocknative.perf;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class MeshStats {
   private static final AtomicLong COUNT = new AtomicLong();
   private static final AtomicLong NANOS = new AtomicLong();
   private static final AtomicLong MAX_NANOS = new AtomicLong();
   private static final AtomicLong SODIUM_COUNT = new AtomicLong();
   private static final AtomicLong SODIUM_NANOS = new AtomicLong();

   private MeshStats() {
   }

   public static void built(long nanos, boolean sodium) {
      COUNT.incrementAndGet();
      NANOS.addAndGet(nanos);
      MAX_NANOS.accumulateAndGet(nanos, Math::max);
      if (sodium) {
         SODIUM_COUNT.incrementAndGet();
         SODIUM_NANOS.addAndGet(nanos);
      }
   }

   public static MeshStats.Snapshot snapshot() {
      return new MeshStats.Snapshot(COUNT.get(), NANOS.get(), MAX_NANOS.get(), SODIUM_COUNT.get(), SODIUM_NANOS.get());
   }

   public static void resetMax() {
      MAX_NANOS.set(0L);
   }

   public record Snapshot(long count, long nanos, long maxNanos, long sodiumCount, long sodiumNanos) {
      public MeshStats.Snapshot since(MeshStats.Snapshot earlier) {
         return new MeshStats.Snapshot(
            this.count - earlier.count,
            this.nanos - earlier.nanos,
            this.maxNanos,
            this.sodiumCount - earlier.sodiumCount,
            this.sodiumNanos - earlier.sodiumNanos
         );
      }

      public double millis() {
         return this.nanos / 1000000.0;
      }

      public double meanMillis() {
         return this.count == 0L ? 0.0 : this.nanos / 1000000.0 / this.count;
      }

      public String describe() {
         String by = this.sodiumCount > 0L ? String.format(Locale.ROOT, "; %d by Sodium in %.0f ms", this.sodiumCount, this.sodiumNanos / 1000000.0) : "";
         return String.format(
            Locale.ROOT,
            "%d meshes in %.0f ms on the builder threads (%.2f ms each, longest %.1f ms%s)",
            this.count,
            this.millis(),
            this.meanMillis(),
            this.maxNanos / 1000000.0,
            by
         );
      }
   }
}
