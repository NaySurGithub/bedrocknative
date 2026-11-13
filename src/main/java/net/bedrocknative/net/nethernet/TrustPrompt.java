package net.bedrocknative.net.nethernet;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public final class TrustPrompt {
   public static final Duration MAX_WAIT = Duration.ofMinutes(10L);
   private static final long SLICE_MILLIS = 100L;
   private final CompletableFuture<Boolean> decision = new CompletableFuture<>();
   private final List<Runnable> onCancelled = new ArrayList<>();
   private boolean cancelled;

   public boolean decide(boolean trusted) {
      return this.decision.complete(trusted);
   }

   public boolean isDone() {
      return this.decision.isDone();
   }

   public boolean isTrusted() {
      return this.decision.isDone() && !this.decision.isCompletedExceptionally() && this.decision.getNow(false);
   }

   public void cancel() {
      List<Runnable> toRun;
      synchronized (this) {
         if (!this.decision.complete(false)) {
            return;
         }

         this.cancelled = true;
         toRun = List.copyOf(this.onCancelled);
      }

      for (Runnable listener : toRun) {
         run(listener);
      }
   }

   public void whenCancelled(Runnable listener) {
      boolean now;
      synchronized (this) {
         now = this.cancelled;
         if (!now) {
            this.onCancelled.add(listener);
         }
      }

      if (now) {
         run(listener);
      }
   }

   private static void run(Runnable listener) {
      try {
         listener.run();
      } catch (RuntimeException var2) {
      }
   }

   public CompletableFuture<Boolean> future() {
      return this.decision;
   }

   public boolean await(BooleanSupplier abandoned) {
      return this.await(abandoned, MAX_WAIT);
   }

   public boolean await(BooleanSupplier abandoned, Duration maxWait) {
      long deadline = System.nanoTime() + maxWait.toNanos();

      try {
         while (!abandoned.getAsBoolean()) {
            long left = deadline - System.nanoTime();
            if (left <= 0L) {
               this.cancel();
               return false;
            }

            try {
               return this.decision.get(Math.min(100L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(left))), TimeUnit.MILLISECONDS);
            } catch (TimeoutException var8) {
            }
         }

         this.cancel();
         return false;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         this.cancel();
         return false;
      } catch (ExecutionException e) {
         return false;
      }
   }
}
