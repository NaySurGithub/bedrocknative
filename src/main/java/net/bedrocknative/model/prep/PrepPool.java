package net.bedrocknative.model.prep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import org.jspecify.annotations.Nullable;

public final class PrepPool implements AutoCloseable {
   private static final Comparator<PrepPool.Ticket> ORDER = Comparator.<PrepPool.Ticket>comparingInt(ticket -> ticket.tier.ordinal())
      .thenComparingLong(ticket -> ticket.tier == PrepPool.Tier.NEAR ? ticket.distance : 0L)
      .thenComparingLong(ticket -> ticket.sequence);
   private final String name;
   private final int workers;
   private final int priority;
   private final int maxQueued;
   private final long maxPreparedBytes;
   private final BiConsumer<String, Throwable> errors;
   private final ReentrantLock lock = new ReentrantLock();
   private final Condition work = this.lock.newCondition();
   private final Condition idle = this.lock.newCondition();
   private final PriorityQueue<PrepPool.Job> queue = new PriorityQueue<>((a, b) -> ORDER.compare(a.ticket, b.ticket));
   private final PriorityQueue<PrepPool.Prepared> prepared = new PriorityQueue<>(
      Comparator.<PrepPool.Prepared>comparingInt(item -> item.tier().ordinal()).thenComparingLong(item -> item.ticket().sequence)
   );
   private final List<Thread> threads = new ArrayList<>();
   private long preparedBytes;
   private int running;
   private int generation;
   private long sequence;
   private boolean closed;
   private long submitted;
   private long finished;
   private long dropped;
   private long cancelled;
   private long failed;
   private long promoted;

   public PrepPool(String name, int workers, int priority, int maxQueued, long maxPreparedBytes, BiConsumer<String, Throwable> errors) {
      this.name = name;
      this.workers = Math.max(1, workers);
      this.priority = Math.clamp((long)priority, 1, 10);
      this.maxQueued = Math.max(1, maxQueued);
      this.maxPreparedBytes = Math.max(0L, maxPreparedBytes);
      this.errors = errors;
   }

   public <R> PrepPool.Ticket submit(
      PrepPool.Tier tier, long distance, boolean droppable, PrepPool.Work<R> work, @Nullable PrepPool.Finish<R> finish, ToLongFunction<? super R> bytes
   ) {
      this.lock.lock();

      PrepPool.Ticket var9;
      try {
         PrepPool.Ticket ticket = new PrepPool.Ticket(this.sequence++, this.generation, tier, distance, true);
         this.submitted++;
         if (!this.closed && (!droppable || tier == PrepPool.Tier.NEAR || this.queue.size() < this.maxQueued)) {
            this.enqueue(new PrepPool.Job(ticket, work, finish, bytes));
            return ticket;
         }

         ticket.state = PrepPool.State.DROPPED;
         this.dropped++;
         var9 = ticket;
      } finally {
         this.lock.unlock();
      }

      return var9;
   }

   public Executor executor(PrepPool.Tier tier) {
      return command -> {
         this.lock.lock();

         try {
            PrepPool.Ticket ticket = new PrepPool.Ticket(this.sequence++, this.generation, tier, 0L, false);
            this.submitted++;
            this.enqueue(new PrepPool.Job(ticket, ignored -> {
               command.run();
               return null;
            }, null, result -> 0L));
         } finally {
            this.lock.unlock();
         }
      };
   }

   private void enqueue(PrepPool.Job job) {
      job.ticket.job = job;
      this.queue.add(job);
      this.start();
      this.work.signal();
   }

   private void start() {
      if (this.threads.isEmpty() && !this.closed) {
         for (int i = 0; i < this.workers; i++) {
            Thread thread = Thread.ofPlatform().daemon().name(this.name + " #" + (i + 1)).priority(this.priority).unstarted(this::loop);
            this.threads.add(thread);
            thread.start();
         }
      }
   }

   public boolean promote(PrepPool.Ticket ticket, long distance) {
      this.lock.lock();

      boolean var5;
      try {
         if (ticket.state == PrepPool.State.RUNNING) {
            ticket.tier = PrepPool.Tier.NEAR;
            ticket.distance = distance;
            this.promoted++;
            return true;
         }

         if (ticket.state == PrepPool.State.DONE) {
            return this.promoteWaiting(ticket, distance);
         }

         PrepPool.Job job = ticket.job;
         if (ticket.state == PrepPool.State.QUEUED && job != null && this.queue.remove(job)) {
            ticket.tier = PrepPool.Tier.NEAR;
            ticket.distance = distance;
            this.queue.add(job);
            this.promoted++;
            return true;
         }

         var5 = false;
      } finally {
         this.lock.unlock();
      }

      return var5;
   }

   private boolean promoteWaiting(PrepPool.Ticket ticket, long distance) {
      PrepPool.Prepared found = null;

      for (PrepPool.Prepared item : this.prepared) {
         if (item.ticket() == ticket) {
            found = item;
            break;
         }
      }

      if (found == null) {
         return false;
      } else {
         this.prepared.remove(found);
         ticket.tier = PrepPool.Tier.NEAR;
         ticket.distance = distance;
         this.prepared.add(new PrepPool.Prepared(ticket, found.result(), found.finish(), found.bytes(), PrepPool.Tier.NEAR));
         this.promoted++;
         return true;
      }
   }

   public void cancel(PrepPool.Ticket ticket) {
      this.lock.lock();

      try {
         if (!ticket.cancellable) {
            return;
         }

         switch (ticket.state) {
            case QUEUED:
               PrepPool.Job job = ticket.job;
               if (job != null) {
                  this.queue.remove(job);
               }

               ticket.job = null;
               ticket.state = PrepPool.State.CANCELLED;
               this.cancelled++;
               this.idleCheck();
               break;
            case RUNNING:
            case DONE:
               ticket.state = PrepPool.State.CANCELLED;
               this.cancelled++;
         }
      } finally {
         this.lock.unlock();
      }
   }

   public void clear() {
      this.lock.lock();

      try {
         this.generation++;
         List<PrepPool.Job> kept = new ArrayList<>();

         for (PrepPool.Job job : this.queue) {
            if (job.ticket.cancellable) {
               job.ticket.state = PrepPool.State.CANCELLED;
               job.ticket.job = null;
               this.cancelled++;
            } else {
               kept.add(job);
            }
         }

         this.queue.clear();
         this.queue.addAll(kept);

         for (PrepPool.Prepared item : this.prepared) {
            if (item.ticket().state == PrepPool.State.DONE) {
               item.ticket().state = PrepPool.State.CANCELLED;
               this.cancelled++;
            }
         }

         this.prepared.clear();
         this.preparedBytes = 0L;
         this.work.signalAll();
         this.idleCheck();
      } finally {
         this.lock.unlock();
      }
   }

   private void loop() {
      while (true) {
         this.lock.lock();

         PrepPool.Job job;
         try {
            while (!this.closed && (this.queue.isEmpty() || this.preparedBytes > this.maxPreparedBytes)) {
               this.work.awaitUninterruptibly();
            }

            if (this.closed) {
               return;
            }

            job = this.queue.poll();
            job.ticket.state = PrepPool.State.RUNNING;
            job.ticket.job = null;
            this.running++;
         } finally {
            this.lock.unlock();
         }

         Object result = null;
         Throwable failure = null;

         try {
            result = job.work.run(job.ticket);
         } catch (Throwable var18) {
            failure = var18;
         }

         if (failure != null) {
            this.report(this.name + ": a preparation job failed (nothing is drawn differently: what it prepared is made when first drawn)", failure);
         }

         long bytes = 0L;
         if (failure == null && result != null && job.finish != null) {
            try {
               bytes = Math.max(0L, job.bytes.applyAsLong(result));
            } catch (RuntimeException var17) {
               bytes = 0L;
            }
         }

         this.lock.lock();

         try {
            this.running--;
            PrepPool.Ticket ticket = job.ticket;
            if (ticket.state == PrepPool.State.CANCELLED || ticket.cancellable && ticket.generation != this.generation) {
               if (ticket.state != PrepPool.State.CANCELLED) {
                  ticket.state = PrepPool.State.CANCELLED;
                  this.cancelled++;
               }
            } else if (failure != null) {
               ticket.state = PrepPool.State.FAILED;
               this.failed++;
            } else {
               ticket.state = PrepPool.State.DONE;
               if (result != null && job.finish != null) {
                  PrepPool.Finish<Object> finish = (PrepPool.Finish<Object>)job.finish;
                  this.prepared.add(new PrepPool.Prepared(ticket, result, finish, bytes, ticket.tier));
                  this.preparedBytes += bytes;
               }
            }

            this.idleCheck();
         } finally {
            this.lock.unlock();
         }
      }
   }

   public PrepPool.Drained drain(long budgetNanos, int maxItems, LongSupplier clock) {
      long started = clock.getAsLong();
      int handed = 0;
      int skipped = 0;

      while (handed < Math.max(1, maxItems) && (handed <= 0 || clock.getAsLong() - started < budgetNanos)) {
         this.lock.lock();

         PrepPool.Prepared item;
         try {
            item = this.prepared.poll();
            if (item == null) {
               break;
            }

            this.preparedBytes = this.preparedBytes - item.bytes();
            this.work.signalAll();
            if (item.ticket().state != PrepPool.State.DONE || item.ticket().generation != this.generation) {
               if (item.ticket().state == PrepPool.State.DONE) {
                  item.ticket().state = PrepPool.State.CANCELLED;
                  this.cancelled++;
               }

               skipped++;
               continue;
            }

            item.ticket().state = PrepPool.State.FINISHED;
            this.finished++;
         } finally {
            this.lock.unlock();
         }

         try {
            item.finish().accept(item.result());
         } catch (RuntimeException var19) {
            this.report(this.name + ": handing a prepared result over failed", var19);
         }

         handed++;
      }

      this.lock.lock();

      int waiting;
      try {
         waiting = this.prepared.size();
      } finally {
         this.lock.unlock();
      }

      return new PrepPool.Drained(handed, skipped, waiting, clock.getAsLong() - started);
   }

   private void report(String message, Throwable error) {
      try {
         this.errors.accept(message, error);
      } catch (Throwable var4) {
      }
   }

   private void idleCheck() {
      if (this.queue.isEmpty() && this.running == 0) {
         this.idle.signalAll();
      }
   }

   public boolean awaitIdle(long timeoutMillis) {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
      this.lock.lock();

      try {
         while (!this.queue.isEmpty() || this.running > 0) {
            long left = deadline - System.nanoTime();
            if (left <= 0L) {
               return false;
            }

            try {
               this.idle.awaitNanos(left);
            } catch (InterruptedException var12) {
               Thread.currentThread().interrupt();
               return false;
            }
         }

         return true;
      } finally {
         this.lock.unlock();
      }
   }

   public PrepPool.Stats stats() {
      this.lock.lock();

      PrepPool.Stats var1;
      try {
         var1 = new PrepPool.Stats(
            this.queue.size(),
            this.running,
            this.prepared.size(),
            this.preparedBytes,
            this.submitted,
            this.finished,
            this.dropped,
            this.cancelled,
            this.failed,
            this.promoted
         );
      } finally {
         this.lock.unlock();
      }

      return var1;
   }

   public boolean hasWaiting() {
      this.lock.lock();

      boolean var1;
      try {
         var1 = !this.prepared.isEmpty();
      } finally {
         this.lock.unlock();
      }

      return var1;
   }

   public int workers() {
      return this.workers;
   }

   @Override
   public void close() {
      this.lock.lock();

      List<Thread> stopping;
      try {
         this.closed = true;
         this.work.signalAll();
         stopping = List.copyOf(this.threads);
      } finally {
         this.lock.unlock();
      }

      for (Thread thread : stopping) {
         try {
            thread.join(5000L);
         } catch (InterruptedException var8) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   public record Drained(int finished, int dropped, int waiting, long nanos) {
   }

   @FunctionalInterface
   public interface Finish<R> {
      void accept(R var1);
   }

   private static final class Job {
      final PrepPool.Ticket ticket;
      final PrepPool.Work<?> work;
      @Nullable
      final PrepPool.Finish<?> finish;
      final ToLongFunction<Object> bytes;

      Job(PrepPool.Ticket ticket, PrepPool.Work<?> work, @Nullable PrepPool.Finish<?> finish, ToLongFunction<Object> bytes) {
         this.ticket = ticket;
         this.work = work;
         this.finish = finish;
         this.bytes = bytes;
      }
   }

   private record Prepared(PrepPool.Ticket ticket, Object result, PrepPool.Finish<Object> finish, long bytes, PrepPool.Tier tier) {
   }

   public static enum State {
      QUEUED,
      RUNNING,
      DONE,
      FINISHED,
      DROPPED,
      CANCELLED,
      FAILED;
   }

   public record Stats(
      int queued, int running, int waiting, long waitingBytes, long submitted, long finished, long dropped, long cancelled, long failed, long promoted
   ) {
   }

   public static final class Ticket {
      private final long sequence;
      private final int generation;
      private final boolean cancellable;
      private volatile PrepPool.Tier tier;
      private volatile PrepPool.State state = PrepPool.State.QUEUED;
      private long distance;
      @Nullable
      private PrepPool.Job job;

      private Ticket(long sequence, int generation, PrepPool.Tier tier, long distance, boolean cancellable) {
         this.sequence = sequence;
         this.generation = generation;
         this.tier = tier;
         this.distance = distance;
         this.cancellable = cancellable;
      }

      public PrepPool.Tier tier() {
         return this.tier;
      }

      public PrepPool.State state() {
         return this.state;
      }

      public boolean live() {
         PrepPool.State now = this.state;
         return now == PrepPool.State.QUEUED || now == PrepPool.State.RUNNING || now == PrepPool.State.DONE;
      }

      @Override
      public String toString() {
         return "job " + this.sequence + " " + this.tier + " " + this.state;
      }
   }

   public static enum Tier {
      NEAR,
      WARM,
      LISTED;
   }

   @FunctionalInterface
   public interface Work<R> {
      @Nullable
      R run(PrepPool.Ticket var1);
   }
}
