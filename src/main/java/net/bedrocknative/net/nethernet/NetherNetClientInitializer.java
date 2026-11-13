package net.bedrocknative.net.nethernet;

import io.netty.channel.Channel;
import java.util.function.Consumer;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;

public final class NetherNetClientInitializer extends BedrockClientInitializer {
   private final Consumer<BedrockClientSession> initSession;

   public NetherNetClientInitializer(Consumer<BedrockClientSession> initSession) {
      this.initSession = initSession;
   }

   @Override
   protected void preInitChannel(Channel channel) {
      channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.SERVER_BOUND);
      channel.pipeline().addLast("nethernet-frame-codec", NetherNetFrameCodec.INSTANCE);
      channel.pipeline()
         .addLast("compression-codec", new CompressionCodec(BedrockChannelInitializer.getCompression(PacketCompressionAlgorithm.ZLIB, 11, true), false));
   }

   @Override
   protected void initPacketCodec(Channel channel) {
      channel.pipeline().addLast("bedrock-packet-codec", new BedrockPacketCodec_v3());
   }

   @Override
   protected BedrockPeer createPeer(Channel channel) {
      return new NetherNetBedrockPeer(channel, (x$0, x$1) -> this.createSession(x$0, x$1));
   }

   protected void initSession(BedrockClientSession session) {
      this.initSession.accept(session);
   }
}
