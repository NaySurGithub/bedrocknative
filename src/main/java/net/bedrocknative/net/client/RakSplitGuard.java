package net.bedrocknative.net.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.netty.channel.raknet.packet.EncapsulatedPacket;
import org.cloudburstmc.netty.channel.raknet.packet.RakDatagramPacket;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;
import org.cloudburstmc.netty.util.RoundRobinArray;

public final class RakSplitGuard extends ChannelInboundHandlerAdapter {
   public static final String NAME = "bn-split-guard";
   static final int ID_SPACE = 65536;
   static final int OLD_SLOTS = 256;
   private final String server;
   private final Map<Integer, RakSplitGuard.LiveSet> live = new HashMap<>();
   private final int[] liveInOldSlot = new int[256];
   private final BitSet seenReliable = new BitSet();
   private boolean tableChecked;
   private boolean enlarged;
   private long datagrams;
   private long fragments;
   private long sets;
   private long finished;
   private long retransmitted;
   private long sharedOldSlot;
   private long sameIdTwice;
   private int mostAtOnce;

   public RakSplitGuard(String server) {
      this.server = server;
   }

   static boolean enlargeTable(RakSessionCodec codec) {
      try {
         Field field = RakSessionCodec.class.getDeclaredField("splitPackets");
         field.setAccessible(true);
         field.set(codec, new RoundRobinArray(65536));
         return true;
      } catch (RuntimeException | ReflectiveOperationException var2) {
         BedrockNative.LOGGER.warn("RakNet split table not enlarged (big packets can be mixed up when 256 split packets overlap)", var2);
         return false;
      }
   }

   public void handlerAdded(ChannelHandlerContext ctx) {
      ctx.executor().schedule(this::summary, 30L, TimeUnit.SECONDS);
   }

   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof RakDatagramPacket datagram) {
         if (!this.tableChecked) {
            this.tableChecked = true;
            if (ctx.pipeline().get("rak-session-codec") instanceof RakSessionCodec codec) {
               this.enlarged = enlargeTable(codec);
            }
         }

         this.datagrams++;

         for (EncapsulatedPacket frame : datagram.getPackets()) {
            this.count(frame);
         }
      }

      ctx.fireChannelRead(msg);
   }

   private void count(EncapsulatedPacket frame) {
      if (frame.getReliability().isReliable()) {
         int index = frame.getReliabilityIndex();
         if (this.seenReliable.get(index)) {
            this.retransmitted++;
            return;
         }

         this.seenReliable.set(index);
      }

      if (frame.isSplit()) {
         this.fragments++;
         int id = frame.getPartId();
         RakSplitGuard.LiveSet set = this.live.get(id);
         if (set == null) {
            set = new RakSplitGuard.LiveSet(frame.getPartCount());
            this.live.put(id, set);
            this.sets++;
            if (this.liveInOldSlot[id & 0xFF]++ > 0) {
               this.sharedOldSlot++;
            }

            this.mostAtOnce = Math.max(this.mostAtOnce, this.live.size());
         } else if (set.parts != frame.getPartCount() || set.received.get(frame.getPartIndex())) {
            this.sameIdTwice++;
            BedrockNative.LOGGER
               .warn(
                  "RakNet split id {} is in use by an unfinished packet ({} of {} parts, {} ms) and a new one of {} parts starts",
                  new Object[]{id, set.received.cardinality(), set.parts, System.currentTimeMillis() - set.born, frame.getPartCount()}
               );
         }

         set.received.set(frame.getPartIndex());
         if (set.received.cardinality() >= set.parts) {
            this.live.remove(id);
            this.liveInOldSlot[id & 0xFF]--;
            this.finished++;
         }
      }
   }

   long sharedOldSlot() {
      return this.sharedOldSlot;
   }

   long finished() {
      return this.finished;
   }

   int unfinished() {
      return this.live.size();
   }

   long sameIdTwice() {
      return this.sameIdTwice;
   }

   long retransmitted() {
      return this.retransmitted;
   }

   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      this.summary();
      ctx.fireChannelInactive();
   }

   public void summary() {
      BedrockNative.LOGGER
         .info(
            "RakNet split packets on {}: {} datagrams, {} fragments, {} sets ({} finished, {} unfinished), most at once {}, {} shared a slot of the library's {}-slot table (table {} slots), {} retransmitted frames dropped, id reused while unfinished {}",
            new Object[]{
               this.server,
               this.datagrams,
               this.fragments,
               this.sets,
               this.finished,
               this.live.size(),
               this.mostAtOnce,
               this.sharedOldSlot,
               256,
               this.enlarged ? 65536 : 256,
               this.retransmitted,
               this.sameIdTwice
            }
         );
   }

   private static final class LiveSet {
      final int parts;
      final BitSet received;
      final long born = System.currentTimeMillis();

      LiveSet(int parts) {
         this.parts = parts;
         this.received = new BitSet(parts);
      }
   }
}
