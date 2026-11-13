package net.bedrocknative.net.nethernet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.MessageToMessageCodec;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;

@Sharable
public final class NetherNetFrameCodec extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {
   public static final String NAME = "nethernet-frame-codec";
   public static final NetherNetFrameCodec INSTANCE = new NetherNetFrameCodec();

   private NetherNetFrameCodec() {
   }

   protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper batch, List<Object> out) {
      if (batch.getCompressed() == null) {
         throw new IllegalStateException("Bedrock batch was not compressed");
      }

      out.add(batch.getCompressed().retainedSlice());
   }

   protected void decode(ChannelHandlerContext ctx, ByteBuf message, List<Object> out) {
      if (message.isReadable()) {
         out.add(BedrockBatchWrapper.newInstance(message.retainedSlice(), null));
      }
   }
}
