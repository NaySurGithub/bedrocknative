package net.bedrocknative.perf;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.util.List;

public final class GcClock {
   private static final List<GarbageCollectorMXBean> PAUSING = ManagementFactory.getGarbageCollectorMXBeans().stream().filter(GcClock::pauses).toList();

   private GcClock() {
   }

   static boolean pauses(GarbageCollectorMXBean bean) {
      return pauses(bean.getName());
   }

   static boolean pauses(String name) {
      return !name.contains("Cycles");
   }

   public static long pauseNanos() {
      long millis = 0L;

      for (GarbageCollectorMXBean bean : PAUSING) {
         long time = bean.getCollectionTime();
         if (time > 0L) {
            millis += time;
         }
      }

      return millis * 1000000L;
   }

   public static List<String> collectors() {
      return PAUSING.stream().map(MemoryManagerMXBean::getName).toList();
   }
}
