package net.bedrocknative.content.base;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

final class RawFiles {
   static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60L);
   private static final int ATTEMPTS = 3;
   private final HttpClient client;
   private final Function<BaseTable.Entry, URI> url;
   private final BaseHosts hosts;
   private final int parallel;

   RawFiles(HttpClient client, Function<BaseTable.Entry, URI> url, BaseHosts hosts, int parallel) {
      this.client = client;
      this.url = url;
      this.hosts = hosts;
      this.parallel = Math.max(1, parallel);
   }

   void fetch(List<BaseTable.Entry> entries, BaseStore store, FetchRun run) throws InterruptedException {
      ConcurrentLinkedQueue<BaseTable.Entry> queue = new ConcurrentLinkedQueue<>(entries);
      int workers = Math.min(this.parallel, Math.max(1, entries.size()));
      CountDownLatch finished = new CountDownLatch(workers);

      for (int i = 0; i < workers; i++) {
         Thread worker = new Thread(() -> {
            BaseTable.Entry entry;
            try {
               while (!run.stopped() && (entry = queue.poll()) != null) {
                  this.fetchOne(entry, store, run);
               }
            } finally {
               finished.countDown();
            }
         }, "BedrockNative base download " + i);
         worker.setDaemon(true);
         worker.start();
      }

      finished.await();
   }

   private void fetchOne(BaseTable.Entry entry, BaseStore store, FetchRun run) {
      String last = "not tried";
      int attempt = 1;

      while (attempt <= 3 && !run.stopped()) {
         try {
            byte[] data = this.get(entry, run);
            store.write(entry, data, data.length);
            run.written(entry);
            run.bytes.addAndGet(data.length);
            run.answered();
            return;
         } catch (RawFiles.Stop var8) {
            run.stop(var8.getMessage());
            return;
         } catch (BaseStore.Mismatch | RawFiles.Refused var9) {
            last = var9.getMessage();
            run.answered();
            break;
         } catch (IOException var10) {
            last = var10.getMessage() == null ? var10.getClass().getSimpleName() : var10.getMessage();
            run.noAnswer(last);

            try {
               Thread.sleep(attempt * 1000L);
            } catch (InterruptedException var7) {
               Thread.currentThread().interrupt();
               run.stop("interrupted");
               return;
            }

            attempt++;
         } catch (InterruptedException var11) {
            Thread.currentThread().interrupt();
            run.stop("interrupted");
            return;
         }
      }

      if (!run.stopped()) {
         run.failed(entry.path(), last);
      }
   }

   private byte[] get(BaseTable.Entry entry, FetchRun run) throws IOException, InterruptedException {
      URI uri = this.url.apply(entry);

      try {
         this.hosts.check(uri);
      } catch (IOException var14) {
         throw new RawFiles.Stop(var14.getMessage());
      }

      HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("Accept-Encoding", "gzip").GET().build();
      run.requests.incrementAndGet();
      HttpResponse<InputStream> response = this.client.send(request, BodyHandlers.ofInputStream());

      byte[] var10;
      try (InputStream body = response.body()) {
         int status = response.statusCode();
         if (status == 429 || status == 403 && rateLimited(response)) {
            throw new RawFiles.Stop("the host's rate limit (HTTP " + status + retryAfter(response) + ")");
         }

         if (status == 404 || status == 403 || status >= 300 && status < 400) {
            throw new RawFiles.Refused("HTTP " + status);
         }

         if (status != 200) {
            throw new IOException("HTTP " + status);
         }

         boolean gzip = response.headers().firstValue("content-encoding").map(value -> value.trim().equalsIgnoreCase("gzip")).orElse(false);

         try (InputStream in = (InputStream)(gzip ? new GZIPInputStream(body) : body)) {
            var10 = readCapped(in, entry.size());
         }
      }

      return var10;
   }

   static byte[] readCapped(InputStream in, long size) throws IOException {
      if (size > 67108864L) {
         throw new IOException("file too large");
      } else {
         byte[] data = new byte[(int)size];
         int total = 0;

         while (total < data.length) {
            int read = in.read(data, total, data.length - total);
            if (read < 0) {
               throw new IOException("truncated: " + total + " of " + size + " bytes");
            }

            total += read;
         }

         if (in.read() >= 0) {
            throw new IOException("longer than the table's " + size + " bytes");
         } else {
            return data;
         }
      }
   }

   private static boolean rateLimited(HttpResponse<?> response) {
      return response.headers().firstValue("x-ratelimit-remaining").map(value -> value.trim().equals("0")).orElse(false)
         || response.headers().firstValue("retry-after").isPresent();
   }

   private static String retryAfter(HttpResponse<?> response) {
      return response.headers().firstValue("retry-after").map(value -> ", retry after " + value.trim().toLowerCase(Locale.ROOT)).orElse("");
   }

   static final class Refused extends IOException {
      Refused(String message) {
         super(message);
      }
   }

   static final class Stop extends IOException {
      Stop(String message) {
         super(message);
      }
   }
}
