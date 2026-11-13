package net.bedrocknative.partner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PartnerFetcher implements PartnerServers.Fetch {
   public static final Duration TIMEOUT = Duration.ofSeconds(5L);
   public static final int MAX_LIST_BYTES = 262144;
   public static final int MAX_REDIRECTS = 3;
   static final String USER_AGENT = "BedrockNative";
   private final HttpClient client;
   private final PartnerGuard guard;

   public PartnerFetcher(PartnerGuard guard) {
      this(guard, HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(Redirect.NEVER).proxy(Builder.NO_PROXY).build());
   }

   PartnerFetcher(PartnerGuard guard, HttpClient client) {
      this.guard = guard;
      this.client = client;
   }

   public PartnerGuard guard() {
      return this.guard;
   }

   @Override
   public byte[] get(URI start, int maxBytes, String accept) throws IOException, InterruptedException {
      URI uri = start;
      String host = start.getHost() == null ? "" : start.getHost().toLowerCase(Locale.ROOT);
      int hop = 0;

      while (true) {
         this.guard.check(uri);

         HttpRequest request;
         try {
            request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", "BedrockNative").header("Accept", accept).GET().build();
         } catch (IllegalArgumentException e) {
            throw new IOException("invalid URL");
         }

         CompletableFuture<HttpResponse<byte[]>> sent = this.client.sendAsync(request, BodyHandlers.limiting(BodyHandlers.ofByteArray(), maxBytes));

         HttpResponse<byte[]> response;
         try {
            response = sent.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
         } catch (TimeoutException e) {
            sent.cancel(true);
            throw new IOException("no full answer within " + TIMEOUT.toSeconds() + " s");
         } catch (InterruptedException e) {
            sent.cancel(true);
            throw e;
         } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof IOException io
               ? new IOException(io.getMessage() == null ? io.getClass().getSimpleName() : io.getMessage())
               : new IOException(String.valueOf(cause));
         }

         int status = response.statusCode();
         if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
            if (status != 200) {
               throw new IOException("HTTP " + status);
            }

            byte[] body = response.body();
            if (body != null && body.length <= maxBytes) {
               return body;
            }

            throw new IOException("answer larger than " + maxBytes + " bytes");
         }

         if (hop >= 3) {
            throw new IOException("too many redirects");
         }

         String location = response.headers().firstValue("location").orElseThrow(() -> new IOException("redirect without a target"));

         try {
            uri = uri.resolve(location.trim());
         } catch (IllegalArgumentException e) {
            throw new IOException("invalid redirect target");
         }

         if (uri.getHost() == null || !uri.getHost().toLowerCase(Locale.ROOT).equals(host)) {
            throw new IOException("refusing a redirect to another host");
         }

         hop++;
      }
   }
}
