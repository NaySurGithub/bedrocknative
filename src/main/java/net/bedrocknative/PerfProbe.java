package net.bedrocknative;

import java.util.concurrent.atomic.AtomicLongArray;
import org.jspecify.annotations.Nullable;

public final class PerfProbe {
   public static final boolean ENABLED = "perf".equals(System.getProperty("bedrocknative.devTest"))
      || Boolean.getBoolean("bedrocknative.perfProbe")
      || System.getProperty("bedrocknative.devTest", "").startsWith("pvp")
      || "startup".equals(System.getProperty("bedrocknative.devTest"));
   private static final PerfProbe.Part[] PARTS = PerfProbe.Part.values();
   private static final AtomicLongArray NANOS = new AtomicLongArray(PARTS.length);
   private static final AtomicLongArray CALLS = new AtomicLongArray(PARTS.length);
   @Nullable
   private static volatile PerfProbe.FrameSink frameSink;

   private PerfProbe() {
   }

   public static void frameSink(@Nullable PerfProbe.FrameSink sink) {
      frameSink = sink;
   }

   public static long start() {
      return !ENABLED && frameSink == null ? 0L : System.nanoTime();
   }

   public static void end(PerfProbe.Part part, long start) {
      if (start != 0L) {
         long nanos = System.nanoTime() - start;
         if (ENABLED) {
            NANOS.addAndGet(part.ordinal(), nanos);
            CALLS.incrementAndGet(part.ordinal());
         }

         PerfProbe.FrameSink sink = frameSink;
         if (sink != null && part.frame) {
            sink.add(part, nanos);
         }
      }
   }

   public static long nanos(PerfProbe.Part part) {
      return NANOS.get(part.ordinal());
   }

   public static long calls(PerfProbe.Part part) {
      return CALLS.get(part.ordinal());
   }

   public static void reset() {
      for (int i = 0; i < PARTS.length; i++) {
         NANOS.set(i, 0L);
         CALLS.set(i, 0L);
      }
   }

   @FunctionalInterface
   public interface FrameSink {
      void add(PerfProbe.Part var1, long var2);
   }

   public static enum Part {
      MODELS(true),
      HUD(true),
      SCREENS(true),
      CHUNKS_NETWORK(false),
      CHUNKS_APPLY(false),
      CHUNKS_DECODE(false),
      PACKETS(false);

      private final boolean frame;

      private Part(boolean frame) {
         this.frame = frame;
      }
   }
}
