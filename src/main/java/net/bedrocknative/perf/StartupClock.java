package net.bedrocknative.perf;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class StartupClock {
   private static final long JVM_START_MILLIS = ManagementFactory.getRuntimeMXBean().getStartTime();
   private static final Map<String, Long> STEP_NANOS = new LinkedHashMap<>();
   private static final Map<String, Long> BACKGROUND_MILLIS = new LinkedHashMap<>();
   private static volatile long titleMillis = -1L;
   private static volatile long mainInitMillis = -1L;
   private static volatile long clientInitMillis = -1L;

   private StartupClock() {
   }

   public static void step(String name, Runnable work) {
      long started = System.nanoTime();

      try {
         work.run();
      } finally {
         record(name, System.nanoTime() - started);
      }
   }

   public static <T> T step(String name, Supplier<T> work) {
      long started = System.nanoTime();

      Object var4;
      try {
         var4 = work.get();
      } finally {
         record(name, System.nanoTime() - started);
      }

      return (T)var4;
   }

   private static void record(String name, long nanos) {
      synchronized (STEP_NANOS) {
         STEP_NANOS.merge(name, nanos, Long::sum);
      }
   }

   public static void mainInitDone(long millisAfterJvmStart) {
      mainInitMillis = millisAfterJvmStart;
   }

   public static void clientInitDone(long millisAfterJvmStart) {
      clientInitMillis = millisAfterJvmStart;
   }

   public static void background(String name, long millis) {
      synchronized (BACKGROUND_MILLIS) {
         BACKGROUND_MILLIS.put(name, millis);
      }
   }

   public static long sinceJvmStart() {
      return System.currentTimeMillis() - JVM_START_MILLIS;
   }

   public static boolean titleShown() {
      if (titleMillis >= 0L) {
         return false;
      } else {
         titleMillis = sinceJvmStart();
         return true;
      }
   }

   public static long titleMillis() {
      return titleMillis;
   }

   public static long mainInitMillis() {
      return mainInitMillis;
   }

   public static long clientInitMillis() {
      return clientInitMillis;
   }

   public static String steps() {
      StringBuilder text = new StringBuilder();
      synchronized (STEP_NANOS) {
         STEP_NANOS.forEach((name, nanos) -> text.append(text.isEmpty() ? "" : ", ").append(name).append(' ').append(nanos / 1000000L));
      }

      return text.toString();
   }

   public static long stepMillis() {
      long total = 0L;
      synchronized (STEP_NANOS) {
         for (long nanos : STEP_NANOS.values()) {
            total += nanos;
         }
      }

      return total / 1000000L;
   }

   public static String backgroundLoads() {
      StringBuilder text = new StringBuilder();
      synchronized (BACKGROUND_MILLIS) {
         BACKGROUND_MILLIS.forEach((name, millis) -> text.append(text.isEmpty() ? "" : ", ").append(name).append(' ').append(millis));
      }

      return text.isEmpty() ? "none finished yet" : text.toString();
   }

   public static String line() {
      return String.format(
         Locale.ROOT,
         "Start-up: title screen %d ms after the JVM started; mod init done at %d ms (main) and %d ms (client), our init steps %d ms in all (%s); background loads: %s",
         titleMillis,
         mainInitMillis,
         clientInitMillis,
         stepMillis(),
         steps(),
         backgroundLoads()
      );
   }

   static void reset() {
      synchronized (STEP_NANOS) {
         STEP_NANOS.clear();
      }

      synchronized (BACKGROUND_MILLIS) {
         BACKGROUND_MILLIS.clear();
      }

      titleMillis = -1L;
      mainInitMillis = -1L;
      clientInitMillis = -1L;
   }
}
