package net.bedrocknative.client.content;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

final class ReloadPhases {
   private static final AtomicLong STARTED = new AtomicLong();
   private static final Map<Long, String> MARKS = new ConcurrentSkipListMap<>();

   private ReloadPhases() {
   }

   static void start() {
      MARKS.clear();
      STARTED.set(System.nanoTime());
   }

   static void mark(String name) {
      long started = STARTED.get();
      if (started != 0L && AtlasReload.running()) {
         long at = System.nanoTime() - started;

         while (MARKS.putIfAbsent(at, name) != null) {
            at++;
         }
      }
   }

   static String finish() {
      StringBuilder line = new StringBuilder();
      MARKS.forEach((at, name) -> line.append(line.isEmpty() ? "" : ", ").append(name).append(" +").append(at / 1000000L));
      MARKS.clear();
      STARTED.set(0L);
      return line.isEmpty() ? "none" : line.toString();
   }
}
