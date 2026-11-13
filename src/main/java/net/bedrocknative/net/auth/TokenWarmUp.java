package net.bedrocknative.net.auth;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class TokenWarmUp {
   public static final long RETRY_MS = 120000L;
   public static final long JOIN_WAIT_MS = 30000L;
   private final ReentrantLock lock = new ReentrantLock();
   private final AtomicBoolean running = new AtomicBoolean();
   private final LongSupplier clock;
   private final Executor executor;
   private final TokenWarmUp.Renewal renewal;
   private final Consumer<String> log;
   private final long joinWaitMs;
   private volatile @Nullable String knownFor;
   private volatile long expiresAt;
   private volatile long failedAt = Long.MIN_VALUE;
   private volatile @Nullable String failedFor;
   private volatile int warmUps;

   public TokenWarmUp(LongSupplier clock, Executor executor, TokenWarmUp.Renewal renewal, Consumer<String> log) {
      this(clock, executor, renewal, log, 30000L);
   }

   TokenWarmUp(LongSupplier clock, Executor executor, TokenWarmUp.Renewal renewal, Consumer<String> log, long joinWaitMs) {
      this.clock = clock;
      this.executor = executor;
      this.renewal = renewal;
      this.log = log;
      this.joinWaitMs = joinWaitMs;
   }

   public boolean ahead(@Nullable String accountId, String why) {
      if (accountId != null && !this.running.get()) {
         long now = this.clock.getAsLong();
         if (accountId.equals(this.knownFor) && this.expiresAt > now) {
            return false;
         }

         if (accountId.equals(this.failedFor) && now - this.failedAt < 120000L) {
            return false;
         }

         if (!this.running.compareAndSet(false, true)) {
            return false;
         }

         try {
            this.executor.execute(() -> this.run(accountId, why));
            return true;
         } catch (RuntimeException e) {
            this.running.set(false);
            this.log.accept("Xbox account warm-up (" + why + ") could not start: " + e.getClass().getSimpleName());
            return false;
         }
      } else {
         return false;
      }
   }

   private void run(String accountId, String why) {
      long started = this.clock.getAsLong();
      this.lock.lock();

      try {
         TokenWarmUp.Result result = this.renewal.renew(accountId);
         this.knownFor = accountId;
         this.expiresAt = result.expiresAtMs();
         this.failedFor = null;
         this.warmUps++;
         long took = this.clock.getAsLong() - started;
         long left = (result.expiresAtMs() - this.clock.getAsLong()) / 60000L;
         this.log
            .accept(
               "Xbox account warm-up ("
                  + why
                  + "): "
                  + (result.renewed() ? "multiplayer token renewed in " + took + " ms" : "multiplayer token still fresh, nothing renewed (" + took + " ms)")
                  + ", valid "
                  + left
                  + " more minutes"
            );
      } catch (Exception e) {
         this.failedFor = accountId;
         this.failedAt = this.clock.getAsLong();
         this.log.accept("Xbox account warm-up (" + why + ") could not renew now (" + describe(e) + "); the join renews as before");
      } finally {
         this.lock.unlock();
         this.running.set(false);
      }
   }

   private static String describe(Exception e) {
      return e instanceof XboxAccountStore.AccountException account
         ? account.problem() + (account.diagnosis() == null ? "" : ", " + account.diagnosis())
         : e.getClass().getSimpleName();
   }

   public <T> T exclusive(Callable<T> work) throws Exception {
      boolean locked = false;
      boolean interrupted = false;

      try {
         locked = this.lock.tryLock(this.joinWaitMs, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         interrupted = true;
      }

      try {
         return work.call();
      } finally {
         if (locked) {
            this.lock.unlock();
         }

         if (interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   public void known(String accountId, long expiresAtMs) {
      this.knownFor = Objects.requireNonNull(accountId);
      this.expiresAt = expiresAtMs;
   }

   public void forget() {
      this.knownFor = null;
      this.expiresAt = 0L;
      this.failedFor = null;
   }

   public boolean running() {
      return this.running.get();
   }

   public int warmUps() {
      return this.warmUps;
   }

   public interface Renewal {
      TokenWarmUp.Result renew(String var1) throws Exception;
   }

   public record Result(long expiresAtMs, boolean renewed) {
   }
}
