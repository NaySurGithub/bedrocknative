package net.bedrocknative.net.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.protocol.BedrockProtocols;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakClientChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.jspecify.annotations.Nullable;

public final class BedrockClientConnection {
   static final Integer[] VANILLA_MTU_SIZES = new Integer[]{1492, 1200, 576};
   static final int TIME_BETWEEN_ATTEMPTS_MS = 500;
   static final long CONNECT_TIMEOUT_MS = 10000L;
   private static final long WATCHDOG_MARGIN_MS = 2000L;

   static boolean legacyHandshake() {
      return !"vanilla".equalsIgnoreCase(System.getProperty("bedrocknative.rakHandshake", "").trim());
   }

   private BedrockClientConnection() {
   }

   public static ChannelFuture connect(EventLoopGroup group, InetSocketAddress address, ClientIdentity identity, BedrockGameListener listener) {
      return connect(group, address, identity, listener, new RakHandshakeTrace(), null, BedrockProtocols.latest());
   }

   public static ChannelFuture connect(
      EventLoopGroup group,
      InetSocketAddress address,
      ClientIdentity identity,
      BedrockGameListener listener,
      RakHandshakeTrace trace,
      @Nullable InetSocketAddress localAddress
   ) {
      return connect(group, address, identity, listener, trace, localAddress, BedrockProtocols.latest());
   }

   public static ChannelFuture connect(
      EventLoopGroup group,
      InetSocketAddress address,
      ClientIdentity identity,
      BedrockGameListener listener,
      RakHandshakeTrace trace,
      @Nullable InetSocketAddress localAddress,
      BedrockProtocol protocol
   ) {
      ChannelFuture future = bootstrap(group, address, identity, listener, trace, protocol).connect(address, localAddress);
      group.schedule(() -> {
         if (!future.isDone() && future.channel() instanceof RakClientChannel rak) {
            rak.getConnectPromise().tryFailure(new ConnectTimeoutException("RakNet connect did not end in 10000 ms"));
         }
      }, 12000L, TimeUnit.MILLISECONDS);
      return future;
   }

   private static Bootstrap bootstrap(
      EventLoopGroup group,
      final InetSocketAddress address,
      final ClientIdentity identity,
      final BedrockGameListener listener,
      final RakHandshakeTrace trace,
      final BedrockProtocol protocol
   ) {
      return (Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)new Bootstrap()
                                    .channelFactory(new BedrockClientConnection.VanillaHandshakeChannels()))
                                 .group(group))
                              .option(RakChannelOption.RAK_PROTOCOL_VERSION, 11))
                           .option(RakChannelOption.RAK_COMPATIBILITY_MODE, true))
                        .option(RakChannelOption.RAK_CLIENT_INTERNAL_ADDRESSES, 20))
                     .option(RakChannelOption.RAK_TIME_BETWEEN_SEND_CONNECTION_ATTEMPTS_MS, legacyHandshake() ? 1000 : 500))
                  .option(RakChannelOption.RAK_CONNECT_TIMEOUT, 10000L))
               .option(RakChannelOption.RAK_SESSION_TIMEOUT, 30000L))
            .option(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong()))
         .handler(new BedrockClientInitializer() {
            @Override
            protected void preInitChannel(Channel channel) throws Exception {
               super.preInitChannel(channel);
               if (channel instanceof RakChannel rak) {
                  rak.rakPipeline().addFirst("bedrocknative-rak-trace", trace);
               }

               if (channel instanceof RakClientChannel client) {
                  trace.onSocketConnectFailure(cause -> client.getConnectPromise().tryFailure(cause));
               }
            }

            protected void initSession(BedrockClientSession session) {
               trace.stop();
               BedrockNative.LOGGER.info("{}", trace.summary());
               BedrockClientConnection.guardSplits(session, address);
               BedrockSessionSetup.init(session, identity, address, listener, protocol);
            }
         });
   }

   private static void guardSplits(BedrockClientSession session, InetSocketAddress address) {
      if (session.getPeer().getChannel() instanceof RakChannel rak) {
         ChannelPipeline pipeline = rak.rakPipeline();
         if (pipeline.get("rak-session-codec") != null) {
            pipeline.addBefore("rak-session-codec", "bn-split-guard", new RakSplitGuard(address.getHostString() + ":" + address.getPort()));
         } else {
            BedrockNative.LOGGER.warn("RakNet split guard not installed: the RakNet pipeline has {}", pipeline.names());
         }
      } else {
         BedrockNative.LOGGER.warn("RakNet split guard not installed: the Bedrock channel is a {}", session.getPeer().getChannel().getClass().getName());
      }
   }

   static final class VanillaHandshakeChannels implements ChannelFactory<RakClientChannel> {
      private final ChannelFactory<RakClientChannel> channels = RakChannelFactory.client(NioDatagramChannel.class);
      private final boolean vanilla;

      VanillaHandshakeChannels() {
         this(!BedrockClientConnection.legacyHandshake());
      }

      VanillaHandshakeChannels(boolean vanilla) {
         this.vanilla = vanilla;
      }

      public RakClientChannel newChannel() {
         RakClientChannel channel = (RakClientChannel)this.channels.newChannel();
         if (this.vanilla) {
            boolean dontFragment;
            try {
               dontFragment = channel.config().setOption(RakChannelOption.RAK_IP_DONT_FRAGMENT, true);
            } catch (RuntimeException | LinkageError e) {
               dontFragment = false;
            }

            if (dontFragment) {
               channel.config().setOption(RakChannelOption.RAK_MTU_SIZES, (Integer[])BedrockClientConnection.VANILLA_MTU_SIZES.clone());
            } else {
               BedrockNative.LOGGER.debug("RakNet: Don't Fragment unavailable, MTU probes stay 1400, 1200, 576");
            }
         }

         return channel;
      }
   }
}
