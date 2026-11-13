package net.bedrocknative;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class LogFileWriter {
   static final int MAX_PENDING = 20000;
   static final long SHUTDOWN_DRAIN_MS = 1500L;
   private final Supplier<Path> path;
   private final Consumer<String> onFailure;
   private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(20000);
   private final AtomicInteger dropped = new AtomicInteger();
   private final Object written = new Object();
   private final AtomicLong queuedCount = new AtomicLong();
   private volatile boolean failed;
   private volatile boolean started;
   private long writtenCount;

   LogFileWriter(Supplier<Path> path, Consumer<String> onFailure) {
      this.path = path;
      this.onFailure = onFailure;
   }

   void add(String entry) {
      if (!this.failed) {
         this.start();
         if (this.queue.offer(entry)) {
            this.queuedCount.incrementAndGet();
         } else {
            this.dropped.incrementAndGet();
         }
      }
   }

   boolean drain(long millis) {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
      long target = this.queuedCount.get();
      synchronized (this.written) {
         while (this.writtenCount < target && !this.failed) {
            long left = deadline - System.nanoTime();
            if (left <= 0L) {
               return false;
            }

            try {
               TimeUnit.NANOSECONDS.timedWait(this.written, left);
            } catch (InterruptedException var12) {
               Thread.currentThread().interrupt();
               return false;
            }
         }

         return true;
      }
   }

   boolean failed() {
      return this.failed;
   }

   private void start() {
      if (!this.started) {
         synchronized (this) {
            if (!this.started) {
               this.started = true;
               Thread thread = new Thread(this::run, "BedrockNative log file");
               thread.setDaemon(true);
               thread.start();

               try {
                  Runtime.getRuntime().addShutdownHook(new Thread(() -> this.drain(1500L), "BedrockNative log file drain"));
               } catch (IllegalStateException var5) {
               }
            }
         }
      }
   }

   private void run() {
      BufferedWriter file = null;

      try {
         Path target = this.path.get();
         Files.createDirectories(target.getParent());
         if (Files.exists(target)) {
            String name = target.getFileName().toString();
            String previous = name.endsWith(".log") ? name.substring(0, name.length() - 4) + "-previous.log" : name + ".previous";
            Files.move(target, target.resolveSibling(previous), StandardCopyOption.REPLACE_EXISTING);
         }

         file = Files.newBufferedWriter(
            target, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
         );

         while (true) {
            String entry = this.queue.take();
            int batch = 0;

            do {
               file.write(entry);
            } while (++batch < 4096 && (entry = this.queue.poll()) != null);

            int lost = this.dropped.getAndSet(0);
            if (lost > 0) {
               file.write("[BedrockNative log file] " + lost + " lines not written: the disk did not keep up");
               file.newLine();
            }

            file.flush();
            synchronized (this.written) {
               this.writtenCount += batch;
               this.written.notifyAll();
            }
         }
      } catch (InterruptedException var19) {
         Thread.currentThread().interrupt();
      } catch (RuntimeException | IOException var20) {
         this.fail(var20);
      } finally {
         if (file != null) {
            try {
               file.close();
            } catch (IOException var17) {
            }
         }
      }
   }

   private void fail(Exception e) {
      this.failed = true;
      this.queue.clear();
      synchronized (this.written) {
         this.written.notifyAll();
      }

      this.onFailure.accept(e.toString());
   }

   static String entry(String line, @Nullable String trace) {
      return trace == null ? line + System.lineSeparator() : line + System.lineSeparator() + trace;
   }
}
