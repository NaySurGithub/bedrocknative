package net.bedrocknative.world.chunk.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ColumnHolds {
   private final Map<Long, Integer> holds = new HashMap<>();
   private final List<ColumnHolds.Parked> parked = new ArrayList<>();
   private final ColumnHolds.ColumnExecutor executor;

   public ColumnHolds(Consumer<Runnable> executor) {
      this((column, task) -> executor.accept(task));
   }

   public ColumnHolds(ColumnHolds.ColumnExecutor executor) {
      this.executor = executor;
   }

   public static long key(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | chunkZ & 4294967295L;
   }

   public synchronized void hold(long column) {
      this.holds.merge(column, 1, Integer::sum);
   }

   public synchronized void release(long column) {
      Integer count = this.holds.get(column);
      if (count != null) {
         if (count > 1) {
            this.holds.put(column, count - 1);
         } else {
            this.holds.remove(column);
            Iterator<ColumnHolds.Parked> it = this.parked.iterator();

            while (it.hasNext()) {
               ColumnHolds.Parked entry = it.next();
               if (entry.column == column) {
                  it.remove();
                  this.executor.execute(column, entry.task);
               }
            }
         }
      }
   }

   public synchronized boolean parkIfHeld(long column, Runnable task) {
      if (!this.holds.containsKey(column)) {
         return false;
      }

      this.parked.add(new ColumnHolds.Parked(column, task));
      return true;
   }

   public synchronized boolean isHeld(long column) {
      return this.holds.containsKey(column);
   }

   public synchronized int parkedCount() {
      return this.parked.size();
   }

   @FunctionalInterface
   public interface ColumnExecutor {
      void execute(long var1, Runnable var3);
   }

   private record Parked(long column, Runnable task) {
   }
}
