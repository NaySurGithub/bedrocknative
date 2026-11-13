package net.bedrocknative.client.perf;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class FrameClock {
   private static volatile long frames;
   private static volatile long lastStartNanos;
   private static volatile long previousStartNanos;
   private static final Queue<Runnable> ONCE = new ConcurrentLinkedQueue<>();

   private FrameClock() {
   }

   public static void frameStarted() {
      long now = System.nanoTime();
      previousStartNanos = lastStartNanos;
      lastStartNanos = now;
      frames++;

      Runnable once;
      while ((once = ONCE.poll()) != null) {
         once.run();
      }
   }

   public static long frames() {
      return frames;
   }

   public static long lastStartNanos() {
      return lastStartNanos;
   }

   public static double lastFrameMillis() {
      long previous = previousStartNanos;
      return previous == 0L ? 0.0 : (lastStartNanos - previous) / 1000000.0;
   }

   public static void afterNextFrame(Runnable action) {
      ONCE.add(action);
   }
}
