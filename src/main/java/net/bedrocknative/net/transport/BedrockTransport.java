package net.bedrocknative.net.transport;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.net.client.BedrockGameListener;

public interface BedrockTransport {
   TransportKind kind();

   ChannelFuture connect(EventLoopGroup var1, ServerTarget var2, ClientIdentity var3, BedrockGameListener var4, ConnectAttempt var5);
}
