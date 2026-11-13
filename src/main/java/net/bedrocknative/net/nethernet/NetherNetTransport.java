package net.bedrocknative.net.nethernet;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import java.nio.file.Path;
import java.util.function.Predicate;
import javax.net.ssl.X509ExtendedTrustManager;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.net.client.BedrockGameListener;
import net.bedrocknative.net.client.BedrockSessionSetup;
import net.bedrocknative.net.transport.BedrockTransport;
import net.bedrocknative.net.transport.ConnectAttempt;
import net.bedrocknative.net.transport.ProbeResult;
import net.bedrocknative.net.transport.ServerTarget;
import net.bedrocknative.net.transport.SignalingScheme;
import net.bedrocknative.net.transport.TransportKind;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannel;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.NetherNetClientChannel;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.jspecify.annotations.Nullable;

public final class NetherNetTransport implements BedrockTransport {
   public static final int HANDSHAKE_TIMEOUT_MS = 10000;
   @Nullable
   private final Path nativeFolder;
   private final NetherNetTrustStore trust;
   @Nullable
   private final X509ExtendedTrustManager tls;

   NetherNetTransport(@Nullable Path nativeFolder, NetherNetTrustStore trust, @Nullable X509ExtendedTrustManager tls) {
      this.nativeFolder = nativeFolder;
      this.trust = trust;
      this.tls = tls;
   }

   public static NetherNetTransport create(Path configFolder) {
      return new NetherNetTransport(configFolder.resolve("natives"), NetherNetTrustStore.open(configFolder), null);
   }

   public static NetherNetTransport forTests(NetherNetTrustStore trust, @Nullable X509ExtendedTrustManager tls) {
      return new NetherNetTransport(null, trust, tls);
   }

   public NetherNetTrustStore trustStore() {
      return this.trust;
   }

   @Override
   public TransportKind kind() {
      return TransportKind.NETHERNET;
   }

   @Override
   public ChannelFuture connect(EventLoopGroup group, ServerTarget target, ClientIdentity identity, BedrockGameListener listener, ConnectAttempt attempt) {
      ProbeResult probe = attempt.probe();
      if (probe != null && probe.supported() && probe.scheme() != null) {
         NetherNetNative.Status loaded = NetherNetNative.load(this.nativeFolder);
         if (!loaded.loaded()) {
            return failed(
               group,
               new NetherNetConnectException(
                  NetherNetConnectException.Stage.UNAVAILABLE, "NetherNet is not available on this system: " + loaded.problem(), null, -1, -1, 0L
               )
            );
         } else {
            Predicate<Exception> question = attempt.trustQuestion();
            return this.open(
               group,
               target,
               identity.netherNetIdentity(),
               probe.scheme(),
               new NetherNetLinkWatch(),
               new NetherNetClientInitializer(session -> BedrockSessionSetup.init(session, identity, target.address(), listener, attempt.protocol())),
               question == null ? null : question::test
            );
         }
      } else {
         return failed(
            group,
            new NetherNetConnectException(
               NetherNetConnectException.Stage.SIGNALING,
               "NetherNet join of " + target.hostPort() + " without a probe that answered",
               new IllegalStateException("no NetherNet probe answer"),
               -1,
               -1,
               0L
            )
         );
      }
   }

   ChannelFuture open(
      EventLoopGroup group, ServerTarget target, OperatorIdentity offerIdentity, SignalingScheme scheme, NetherNetLinkWatch watch, ChannelHandler handler
   ) {
      return this.open(group, target, offerIdentity, scheme, watch, handler, null);
   }

   ChannelFuture open(
      EventLoopGroup group,
      ServerTarget target,
      OperatorIdentity offerIdentity,
      SignalingScheme scheme,
      NetherNetLinkWatch watch,
      ChannelHandler handler,
      @Nullable Predicate<UntrustedServerException> question
   ) {
      long start = System.nanoTime();
      ServerIdentityTrust serverTrust = new ServerIdentityTrust(this.trust, scheme == SignalingScheme.HTTPS, target.hostPort());
      HttpSignaling signaling = new HttpSignaling(scheme, target.host(), target.address(), serverTrust, this.tls);
      signaling.askDuringJoin(question);
      BedrockNative.LOGGER.info("Connecting over NetherNet to {} ({} signaling)", target.hostPort(), scheme.id());
      ChannelFuture connect = ((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)new Bootstrap().group(group))
                           .channelFactory(NetherNetChannelFactory.client(signaling)))
                        .option(NetherChannelOption.NETHER_CLIENT_IDENTITY, offerIdentity))
                     .option(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, 10000))
                  .option(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 1))
               .option(NetherChannelOption.NETHER_METRICS, watch))
            .handler(handler))
         .connect(target.address());
      ChannelPromise result = connect.channel().newPromise();
      connect.addListener(
         done -> {
            long millis = (System.nanoTime() - start) / 1000000L;
            if (done.isSuccess()) {
               org.cloudburstmc.netty.channel.nethernet.NetherNetChannel.Path path = connect.channel() instanceof NetherNetChannel channel
                  ? channel.selectedPath()
                  : null;
               BedrockNative.LOGGER
                  .info(
                     "NetherNet: channels open in {} ms, selected pair {}{}",
                     new Object[]{
                        millis,
                        watch.pairTypes() != null ? watch.pairTypes() : (path != null ? path.localType() + "/" + path.remoteType() : "unknown"),
                        path == null ? "" : " to " + path.remote().getAddress().getHostAddress() + ":" + path.remote().getPort()
                     }
                  );
               result.trySuccess();
            } else {
               NetherNetConnectException failure = failure(signaling, watch, done.cause(), millis);
               BedrockNative.LOGGER
                  .info(
                     "NetherNet: join of {} failed after {} ms at {}: {}",
                     new Object[]{
                        target.hostPort(),
                        millis,
                        failure.stage(),
                        failure.getCause() == null ? failure.getMessage() : SignalingHttp.describe(failure.getCause())
                     }
                  );
               result.tryFailure(failure);
            }
         }
      );
      return result;
   }

   static NetherNetConnectException failure(HttpSignaling signaling, NetherNetLinkWatch watch, @Nullable Throwable cause, long millis) {
      UntrustedServerException asked = signaling.asked();
      if (asked != null) {
         boolean answered = signaling.stage() == HttpSignaling.Stage.ANSWERED;
         return new NetherNetConnectException(
            answered ? NetherNetConnectException.Stage.LINK : NetherNetConnectException.Stage.SIGNALING,
            answered ? "NetherNet: the link did not come up in the time left after the trust question" : "NetherNet: the join ended at the trust question",
            asked,
            signaling.offeredCandidates(),
            signaling.answerCandidates(),
            millis
         );
      } else {
         Throwable signalingFailure = signaling.failure();
         if (signalingFailure != null) {
            return new NetherNetConnectException(
               NetherNetConnectException.Stage.SIGNALING,
               "NetherNet signaling failed: " + messageOf(signalingFailure),
               signalingFailure,
               signaling.offeredCandidates(),
               signaling.answerCandidates(),
               millis
            );
         } else if (signaling.stage() == HttpSignaling.Stage.IDLE) {
            return new NetherNetConnectException(
               NetherNetConnectException.Stage.OFFER,
               "NetherNet made no offer: " + (cause == null ? "unknown" : messageOf(cause)),
               cause,
               signaling.offeredCandidates(),
               signaling.answerCandidates(),
               millis
            );
         } else {
            return signaling.stage() != HttpSignaling.Stage.ANSWERED
               ? new NetherNetConnectException(
                  NetherNetConnectException.Stage.SIGNALING,
                  "NetherNet signaling did not answer in time",
                  null,
                  signaling.offeredCandidates(),
                  signaling.answerCandidates(),
                  millis
               )
               : new NetherNetConnectException(
                  NetherNetConnectException.Stage.LINK,
                  "NetherNet link did not come up (peer " + watch.peer() + ", ICE " + watch.ice() + ")",
                  cause,
                  signaling.offeredCandidates(),
                  signaling.answerCandidates(),
                  millis
               );
         }
      }
   }

   private static String messageOf(Throwable error) {
      return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
   }

   private static ChannelFuture failed(EventLoopGroup group, NetherNetConnectException cause) {
      BedrockNative.LOGGER.info("NetherNet: {}", cause.getMessage());
      Channel channel = new NetherNetClientChannel(null);
      ChannelPromise promise = channel.newPromise();
      group.register(channel).addListener(registered -> {
         channel.close();
         promise.tryFailure(cause);
      });
      return promise;
   }
}
