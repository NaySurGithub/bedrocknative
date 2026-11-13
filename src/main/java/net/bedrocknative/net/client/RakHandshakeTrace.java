package net.bedrocknative.net.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class RakHandshakeTrace extends ChannelDuplexHandler {
   public static final String NAME = "bedrocknative-rak-trace";
   static final int OPEN_CONNECTION_REQUEST_1 = 5;
   static final int OPEN_CONNECTION_REPLY_1 = 6;
   static final int OPEN_CONNECTION_REQUEST_2 = 7;
   static final int OPEN_CONNECTION_REPLY_2 = 8;
   static final int ALREADY_CONNECTED = 18;
   static final int NO_FREE_INCOMING_CONNECTIONS = 20;
   static final int INCOMPATIBLE_PROTOCOL_VERSION = 25;
   static final int IP_RECENTLY_CONNECTED = 26;
   static final int UNCONNECTED_PONG = 28;
   private static final int IPV4_HEADER = 20;
   private static final int IPV6_HEADER = 40;
   private static final int UDP_HEADER = 8;
   private final long start = System.nanoTime();
   private final Map<Integer, Integer> request1ByMtu = new TreeMap<>((a, b) -> b - a);
   private volatile int request1;
   private volatile int request2;
   private volatile int reply1;
   private volatile long reply1Millis = -1L;
   private volatile int reply1Mtu;
   private volatile boolean reply1Security;
   private volatile int reply2;
   private volatile long reply2Millis = -1L;
   private volatile int reply2Mtu;
   private volatile int datagramsOut;
   private volatile int datagramsIn;
   private volatile int pongs;
   @Nullable
   private volatile String refusal;
   private volatile int otherIn;
   private volatile int lastOtherId = -1;
   @Nullable
   private volatile InetSocketAddress remote;
   @Nullable
   private volatile ChannelHandlerContext context;
   @Nullable
   private volatile Throwable socketError;
   @Nullable
   private volatile Consumer<Throwable> socketFailure;

   public void onSocketConnectFailure(Consumer<Throwable> action) {
      this.socketFailure = action;
      Throwable failed = this.socketError;
      if (failed != null) {
         action.accept(failed);
      }
   }

   public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
      promise.addListener(done -> {
         if (!done.isSuccess()) {
            this.socketError = done.cause();
            Consumer<Throwable> action = this.socketFailure;
            if (action != null) {
               action.accept(done.cause());
            }
         }
      });
      ctx.connect(remoteAddress, localAddress, promise);
   }

   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof DatagramPacket packet) {
         ByteBuf content = (ByteBuf)packet.content();
         this.inbound(content, (InetSocketAddress)packet.sender());
         if (content.isReadable() && content.getUnsignedByte(content.readerIndex()) == 28) {
            packet.release();
            return;
         }
      }

      ctx.fireChannelRead(msg);
   }

   public int pongs() {
      return this.pongs;
   }

   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      if (msg instanceof DatagramPacket packet) {
         this.outbound((ByteBuf)packet.content(), (InetSocketAddress)packet.recipient());
      }

      ctx.write(msg, promise);
   }

   void outbound(ByteBuf content, @Nullable InetSocketAddress to) {
      if (content.isReadable()) {
         if (to != null) {
            this.remote = to;
         }

         int id = content.getUnsignedByte(content.readerIndex());
         if (id == 5) {
            this.request1++;
            int headers = (to != null && to.getAddress() instanceof Inet6Address ? 40 : 20) + 8;
            synchronized (this.request1ByMtu) {
               this.request1ByMtu.merge(content.readableBytes() + headers, 1, Integer::sum);
            }
         } else if (id == 7) {
            this.request2++;
         } else if ((id & 128) != 0) {
            this.datagramsOut++;
         }
      }
   }

   void inbound(ByteBuf content, @Nullable InetSocketAddress from) {
      if (content.isReadable()) {
         int at = content.readerIndex();
         int id = content.getUnsignedByte(at);
         switch (id) {
            case 6:
               this.reply1++;
               if (this.reply1Millis < 0L) {
                  this.reply1Millis = this.millis();
               }

               if (content.readableBytes() >= 28) {
                  boolean security = content.getBoolean(at + 25);
                  this.reply1Security = security;
                  int mtuAt = at + 26 + (security ? 4 : 0);
                  if (mtuAt + 2 <= content.writerIndex()) {
                     this.reply1Mtu = content.getUnsignedShort(mtuAt);
                  }
               }
               break;
            case 7:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 19:
            case 21:
            case 22:
            case 23:
            case 24:
            case 27:
            default:
               if ((id & 128) != 0) {
                  this.datagramsIn++;
               } else {
                  this.otherIn++;
                  this.lastOtherId = id;
               }
               break;
            case 8:
               this.reply2++;
               if (this.reply2Millis < 0L) {
                  this.reply2Millis = this.millis();
               }

               if (content.readableBytes() > 25) {
                  int addressAt = at + 25;
                  int addressLength = content.getUnsignedByte(addressAt) == 6 ? 29 : 7;
                  int mtuAt = addressAt + addressLength;
                  if (mtuAt + 2 <= content.writerIndex()) {
                     this.reply2Mtu = content.getUnsignedShort(mtuAt);
                  }
               }
               break;
            case 18:
               this.refusal = "already connected";
               break;
            case 20:
               this.refusal = "no free incoming connections";
               break;
            case 25:
               this.refusal = "incompatible RakNet protocol" + (content.readableBytes() > 1 ? " (server's " + content.getUnsignedByte(at + 1) + ")" : "");
               break;
            case 26:
               this.refusal = "address recently connected";
               break;
            case 28:
               this.pongs++;
         }
      }
   }

   public void handlerAdded(ChannelHandlerContext ctx) {
      this.context = ctx;
   }

   public void stop() {
      ChannelHandlerContext ctx = this.context;
      if (ctx != null) {
         ctx.executor().execute(() -> {
            if (ctx.pipeline().context(this) != null) {
               ctx.pipeline().remove(this);
            }
         });
      }
   }

   public boolean answered() {
      return this.reply1 > 0 || this.reply2 > 0 || this.datagramsIn > 0 || this.refusal != null;
   }

   public String summary() {
      StringBuilder text = new StringBuilder("RakNet handshake");
      InetSocketAddress to = this.remote;
      if (to != null) {
         text.append(" with ").append(to.getAddress() == null ? to.getHostString() : to.getAddress().getHostAddress()).append(':').append(to.getPort());
      }

      Throwable failed = this.socketError;
      if (failed != null && this.request1 == 0) {
         return text.append(": the socket could not connect (").append(failed).append(')').toString();
      } else {
         text.append(String.format(Locale.ROOT, " over %.1f s: OpenConnectionRequest1 x%d", this.millis() / 1000.0, this.request1));
         synchronized (this.request1ByMtu) {
            if (!this.request1ByMtu.isEmpty()) {
               text.append(" (MTU");
               boolean first = true;

               for (Entry<Integer, Integer> probe : this.request1ByMtu.entrySet()) {
                  text.append(first ? " " : ", ").append(probe.getKey()).append(" x").append(probe.getValue());
                  first = false;
               }

               text.append(')');
            }
         }

         if (this.reply1 == 0) {
            text.append(", no OpenConnectionReply1");
         } else {
            text.append(", OpenConnectionReply1 x")
               .append(this.reply1)
               .append(" first after ")
               .append(this.reply1Millis)
               .append(" ms (MTU ")
               .append(this.reply1Mtu)
               .append(this.reply1Security ? ", with a cookie" : "")
               .append(')');
            text.append(", OpenConnectionRequest2 x").append(this.request2);
            text.append(
               this.reply2 == 0 ? ", no OpenConnectionReply2" : ", OpenConnectionReply2 after " + this.reply2Millis + " ms (MTU " + this.reply2Mtu + ")"
            );
         }

         if (this.datagramsOut > 0 || this.datagramsIn > 0) {
            text.append(", session datagrams sent ").append(this.datagramsOut).append(" received ").append(this.datagramsIn);
         }

         if (this.refusal != null) {
            text.append(", refused: ").append(this.refusal);
         }

         if (this.pongs > 0) {
            text.append(", pongs ").append(this.pongs);
         }

         if (this.otherIn > 0) {
            text.append(", ").append(this.otherIn).append(" other packets (last id 0x").append(Integer.toHexString(this.lastOtherId)).append(')');
         }

         return text.toString();
      }
   }

   private long millis() {
      return (System.nanoTime() - this.start) / 1000000L;
   }
}
