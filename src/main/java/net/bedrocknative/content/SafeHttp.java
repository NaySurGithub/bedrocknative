package net.bedrocknative.content;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.ResponseInfo;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;

public final class SafeHttp {
   public static final int MAX_REDIRECTS = 5;
   private static final Executor LOOKUPS = Executors.newVirtualThreadPerTaskExecutor();

   private SafeHttp() {
   }

   public static HttpClient client(Duration connectTimeout) {
      return HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(Redirect.NEVER).build();
   }

   public static HttpResponse<InputStream> get(HttpClient client, URI start, UrlGuard guard, Duration timeout) throws IOException, InterruptedException {
      URI uri = start;
      int hop = 0;

      while (true) {
         guard.check(uri);

         HttpResponse<InputStream> response;
         try {
            response = client.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(), BodyHandlers.ofInputStream());
         } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL");
         }

         URI next = redirect(uri, response.statusCode(), response.headers().firstValue("location").orElse(null));
         if (next == null) {
            return response;
         }

         response.body().close();
         if (hop >= 5) {
            throw new IOException("Too many redirects");
         }

         uri = next;
         hop++;
      }
   }

   public static CompletableFuture<HttpResponse<byte[]>> getBytes(HttpClient client, URI start, UrlGuard guard, Duration timeout, String accept, int maxBytes) {
      return getBytes(client, start, guard, timeout, accept, info -> maxBytes);
   }

   public static CompletableFuture<HttpResponse<byte[]>> getBytes(
      HttpClient client, URI start, UrlGuard guard, Duration timeout, String accept, ToLongFunction<ResponseInfo> maxBytes
   ) {
      CompletableFuture<HttpResponse<byte[]>> result = new CompletableFuture<>();
      AtomicReference<CompletableFuture<?>> current = new AtomicReference<>();
      result.whenComplete((response, error) -> {
         CompletableFuture<?> running = current.get();
         if (result.isCancelled() && running != null) {
            running.cancel(true);
         }
      });
      hop(client, start, guard, timeout, accept, maxBytes, 0, result, current);
      return result;
   }

   private static void hop(
      HttpClient client,
      URI uri,
      UrlGuard guard,
      Duration timeout,
      String accept,
      ToLongFunction<ResponseInfo> maxBytes,
      int hops,
      CompletableFuture<HttpResponse<byte[]>> result,
      AtomicReference<CompletableFuture<?>> current
   ) {
      CompletableFuture<Void> lookup = CompletableFuture.runAsync(() -> {
         try {
            guard.check(uri);
         } catch (IOException e) {
            throw new CompletionException(e);
         }
      }, LOOKUPS);
      current.set(lookup);
      lookup.<HttpResponse<byte[]>>thenCompose(ignored -> {
         if (result.isDone()) {
            return CompletableFuture.failedFuture(new CancellationException());
         }

         HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", accept).GET().build();
         BodyHandler<byte[]> capped = info -> BodyHandlers.limiting(BodyHandlers.ofByteArray(), Math.max(0L, maxBytes.applyAsLong(info))).apply(info);
         CompletableFuture<HttpResponse<byte[]>> sent = client.sendAsync(request, capped);
         current.set(sent);
         return sent;
      }).whenComplete((response, error) -> {
         if (error == null) {
            URI next = redirect(uri, response.statusCode(), response.headers().firstValue("location").orElse(null));
            if (next == null) {
               result.complete((HttpResponse<byte[]>)response);
            } else if (hops >= 5) {
               result.completeExceptionally(new IOException("Too many redirects"));
            } else {
               hop(client, next, guard, timeout, accept, maxBytes, hops + 1, result, current);
            }
         } else {
            result.completeExceptionally(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
         }
      });
   }

   public static URI redirect(URI from, int status, String location) {
      if (location != null && (status == 301 || status == 302 || status == 303 || status == 307 || status == 308)) {
         try {
            return from.resolve(location.trim());
         } catch (IllegalArgumentException e) {
            return null;
         }
      } else {
         return null;
      }
   }
}
