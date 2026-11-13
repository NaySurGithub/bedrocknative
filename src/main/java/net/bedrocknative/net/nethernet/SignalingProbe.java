package net.bedrocknative.net.nethernet;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509ExtendedTrustManager;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.transport.NetherNetProbe;
import net.bedrocknative.net.transport.ProbeResult;
import net.bedrocknative.net.transport.ServerTarget;
import net.bedrocknative.net.transport.SignalingScheme;
import org.jspecify.annotations.Nullable;

public final class SignalingProbe implements NetherNetProbe {
   public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(1500L);
   static final int MAX_BODY = 65536;
   static final long OUTER_SLACK_MILLIS = 500L;
   private final @Nullable X509ExtendedTrustManager trust;

   public SignalingProbe() {
      this(null);
   }

   SignalingProbe(@Nullable X509ExtendedTrustManager trust) {
      this.trust = trust;
   }

   @Override
   public CompletableFuture<ProbeResult> probe(String host, InetSocketAddress address, @Nullable SignalingScheme pinned, Duration timeout) {
      CompletableFuture<ProbeResult> result = new CompletableFuture<>();
      long outerMillis = timeout.toMillis() * 4L + 500L;
      result.completeOnTimeout(ProbeResult.failed(ProbeResult.Reason.TIMEOUT, List.of(), outerMillis), outerMillis, TimeUnit.MILLISECONDS);
      Thread worker = Thread.ofVirtual().name("NetherNet probe " + ServerTarget.hostPort(host, address.getPort())).unstarted(() -> {
         ProbeResult outcome;
         try {
            outcome = this.probeNow(host, address, pinned, timeout);
         } catch (RuntimeException | Error e) {
            outcome = ProbeResult.failed(ProbeResult.Reason.ERROR, List.of(), 0L);
            BedrockNative.LOGGER.warn("NetherNet probe of {} failed", ServerTarget.hostPort(host, address.getPort()), e);
         }

         result.complete(outcome);
      });
      result.whenComplete((outcome, error) -> {
         if (worker.isAlive()) {
            worker.interrupt();
         }
      });
      worker.start();
      return result;
   }

   ProbeResult probeNow(String host, InetSocketAddress address, @Nullable SignalingScheme pinned, Duration timeout) {
      String target = ServerTarget.hostPort(host, address.getPort());
      long start = System.nanoTime();
      List<SignalingScheme> tried = new ArrayList<>();
      tried.add(SignalingScheme.HTTPS);

      ProbeResult result;
      try {
         result = this.get(SignalingScheme.HTTPS, host, address, timeout, tried, start);
      } catch (SignalingHttp.TlsFailure failure) {
         TlsFallbackRule.Decision decision = TlsFallbackRule.afterFailedHandshake(failure.certificatePresented, failure.reply, pinned);

         BedrockNative.LOGGER.info("NetherNet probe of {}: HTTPS {} ({})", new Object[]{target, switch (decision) {
            case PLAIN_HTTP -> "not offered, trying plain HTTP";
            case CERTIFICATE_REFUSED -> "certificate not valid, no plain HTTP";
            case DOWNGRADE_REFUSED -> "not offered, and this server used HTTPS before: no plain HTTP";
            case TLS_FAILED -> "failed, no plain HTTP";
         }, failure.getMessage()});

         result = switch (decision) {
            case PLAIN_HTTP -> {
               tried.add(SignalingScheme.HTTP);

               ProbeResult var20;
               try {
                  var20 = this.get(SignalingScheme.HTTP, host, address, timeout, tried, System.nanoTime());
               } catch (IOException e) {
                  var20 = failure(e, tried, start, target);
                  yield var20;
               }

               yield var20;
            }
            case CERTIFICATE_REFUSED -> {
               ProbeResult var18 = ProbeResult.failed(ProbeResult.Reason.TLS_CERTIFICATE, tried, elapsed(start));
               yield var18;
            }
            case DOWNGRADE_REFUSED -> {
               ProbeResult var17 = ProbeResult.failed(ProbeResult.Reason.TLS_DOWNGRADE, tried, elapsed(start));
               yield var17;
            }
            case TLS_FAILED -> {
               ProbeResult var12 = ProbeResult.failed(ProbeResult.Reason.ERROR, tried, elapsed(start));
               yield var12;
            }
         };
      } catch (IOException e) {
         result = failure(e, tried, start, target);
      }

      BedrockNative.LOGGER.info("{} ({})", result, target);
      return result;
   }

   private ProbeResult get(SignalingScheme scheme, String host, InetSocketAddress address, Duration timeout, List<SignalingScheme> tried, long start) throws IOException {
      try (Socket socket = SignalingHttp.open(scheme, host, address, this.trust, timeout, timeout)) {
         SignalingHttp.Reply reply = SignalingHttp.exchange(
            socket,
            "GET",
            "/v1/join",
            host,
            address.getPort(),
            Map.of("Accept", "application/json", "User-Agent", "libhttpclient/1.0.0.0"),
            null,
            65536,
            timeout
         );
         boolean ok = reply.status() / 100 == 2;
         if (ok && looksLikeHtml(reply)) {
            BedrockNative.LOGGER
               .info("NetherNet probe of {}: HTTP {} from a web page, not NetherNet", ServerTarget.hostPort(host, address.getPort()), reply.status());
            ok = false;
         }

         return ProbeResult.answered(scheme, reply.status(), ok ? NetherNetStatus.parse(reply.text()) : null, tried, elapsed(start));
      }
   }

   private static boolean looksLikeHtml(SignalingHttp.Reply reply) {
      String type = reply.headers().getOrDefault("content-type", "").toLowerCase(Locale.ROOT);
      if (!type.contains("text/html") && !type.contains("application/xhtml")) {
         String body = reply.text().stripLeading();
         return body.startsWith("<!doctype") || body.startsWith("<html") || body.startsWith("<?xml");
      } else {
         return true;
      }
   }

   private static ProbeResult failure(IOException error, List<SignalingScheme> tried, long start, String target) {
      ProbeResult.Reason reason;
      if (error instanceof SignalingHttp.TooLargeException) {
         reason = ProbeResult.Reason.TOO_LARGE;
      } else if (error instanceof SocketTimeoutException) {
         reason = ProbeResult.Reason.TIMEOUT;
      } else if (!(error instanceof ConnectException)
         && !(error instanceof NoRouteToHostException)
         && !(error instanceof SocketException socket && socket.getMessage() != null && socket.getMessage().contains("reset"))) {
         reason = ProbeResult.Reason.ERROR;
         BedrockNative.LOGGER.info("NetherNet probe of {}: {}", target, SignalingHttp.describe(error));
      } else {
         reason = ProbeResult.Reason.REFUSED;
      }

      return ProbeResult.failed(reason, tried, elapsed(start));
   }

   private static long elapsed(long start) {
      return (System.nanoTime() - start) / 1000000L;
   }
}
