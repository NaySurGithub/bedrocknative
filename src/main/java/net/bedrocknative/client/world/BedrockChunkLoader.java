package net.bedrocknative.client.world;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.bedrocknative.client.mixin.world.LevelChunkSectionAccessor;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.world.light.ThreadedLight;
import net.bedrocknative.client.world.light.ThreadedLightEngine;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.chunk.BedrockChunkCodec;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache.Storage;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.PalettedContainerRO.PackedData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public final class BedrockChunkLoader {
   static final Set<Types> CLIENT_HEIGHTMAPS = Arrays.stream(Types.values())
      .filter(Types::sendToClient)
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Types.class)));

   private BedrockChunkLoader() {
   }

   public static LevelChunkSection buildSection(PalettedContainerFactory factory, BedrockChunkCodec.SubChunk subChunk, PalettedContainer<Holder<Biome>> biomes) {
      int[] blocks = subChunk.layers().isEmpty() ? null : subChunk.layers().getFirst();
      int[] liquid = subChunk.layers().size() > 1 ? subChunk.layers().get(1) : null;
      PalettedContainer<BlockState> states = blocks == null
         ? factory.createForBlockStates()
         : BedrockChunkLoader.SectionPacker.SCRATCH.get().pack(factory, blocks, liquid);
      return new LevelChunkSection(states, biomes != null ? biomes : factory.createForBiomes());
   }

   public static LevelChunkSection emptySection(PalettedContainerFactory factory, PalettedContainer<Holder<Biome>> biomes) {
      return new LevelChunkSection(factory.createForBlockStates(), biomes != null ? biomes : factory.createForBiomes());
   }

   public static void apply(ClientLevel level, int chunkX, int chunkZ, PreparedColumn column) {
      ClientChunkCache cache = level.getChunkSource();
      Storage storage = cache.storage;
      if (storage.inRange(chunkX, chunkZ)) {
         FrameWatch watch = FrameTimes.watch();
         long started = System.nanoTime();
         int index = storage.getIndex(chunkX, chunkZ);
         LevelChunk chunk = (LevelChunk)storage.chunks.get(index);
         ChunkPos pos = new ChunkPos(chunkX, chunkZ);
         boolean fresh = !ClientChunkCache.isValidChunk(chunk, chunkX, chunkZ);
         if (fresh) {
            if (chunk != null) {
               forgetLight(level, chunk.getPos());
               SodiumChunks.unloaded(level, chunk.getPos().x(), chunk.getPos().z());
               long evicted = System.nanoTime();
               watch.applyPart(FrameWatch.ApplyPart.EVICT, evicted - started);
               started = evicted;
            }

            chunk = new LevelChunk(level, pos);
         }

         LevelChunkSection[] target = chunk.getSections();
         Map<Integer, LevelChunkSection> sections = column.sections();
         boolean replaceAll = column.replaceAll();
         if (replaceAll) {
            chunk.clearAllBlockEntities();

            for (int i = 0; i < target.length; i++) {
               target[i] = sections.getOrDefault(i, emptySection(column.factory(), null));
            }
         } else {
            if (!fresh) {
               BedrockBlocks.removeBlockEntities(level, chunk, sections.keySet());
            }

            sections.forEach((i, section) -> {
               if (i >= 0 && i < target.length) {
                  target[i] = section;
               }
            });
         }

         long[][] heightmaps = column.heightmaps();
         if (heightmaps != null && heightmaps.length == CLIENT_HEIGHTMAPS.size() && (replaceAll || sections.size() == target.length)) {
            int i = 0;

            for (Types type : CLIENT_HEIGHTMAPS) {
               chunk.setHeightmap(type, heightmaps[i++]);
            }
         } else {
            Heightmap.primeHeightmaps(chunk, CLIENT_HEIGHTMAPS);
         }

         long primed = System.nanoTime();
         chunk.initializeLightSources();
         long sourced = System.nanoTime();
         watch.applyPart(FrameWatch.ApplyPart.SKY_SOURCES, sourced - primed);
         if (fresh) {
            storage.replace(index, chunk);
         } else {
            storage.refreshEmptySections(chunk);
         }

         LegacyShapes.chunkLoaded(level, chunk);
         level.onChunkLoaded(pos);
         long inserted = System.nanoTime();
         watch.applyPart(FrameWatch.ApplyPart.INSERT, primed - started + inserted - sourced);
         light(level, chunk);
         long lit = System.nanoTime();
         watch.applyPart(FrameWatch.ApplyPart.LIGHT_ENQUEUE, lit - inserted);
         SodiumChunks.loaded(level, chunkX, chunkZ, ThreadedLight.of(level) == null);
         Collection<Integer> indices = (Collection<Integer>)(replaceAll ? IntStream.range(0, target.length).boxed().toList() : sections.keySet());
         BedrockBlocks.createBlockEntities(level, chunk, indices, column.blockEntities(), column.blockEntityBlocks());
         watch.applyPart(FrameWatch.ApplyPart.BLOCK_ENTITIES, System.nanoTime() - lit);
      }
   }

   public static void applyBiomes(ClientLevel level, int chunkX, int chunkZ, List<PalettedContainer<Holder<Biome>>> biomes) {
      Storage storage = level.getChunkSource().storage;
      if (storage.inRange(chunkX, chunkZ)) {
         LevelChunk chunk = (LevelChunk)storage.chunks.get(storage.getIndex(chunkX, chunkZ));
         if (ClientChunkCache.isValidChunk(chunk, chunkX, chunkZ)) {
            LevelChunkSection[] sections = chunk.getSections();
            int count = Math.min(sections.length, biomes.size());

            for (int i = 0; i < count; i++) {
               ((LevelChunkSectionAccessor)sections[i]).bedrocknative$setBiomes((PalettedContainerRO<Holder<Biome>>)biomes.get(i));
            }

            chunk.markUnsaved();
            level.setSectionRangeDirty(chunkX - 1, level.getMinSectionY(), chunkZ - 1, chunkX + 1, level.getMaxSectionY(), chunkZ + 1);
         }
      }
   }

   public static void forgetLight(ClientLevel level, ChunkPos pos) {
      LevelLightEngine light = level.getChunkSource().getLightEngine();
      if (light instanceof ThreadedLightEngine threaded) {
         threaded.forgetColumn(pos, light.getMinLightSection(), light.getMaxLightSection(), level.getMinSectionY(), level.getMaxSectionY());
      } else {
         light.setLightEnabled(pos, false);

         for (int sectionY = light.getMinLightSection(); sectionY < light.getMaxLightSection(); sectionY++) {
            SectionPos section = SectionPos.of(pos, sectionY);
            light.queueSectionData(LightLayer.BLOCK, section, null);
            light.queueSectionData(LightLayer.SKY, section, null);
         }

         for (int sectionY = level.getMinSectionY(); sectionY <= level.getMaxSectionY(); sectionY++) {
            light.updateSectionStatus(SectionPos.of(pos, sectionY), true);
         }
      }
   }

   public static void beforeViewRadius(ClientLevel level, int viewRange) {
      long started = System.nanoTime();
      Storage storage = level.getChunkSource().storage;
      List<ChunkPos> held = new ArrayList<>();

      for (int i = 0; i < storage.chunks.length(); i++) {
         LevelChunk chunk = (LevelChunk)storage.chunks.get(i);
         if (chunk != null) {
            held.add(chunk.getPos());
         }
      }

      for (ChunkPos pos : outsideViewRadius(held, storage.viewCenterX, storage.viewCenterZ, viewRange)) {
         forgetLight(level, pos);
         SodiumChunks.unloaded(level, pos.x(), pos.z());
      }

      FrameTimes.watch().add(FrameWatch.Bucket.CHUNK_UNLOAD, System.nanoTime() - started);
   }

   static List<ChunkPos> outsideViewRadius(Iterable<ChunkPos> held, int centerX, int centerZ, int viewRange) {
      int radius = Math.max(2, viewRange) + 3;
      List<ChunkPos> dropped = new ArrayList<>();

      for (ChunkPos pos : held) {
         if (Math.abs(pos.x() - centerX) > radius || Math.abs(pos.z() - centerZ) > radius) {
            dropped.add(pos);
         }
      }

      return dropped;
   }

   public static boolean inRange(ClientLevel level, int chunkX, int chunkZ) {
      return level.getChunkSource().storage.inRange(chunkX, chunkZ);
   }

   public static void runLight(ClientLevel level) {
      level.getChunkSource().getLightEngine().runLightUpdates();
   }

   private static void light(ClientLevel level, LevelChunk chunk) {
      LevelLightEngine light = level.getChunkSource().getLightEngine();
      ChunkPos pos = chunk.getPos();
      LevelChunkSection[] sections = chunk.getSections();
      if (light instanceof ThreadedLightEngine threaded) {
         boolean[] empty = new boolean[sections.length];

         for (int i = 0; i < sections.length; i++) {
            empty[i] = sections[i].hasOnlyAir();
         }

         threaded.lightColumn(pos, empty, level.getMinSectionY());
      } else {
         for (int i = 0; i < sections.length; i++) {
            light.updateSectionStatus(SectionPos.of(pos, level.getSectionYFromSectionIndex(i)), sections[i].hasOnlyAir());
         }

         light.setLightEnabled(pos, true);
         light.propagateLightSources(pos);
      }

      level.setSectionRangeDirty(pos.x() - 1, level.getMinSectionY(), pos.z() - 1, pos.x() + 1, level.getMaxSectionY(), pos.z() + 1);
   }

   private static final class SectionPacker {
      static final ThreadLocal<BedrockChunkLoader.SectionPacker> SCRATCH = ThreadLocal.withInitial(BedrockChunkLoader.SectionPacker::new);
      private static final int SLOTS = 8192;
      private static final int AIR = 0;
      private final int[] keys = new int[8192];
      private final int[] entries = new int[8192];
      private final int[] stamps = new int[8192];
      private int stamp;
      private final int[] indices = new int[4096];
      private int[] dry = new int[64];
      private int[] wet = new int[64];
      private BlockState[] base = new BlockState[64];
      private boolean[] waterloggable = new boolean[64];
      private int distinct;
      private final int[] liquidIds = new int[8];
      private final boolean[] liquidWater = new boolean[8];
      private int liquids;
      private final List<BlockState> palette = new ArrayList<>();
      private final Reference2IntOpenHashMap<BlockState> paletteIndex = new Reference2IntOpenHashMap();

      PalettedContainer<BlockState> pack(PalettedContainerFactory factory, int[] blocks, int[] liquid) {
         BlockMappings mappings = BlockMappings.get();
         if (++this.stamp == 0) {
            Arrays.fill(this.stamps, 0);
            this.stamp = 1;
         }

         this.distinct = 0;
         this.liquids = 0;
         this.palette.clear();
         this.paletteIndex.clear();
         BlockState air = Blocks.AIR.defaultBlockState();
         this.palette.add(air);
         this.paletteIndex.put(air, 0);
         int[] keys = this.keys;
         int[] entries = this.entries;
         int[] stamps = this.stamps;
         int[] indices = this.indices;
         int current = this.stamp;
         boolean withLiquid = liquid != null;
         int lastId = 0;
         int lastEntry = -1;

         for (int index = 0; index < 4096; index++) {
            int id = blocks[index];
            int entry = lastEntry;
            if (id != lastId || lastEntry < 0) {
               int slot = id * -1640531527 >>> 19;

               while (true) {
                  if (stamps[slot] != current) {
                     entry = this.meet(mappings, id, withLiquid, slot);
                     break;
                  }

                  if (keys[slot] == id) {
                     entry = entries[slot];
                     break;
                  }

                  slot = slot + 1 & 8191;
               }

               lastId = id;
               lastEntry = entry;
            }

            int paletteAt;
            if (withLiquid && this.waterloggable[entry] && this.water(mappings, liquid[index])) {
               paletteAt = this.wet[entry];
               if (paletteAt < 0) {
                  paletteAt = this.wet[entry] = this.add((BlockState)this.base[entry].setValue(BlockStateProperties.WATERLOGGED, true));
               }
            } else {
               paletteAt = this.dry[entry];
               if (paletteAt < 0) {
                  paletteAt = this.dry[entry] = this.add(this.base[entry]);
               }
            }

            indices[index] = paletteAt;
         }

         PalettedContainer<BlockState> packed = this.packed(factory);
         return packed != null ? packed : slow(factory, mappings, blocks, liquid);
      }

      private int meet(BlockMappings mappings, int id, boolean withLiquid, int slot) {
         int entry = this.distinct++;
         if (entry == this.base.length) {
            int grown = entry * 2;
            this.dry = Arrays.copyOf(this.dry, grown);
            this.wet = Arrays.copyOf(this.wet, grown);
            this.base = Arrays.copyOf(this.base, grown);
            this.waterloggable = Arrays.copyOf(this.waterloggable, grown);
         }

         BlockState state = mappings.toJava(id);
         this.base[entry] = state;
         this.waterloggable[entry] = withLiquid && state.hasProperty(BlockStateProperties.WATERLOGGED);
         this.dry[entry] = state.isAir() ? 0 : -1;
         this.wet[entry] = -1;
         this.stamps[slot] = this.stamp;
         this.keys[slot] = id;
         this.entries[slot] = entry;
         return entry;
      }

      private boolean water(BlockMappings mappings, int liquidId) {
         for (int i = 0; i < this.liquids; i++) {
            if (this.liquidIds[i] == liquidId) {
               return this.liquidWater[i];
            }
         }

         boolean water = mappings.toJava(liquidId).is(Blocks.WATER);
         if (this.liquids < this.liquidIds.length) {
            this.liquidIds[this.liquids] = liquidId;
            this.liquidWater[this.liquids++] = water;
         }

         return water;
      }

      private int add(BlockState state) {
         if (state.isAir()) {
            return 0;
         } else {
            int at = this.paletteIndex.getOrDefault(state, -1);
            if (at < 0) {
               at = this.palette.size();
               this.palette.add(state);
               this.paletteIndex.put(state, at);
            }

            return at;
         }
      }

      @Nullable
      private PalettedContainer<BlockState> packed(PalettedContainerFactory factory) {
         int size = this.palette.size();
         if (size == 1) {
            return factory.createForBlockStates();
         } else {
            int bits = Math.max(4, Mth.ceillog2(size));
            long[] raw = new SimpleBitStorage(bits, 4096, this.indices).getRaw();
            return (PalettedContainer<BlockState>)PalettedContainer.unpack(
                  factory.blockStatesStrategy(), new PackedData(List.copyOf(this.palette), Optional.of(LongStream.of(raw)), bits)
               )
               .result()
               .orElse(null);
         }
      }

      private static PalettedContainer<BlockState> slow(PalettedContainerFactory factory, BlockMappings mappings, int[] blocks, int[] liquid) {
         PalettedContainer<BlockState> states = factory.createForBlockStates();

         for (int index = 0; index < 4096; index++) {
            BlockState state = mappings.toJava(blocks[index]);
            if (liquid != null && state.hasProperty(BlockStateProperties.WATERLOGGED) && mappings.toJava(liquid[index]).is(Blocks.WATER)) {
               state = (BlockState)state.setValue(BlockStateProperties.WATERLOGGED, true);
            }

            if (!state.isAir()) {
               states.getAndSetUnchecked(index & 15, index >> 8 & 15, index >> 4 & 15, state);
            }
         }

         return states;
      }
   }
}
