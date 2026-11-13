package net.bedrocknative.net.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.jspecify.annotations.Nullable;

final class ItemPacketHold extends ChannelInboundHandlerAdapter {
   static final String NAME = "bedrocknative-item-hold";
   private static final Set<Integer> ITEM_PACKETS = Set.of(12, 15, 30, 31, 32, 49, 50, 52, 145);
   static final int MAX_HELD_PACKETS = 8192;
   static final long MAX_HELD_BYTES = 33554432L;
   static final long REGISTRY_GRACE_NANOS = TimeUnit.SECONDS.toNanos(30L);
   private final List<ByteBuf> held = new ArrayList<>();
   private final Consumer<String> onOverflow;
   private final LongSupplier clock;
   @Nullable
   private ChannelHandlerContext context;
   private ItemPacketHold.Mode mode = ItemPacketHold.Mode.HOLDING;
   private long heldBytes;
   private long dropped;
   private long droppedBytes;
   private long startGame = -1L;
   private boolean closed;
   @Nullable
   private ScheduledFuture<?> grace;

   ItemPacketHold(Consumer<String> onOverflow) {
      this(onOverflow, System::nanoTime);
   }

   ItemPacketHold(Consumer<String> onOverflow, LongSupplier clock) {
      this.onOverflow = onOverflow;
      this.clock = clock;
   }

   public void handlerAdded(ChannelHandlerContext ctx) {
      this.context = ctx;
   }

   public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (this.mode != ItemPacketHold.Mode.PASSING && msg instanceof ByteBuf buf && carriesItems(buf)) {
         if (this.mode == ItemPacketHold.Mode.HOLDING && this.graceOver()) {
            this.giveUp("no item registry " + seconds(this.clock.getAsLong() - this.startGame) + " s after StartGame");
         }

         int size = buf.readableBytes();
         if (this.mode == ItemPacketHold.Mode.DROPPING) {
            this.dropped++;
            this.droppedBytes += size;
            ReferenceCountUtil.release(buf);
         } else if (this.held.size() < 8192 && this.heldBytes + size <= 33554432L) {
            ByteBuf copy = ctx.alloc().heapBuffer(size, size);
            copy.writeBytes(buf, buf.readerIndex(), size);
            ReferenceCountUtil.release(buf);
            this.held.add(copy);
            this.heldBytes += size;
         } else {
            String reason = "Server sent too much before its item registry ("
               + (this.held.size() + 1)
               + " item packets, "
               + kilobytes(this.heldBytes + size)
               + " KB"
               + (this.startGame < 0L ? ", before StartGame)" : ", " + seconds(this.clock.getAsLong() - this.startGame) + " s after StartGame)");
            ReferenceCountUtil.release(buf);
            this.discard();
            this.mode = ItemPacketHold.Mode.DROPPING;
            BedrockNative.LOGGER.warn(reason);
            this.onOverflow.accept(reason);
         }
      } else {
         ctx.fireChannelRead(msg);
      }
   }

   void startGame() {
      if (this.startGame < 0L) {
         this.startGame = this.clock.getAsLong();
         ChannelHandlerContext ctx = this.context;
         if (ctx != null && this.mode == ItemPacketHold.Mode.HOLDING) {
            this.grace = ctx.executor().schedule(this::checkGrace, REGISTRY_GRACE_NANOS + TimeUnit.MILLISECONDS.toNanos(100L), TimeUnit.NANOSECONDS);
         }
      }
   }

   void checkGrace() {
      if (!this.closed && this.mode == ItemPacketHold.Mode.HOLDING && this.graceOver()) {
         this.giveUp("no item registry " + seconds(this.clock.getAsLong() - this.startGame) + " s after StartGame");
      }
   }

   void release() {
      ItemPacketHold.Mode was = this.mode;
      this.mode = ItemPacketHold.Mode.PASSING;
      this.cancelGrace();
      if (was == ItemPacketHold.Mode.DROPPING) {
         BedrockNative.LOGGER
            .info(
               "Item registry arrived {}; {} item packets ({} KB) were dropped while it was missing",
               new Object[]{this.sinceStartGame(), this.dropped, kilobytes(this.droppedBytes)}
            );
      }

      if (was == ItemPacketHold.Mode.HOLDING) {
         BedrockNative.LOGGER
            .info(
               "Item packets held until the item registry: {} ({} KB), registry {}",
               new Object[]{this.held.size(), kilobytes(this.heldBytes), this.sinceStartGame()}
            );
         if (this.context != null && !this.held.isEmpty()) {
            List<ByteBuf> packets = new ArrayList<>(this.held);
            this.held.clear();
            this.heldBytes = 0L;
            packets.forEach(this.context::fireChannelRead);
         } else {
            this.discard();
         }
      }
   }

   void registryFailed() {
      if (this.mode == ItemPacketHold.Mode.HOLDING) {
         this.giveUp("the item registry could not be read");
      }
   }

   long dropped() {
      return this.dropped;
   }

   boolean dropping() {
      return this.mode == ItemPacketHold.Mode.DROPPING;
   }

   int heldPackets() {
      return this.held.size();
   }

   long heldBytes() {
      return this.heldBytes;
   }

   public void handlerRemoved(ChannelHandlerContext ctx) {
      this.closed = true;
      this.cancelGrace();
      this.discard();
   }

   private void cancelGrace() {
      ScheduledFuture<?> timer = this.grace;
      this.grace = null;
      if (timer != null) {
         timer.cancel(false);
      }
   }

   public void channelInactive(ChannelHandlerContext ctx) {
      this.closed = true;
      this.cancelGrace();
      this.discard();
      ctx.fireChannelInactive();
   }

   private void giveUp(String why) {
      this.mode = ItemPacketHold.Mode.DROPPING;
      this.dropped = this.dropped + this.held.size();
      this.droppedBytes = this.droppedBytes + this.heldBytes;
      BedrockNative.LOGGER
         .warn(
            "Item packets: {}: {} held ({} KB) dropped; later ones are dropped until a registry arrives",
            new Object[]{why, this.held.size(), kilobytes(this.heldBytes)}
         );
      this.discard();
   }

   private boolean graceOver() {
      return this.startGame >= 0L && this.clock.getAsLong() - this.startGame > REGISTRY_GRACE_NANOS;
   }

   private String sinceStartGame() {
      return this.startGame < 0L ? "before StartGame" : seconds(this.clock.getAsLong() - this.startGame) + " s after StartGame";
   }

   private static boolean carriesItems(ByteBuf buf) {
      try {
         return ITEM_PACKETS.contains(VarInts.readUnsignedInt(buf.duplicate()) & 1023);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   private static String seconds(long nanos) {
      return String.format(Locale.ROOT, "%.1f", nanos / 1.0E9);
   }

   private static long kilobytes(long bytes) {
      return (bytes + 1023L) / 1024L;
   }

   private void discard() {
      this.held.forEach(ReferenceCountUtil::release);
      this.held.clear();
      this.heldBytes = 0L;
   }

   private static enum Mode {
      HOLDING,
      DROPPING,
      PASSING;
   }
}
