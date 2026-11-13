package net.bedrocknative.client.net;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

final class DevLoadWatch {
   static final long INVALID_TICK_MS = 80L;
   private static final AtomicLong LAST_TICK = new AtomicLong();
   private static final AtomicLong LAST_FRAME = new AtomicLong();
   private static final AtomicLong MAX_TICK = new AtomicLong();
   private static final AtomicLong MAX_FRAME = new AtomicLong();
   private static volatile long ignoreUntil;
   private static volatile String doing = "";
   private static final List<String> LATE = new CopyOnWriteArrayList<>();

   private DevLoadWatch() {
   }

   static void tick(boolean driving) {
      long now = System.nanoTime();
      long last = LAST_TICK.getAndSet(now);
      if (last != 0L && now > ignoreUntil) {
         long gap = now - last;
         MAX_TICK.accumulateAndGet(gap, Math::max);
         if (gap > 80000000L && LATE.size() < 8) {
            LATE.add(String.format("%d ms %s, last step: %s", gap / 1000000L, driving ? "while driving the keys" : "while idle", doing));
         }
      }
   }

   static void doing(String step) {
      doing = step;
   }

   static String late() {
      return LATE.isEmpty() ? "none" : String.join("; ", LATE);
   }

   static void frame() {
      long now = System.nanoTime();
      long last = LAST_FRAME.getAndSet(now);
      if (last != 0L && now > ignoreUntil) {
         MAX_FRAME.accumulateAndGet(now - last, Math::max);
      }
   }

   static void ignoreFor(long millis) {
      ignoreUntil = System.nanoTime() + millis * 1000000L;
   }

   static void reset() {
      MAX_TICK.set(0L);
      MAX_FRAME.set(0L);
      LAST_TICK.set(0L);
      LAST_FRAME.set(0L);
      LATE.clear();
   }

   static double maxTickMs() {
      return MAX_TICK.get() / 1000000.0;
   }

   static double maxFrameMs() {
      return MAX_FRAME.get() / 1000000.0;
   }

   static boolean overloaded() {
      return maxTickMs() > 80.0;
   }
}
