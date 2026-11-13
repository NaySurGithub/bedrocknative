package net.bedrocknative.client.world;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import net.bedrocknative.world.chunk.BedrockChunkCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.jspecify.annotations.Nullable;

public final class ColumnDecoder {
   private ColumnDecoder() {
   }

   public static ColumnDecoder.Decoded levelChunk(ByteBuf payload, int subChunkCount, ColumnDecoder.Dimension dimension, boolean precompute) {
      return levelChunk(payload, subChunkCount, dimension, precompute, null);
   }

   public static ColumnDecoder.Decoded levelChunk(
      ByteBuf payload, int subChunkCount, ColumnDecoder.Dimension dimension, boolean precompute, ColumnDecoder.@Nullable LayerSink ids
   ) {
      BedrockChunkCodec.LevelChunkData data = BedrockChunkCodec.readLevelChunk(payload, subChunkCount, dimension.sectionCount());
      PalettedContainerFactory containers = dimension.containers();
      List<PalettedContainer<Holder<Biome>>> columnBiome = biomeContainers(dimension, data.biomes());
      Map<Integer, LevelChunkSection> sections = new HashMap<>();
      List<BedrockChunkCodec.SubChunk> subChunks = data.subChunks();
      TreeMap<Integer, int[]> layers = ids != null ? new TreeMap<>() : null;

      for (int i = 0; i < subChunks.size(); i++) {
         BedrockChunkCodec.SubChunk sub = subChunks.get(i);
         int index = (sub.sectionY() != null ? sub.sectionY() : dimension.minSectionY() + i) - dimension.minSectionY();
         sections.put(index, BedrockChunkLoader.buildSection(containers, sub, biome(columnBiome, index)));
         if (layers != null && !sub.layers().isEmpty()) {
            layers.put(index, sub.layers().getFirst());
         }
      }

      if (ids != null) {
         ids.column(new ArrayList<>(layers.values()));
      }

      for (int index = 0; index < columnBiome.size(); index++) {
         if (!sections.containsKey(index)) {
            sections.put(index, BedrockChunkLoader.emptySection(containers, columnBiome.get(index)));
         }
      }

      long[][] heightmaps = null;
      int[] blockEntityBlocks = null;
      if (precompute) {
         heightmaps = heightmaps(dimension, fullColumn(dimension, sections, true));
         blockEntityBlocks = blockEntityBlocks(sections, dimension.sectionCount());
      }

      return new ColumnDecoder.Decoded(
         new PreparedColumn(containers, sections, true, data.blockEntities(), heightmaps, blockEntityBlocks, true), data.blockEntityProblem()
      );
   }

   public static Map<Long, PreparedColumn> subChunks(
      SubChunkPacket packet,
      ColumnDecoder.Dimension dimension,
      Map<Long, List<PalettedContainer<Holder<Biome>>>> columnBiomes,
      boolean precompute,
      BiConsumer<String, RuntimeException> badData
   ) {
      return subChunks(packet, dimension, columnBiomes, precompute, badData, null);
   }

   public static Map<Long, PreparedColumn> subChunks(
      SubChunkPacket packet,
      ColumnDecoder.Dimension dimension,
      Map<Long, List<PalettedContainer<Holder<Biome>>>> columnBiomes,
      boolean precompute,
      BiConsumer<String, RuntimeException> badData,
      ColumnDecoder.@Nullable LayerSink ids
   ) {
      Vector3i center = packet.getCenterPosition();
      Map<Long, Map<Integer, LevelChunkSection>> byChunk = new LinkedHashMap<>();
      Map<Long, List<NbtMap>> blockEntities = new HashMap<>();
      Map<Long, TreeMap<Integer, int[]>> layers = ids != null ? new LinkedHashMap<>() : null;
      BedrockChunkCodec.UniformStorages uniform = new BedrockChunkCodec.UniformStorages();
      PalettedContainerFactory containers = dimension.containers();

      for (SubChunkData sub : packet.getSubChunks()) {
         int chunkX = center.getX() + sub.getPosition().getX();
         int index = center.getY() + sub.getPosition().getY() - dimension.minSectionY();
         int chunkZ = center.getZ() + sub.getPosition().getZ();
         long key = BedrockChunks.key(chunkX, chunkZ);
         Map<Integer, LevelChunkSection> sections = byChunk.computeIfAbsent(key, k -> new HashMap<>());
         PalettedContainer<Holder<Biome>> biome = biome(columnBiomes.get(key), index);
         LevelChunkSection section;
         if (sub.getResult() == SubChunkRequestResult.SUCCESS_ALL_AIR) {
            section = BedrockChunkLoader.emptySection(containers, biome);
         } else {
            if (sub.getResult() != SubChunkRequestResult.SUCCESS) {
               continue;
            }

            ByteBuf data = sub.getData();
            BedrockChunkCodec.SubChunk decoded = BedrockChunkCodec.readSubChunk(data, uniform);
            if (layers != null && !decoded.layers().isEmpty()) {
               layers.computeIfAbsent(key, k -> new TreeMap<>()).put(index, decoded.layers().getFirst());
            }

            section = BedrockChunkLoader.buildSection(containers, decoded, biome);
            if (data.isReadable()) {
               try {
                  blockEntities.computeIfAbsent(key, k -> new ArrayList<>()).addAll(BedrockChunkCodec.readBlockEntities(data));
               } catch (RuntimeException e) {
                  badData.accept("sub-chunk block entities", e);
               }
            }
         }

         sections.put(index, section);
      }

      if (layers != null) {
         layers.values().forEach(column -> ids.column(new ArrayList<>(column.values())));
      }

      Map<Long, PreparedColumn> result = new LinkedHashMap<>();
      byChunk.forEach((keyx, sectionsx) -> {
         long[][] heightmaps = null;
         int[] blockEntityBlocks = null;
         if (precompute && !sectionsx.isEmpty()) {
            LevelChunkSection[] full = fullColumn(dimension, sectionsx, false);
            heightmaps = full == null ? null : heightmaps(dimension, full);
            blockEntityBlocks = blockEntityBlocks(sectionsx, dimension.sectionCount());
         }

         result.put(keyx, new PreparedColumn(containers, sectionsx, false, blockEntities.getOrDefault(keyx, List.of()), heightmaps, blockEntityBlocks, false));
      });
      return result;
   }

   public static List<Long> columnsOf(SubChunkPacket packet) {
      Vector3i center = packet.getCenterPosition();
      List<Long> keys = new ArrayList<>();
      LongOpenHashSet seen = new LongOpenHashSet();

      for (SubChunkData sub : packet.getSubChunks()) {
         long key = BedrockChunks.key(center.getX() + sub.getPosition().getX(), center.getZ() + sub.getPosition().getZ());
         if (seen.add(key)) {
            keys.add(key);
         }
      }

      return keys;
   }

   static LevelChunkSection @Nullable [] fullColumn(ColumnDecoder.Dimension dimension, Map<Integer, LevelChunkSection> sections, boolean replaceAll) {
      int count = dimension.sectionCount();
      LevelChunkSection[] full = new LevelChunkSection[count];

      for (int i = 0; i < count; i++) {
         LevelChunkSection section = sections.get(i);
         if (section == null) {
            if (!replaceAll) {
               return null;
            }

            section = BedrockChunkLoader.emptySection(dimension.containers(), null);
         }

         full[i] = section;
      }

      return full;
   }

   public static long @Nullable [][] heightmaps(ColumnDecoder.Dimension dimension, LevelChunkSection @Nullable [] sections) {
      if (sections == null) {
         return null;
      }

      ProtoChunk chunk = new ProtoChunk(ChunkPos.ZERO, UpgradeData.EMPTY, dimension.heights(), dimension.containers(), null);
      LevelChunkSection[] target = chunk.getSections();
      if (target.length != sections.length) {
         return null;
      }

      System.arraycopy(sections, 0, target, 0, sections.length);
      Heightmap.primeHeightmaps(chunk, BedrockChunkLoader.CLIENT_HEIGHTMAPS);
      long[][] result = new long[BedrockChunkLoader.CLIENT_HEIGHTMAPS.size()][];
      int i = 0;

      for (Types type : BedrockChunkLoader.CLIENT_HEIGHTMAPS) {
         result[i++] = (long[])chunk.getOrCreateHeightmapUnprimed(type).getRawData().clone();
      }

      return result;
   }

   public static int[] blockEntityBlocks(Map<Integer, LevelChunkSection> sections, int sectionCount) {
      int[] found = new int[16];
      int count = 0;
      int[] indices = sections.keySet().stream().mapToInt(Integer::intValue).filter(i -> i >= 0 && i < sectionCount).sorted().toArray();

      for (int index : indices) {
         LevelChunkSection section = sections.get(index);
         if (!section.hasOnlyAir() && section.maybeHas(BlockStateBase::hasBlockEntity)) {
            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     if (section.getBlockState(x, y, z).hasBlockEntity()) {
                        if (count == found.length) {
                           found = Arrays.copyOf(found, count * 2);
                        }

                        found[count++] = index << 12 | y << 8 | z << 4 | x;
                     }
                  }
               }
            }
         }
      }

      return Arrays.copyOf(found, count);
   }

   private static List<PalettedContainer<Holder<Biome>>> biomeContainers(ColumnDecoder.Dimension dimension, List<int[]> bedrockBiomes) {
      List<PalettedContainer<Holder<Biome>>> result = new ArrayList<>(bedrockBiomes.size());

      for (int[] ids : bedrockBiomes) {
         result.add(dimension.biomes().container(dimension.containers(), ids));
      }

      return result;
   }

   static @Nullable PalettedContainer<Holder<Biome>> biome(@Nullable List<PalettedContainer<Holder<Biome>>> column, int index) {
      return column != null && index >= 0 && index < column.size() ? column.get(index).copy() : null;
   }

   public record Decoded(PreparedColumn column, @Nullable String blockEntityProblem) {
   }

   public record Dimension(PalettedContainerFactory containers, BedrockBiomes biomes, int minSectionY, int sectionCount, LevelHeightAccessor heights) {
   }

   @FunctionalInterface
   public interface LayerSink {
      void column(List<int @Nullable []> var1);
   }
}
