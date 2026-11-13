package net.bedrocknative.world.chunk.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;

public final class BlobTracker {
   static final int MAX_LIST = 1024;
   static final long RETRY_NANOS = 6000000000L;
   static final int MAX_ATTEMPTS = 3;
   private final BlobStore store;
   private final LongSupplier nanos;
   private final Object lock = new Object();
   private final Map<Long, BlobTracker.Outstanding> outstanding = new HashMap<>();
   private final LongArrayList naks = new LongArrayList();
   private final LongArrayList acks = new LongArrayList();
   private long hits;
   private long misses;
   private long received;
   private long receivedBytes;
   private long invalid;
   private long unexpected;
   private long retries;
   private long giveUps;
   private boolean warnedInvalid;

   public BlobTracker(BlobStore store, LongSupplier nanos) {
      this.store = store;
      this.nanos = nanos;
   }

   public byte[] lookup(long hash, BlobTracker.Waiter waiter) {
      synchronized (this.lock) {
         byte[] data = this.store.get(hash);
         if (data != null) {
            this.acks.add(hash);
            this.hits++;
            return data;
         }

         BlobTracker.Outstanding pending = this.outstanding.get(hash);
         if (pending == null) {
            pending = new BlobTracker.Outstanding();
            pending.sentAt = this.nanos.getAsLong();
            this.outstanding.put(hash, pending);
            this.naks.add(hash);
            this.misses++;
         }

         pending.waiters.add(waiter);
         return null;
      }
   }

   public void onMissResponse(ClientCacheMissResponsePacket packet) {
      List<Runnable> deliveries = new ArrayList<>();
      synchronized (this.lock) {
         ObjectIterator var4 = packet.getBlobs().long2ObjectEntrySet().iterator();

         while (var4.hasNext()) {
            Entry<ByteBuf> entry = (Entry<ByteBuf>)var4.next();
            long hash = entry.getLongKey();
            BlobTracker.Outstanding pending = this.outstanding.get(hash);
            if (pending == null) {
               this.unexpected++;
            } else {
               ByteBuf buffer = (ByteBuf)entry.getValue();
               byte[] data = ByteBufUtil.getBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
               if (XxHash64.hash(data) != hash) {
                  this.invalid++;
                  if (!this.warnedInvalid) {
                     this.warnedInvalid = true;
                     BedrockNative.LOGGER
                        .warn("Blob cache: the server sent {} bytes for blob {} that do not hash to it; ignored", data.length, Long.toUnsignedString(hash));
                  }
               } else {
                  this.outstanding.remove(hash);
                  this.store.put(hash, data);
                  this.received++;
                  this.receivedBytes += data.length;

                  for (BlobTracker.Waiter waiter : pending.waiters) {
                     deliveries.add(() -> waiter.onBlob(hash, data));
                  }
               }
            }
         }
      }

      deliveries.forEach(Runnable::run);
   }

   public void tick() {
      List<Runnable> giveUpCalls = null;
      synchronized (this.lock) {
         if (this.outstanding.isEmpty()) {
            return;
         }

         long now = this.nanos.getAsLong();
         Iterator<java.util.Map.Entry<Long, BlobTracker.Outstanding>> it = this.outstanding.entrySet().iterator();

         while (it.hasNext()) {
            java.util.Map.Entry<Long, BlobTracker.Outstanding> entry = it.next();
            BlobTracker.Outstanding pending = entry.getValue();
            if (now - pending.sentAt >= 6000000000L) {
               long hash = entry.getKey();
               if (pending.attempts < 3) {
                  pending.attempts++;
                  pending.sentAt = now;
                  this.naks.add(hash);
                  this.retries++;
               } else {
                  it.remove();
                  this.giveUps++;
                  if (giveUpCalls == null) {
                     giveUpCalls = new ArrayList<>();
                  }

                  for (BlobTracker.Waiter waiter : pending.waiters) {
                     giveUpCalls.add(() -> waiter.onGiveUp(hash));
                  }
               }
            }
         }
      }

      if (giveUpCalls != null) {
         giveUpCalls.forEach(Runnable::run);
      }
   }

   public List<ClientCacheBlobStatusPacket> drainStatus() {
      synchronized (this.lock) {
         if (this.naks.isEmpty() && this.acks.isEmpty()) {
            return List.of();
         }

         List<ClientCacheBlobStatusPacket> packets = new ArrayList<>();
         int nakAt = 0;
         int ackAt = 0;

         while (nakAt < this.naks.size() || ackAt < this.acks.size()) {
            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            int nakTo = Math.min(this.naks.size(), nakAt + 1024);
            int ackTo = Math.min(this.acks.size(), ackAt + 1024);

            for (int i = nakAt; i < nakTo; i++) {
               packet.getNaks().add(this.naks.getLong(i));
            }

            for (int i = ackAt; i < ackTo; i++) {
               packet.getAcks().add(this.acks.getLong(i));
            }

            nakAt = nakTo;
            ackAt = ackTo;
            packets.add(packet);
         }

         this.naks.clear();
         this.acks.clear();
         return packets;
      }
   }

   public int outstandingCount() {
      synchronized (this.lock) {
         return this.outstanding.size();
      }
   }

   public BlobTracker.Stats stats() {
      synchronized (this.lock) {
         return new BlobTracker.Stats(this.hits, this.misses, this.received, this.receivedBytes, this.invalid, this.unexpected, this.retries, this.giveUps);
      }
   }

   private static final class Outstanding {
      final List<BlobTracker.Waiter> waiters = new ArrayList<>(1);
      long sentAt;
      int attempts = 1;
   }

   public record Stats(long hits, long misses, long received, long receivedBytes, long invalid, long unexpected, long retries, long giveUps) {
   }

   public interface Waiter {
      void onBlob(long var1, byte[] var3);

      void onGiveUp(long var1);
   }
}
