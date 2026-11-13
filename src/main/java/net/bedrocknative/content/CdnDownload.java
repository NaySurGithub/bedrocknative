package net.bedrocknative.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.LongConsumer;

public final class CdnDownload {
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20L);
   private static final HttpClient HTTP = SafeHttp.client(CONNECT_TIMEOUT);
   private final UrlGuard guard;
   private volatile boolean cancelled;
   private volatile InputStream body;

   public CdnDownload() {
      this(UrlGuard.PUBLIC_ONLY);
   }

   public CdnDownload(UrlGuard guard) {
      this.guard = guard;
   }

   public byte[] fetch(String url, Path target, long maxBytes, Duration deadline, LongConsumer progress) throws IOException {
      long end = System.nanoTime() + deadline.toNanos();

      URI uri;
      try {
         uri = URI.create(url.trim());
      } catch (IllegalArgumentException e) {
         throw new IOException("Invalid CDN URL");
      }

      HttpResponse<InputStream> response;
      try {
         response = SafeHttp.get(HTTP, uri, this.guard, CONNECT_TIMEOUT);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException("CDN download interrupted");
      }

      try (
         InputStream in = response.body();
         OutputStream out = Files.newOutputStream(target);
      ) {
         this.body = in;
         if (this.cancelled) {
            throw new IOException("CDN download cancelled");
         }

         if (response.statusCode() != 200) {
            throw new IOException("CDN answered HTTP " + response.statusCode());
         }

         if (response.headers().firstValueAsLong("content-length").orElse(-1L) > maxBytes) {
            throw new IOException("CDN pack is larger than " + maxBytes + " bytes");
         }

         progress.accept(0L);
         MessageDigest digest = PackCache.sha256();
         byte[] buffer = new byte[65536];
         long total = 0L;

         int read;
         while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
               throw new IOException("CDN pack is larger than " + maxBytes + " bytes");
            }

            if (this.cancelled || System.nanoTime() > end) {
               throw new IOException(this.cancelled ? "CDN download cancelled" : "CDN download timed out");
            }

            digest.update(buffer, 0, read);
            out.write(buffer, 0, read);
            progress.accept(total);
         }

         return digest.digest();
      } catch (IOException e) {
         throw this.cancelled ? new IOException("CDN download cancelled") : e;
      }
   }

   public void cancel() {
      this.cancelled = true;
      InputStream stream = this.body;
      if (stream != null) {
         try {
            stream.close();
         } catch (IOException var3) {
         }
      }
   }
}
