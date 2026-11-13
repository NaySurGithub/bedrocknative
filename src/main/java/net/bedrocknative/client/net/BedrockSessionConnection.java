package net.bedrocknative.client.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

public final class BedrockSessionConnection extends Connection {
   private final EmbeddedChannel embedded;
   private final SocketAddress serverAddress;
   private final Runnable onClose;

   public BedrockSessionConnection(SocketAddress serverAddress, final Consumer<Packet<?>> outbound, Runnable onClose) {
      super(PacketFlow.CLIENTBOUND);
      this.serverAddress = serverAddress;
      this.onClose = onClose;
      this.embedded = new EmbeddedChannel(new ChannelHandler[]{new ChannelOutboundHandlerAdapter() {
         {
            Objects.requireNonNull(BedrockSessionConnection.this);
         }

         public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof Packet<?> packet) {
               outbound.accept(packet);
            }

            promise.trySuccess();
         }
      }, this});
   }

   public void tick() {
      this.embedded.runPendingTasks();
      super.tick();
   }

   public void disconnect(DisconnectionDetails details) {
      boolean wasConnected = this.isConnected();
      super.disconnect(details);
      if (wasConnected) {
         this.onClose.run();
      }
   }

   public SocketAddress getRemoteAddress() {
      return this.serverAddress;
   }
}
