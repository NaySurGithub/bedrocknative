package net.bedrocknative.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public final class ParallelRange {
   static final int BLOCK = 128;

   private ParallelRange() {
   }

   public static void run(int count, IntConsumer work, Executor executor, int helpers) {
      run(count, 128, work, executor, helpers);
   }

   static void run(int count, int block, IntConsumer work, Executor executor, int helpers) {
      if (count > 0) {
         ParallelRange.Shared shared = new ParallelRange.Shared(count, block, work);
         int wanted = Math.min(helpers, (count - 1) / block);

         for (int i = 0; i < wanted; i++) {
            try {
               executor.execute(shared::help);
            } catch (RuntimeException var11) {
               break;
            }
         }

         try {
            shared.take(true);
         } catch (Error | RuntimeException var10) {
            shared.stop();
            shared.awaitHelpers();
            throw var10;
         }

         shared.awaitHelpers();

         for (int start : shared.failedBlocks()) {
            for (int index = start; index < Math.min(start + block, count); index++) {
               work.accept(index);
            }
         }
      }
   }

   private static final class Shared {
      private final int count;
      private final int block;
      private final IntConsumer work;
      private final AtomicInteger next = new AtomicInteger();
      private final Object lock = new Object();
      private int working;
      private final List<Integer> failed = new ArrayList<>();

      Shared(int count, int block, IntConsumer work) {
         this.count = count;
         this.block = block;
         this.work = work;
      }

      void help() {
         synchronized (this.lock) {
            this.working++;
         }

         try {
            this.take(false);
         } finally {
            synchronized (this.lock) {
               this.working--;
               this.lock.notifyAll();
            }
         }
      }

      void take(boolean caller) {
         int start;
         while ((start = this.next.getAndAdd(this.block)) < this.count) {
            int end = Math.min(start + this.block, this.count);
            if (caller) {
               for (int index = start; index < end; index++) {
                  this.work.accept(index);
               }
            } else {
               try {
                  for (int index = start; index < end; index++) {
                     this.work.accept(index);
                  }
               } catch (Throwable var8) {
                  synchronized (this.lock) {
                     this.failed.add(start);
                  }
               }
            }
         }
      }

      void stop() {
         this.next.set(this.count);
      }

      void awaitHelpers() {
         boolean interrupted = false;
         synchronized (this.lock) {
            while (this.working > 0) {
               try {
                  this.lock.wait();
               } catch (InterruptedException var5) {
                  interrupted = true;
               }
            }
         }

         if (interrupted) {
            Thread.currentThread().interrupt();
         }
      }

      List<Integer> failedBlocks() {
         synchronized (this.lock) {
            return List.copyOf(this.failed);
         }
      }
   }
}
