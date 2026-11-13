package net.bedrocknative.net.transport;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class JavaProbeGate {
   private final Supplier<CompletableFuture<JavaStatus>> launcher;
   private final long graceNanos;
   private final long armedAt;
   private boolean launched;
   private boolean withdrawn;
   private @Nullable CompletableFuture<JavaStatus> running;

   public JavaProbeGate(Supplier<CompletableFuture<JavaStatus>> launcher, long graceMillis) {
      this.launcher = launcher;
      this.graceNanos = Math.max(0L, graceMillis) * 1000000L;
      this.armedAt = System.nanoTime();
   }

   public synchronized @Nullable CompletableFuture<JavaStatus> tick() {
      return !this.launched && !this.withdrawn && System.nanoTime() - this.armedAt >= this.graceNanos ? this.launch() : null;
   }

   public synchronized @Nullable CompletableFuture<JavaStatus> launchNow() {
      return !this.launched && !this.withdrawn ? this.launch() : null;
   }

   private CompletableFuture<JavaStatus> launch() {
      this.launched = true;
      this.running = this.launcher.get();
      return this.running;
   }

   public synchronized void withdraw() {
      this.withdrawn = true;
      if (this.running != null) {
         this.running.cancel(true);
      }
   }

   public synchronized boolean launched() {
      return this.launched;
   }

   public synchronized boolean withdrawn() {
      return this.withdrawn;
   }
}
