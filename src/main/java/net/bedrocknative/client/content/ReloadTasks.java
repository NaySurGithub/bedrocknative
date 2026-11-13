package net.bedrocknative.client.content;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.Minecraft;

public final class ReloadTasks implements Executor {
   private static final Set<ReloadTasks.Step> PENDING = ConcurrentHashMap.newKeySet();
   private static final AtomicLong CLIENT_NANOS = new AtomicLong();
   private final Executor main;

   private ReloadTasks(Executor main) {
      this.main = main;
   }

   public static Executor wrap(Executor main) {
      return new ReloadTasks(main);
   }

   public static void tasksDropped(Minecraft minecraft) {
      for (ReloadTasks.Step step : PENDING) {
         minecraft.schedule(step);
      }
   }

   @Override
   public void execute(Runnable command) {
      ReloadTasks.Step step = new ReloadTasks.Step(command);
      PENDING.add(step);
      this.main.execute(step);
   }

   public static long clientNanos() {
      return CLIENT_NANOS.get();
   }

   public static void later(Minecraft minecraft, Runnable command) {
      ReloadTasks.Step step = new ReloadTasks.Step(command);
      PENDING.add(step);
      minecraft.schedule(step);
   }

   private record Step(Runnable command) implements Runnable {
      @Override
      public void run() {
         if (ReloadTasks.PENDING.remove(this)) {
            long started = System.nanoTime();

            try {
               FrameTimes.time(FrameWatch.Bucket.RELOAD, this.command);
            } finally {
               ReloadTasks.CLIENT_NANOS.addAndGet(System.nanoTime() - started);
            }
         }
      }

      @Override
      public boolean equals(Object other) {
         return this == other;
      }

      @Override
      public int hashCode() {
         return System.identityHashCode(this);
      }
   }
}
