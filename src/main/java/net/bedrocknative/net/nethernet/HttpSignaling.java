package net.bedrocknative.net.nethernet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.net.ssl.X509ExtendedTrustManager;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.transport.SignalingScheme;
import org.cloudburstmc.netty.channel.nethernet.NetherNetConstants;
import org.cloudburstmc.netty.channel.nethernet.signaling.IceServerInfo;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetClientSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetClientSignaling.FailureHandler;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetSignaling.SignalHandler;
import org.jspecify.annotations.Nullable;

public final class HttpSignaling implements NetherNetClientSignaling {
   static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5L);
   static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10L);
   static final int MAX_ANSWER = 65536;
   private final SignalingScheme scheme;
   private final String host;
   private final InetSocketAddress address;
   private final ServerIdentityTrust trust;
   @Nullable
   private final X509ExtendedTrustManager tls;
   private final Duration answerTimeout;
   private final Duration connectTimeout;
   @Nullable
   private volatile Predicate<UntrustedServerException> question;
   @Nullable
   private volatile UntrustedServerException asked;
   @Nullable
   private volatile Socket inFlight;
   private final String localNetworkId = Long.toUnsignedString(ThreadLocalRandom.current().nextLong());
   @Nullable
   private volatile String connectionId;
   @Nullable
   private volatile SignalHandler handler;
   @Nullable
   private volatile FailureHandler failureHandler;
   private volatile boolean closed;
   private volatile HttpSignaling.Stage stage = HttpSignaling.Stage.IDLE;
   @Nullable
   private volatile Throwable failure;
   @Nullable
   private volatile ServerIdentityTrust.Verified verified;
   private volatile int offeredCandidates = -1;
   private volatile int answerCandidates = -1;

   public HttpSignaling(SignalingScheme scheme, String host, InetSocketAddress address, ServerIdentityTrust trust, @Nullable X509ExtendedTrustManager tls) {
      this(scheme, host, address, trust, tls, ANSWER_TIMEOUT);
   }

   HttpSignaling(
      SignalingScheme scheme, String host, InetSocketAddress address, ServerIdentityTrust trust, @Nullable X509ExtendedTrustManager tls, Duration answerTimeout
   ) {
      this(scheme, host, address, trust, tls, CONNECT_TIMEOUT, answerTimeout);
   }

   HttpSignaling(
      SignalingScheme scheme,
      String host,
      InetSocketAddress address,
      ServerIdentityTrust trust,
      @Nullable X509ExtendedTrustManager tls,
      Duration connectTimeout,
      Duration answerTimeout
   ) {
      this.scheme = scheme;
      this.host = host;
      this.address = address;
      this.trust = trust;
      this.tls = tls;
      this.answerTimeout = answerTimeout;
      this.connectTimeout = connectTimeout;
   }

   @Override
   public boolean usesTrickleIce() {
      return false;
   }

   @Override
   public CompletableFuture<List<IceServerInfo>> connect(SocketAddress remoteAddress) {
      return this.closed
         ? CompletableFuture.failedFuture(new IllegalStateException("This signaling already served a connection"))
         : CompletableFuture.completedFuture(List.of());
   }

   @Override
   public void sendDescription(String targetNetworkId, String offer) {
      if (!this.closed && this.stage == HttpSignaling.Stage.IDLE) {
         this.stage = HttpSignaling.Stage.POSTED;
         List<String> types = candidateTypes(offer);
         this.offeredCandidates = types.size();
         String target = this.hostPort();
         BedrockNative.LOGGER
            .info("NetherNet: offer {} candidates ({}) to {} over {}", new Object[]{types.size(), String.join(",", types), target, this.scheme.id()});
         if (BedrockNative.LOGGER.isDebugEnabled()) {
            BedrockNative.LOGGER.debug("NetherNet offer:\n{}", SdpRedactor.redact(offer));
         }

         Thread.ofVirtual().name("NetherNet signaling " + target).start(() -> this.post(offer));
      }
   }

   private void post(String offer) {
      long start = System.nanoTime();

      String answer;
      try (Socket socket = SignalingHttp.open(this.scheme, this.host, this.address, this.tls, this.connectTimeout, this.answerTimeout)) {
         this.inFlight = socket;
         if (this.closed) {
            throw new IOException("the signaling was closed");
         }

         Map<String, String> headers = new LinkedHashMap<>();
         headers.put("Content-Type", "application/sdp");
         headers.put("Accept", "application/sdp");
         headers.put("User-Agent", "libhttpclient/1.0.0.0");
         SignalingHttp.Reply reply = SignalingHttp.exchange(
            socket,
            "POST",
            "/v1/join/" + this.localNetworkId,
            this.host,
            this.address.getPort(),
            headers,
            offer.getBytes(StandardCharsets.UTF_8),
            65536,
            this.answerTimeout
         );
         String body = reply.text();
         if (reply.status() / 100 != 2) {
            throw new SignalingRefusedException(reply.status(), -1, SignalingHttp.printable(body));
         }

         if (!body.startsWith("v=")) {
            throw new SignalingRefusedException(reply.status(), SignalingRefusedException.code(body), SignalingHttp.printable(body));
         }

         answer = body;
      } catch (RuntimeException | IOException var14) {
         this.fail(var14, start);
         return;
      }

      long millis = (System.nanoTime() - start) / 1000000L;
      this.answerCandidates = candidateTypes(answer).size();
      if (BedrockNative.LOGGER.isDebugEnabled()) {
         BedrockNative.LOGGER.debug("NetherNet answer:\n{}", SdpRedactor.redact(answer));
      }

      ServerIdentityTrust.Verified server;
      try {
         server = this.trust.verify(answer);
      } catch (UntrustedServerException var11) {
         BedrockNative.LOGGER
            .info("NetherNet: answer in {} ms, identity {}, key sha256 {}: not trusted yet", new Object[]{millis, var11.displayDomain(), var11.shortDigest()});
         server = this.askDuringJoin(var11, answer, start);
         if (server == null) {
            return;
         }
      } catch (Exception var12) {
         this.fail(new ServerIdentityException(var12), start);
         return;
      }

      this.verified = server;
      BedrockNative.LOGGER
         .info(
            "NetherNet: answer in {} ms, {} candidates, identity {}, key sha256 {}, trust: {}",
            new Object[]{
               millis,
               this.answerCandidates,
               PromptText.plain(server.domain(), 64),
               server.shortDigest(),
               this.scheme == SignalingScheme.HTTPS ? "https certificate" : (this.asked != null ? "trusted by the player during the join" : "known key")
            }
         );
      this.stage = HttpSignaling.Stage.ANSWERED;
      SignalHandler target = this.handler;
      String id = this.connectionId;
      if (!this.closed && target != null && id != null) {
         target.onSignal(NetherNetConstants.buildSignalConnectResponse(id, withoutIdentity(answer)));
      }
   }

   @Nullable
   private ServerIdentityTrust.Verified askDuringJoin(UntrustedServerException untrusted, String answer, long start) {
      Predicate<UntrustedServerException> ask = this.question;
      if (ask != null && !this.closed) {
         this.asked = untrusted;
         this.stage = HttpSignaling.Stage.ASKING;
         long askedAt = System.nanoTime();

         boolean trusted;
         try {
            trusted = ask.test(untrusted);
         } catch (RuntimeException var14) {
            BedrockNative.LOGGER.warn("NetherNet: the trust question failed", var14);
            trusted = false;
         }

         long millis = (System.nanoTime() - askedAt) / 1000000L;
         if (this.closed) {
            BedrockNative.LOGGER.info("NetherNet: the trust question took {} ms, past the join's time{}", millis, trusted ? " (the key is kept)" : "");
            this.failure = untrusted;
            this.stage = HttpSignaling.Stage.FAILED;
            return null;
         } else if (!trusted) {
            this.fail(untrusted, start);
            return null;
         } else {
            try {
               ServerIdentityTrust.Verified server = this.trust.verify(answer);
               BedrockNative.LOGGER.info("NetherNet: key sha256 {} trusted by the player in {} ms, the join goes on", server.shortDigest(), millis);
               return server;
            } catch (UntrustedServerException var12) {
               this.fail(var12, start);
               return null;
            } catch (Exception var13) {
               this.fail(new ServerIdentityException(var13), start);
               return null;
            }
         }
      } else {
         this.fail(untrusted, start);
         return null;
      }
   }

   public void askDuringJoin(@Nullable Predicate<UntrustedServerException> question) {
      this.question = question;
   }

   @Nullable
   public UntrustedServerException asked() {
      return this.asked;
   }

   private void fail(Throwable error, long start) {
      this.failure = error;
      this.stage = HttpSignaling.Stage.FAILED;
      if (!this.closed) {
         if (!(error instanceof UntrustedServerException)) {
            BedrockNative.LOGGER
               .info(
                  "NetherNet: signaling with {} failed after {} ms: {}",
                  new Object[]{this.hostPort(), (System.nanoTime() - start) / 1000000L, SignalingHttp.describe(error)}
               );
         }

         FailureHandler target = this.failureHandler;
         if (target != null) {
            target.onFailure(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
         }
      }
   }

   static String withoutIdentity(String sdp) {
      String eol = sdp.contains("\r\n") ? "\r\n" : "\n";
      StringBuilder out = new StringBuilder(sdp.length());

      for (String line : sdp.split("\r\n|\n", -1)) {
         if (!line.startsWith("a=identity:")) {
            out.append(line).append(eol);
         }
      }

      return out.substring(0, out.length() - eol.length());
   }

   static List<String> candidateTypes(String sdp) {
      List<String> types = new ArrayList<>();

      for (String line : sdp.split("\r\n|\n")) {
         if (line.startsWith("a=candidate:")) {
            int typ = line.indexOf(" typ ");
            types.add(typ < 0 ? "?" : line.substring(typ + 5).split(" ")[0]);
         }
      }

      return types;
   }

   private String hostPort() {
      return SignalingHttp.hostHeader(this.host, this.address.getPort());
   }

   @Override
   public void setSignalHandler(String connectionId, SignalHandler handler) {
      this.connectionId = connectionId;
      this.handler = handler;
   }

   @Override
   public void removeSignalHandler(String connectionId) {
      if (connectionId.equals(this.connectionId)) {
         this.handler = null;
      }
   }

   @Override
   public void setFailureHandler(FailureHandler handler) {
      this.failureHandler = handler;
   }

   @Override
   public String getLocalNetworkId() {
      return this.localNetworkId;
   }

   @Override
   public boolean isActive() {
      return !this.closed;
   }

   @Override
   public void close() {
      this.closed = true;
      Socket socket = this.inFlight;
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException var3) {
         }
      }
   }

   public HttpSignaling.Stage stage() {
      return this.stage;
   }

   @Nullable
   public Throwable failure() {
      return this.failure;
   }

   @Nullable
   public ServerIdentityTrust.Verified verified() {
      return this.verified;
   }

   public int offeredCandidates() {
      return this.offeredCandidates;
   }

   public int answerCandidates() {
      return this.answerCandidates;
   }

   public static enum Stage {
      IDLE,
      POSTED,
      ASKING,
      ANSWERED,
      FAILED;
   }
}
