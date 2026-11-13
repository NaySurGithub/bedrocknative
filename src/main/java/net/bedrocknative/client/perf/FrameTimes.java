package net.bedrocknative.client.perf;

import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.perf.GcClock;

public final class FrameTimes {
   private static final boolean CHUNK_LOG = System.getProperty("bedrocknative.devTest") != null || Boolean.getBoolean("bedrocknative.chunkLog");
   private static final FrameWatch WATCH = new FrameWatch(line -> BedrockNative.LOGGER.info(line), line -> {
      if (CHUNK_LOG) {
         BedrockNative.LOGGER.info(line);
      } else {
         BedrockNative.LOGGER.debug(line);
      }
   }, line -> BedrockNative.LOGGER.info(line), GcClock::pauseNanos);

   private FrameTimes() {
   }

   public static void register() {
      PerfProbe.frameSink((part, nanos) -> WATCH.add(part == PerfProbe.Part.MODELS ? FrameWatch.Bucket.MODELS : FrameWatch.Bucket.UI, nanos));
   }

   public static FrameWatch watch() {
      return WATCH;
   }

   public static void time(FrameWatch.Bucket bucket, Runnable work) {
      long started = System.nanoTime();

      try {
         work.run();
      } finally {
         WATCH.add(bucket, System.nanoTime() - started);
      }
   }

   public static void timeAround(FrameWatch.Bucket bucket, Runnable work) {
      long started = System.nanoTime();
      long named = WATCH.namedNanos();

      try {
         work.run();
      } finally {
         endAround(bucket, started, named);
      }
   }

   public static long start() {
      return System.nanoTime();
   }

   public static void end(FrameWatch.Bucket bucket, long started) {
      WATCH.add(bucket, System.nanoTime() - started);
   }

   public static void endAround(FrameWatch.Bucket bucket, long started, long namedBefore) {
      long inside = WATCH.namedNanos() - namedBefore;
      WATCH.add(bucket, Math.max(0L, System.nanoTime() - started - inside));
   }
}
