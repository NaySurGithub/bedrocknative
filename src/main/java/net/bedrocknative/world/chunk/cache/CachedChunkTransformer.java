package net.bedrocknative.world.chunk.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.HeightMapDataType;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket;

public final class CachedChunkTransformer {
   static final int MAX_RESENDS = 2;
   static final long RESEND_FORGET_NANOS = 120000000000L;
   private final BlobTracker tracker;
   private final CachedChunkTransformer.Output output;
   private final LongSupplier nanos;
   private final ColumnHolds holds;
   private final Map<String, CachedChunkTransformer.Resend> resent = new ConcurrentHashMap<>();
   private volatile Consumer<BedrockPacket> sender = packet -> {};
   private final AtomicInteger levelChunks = new AtomicInteger();
   private final AtomicInteger subChunkPackets = new AtomicInteger();
   private final AtomicInteger subChunksResent = new AtomicInteger();
   private final AtomicInteger subChunksLost = new AtomicInteger();
   private final AtomicInteger columnsLost = new AtomicInteger();

   public CachedChunkTransformer(BlobStore store, LongSupplier nanos, CachedChunkTransformer.Output output) {
      this.tracker = new BlobTracker(store, nanos);
      this.output = output;
      this.nanos = nanos;
      this.holds = new ColumnHolds(output::execute);
   }

   public boolean deferIfHeld(int chunkX, int chunkZ, Runnable task) {
      return this.holds.parkIfHeld(ColumnHolds.key(chunkX, chunkZ), task);
   }

   public void onLevelChunk(LevelChunkPacket packet) {
      this.levelChunks.incrementAndGet();
      new CachedChunkTransformer.LevelChunkUnit(packet).begin();
   }

   public void onSubChunks(SubChunkPacket packet) {
      this.subChunkPackets.incrementAndGet();
      new CachedChunkTransformer.SubChunkUnit(packet).begin();
   }

   public void onMissResponse(ClientCacheMissResponsePacket packet) {
      this.tracker.onMissResponse(packet);
   }

   public List<ClientCacheBlobStatusPacket> drainStatus() {
      return this.tracker.drainStatus();
   }

   public void tick(Consumer<BedrockPacket> send) {
      this.sender = send;
      this.tracker.tick();
      long now = this.nanos.getAsLong();
      this.resent.values().removeIf(resend -> now - resend.at() >= 120000000000L);
   }

   public CachedChunkTransformer.Summary summary() {
      return new CachedChunkTransformer.Summary(
         this.tracker.stats(),
         this.tracker.outstandingCount(),
         this.levelChunks.get(),
         this.subChunkPackets.get(),
         this.subChunksResent.get(),
         this.subChunksLost.get(),
         this.columnsLost.get()
      );
   }

   public int outstanding() {
      return this.tracker.outstandingCount();
   }

   private static List<CachedChunkTransformer.Entry> copyEntries(SubChunkPacket packet) {
      List<CachedChunkTransformer.Entry> entries = new ArrayList<>(packet.getSubChunks().size());

      for (SubChunkData sub : packet.getSubChunks()) {
         entries.add(
            new CachedChunkTransformer.Entry(
               sub.getPosition(), sub.getResult(), sub.getHeightMapType(), sub.getRenderHeightMapType(), bytes(sub.getData()), sub.getBlobId()
            )
         );
      }

      return entries;
   }

   private static byte[] bytes(ByteBuf buffer) {
      return buffer == null ? new byte[0] : ByteBufUtil.getBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
   }

   private record Entry(
      Vector3i position, SubChunkRequestResult result, HeightMapDataType heightMapType, HeightMapDataType renderHeightMapType, byte[] data, Long blobId
   ) {
      boolean cached() {
         return this.result == SubChunkRequestResult.SUCCESS && this.blobId != null;
      }
   }

   private final class LevelChunkUnit extends CachedChunkTransformer.Unit {
      private final int chunkX;
      private final int chunkZ;
      private final int dimension;
      private final int subChunksLength;
      private final boolean requestSubChunks;
      private final int subChunkLimit;
      private final boolean clientBiomeUpdate;
      private final long[] references;
      private final byte[] inline;

      LevelChunkUnit(LevelChunkPacket packet) {
         this(packet, packet.getBlobIds().toLongArray());
      }

      private LevelChunkUnit(LevelChunkPacket packet, long[] references) {
         Objects.requireNonNull(CachedChunkTransformer.this);
         super(references);
         this.references = references;
         this.chunkX = packet.getChunkX();
         this.chunkZ = packet.getChunkZ();
         this.dimension = packet.getDimension();
         this.subChunksLength = packet.getSubChunksLength();
         this.requestSubChunks = packet.isRequestSubChunks();
         this.subChunkLimit = packet.getSubChunkLimit();
         this.clientBiomeUpdate = packet.isClientBiomeUpdate();
         this.inline = CachedChunkTransformer.bytes(packet.getData());
      }

      @Override
      long[] columns() {
         return this.requestSubChunks ? new long[0] : new long[]{ColumnHolds.key(this.chunkX, this.chunkZ)};
      }

      @Override
      void complete(Map<Long, byte[]> blobs, Set<Long> failed) {
         ByteArrayOutputStream data = new ByteArrayOutputStream();
         if (failed.isEmpty()) {
            for (long reference : this.references) {
               data.writeBytes(blobs.get(reference));
            }
         } else {
            if (!this.requestSubChunks) {
               CachedChunkTransformer.this.columnsLost.incrementAndGet();
               BedrockNative.LOGGER
                  .warn(
                     "Blob cache: column {}, {} dropped; {} of its {} blobs never arrived; its sub-chunks are requested one by one",
                     new Object[]{this.chunkX, this.chunkZ, failed.size(), this.references.length}
                  );
               CachedChunkTransformer.this.output.columnLost(this.dimension, this.chunkX, this.chunkZ, this.subChunksLength);
               return;
            }

            BedrockNative.LOGGER
               .warn("Blob cache: the biomes of column {}, {} never arrived; its sub-chunks are requested without them", this.chunkX, this.chunkZ);
         }

         data.writeBytes(this.inline);
         LevelChunkPacket plain = new LevelChunkPacket();
         plain.setChunkX(this.chunkX);
         plain.setChunkZ(this.chunkZ);
         plain.setDimension(this.dimension);
         plain.setSubChunksLength(this.subChunksLength);
         plain.setRequestSubChunks(this.requestSubChunks);
         plain.setSubChunkLimit(this.subChunkLimit);
         plain.setClientBiomeUpdate(this.clientBiomeUpdate);
         plain.setCachingEnabled(false);
         plain.setData(Unpooled.wrappedBuffer(data.toByteArray()));
         CachedChunkTransformer.this.output.levelChunk(plain);
      }
   }

   public interface Output {
      void levelChunk(LevelChunkPacket var1);

      void subChunks(SubChunkPacket var1);

      default void columnLost(int dimension, int chunkX, int chunkZ, int subChunks) {
      }

      default void execute(Runnable task) {
         task.run();
      }

      default void execute(long column, Runnable task) {
         this.execute(task);
      }
   }

   private record Resend(int count, long at) {
   }

   private final class SubChunkUnit extends CachedChunkTransformer.Unit {
      private final int dimension;
      private final Vector3i center;
      private final List<CachedChunkTransformer.Entry> entries;

      SubChunkUnit(SubChunkPacket packet) {
         this(packet, CachedChunkTransformer.copyEntries(packet));
      }

      private SubChunkUnit(SubChunkPacket packet, List<CachedChunkTransformer.Entry> entries) {
         Objects.requireNonNull(CachedChunkTransformer.this);
         super(entries.stream().filter(CachedChunkTransformer.Entry::cached).mapToLong(CachedChunkTransformer.Entry::blobId).toArray());
         this.dimension = packet.getDimension();
         this.center = packet.getCenterPosition();
         this.entries = entries;
      }

      private String key(CachedChunkTransformer.Entry entry) {
         return this.dimension
            + ":"
            + (this.center.getX() + entry.position().getX())
            + ","
            + (this.center.getY() + entry.position().getY())
            + ","
            + (this.center.getZ() + entry.position().getZ());
      }

      @Override
      long[] columns() {
         return this.entries
            .stream()
            .filter(CachedChunkTransformer.Entry::cached)
            .mapToLong(entry -> ColumnHolds.key(this.center.getX() + entry.position().getX(), this.center.getZ() + entry.position().getZ()))
            .distinct()
            .toArray();
      }

      @Override
      void complete(Map<Long, byte[]> blobs, Set<Long> failed) {
         SubChunkPacket plain = new SubChunkPacket();
         plain.setDimension(this.dimension);
         plain.setCenterPosition(this.center);
         plain.setCacheEnabled(false);

         for (CachedChunkTransformer.Entry entry : this.entries) {
            byte[] data = entry.data();
            if (!entry.cached()) {
               if (!CachedChunkTransformer.this.resent.isEmpty()) {
                  CachedChunkTransformer.this.resent.remove(this.key(entry));
               }
            } else {
               String key = this.key(entry);
               if (failed.contains(entry.blobId())) {
                  this.lose(key, entry.position());
                  continue;
               }

               CachedChunkTransformer.this.resent.remove(key);
               byte[] blob = blobs.get(entry.blobId());
               data = new byte[blob.length + (data == null ? 0 : data.length)];
               System.arraycopy(blob, 0, data, 0, blob.length);
               if (entry.data() != null) {
                  System.arraycopy(entry.data(), 0, data, blob.length, entry.data().length);
               }
            }

            SubChunkData sub = new SubChunkData();
            sub.setPosition(entry.position());
            sub.setResult(entry.result());
            sub.setHeightMapType(entry.heightMapType());
            sub.setRenderHeightMapType(entry.renderHeightMapType());
            sub.setData(Unpooled.wrappedBuffer(data == null ? new byte[0] : data));
            sub.setCacheEnabled(false);
            plain.getSubChunks().add(sub);
         }

         if (!plain.getSubChunks().isEmpty()) {
            CachedChunkTransformer.this.output.subChunks(plain);
         }
      }

      private void lose(String key, Vector3i offset) {
         long now = CachedChunkTransformer.this.nanos.getAsLong();
         CachedChunkTransformer.Resend previous = CachedChunkTransformer.this.resent.get(key);
         int asked = previous != null && now - previous.at() < 120000000000L ? previous.count() : 0;
         if (asked < 2) {
            CachedChunkTransformer.this.resent.put(key, new CachedChunkTransformer.Resend(asked + 1, now));
            CachedChunkTransformer.this.subChunksResent.incrementAndGet();
            SubChunkRequestPacket request = new SubChunkRequestPacket();
            request.setDimension(this.dimension);
            request.setSubChunkPosition(this.center);
            request.getPositionOffsets().add(offset);
            CachedChunkTransformer.this.sender.accept(request);
         } else {
            CachedChunkTransformer.this.subChunksLost.incrementAndGet();
            CachedChunkTransformer.this.resent.remove(key);
            BedrockNative.LOGGER
               .warn(
                  "Blob cache: sub-chunk {} (dimension {}) lost; its blob never arrived, also after asking for it {} times",
                  new Object[]{key, this.dimension, asked}
               );
         }
      }
   }

   public record Summary(
      BlobTracker.Stats blobs, int outstanding, int levelChunks, int subChunkPackets, int subChunksResent, int subChunksLost, int columnsLost
   ) {
   }

   private abstract class Unit implements BlobTracker.Waiter {
      private final long[] references;
      private final Map<Long, byte[]> blobs;
      private final Set<Long> failed;
      private int remaining;
      private long[] heldColumns;

      Unit(long[] references) {
         Objects.requireNonNull(CachedChunkTransformer.this);
         super();
         this.blobs = new HashMap<>();
         this.failed = new HashSet<>();
         this.heldColumns = new long[0];
         this.references = references;
      }

      final synchronized void begin() {
         this.remaining = this.references.length;

         for (long reference : this.references) {
            byte[] data = CachedChunkTransformer.this.tracker.lookup(reference, this);
            if (data != null) {
               this.blobs.put(reference, data);
               this.remaining--;
            }
         }

         if (this.remaining == 0) {
            this.complete(this.blobs, this.failed);
         } else {
            this.heldColumns = this.columns();

            for (long column : this.heldColumns) {
               CachedChunkTransformer.this.holds.hold(column);
            }
         }
      }

      @Override
      public final synchronized void onBlob(long hash, byte[] data) {
         this.blobs.put(hash, data);
         if (--this.remaining == 0) {
            this.finish();
         }
      }

      @Override
      public final synchronized void onGiveUp(long hash) {
         this.failed.add(hash);
         if (--this.remaining == 0) {
            this.finish();
         }
      }

      // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      private void finish() {
         boolean var13 = false /* VF: Semaphore variable */;

         try {
            var13 = true;
            this.complete(this.blobs, this.failed);
            var13 = false;
         } finally {
            if (var13) {
               for (long column : this.heldColumns) {
                  CachedChunkTransformer.this.holds.release(column);
               }
            }
         }

         for (long column : this.heldColumns) {
            CachedChunkTransformer.this.holds.release(column);
         }
      }

      abstract long[] columns();

      abstract void complete(Map<Long, byte[]> var1, Set<Long> var2);
   }
}
