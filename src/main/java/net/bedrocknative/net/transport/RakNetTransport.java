package net.bedrocknative.net.transport;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.net.client.BedrockClientConnection;
import net.bedrocknative.net.client.BedrockGameListener;
import net.bedrocknative.net.client.RakHandshakeTrace;

public final class RakNetTransport implements BedrockTransport {
   public static final RakNetTransport INSTANCE = new RakNetTransport();

   private RakNetTransport() {
   }

   @Override
   public TransportKind kind() {
      return TransportKind.RAKNET;
   }

   @Override
   public ChannelFuture connect(EventLoopGroup group, ServerTarget target, ClientIdentity identity, BedrockGameListener listener, ConnectAttempt attempt) {
      RakHandshakeTrace trace = attempt.rakTrace() != null ? attempt.rakTrace() : new RakHandshakeTrace();
      return BedrockClientConnection.connect(group, target.address(), identity, listener, trace, attempt.localAddress(), attempt.protocol());
   }
}
