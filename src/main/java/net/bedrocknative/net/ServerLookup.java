package net.bedrocknative.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class ServerLookup {
   public static final ServerLookup.Resolver SYSTEM = InetAddress::getAllByName;
   static final long SLOW_MS = 1000L;
   private final String host;
   private final CompletableFuture<InetSocketAddress> result;

   private ServerLookup(String host, CompletableFuture<InetSocketAddress> result) {
      this.host = host;
      this.result = result;
   }

   public static ServerLookup start(String host, int port, ServerLookup.Resolver resolver, Consumer<String> log) {
      CompletableFuture<InetSocketAddress> result = new CompletableFuture<>();
      Thread.ofVirtual().name("Server lookup " + host).start(() -> {
         try {
            result.complete(lookUp(host, port, resolver, log));
         } catch (Throwable var6) {
            result.completeExceptionally(var6);
         }
      });
      return new ServerLookup(host, result);
   }

   static InetSocketAddress lookUp(String host, int port, ServerLookup.Resolver resolver, Consumer<String> log) throws UnknownHostException {
      long start = System.nanoTime();
      InetAddress[] all = resolver.lookUp(host);
      long millis = (System.nanoTime() - start) / 1000000L;
      if (all != null && all.length != 0) {
         InetAddress chosen = all[0];
         if (millis >= 1000L || all.length > 1) {
            log.accept(
               "Address of "
                  + host
                  + ": "
                  + chosen.getHostAddress()
                  + " (of "
                  + Arrays.stream(all).map(InetAddress::getHostAddress).toList()
                  + "), looked up in "
                  + millis
                  + " ms"
            );
         }

         return new InetSocketAddress(chosen, port);
      } else {
         throw new UnknownHostException(host);
      }
   }

   public String host() {
      return this.host;
   }

   public boolean done() {
      return this.result.isDone();
   }

   public InetSocketAddress await() throws UnknownHostException {
      try {
         return this.result.join();
      } catch (CompletionException var4) {
         Throwable cause = var4.getCause();
         if (cause instanceof UnknownHostException unknown) {
            throw unknown;
         } else if (cause instanceof RuntimeException runtime) {
            throw runtime;
         } else if (cause instanceof Error error) {
            throw error;
         } else {
            UnknownHostException wrapped = new UnknownHostException(this.host + ": " + cause);
            wrapped.initCause(cause);
            throw wrapped;
         }
      }
   }

   @Nullable
   public InetSocketAddress addressOrNull() {
      try {
         return this.await();
      } catch (UnknownHostException var2) {
         return null;
      }
   }

   @FunctionalInterface
   public interface Resolver {
      InetAddress[] lookUp(String var1) throws UnknownHostException;
   }
}
