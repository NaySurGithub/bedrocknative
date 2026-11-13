package net.bedrocknative.client.world;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.client.net.SessionTimeline;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.world.biome.BiomeClimates;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.chunk.BedrockChunkCodec;
import net.bedrocknative.world.chunk.BlockIdCheck;
import net.bedrocknative.world.chunk.ChunkDiagnostics;
import net.bedrocknative.world.chunk.ChunkFormatStats;
import net.bedrocknative.world.chunk.DimensionRange;
import net.bedrocknative.world.chunk.cache.BlobCaches;
import net.bedrocknative.world.chunk.cache.BlobTracker;
import net.bedrocknative.world.chunk.cache.CachedChunkTransformer;
import net.bedrocknative.world.chunk.stream.ColumnQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.dimension.DimensionType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockChunks {
   private static final int MAX_REQUEST_OFFSETS = 231;
   private static final int MAX_REQUESTS_PER_TICK = 4;
   private final Minecraft minecraft;
   private final ChunkStream stream;
   private final Map<Long, List<PalettedContainer<Holder<Biome>>>> columnBiomes = new ConcurrentHashMap<>();
   private final Int2ObjectMap<String> serverBiomeNames = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap());
   private final Int2ObjectMap<BiomeClimates.Climate> serverBiomeClimates = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap());
   private volatile PalettedContainerFactory containers;
   private BedrockBiomes biomes;
   private volatile ColumnDecoder.Dimension view;
   private int dimension;
   private int minSectionY;
   private int sectionCount;
   private int requestFirstSection;
   private int requestSections;
   private boolean warnedBadData;
   private final Queue<BedrockChunks.PendingColumn> pendingRequests = new ConcurrentLinkedQueue<>();
   private volatile int publishedX;
   private volatile int publishedZ;
   private volatile int publishedRadius = -1;
   private static final int LEVEL_CHUNKS = 0;
   private static final int REQUEST_MODE = 1;
   private static final int OTHER_DIMENSION = 2;
   private static final int FULL_ERRORS = 3;
   private static final int SUB_CHUNK_PACKETS = 4;
   private static final int SUB_SUCCESS = 5;
   private static final int SUB_ALL_AIR = 6;
   private static final int SUB_OTHER = 7;
   private static final int REQUESTS_SENT = 8;
   private static final int OFFSETS_SENT = 9;
   private static final int APPLIED_COLUMNS = 10;
   private final AtomicIntegerArray stats = new AtomicIntegerArray(11);
   private volatile int lastOtherDimension;
   private int statTicks;
   private String lastStats = "";
   private String lastCacheStats = "";
   private volatile CachedChunkTransformer blobCache;
   @Nullable
   private volatile BlockIdCheck blockIdCheck;
   private final ChunkDiagnostics diagnostics = new ChunkDiagnostics();
   private volatile String serverLabel = "?";
   private boolean formatsLogged;
   private volatile boolean toldUnreadable;

   public BedrockChunks(Minecraft minecraft) {
      this.minecraft = minecraft;
      this.stream = new ChunkStream(minecraft, () -> {
         if (this.stats.incrementAndGet(10) == 1) {
            SessionTimeline.mark("first chunk");
         }
      });
   }

   public void serverLabel(String label) {
      this.serverLabel = label;
   }

   public int start(RegistryAccess access, DimensionType dimensionType, int dimension, DimensionRange range) {
      this.containers = PalettedContainerFactory.create(access);
      this.biomes = new BedrockBiomes(access, this.serverBiomeNames, this.serverBiomeClimates);
      this.dimension = dimension;
      this.minSectionY = dimensionType.minY() >> 4;
      this.sectionCount = dimensionType.height() >> 4;
      this.view = new ColumnDecoder.Dimension(
         this.containers, this.biomes, this.minSectionY, this.sectionCount, LevelHeightAccessor.create(dimensionType.minY(), dimensionType.height())
      );
      this.requestFirstSection = range.firstSection();
      this.requestSections = range.sectionCount();
      this.columnBiomes.clear();
      this.pendingRequests.clear();
      this.blockIdCheck = new BlockIdCheck(sessionIds(BlockMappings.get()));
      this.diagnostics.reset(this.serverLabel);
      ChunkFormatStats.SESSION.reset();
      this.formatsLogged = false;
      this.publishedRadius = -1;

      for (int i = 0; i < this.stats.length(); i++) {
         this.stats.set(i, 0);
      }

      this.statTicks = 0;
      this.lastStats = "";
      return this.stream.newGeneration();
   }

   public void activate(int generation) {
      this.stream.activate(generation);
   }

   public void close() {
      this.stream.close();
   }

   public long viewChangeReceived() {
      return this.stream.queue().viewChangeReceived();
   }

   public void viewChangeApplied(long number) {
      this.stream.queue().viewChangeApplied(number);
   }

   public ChunkStream stream() {
      return this.stream;
   }

   public int chunkPacketsReceived() {
      return this.stats.get(0) + this.stats.get(4);
   }

   private void logStats() {
      if (++this.statTicks % 200 == 0 && this.statTicks <= 2400) {
         CachedChunkTransformer.Summary cache = this.blobCacheSummary();
         if (cache != null) {
            BlobTracker.Stats blobs = cache.blobs();
            String now = String.format(
               "%d blobs found in the cache, %d missing, %d received (%d KB), %d outstanding; retries %d, given up %d, invalid %d, unexpected %d; LevelChunk %d, SubChunk %d, sub-chunks asked again %d, lost %d, columns lost %d",
               blobs.hits(),
               blobs.misses(),
               blobs.received(),
               blobs.receivedBytes() >> 10,
               cache.outstanding(),
               blobs.retries(),
               blobs.giveUps(),
               blobs.invalid(),
               blobs.unexpected(),
               cache.levelChunks(),
               cache.subChunkPackets(),
               cache.subChunksResent(),
               cache.subChunksLost(),
               cache.columnsLost()
            );
            if (!now.equals(this.lastCacheStats)) {
               this.lastCacheStats = now;
               BedrockNative.LOGGER.info("Blob cache after {} s: {}", this.statTicks / 20, now);
            }
         }

         String now = String.format(
            "LevelChunk %d (request mode %d, other dimension %d%s, unreadable %d), SubChunk packets %d (success %d, all air %d, other %d), requests sent %d (%d offsets), full columns applied %d; dimension %d, %d sections from %d, %d requested from %d",
            this.stats.get(0),
            this.stats.get(1),
            this.stats.get(2),
            this.stats.get(2) > 0 ? " e.g. " + this.lastOtherDimension : "",
            this.stats.get(3),
            this.stats.get(4),
            this.stats.get(5),
            this.stats.get(6),
            this.stats.get(7),
            this.stats.get(8),
            this.stats.get(9),
            this.stats.get(10),
            this.dimension,
            this.sectionCount,
            this.minSectionY,
            this.requestSections,
            this.requestFirstSection
         );
         if (!now.equals(this.lastStats)) {
            this.lastStats = now;
            BedrockNative.LOGGER.info("Chunks after {} s: {}", this.statTicks / 20, now);
         }

         if (!this.formatsLogged && this.stats.get(0) + this.stats.get(4) > 0) {
            this.formatsLogged = true;
            BedrockNative.LOGGER
               .info(
                  "Chunk formats from {} after {} s: {}; {}; block states sent by name: {}",
                  new Object[]{
                     this.serverLabel,
                     this.statTicks / 20,
                     this.diagnostics.summary(),
                     ChunkFormatStats.SESSION.summary(),
                     BlockMappings.get().persistentSummary()
                  }
               );
         }
      }
   }

   public void tickRequests(Consumer<BedrockPacket> send) {
      this.logStats();
      CachedChunkTransformer cached = this.blobCache;
      if (cached != null) {
         cached.tick(send);
      }

      this.sendPendingRequests(send);
      if (cached != null) {
         cached.drainStatus().forEach(send);
      }
   }

   private void sendPendingRequests(Consumer<BedrockPacket> send) {
      if (!this.pendingRequests.isEmpty()) {
         Map<Long, BedrockChunks.PendingColumn> latest = new LinkedHashMap<>();

         BedrockChunks.PendingColumn column;
         while ((column = this.pendingRequests.poll()) != null) {
            latest.put(key(column.x(), column.z()), column);
         }

         int radius = this.publishedRadius;
         int centerX = radius < 0 ? latest.values().iterator().next().x() : this.publishedX;
         int centerZ = radius < 0 ? latest.values().iterator().next().z() : this.publishedZ;
         List<BedrockChunks.PendingColumn> columns = new ArrayList<>(latest.values());
         columns.sort(Comparator.comparingInt(c -> (c.x() - centerX) * (c.x() - centerX) + (c.z() - centerZ) * (c.z() - centerZ)));
         SubChunkRequestPacket request = null;
         int sent = 0;

         for (BedrockChunks.PendingColumn pending : columns) {
            int dx = pending.x() - centerX;
            int dz = pending.z() - centerZ;
            int reach = radius < 0 ? 127 : Math.min(127, radius + 2);
            if (Math.abs(dx) <= reach && Math.abs(dz) <= reach) {
               if (request != null && request.getPositionOffsets().size() + pending.sections() > 231) {
                  this.countRequest(request);
                  send.accept(request);
                  sent++;
                  request = null;
               }

               if (sent >= 4) {
                  this.pendingRequests.add(pending);
               } else {
                  if (request == null) {
                     request = new SubChunkRequestPacket();
                     request.setDimension(this.dimension);
                     request.setSubChunkPosition(Vector3i.from(centerX, 0, centerZ));
                  }

                  for (int i = 0; i < pending.sections(); i++) {
                     request.getPositionOffsets().add(Vector3i.from(dx, this.requestFirstSection + i, dz));
                  }
               }
            }
         }

         if (request != null) {
            this.countRequest(request);
            send.accept(request);
         }
      }
   }

   private void countRequest(SubChunkRequestPacket request) {
      this.stats.incrementAndGet(8);
      this.stats.addAndGet(9, request.getPositionOffsets().size());
   }

   public void onBiomeDefinitions(BiomeDefinitionListPacket packet) {
      if (packet.getBiomes() != null) {
         packet.getBiomes().getDefinitions().forEach((name, data) -> {
            if (data.getId() != null) {
               this.serverBiomeNames.put(data.getId(), name);
               this.serverBiomeClimates.put(data.getId(), new BiomeClimates.Climate(data.getTemperature(), data.getDownfall()));
            }
         });
         BedrockNative.LOGGER
            .debug("Server announced {} biomes, {} with network ids", packet.getBiomes().getDefinitions().size(), this.serverBiomeNames.size());
      }
   }

   private CachedChunkTransformer blobCache() {
      CachedChunkTransformer cached = this.blobCache;
      if (cached == null) {
         synchronized (this) {
            cached = this.blobCache;
            if (cached == null) {
               cached = new CachedChunkTransformer(BlobCaches.shared(), System::nanoTime, new CachedChunkTransformer.Output() {
                  {
                     Objects.requireNonNull(BedrockChunks.this);
                  }

                  @Override
                  public void levelChunk(LevelChunkPacket packet) {
                     BedrockChunks.this.applyLevelChunk(packet);
                  }

                  @Override
                  public void subChunks(SubChunkPacket packet) {
                     BedrockChunks.this.applySubChunks(packet);
                  }

                  @Override
                  public void columnLost(int lostDimension, int chunkX, int chunkZ, int subChunks) {
                     BedrockChunks.this.requestLostColumn(lostDimension, chunkX, chunkZ, subChunks);
                  }

                  @Override
                  public void execute(Runnable task) {
                     BedrockChunks.this.minecraft.execute(task);
                  }

                  @Override
                  public void execute(long column, Runnable task) {
                     if (!BedrockChunks.this.stream.queue().park((int)(column >> 32), (int)column, task)) {
                        BedrockChunks.this.minecraft.execute(task);
                     }
                  }
               });
               this.blobCache = cached;
            }
         }
      }

      return cached;
   }

   private void requestLostColumn(int lostDimension, int chunkX, int chunkZ, int subChunks) {
      if (lostDimension == this.dimension) {
         int count = subChunks <= 0 ? this.requestSections : Math.min(this.requestSections, subChunks);
         this.pendingRequests.add(new BedrockChunks.PendingColumn(chunkX, chunkZ, count));
      }
   }

   public boolean deferUntilChunkArrives(int chunkX, int chunkZ, Runnable task) {
      CachedChunkTransformer cached = this.blobCache;
      return cached != null && cached.deferIfHeld(chunkX, chunkZ, task) ? true : ChunkStream.ENABLED && this.stream.queue().park(chunkX, chunkZ, task);
   }

   public void onBlobMiss(ClientCacheMissResponsePacket packet) {
      this.blobCache().onMissResponse(packet);
   }

   public CachedChunkTransformer.Summary blobCacheSummary() {
      CachedChunkTransformer cached = this.blobCache;
      return cached == null ? null : cached.summary();
   }

   public void onLevelChunk(LevelChunkPacket packet) {
      ChunkStream.arrivals().levelChunk();
      if (packet.isClientBiomeUpdate()) {
         this.applyBiomeUpdate(packet);
      } else if (packet.isCachingEnabled()) {
         this.blobCache().onLevelChunk(packet);
      } else {
         this.applyLevelChunk(packet);
      }
   }

   private void applyBiomeUpdate(LevelChunkPacket packet) {
      int chunkX = packet.getChunkX();
      int chunkZ = packet.getChunkZ();
      if (packet.getDimension() == this.dimension) {
         try {
            List<PalettedContainer<Holder<Biome>>> biomes = this.biomeContainers(
               BedrockChunkCodec.readLevelChunk(packet.getData(), 0, this.sectionCount).biomes()
            );
            this.minecraft.execute(() -> {
               if (this.minecraft.level != null) {
                  BedrockChunkLoader.applyBiomes(this.minecraft.level, chunkX, chunkZ, biomes);
               }
            });
         } catch (RuntimeException var5) {
            this.warnBadData("biome update", var5);
         }
      }
   }

   private void applyLevelChunk(LevelChunkPacket packet) {
      this.stats.incrementAndGet(0);
      if (packet.getDimension() != this.dimension) {
         if (this.stats.incrementAndGet(2) == 1) {
            BedrockNative.LOGGER.info("LevelChunk for dimension {} while the player is in {}: ignored", packet.getDimension(), this.dimension);
         }

         this.lastOtherDimension = packet.getDimension();
      } else {
         int chunkX = packet.getChunkX();
         int chunkZ = packet.getChunkZ();
         if (packet.isRequestSubChunks()) {
            this.stats.incrementAndGet(1);
            int count = packet.getSubChunkLimit() < 0 ? this.requestSections : Math.min(this.requestSections, packet.getSubChunkLimit() + 1);
            this.pendingRequests.add(new BedrockChunks.PendingColumn(chunkX, chunkZ, count));

            try {
               this.columnBiomes.put(key(chunkX, chunkZ), this.biomeContainers(BedrockChunkCodec.readLevelChunk(packet.getData(), 0, count).biomes()));
            } catch (RuntimeException var10) {
               this.warnBadData("chunk biomes", var10);
            }
         } else {
            ColumnDecoder.Dimension decoding = this.view;
            int subChunkCount = packet.getSubChunksLength();
            ChunkDiagnostics.Column described = new ChunkDiagnostics.Column(
               chunkX, chunkZ, subChunkCount, packet.isCachingEnabled(), packet.getBlobIds().size()
            );
            ColumnDecoder.LayerSink ids = this.blockIdSink();
            if (!ChunkStream.ENABLED) {
               PreparedColumn column = this.decodeLevelChunk(packet.getData(), described, decoding, false, ids);
               if (column != null) {
                  this.minecraft.execute(() -> {
                     if (this.minecraft.level != null) {
                        long perf = PerfProbe.start();
                        long started = FrameTimes.start();
                        BedrockChunkLoader.apply(this.minecraft.level, chunkX, chunkZ, column);
                        FrameTimes.end(FrameWatch.Bucket.CHUNK_APPLY, started);
                        FrameTimes.watch().columns(1, 0, 0);
                        PerfProbe.end(PerfProbe.Part.CHUNKS_APPLY, perf);
                        if (this.stats.incrementAndGet(10) == 1) {
                           SessionTimeline.mark("first chunk");
                        }
                     }
                  });
               }
            } else {
               ColumnQueue.Ticket ticket = this.stream.queue().reserve(chunkX, chunkZ);
               ByteBuf payload = packet.getData();
               ReferenceCountUtil.retain(packet);
               this.stream.decode(chunkX, chunkZ, () -> {
                  try {
                     PreparedColumn column = this.decodeLevelChunk(payload, described, decoding, true, ids);
                     if (column == null) {
                        this.stream.queue().fail(ticket);
                     } else {
                        this.stream.queue().fill(ticket, column);
                     }
                  } catch (RuntimeException var11x) {
                     this.stream.queue().fail(ticket);
                     throw var11x;
                  } finally {
                     ReferenceCountUtil.release(packet);
                  }
               });
            }
         }
      }
   }

   @Nullable
   private PreparedColumn decodeLevelChunk(
      ByteBuf payload, ChunkDiagnostics.Column column, ColumnDecoder.Dimension decoding, boolean precompute, @Nullable ColumnDecoder.LayerSink ids
   ) {
      int subChunkCount = column.subChunkCount();
      int payloadStart = payload.readerIndex();
      int payloadLength = payload.readableBytes();

      ColumnDecoder.Decoded decoded;
      try {
         decoded = ColumnDecoder.levelChunk(payload, subChunkCount, decoding, precompute, ids);
      } catch (RuntimeException var11) {
         this.stats.incrementAndGet(3);
         this.warnBadData("a full chunk (" + subChunkCount + " sub-chunks)", var11);
         this.unreadableColumn(column, payload, payloadStart, payloadLength, var11);
         return null;
      }

      this.diagnostics.columnRead(column);
      if (decoded.blockEntityProblem() != null) {
         this.warnBadData("block entities of a full chunk (" + decoded.blockEntityProblem() + ")", new IllegalArgumentException(decoded.blockEntityProblem()));
      }

      return decoded.column();
   }

   public void onSubChunks(SubChunkPacket packet) {
      ChunkStream.arrivals().subChunks(packet.getSubChunks().size());
      if (packet.isCacheEnabled()) {
         this.blobCache().onSubChunks(packet);
      } else {
         this.applySubChunks(packet);
      }
   }

   private void applySubChunks(SubChunkPacket packet) {
      this.stats.incrementAndGet(4);
      if (packet.getDimension() != this.dimension) {
         if (this.stats.incrementAndGet(2) == 1) {
            BedrockNative.LOGGER.info("SubChunk for dimension {} while the player is in {}: ignored", packet.getDimension(), this.dimension);
         }

         this.lastOtherDimension = packet.getDimension();
      } else {
         for (SubChunkData sub : packet.getSubChunks()) {
            if (sub.getResult() == SubChunkRequestResult.SUCCESS) {
               this.stats.incrementAndGet(5);
            } else if (sub.getResult() == SubChunkRequestResult.SUCCESS_ALL_AIR) {
               this.stats.incrementAndGet(6);
            } else if (this.stats.incrementAndGet(7) == 1) {
               BedrockNative.LOGGER.info("Sub-chunk {} answered {}", sub.getPosition(), sub.getResult());
            }
         }

         ColumnDecoder.Dimension decoding = this.view;
         List<Long> keys = ColumnDecoder.columnsOf(packet);
         Map<Long, List<PalettedContainer<Holder<Biome>>>> biomesNow = new HashMap<>();

         for (long key : keys) {
            List<PalettedContainer<Holder<Biome>>> column = this.columnBiomes.get(key);
            if (column != null) {
               biomesNow.put(key, column);
            }
         }

         ColumnDecoder.LayerSink ids = this.blockIdSink();
         if (!ChunkStream.ENABLED) {
            Map<Long, PreparedColumn> columns = ColumnDecoder.subChunks(packet, decoding, biomesNow, false, this::warnBadData, ids);
            this.minecraft.execute(() -> {
               if (this.minecraft.level != null) {
                  long perf = PerfProbe.start();
                  long started = FrameTimes.start();
                  int applied = 0;

                  for (Entry<Long, PreparedColumn> entry : columns.entrySet()) {
                     if (!PreparedColumn.empty(entry.getValue())) {
                        BedrockChunkLoader.apply(this.minecraft.level, (int)(entry.getKey() >> 32), (int)entry.getKey().longValue(), entry.getValue());
                        applied++;
                     }
                  }

                  FrameTimes.end(FrameWatch.Bucket.CHUNK_APPLY, started);
                  FrameTimes.watch().columns(applied, 0, 0);
                  PerfProbe.end(PerfProbe.Part.CHUNKS_APPLY, perf);
               }
            });
         } else {
            Map<Long, ColumnQueue.Ticket> tickets = new LinkedHashMap<>();

            for (long keyx : keys) {
               tickets.put(keyx, this.stream.queue().reserve((int)(keyx >> 32), (int)keyx));
            }

            ReferenceCountUtil.retain(packet);
            Vector3i center = packet.getCenterPosition();
            this.stream.decode(center.getX(), center.getZ(), () -> {
               try {
                  Map<Long, PreparedColumn> columns = ColumnDecoder.subChunks(packet, decoding, biomesNow, true, this::warnBadData, ids);
                  tickets.forEach((keyx, ticket) -> {
                     PreparedColumn column = columns.get(keyx);
                     if (column == null) {
                        this.stream.queue().fail(ticket);
                     } else {
                        this.stream.queue().fill(ticket, column);
                     }
                  });
               } catch (RuntimeException var10x) {
                  tickets.values().forEach(this.stream.queue()::fail);
                  this.warnBadData("a SubChunk answer", var10x);
               } finally {
                  ReferenceCountUtil.release(packet);
               }
            });
         }
      }
   }

   public void onPublisherUpdate(Vector3i center, int radiusBlocks) {
      int centerX = center.getX() >> 4;
      int centerZ = center.getZ() >> 4;
      int keep = (radiusBlocks >> 4) + 2;
      this.publishedX = centerX;
      this.publishedZ = centerZ;
      this.publishedRadius = radiusBlocks >> 4;
      this.columnBiomes.keySet().removeIf(key -> Math.abs((int)(key >> 32) - centerX) > keep || Math.abs((int)key.longValue() - centerZ) > keep);
   }

   private List<PalettedContainer<Holder<Biome>>> biomeContainers(List<int[]> bedrockBiomes) {
      List<PalettedContainer<Holder<Biome>>> result = new ArrayList<>(bedrockBiomes.size());

      for (int[] ids : bedrockBiomes) {
         result.add(this.biomes.container(this.containers, ids));
      }

      return result;
   }

   private static BlockIdCheck.Ids sessionIds(final BlockMappings mappings) {
      final int air = mappings.airNetworkId();
      return new BlockIdCheck.Ids() {
         @Override
         public int air() {
            return air;
         }

         @Nullable
         @Override
         public String name(int id) {
            return mappings.bedrockName(id);
         }

         @Override
         public boolean blockEntity(int id) {
            return mappings.toJava(id).hasBlockEntity();
         }
      };
   }

   @Nullable
   private ColumnDecoder.LayerSink blockIdSink() {
      BlockIdCheck check = this.blockIdCheck;
      return check != null && check.wants() ? layers -> logBlockIds(check.column(layers)) : null;
   }

   private static void logBlockIds(@Nullable BlockIdCheck.Report report) {
      if (report != null) {
         if (report.suspicious()) {
            BedrockNative.LOGGER
               .warn(
                  "Block ids check: {}; ids {}; block entities are made only where the server sent their data until the next dimension",
                  report.text(),
                  BlockMappings.get().numbering()
               );
         } else {
            BedrockNative.LOGGER.info("Block ids check: {}", report.text());
         }
      }
   }

   private void warnBadData(String what, RuntimeException e) {
      if (!this.warnedBadData) {
         this.warnedBadData = true;
         BedrockNative.LOGGER.warn("Could not read {}; continuing without", what, e);
      }
   }

   private void unreadableColumn(ChunkDiagnostics.Column column, ByteBuf payload, int payloadStart, int payloadLength, RuntimeException e) {
      ChunkDiagnostics.Failure failure = this.diagnostics.columnFailed(column, payload, payloadStart, payloadLength, e);
      if (failure.firstDescription() != null) {
         BedrockNative.LOGGER.warn(failure.firstDescription());
      }

      if (failure.allFailed() != null) {
         BedrockNative.LOGGER.error(failure.allFailed());
         if (!this.toldUnreadable) {
            this.toldUnreadable = true;
            this.minecraft.execute(() -> this.minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("bedrocknative.chunks.unreadable")));
         }
      }
   }

   static long key(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | chunkZ & 4294967295L;
   }

   private record PendingColumn(int x, int z, int sections) {
   }
}
