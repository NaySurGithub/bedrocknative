package net.bedrocknative.world.chunk.stream;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class ColumnQueue<T> {
   public static final int URGENT_RADIUS = 1;
   private static final int INDEX_BITS = 21;
   private static final long INDEX_MASK = 2097151L;
   private static final long MAX_DISTANCE = 4398046511103L;
   static final long BEHIND_PENALTY = 20L;
   private final Object lock = new Object();
   private final Long2ObjectOpenHashMap<ColumnQueue.Column> columns = new Long2ObjectOpenHashMap();
   private final LongSupplier nanoTime;
   private final ApplyBudget budget;
   private volatile int generation;
   private volatile int activeGeneration = -1;
   private volatile long viewChangesReceived;
   private volatile long viewChangesApplied;
   private int pendingData;
   private long reserved;
   private long appliedTotal;
   private long droppedTotal;
   private long failedTotal;
   private int maxWaiting;
   private long[] order = new long[64];
   private ColumnQueue.Column[] candidates = new ColumnQueue.Column[64];
   private final List<ColumnQueue.Event> takenScratch = new ArrayList<>(4);

   public ColumnQueue(LongSupplier nanoTime, ApplyBudget budget) {
      this.nanoTime = nanoTime;
      this.budget = budget;
   }

   public static long key(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }

   public ColumnQueue.Ticket reserve(int x, int z) {
      synchronized (this.lock) {
         ColumnQueue.Event event = new ColumnQueue.Event(null);
         this.column(x, z).events.add(event);
         this.pendingData++;
         this.reserved++;
         this.maxWaiting = Math.max(this.maxWaiting, this.columns.size());
         return new ColumnQueue.Ticket(x, z, this.generation, event);
      }
   }

   public void offer(int x, int z, T data) {
      this.fill(this.reserve(x, z), data);
   }

   public void fill(ColumnQueue.Ticket ticket, T data) {
      synchronized (this.lock) {
         ColumnQueue.Event event = ticket.event;
         if (ticket.generation == this.generation && !event.dropped && !event.ready) {
            event.data = data;
            event.ready = true;
            this.pendingData--;
         }
      }
   }

   public void fail(ColumnQueue.Ticket ticket) {
      synchronized (this.lock) {
         ColumnQueue.Event event = ticket.event;
         if (ticket.generation == this.generation && !event.dropped && !event.ready) {
            event.dropped = true;
            this.pendingData--;
            this.failedTotal++;
            ColumnQueue.Column column = (ColumnQueue.Column)this.columns.get(key(ticket.x, ticket.z));
            if (column != null) {
               column.events.remove(event);
               if (column.events.isEmpty()) {
                  this.columns.remove(key(ticket.x, ticket.z));
               }
            }
         }
      }
   }

   public boolean park(int x, int z, Runnable task) {
      synchronized (this.lock) {
         ColumnQueue.Column column = (ColumnQueue.Column)this.columns.get(key(x, z));
         if (column == null) {
            return false;
         }

         column.events.add(new ColumnQueue.Event(task));
         return true;
      }
   }

   public int newGeneration() {
      synchronized (this.lock) {
         ObjectIterator var2 = this.columns.values().iterator();

         while (var2.hasNext()) {
            ColumnQueue.Column column = (ColumnQueue.Column)var2.next();

            for (ColumnQueue.Event event : column.events) {
               event.dropped = true;
               event.data = null;
            }
         }

         this.columns.clear();
         this.pendingData = 0;
         return ++this.generation;
      }
   }

   public int generation() {
      return this.generation;
   }

   public void activate(int generation) {
      this.activeGeneration = generation;
   }

   public long viewChangeReceived() {
      synchronized (this.lock) {
         return ++this.viewChangesReceived;
      }
   }

   public void viewChangeApplied(long number) {
      if (number > this.viewChangesApplied) {
         this.viewChangesApplied = number;
      }
   }

   private boolean viewChangePending() {
      return this.viewChangesReceived > this.viewChangesApplied;
   }

   public ColumnQueue.State state(int x, int z) {
      synchronized (this.lock) {
         ColumnQueue.Column column = (ColumnQueue.Column)this.columns.get(key(x, z));
         if (column == null) {
            return ColumnQueue.State.NONE;
         }

         for (ColumnQueue.Event event : column.events) {
            if (event.task == null) {
               return event.ready ? ColumnQueue.State.READY : ColumnQueue.State.DECODING;
            }
         }

         return ColumnQueue.State.NONE;
      }
   }

   public int waiting() {
      synchronized (this.lock) {
         return this.columns.size();
      }
   }

   public ColumnQueue.Totals totals() {
      synchronized (this.lock) {
         return new ColumnQueue.Totals(
            this.reserved, this.appliedTotal, this.droppedTotal, this.failedTotal, this.columns.size(), this.pendingData, this.maxWaiting
         );
      }
   }

   public void resetMaxWaiting() {
      synchronized (this.lock) {
         this.maxWaiting = this.columns.size();
      }
   }

   public ColumnQueue.Drained drain(int centerX, int centerZ, long budgetNanos, ColumnQueue.Target<T> target) {
      return this.drain(ColumnQueue.Priority.around(centerX, centerZ), budgetNanos, target);
   }

   public ColumnQueue.Drained drain(ColumnQueue.Priority priority, long budgetNanos, ColumnQueue.Target<T> target) {
      return this.drain(priority, budgetNanos, Integer.MAX_VALUE, target);
   }

   public ColumnQueue.Drained drain(ColumnQueue.Priority priority, long budgetNanos, int maxColumns, ColumnQueue.Target<T> target) {
      if (this.activeGeneration != this.generation) {
         synchronized (this.lock) {
            return new ColumnQueue.Drained(0, 0, 0, 0, this.columns.size(), this.pendingData, 0L, 0L, false);
         }
      } else {
         long started = this.nanoTime.getAsLong();
         int count = this.snapshot(priority);
         int applied = 0;
         int urgent = 0;
         int dropped = 0;
         int tasks = 0;
         boolean budgetReached = false;
         List<ColumnQueue.Event> taken = this.takenScratch;
         taken.clear();

         for (int i = 0; i < count; i++) {
            ColumnQueue.Column column = this.candidates[(int)(this.order[i] & 2097151L)];
            this.candidates[(int)(this.order[i] & 2097151L)] = null;
            boolean near = Math.max(Math.abs(column.x - priority.playerX()), Math.abs(column.z - priority.playerZ())) <= 1;
            if (!near && (applied - urgent >= maxColumns || !this.budget.allowsAnother(this.nanoTime.getAsLong() - started, applied, budgetNanos))) {
               budgetReached = true;

               for (int rest = i + 1; rest < count; rest++) {
                  this.candidates[(int)(this.order[rest] & 2097151L)] = null;
               }
               break;
            }

            boolean inRange = target.inRange(column.x, column.z);
            taken.clear();
            boolean drop;
            synchronized (this.lock) {
               long key = key(column.x, column.z);
               if (this.columns.get(key) != column) {
                  continue;
               }

               drop = !inRange && !this.viewChangePending();
               if (drop) {
                  for (ColumnQueue.Event event : column.events) {
                     if (event.task == null && !event.ready) {
                        this.pendingData--;
                     }

                     event.dropped = true;
                     taken.add(event);
                  }

                  column.events.clear();
                  this.columns.remove(key);
                  this.droppedTotal++;
               } else if (inRange) {
                  while (!column.events.isEmpty() && column.events.peekFirst().ready) {
                     taken.add(column.events.pollFirst());
                  }

                  if (column.events.isEmpty()) {
                     this.columns.remove(key);
                  }
               }
            }

            if (!taken.isEmpty()) {
               long columnStarted = this.nanoTime.getAsLong();
               boolean appliedData = false;

               for (ColumnQueue.Event event : taken) {
                  try {
                     if (event.task != null) {
                        tasks++;
                        target.run(event.task);
                     } else if (!drop) {
                        Object data = event.data;
                        event.data = null;
                        target.apply(column.x, column.z, (T)data);
                        appliedData = true;
                     }
                  } catch (RuntimeException e) {
                     target.failed(column.x, column.z, e);
                  }
               }

               if (drop) {
                  dropped++;
               } else if (appliedData) {
                  applied++;
                  if (near) {
                     urgent++;
                  }

                  this.budget.recordApply(this.nanoTime.getAsLong() - columnStarted);
               }
            }
         }

         long applyNanos = this.nanoTime.getAsLong() - started;
         long finishNanos = 0L;
         if (applied > 0) {
            long finishStarted = this.nanoTime.getAsLong();

            try {
               target.finish();
            } catch (RuntimeException e) {
               target.failed(priority.playerX(), priority.playerZ(), e);
            }

            finishNanos = this.nanoTime.getAsLong() - finishStarted;
            this.budget.recordFinish(finishNanos, applied);
         }

         int waiting;
         int decoding;
         synchronized (this.lock) {
            this.appliedTotal += applied;
            waiting = this.columns.size();
            decoding = this.pendingData;
         }

         return new ColumnQueue.Drained(applied, urgent, dropped, tasks, waiting, decoding, applyNanos, finishNanos, budgetReached);
      }
   }

   private int snapshot(ColumnQueue.Priority priority) {
      synchronized (this.lock) {
         int count = this.columns.size();
         if (this.order.length < count) {
            int size = Math.max(count, this.order.length * 2);
            this.order = new long[size];
            this.candidates = new ColumnQueue.Column[size];
         }

         int i = 0;

         for (ObjectIterator var5 = this.columns.values().iterator(); var5.hasNext(); i++) {
            ColumnQueue.Column column = (ColumnQueue.Column)var5.next();
            boolean near = Math.max(Math.abs(column.x - priority.playerX()), Math.abs(column.z - priority.playerZ())) <= 1;
            long key = near ? 0L : 1L + priority.key(column.x, column.z);
            this.order[i] = Math.min(key, 4398046511103L) << 21 | i;
            this.candidates[i] = column;
         }

         Arrays.sort(this.order, 0, count);
         return count;
      }
   }

   private ColumnQueue.Column column(int x, int z) {
      long key = key(x, z);
      ColumnQueue.Column column = (ColumnQueue.Column)this.columns.get(key);
      if (column == null) {
         column = new ColumnQueue.Column(x, z);
         this.columns.put(key, column);
      }

      return column;
   }

   private static final class Column {
      final int x;
      final int z;
      final ArrayDeque<ColumnQueue.Event> events = new ArrayDeque<>(2);

      Column(int x, int z) {
         this.x = x;
         this.z = z;
      }
   }

   public record Drained(int applied, int urgent, int dropped, int tasks, int waiting, int decoding, long applyNanos, long finishNanos, boolean budgetReached) {
   }

   private static final class Event {
      final @Nullable Runnable task;
      @Nullable Object data;
      boolean ready;
      boolean dropped;

      Event(@Nullable Runnable task) {
         this.task = task;
         this.ready = task != null;
      }
   }

   public record Priority(int playerX, int playerZ, double aheadX, double aheadZ, double forwardX, double forwardZ) {
      public static ColumnQueue.Priority around(int playerX, int playerZ) {
         return new ColumnQueue.Priority(playerX, playerZ, playerX + 0.5, playerZ + 0.5, 0.0, 0.0);
      }

      long key(int x, int z) {
         double dx = x + 0.5 - this.aheadX;
         double dz = z + 0.5 - this.aheadZ;
         long distance = Math.round(dx * dx + dz * dz);
         int ox = x - this.playerX;
         int oz = z - this.playerZ;
         if (Math.max(Math.abs(ox), Math.abs(oz)) > 1 && this.forwardX * ox + this.forwardZ * oz < 0.0) {
            distance += 20L;
         }

         return Math.min(distance, 4398046511103L);
      }
   }

   public enum State {
      NONE,
      DECODING,
      READY;
   }

   public interface Target<T> {
      boolean inRange(int var1, int var2);

      void apply(int var1, int var2, T var3);

      void run(Runnable var1);

      void finish();

      void failed(int var1, int var2, RuntimeException var3);
   }

   public static final class Ticket {
      private final int x;
      private final int z;
      private final int generation;
      private final ColumnQueue.Event event;

      private Ticket(int x, int z, int generation, ColumnQueue.Event event) {
         this.x = x;
         this.z = z;
         this.generation = generation;
         this.event = event;
      }

      public int x() {
         return this.x;
      }

      public int z() {
         return this.z;
      }
   }

   public record Totals(long reserved, long applied, long dropped, long failed, int waiting, int decoding, int maxWaiting) {
   }
}
