package net.bedrocknative.world.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.jspecify.annotations.Nullable;

public final class BedrockChunkCodec {
   public static final int SECTION_VOLUME = 4096;
   private static final int COPY_PREVIOUS_BITS = 127;
   private static final int MAX_BIOME_STORAGES = 64;
   @Nullable
   private static volatile BedrockChunkCodec.BlockStateResolver sessionBlockStates;

   private BedrockChunkCodec() {
   }

   public static void useBlockStates(@Nullable BedrockChunkCodec.BlockStateResolver resolver) {
      sessionBlockStates = resolver;
   }

   public static BedrockChunkCodec.SubChunk readSubChunk(ByteBuf buf) {
      return readSubChunk(buf, null);
   }

   public static BedrockChunkCodec.SubChunk readSubChunk(ByteBuf buf, @Nullable BedrockChunkCodec.UniformStorages uniform) {
      return readSubChunk(buf, uniform, sessionBlockStates);
   }

   public static BedrockChunkCodec.SubChunk readSubChunk(
      ByteBuf buf, @Nullable BedrockChunkCodec.UniformStorages uniform, @Nullable BedrockChunkCodec.BlockStateResolver blockStates
   ) {
      int start = buf.readerIndex();
      int version = buf.readUnsignedByte();
      ChunkFormatStats.SESSION.subChunkVersion(version);
      Integer sectionY = null;
      int layerCount;
      switch (version) {
         case 1:
            layerCount = 1;
            break;
         case 8:
            layerCount = buf.readUnsignedByte();
            break;
         case 9:
            layerCount = buf.readUnsignedByte();
            sectionY = Integer.valueOf(buf.readByte());
            break;
         default:
            throw new BedrockChunkCodec.FormatException("Unsupported sub-chunk version " + version, null, -1, -1, -1, version, start, start);
      }

      List<int[]> layers = new ArrayList<>(layerCount);

      for (int i = 0; i < layerCount; i++) {
         int storageStart = buf.readerIndex();

         try {
            layers.add(readStorage(buf, null, uniform, blockStates, true));
         } catch (RuntimeException var11) {
            throw BedrockChunkCodec.FormatException.of(var11, -1, i, -1, version, storageStart, buf.readerIndex());
         }
      }

      return new BedrockChunkCodec.SubChunk(sectionY, layers);
   }

   public static int[] readPalettedStorage(ByteBuf buf, int[] previous) {
      return readStorage(buf, previous, null, sessionBlockStates, previous == null);
   }

   private static int[] readStorage(
      ByteBuf buf,
      int[] previous,
      @Nullable BedrockChunkCodec.UniformStorages uniform,
      @Nullable BedrockChunkCodec.BlockStateResolver blockStates,
      boolean blocks
   ) {
      int header = buf.readUnsignedByte();
      int bits = header >> 1;
      if (bits == 127) {
         if (previous == null) {
            throw new IllegalArgumentException("Copy-previous palette with nothing to copy");
         } else {
            return previous;
         }
      } else {
         boolean persistent = blocks && (header & 1) == 0;
         if (blocks) {
            ChunkFormatStats.SESSION.blockStorage(persistent);
         }

         if (persistent && blockStates == null) {
            throw new IllegalArgumentException("Persistent (NBT) palette and no block state resolver");
         } else if (bits == 0) {
            int value = persistent ? persistentPalette(buf, 1, blockStates)[0] : readZigZagVarInt(buf);
            if (uniform != null) {
               return uniform.of(value);
            } else {
               int[] result = new int[4096];
               Arrays.fill(result, value);
               return result;
            }
         } else if (!validBits(bits)) {
            throw new IllegalArgumentException("Invalid bits per entry " + bits);
         } else {
            int perWord = 32 / bits;
            int wordCount = (4096 + perWord - 1) / perWord;
            int wordsStart = buf.readerIndex();
            buf.skipBytes(wordCount * 4);
            int paletteSize = readZigZagVarInt(buf);
            if (paletteSize > 0 && paletteSize <= 4096) {
               int[] palette;
               if (persistent) {
                  palette = persistentPalette(buf, paletteSize, blockStates);
                  paletteSize = palette.length;
               } else {
                  palette = new int[paletteSize];

                  for (int i = 0; i < paletteSize; i++) {
                     palette[i] = readZigZagVarInt(buf);
                  }
               }

               int mask = (1 << bits) - 1;
               if (paletteSize == 1 && allZero(buf, wordsStart, wordCount, perWord, bits)) {
                  if (uniform != null) {
                     return uniform.of(palette[0]);
                  } else {
                     int[] result = new int[4096];
                     Arrays.fill(result, palette[0]);
                     return result;
                  }
               } else {
                  int[] result = new int[4096];
                  int word = buf.getIntLE(wordsStart);
                  int wordIndex = 0;
                  int inWord = 0;

                  for (int x = 0; x < 16; x++) {
                     for (int z = 0; z < 16; z++) {
                        int column = z << 4 | x;

                        for (int y = 0; y < 16; y++) {
                           int paletteIndex = word >>> inWord * bits & mask;
                           if (paletteIndex >= paletteSize) {
                              throw new IllegalArgumentException("Palette index " + paletteIndex + " out of " + paletteSize);
                           }

                           result[y << 8 | column] = palette[paletteIndex];
                           if (++inWord == perWord) {
                              if (++wordIndex < wordCount) {
                                 inWord = 0;
                                 word = buf.getIntLE(wordsStart + wordIndex * 4);
                              }
                           }
                        }
                     }
                  }

                  return result;
               }
            } else {
               throw new IllegalArgumentException("Invalid palette size " + paletteSize);
            }
         }
      }
   }

   private static boolean validBits(int bits) {
      return bits >= 0 && bits <= 6 || bits == 8 || bits == 16;
   }

   private static int[] persistentPalette(ByteBuf buf, int count, BedrockChunkCodec.BlockStateResolver blockStates) {
      int[] palette = new int[count];
      int littleEndian = 0;
      int extra = 0;
      ByteBufInputStream stream = new ByteBufInputStream(buf);
      long limit = nbtReadLimit(buf.readableBytes());
      NBTInputStream network = NbtUtils.createNetworkReader(stream, limit);
      NBTInputStream disk = null;

      try {
         for (int i = 0; i < count; i++) {
            int at = buf.readerIndex();
            boolean le = buf.readableBytes() >= 3 && buf.getUnsignedByte(at) == 10 && buf.getUnsignedByte(at + 1) == 0 && buf.getUnsignedByte(at + 2) == 0;
            if (le && disk == null) {
               disk = NbtUtils.createReaderLE(stream, limit);
            }

            Object tag = (le ? disk : network).readTag();
            if (!(tag instanceof NbtMap state)) {
               throw new IllegalArgumentException(
                  "Persistent palette entry " + i + " is not a compound: " + (tag == null ? null : tag.getClass().getSimpleName())
               );
            }

            if (le) {
               littleEndian++;
            }

            palette[i] = blockStates.networkId(state);
         }

         while (count + extra < 4096 && buf.readableBytes() > 2 && buf.getUnsignedByte(buf.readerIndex()) == 10) {
            int atx = buf.readerIndex();

            Object tagx;
            try {
               tagx = NbtUtils.createNetworkReader(stream, limit).readTag();
            } catch (RuntimeException | IOException var16) {
               tagx = null;
            }

            if (!(tagx instanceof NbtMap state) || !(state.get("name") instanceof String)) {
               buf.readerIndex(atx);
               break;
            }

            if (extra == 0) {
               palette = Arrays.copyOf(palette, Math.min(4096, count * 2 + 1));
            } else if (count + extra == palette.length) {
               palette = Arrays.copyOf(palette, Math.min(4096, palette.length * 2));
            }

            palette[count + extra++] = blockStates.networkId(state);
         }
      } catch (IOException var17) {
         throw new IllegalArgumentException("Malformed persistent palette NBT", var17);
      }

      ChunkFormatStats.SESSION.persistentEntries(count, littleEndian, extra);
      return extra == 0 ? palette : Arrays.copyOf(palette, count + extra);
   }

   private static boolean allZero(ByteBuf buf, int wordsStart, int wordCount, int perWord, int bits) {
      for (int i = 0; i < wordCount; i++) {
         int entries = Math.min(perWord, 4096 - i * perWord);
         int used = entries * bits;
         int mask = used >= 32 ? -1 : (1 << used) - 1;
         if ((buf.getIntLE(wordsStart + i * 4) & mask) != 0) {
            return false;
         }
      }

      return true;
   }

   public static BedrockChunkCodec.LevelChunkData readLevelChunk(ByteBuf buf, int subChunkCount, int sectionCount) {
      return readLevelChunk(buf, subChunkCount, sectionCount, sessionBlockStates);
   }

   public static BedrockChunkCodec.LevelChunkData readLevelChunk(
      ByteBuf buf, int subChunkCount, int sectionCount, @Nullable BedrockChunkCodec.BlockStateResolver blockStates
   ) {
      BedrockChunkCodec.UniformStorages uniform = new BedrockChunkCodec.UniformStorages();
      List<BedrockChunkCodec.SubChunk> subChunks = new ArrayList<>(subChunkCount);

      for (int i = 0; i < subChunkCount; i++) {
         int start = buf.readerIndex();

         try {
            subChunks.add(readSubChunk(buf, uniform, blockStates));
         } catch (RuntimeException var11) {
            throw BedrockChunkCodec.FormatException.of(var11, i, -1, -1, start < buf.writerIndex() ? buf.getUnsignedByte(start) : -1, start, buf.readerIndex());
         }
      }

      List<int[]> biomes = new ArrayList<>(sectionCount);
      int first = buf.isReadable() ? buf.getUnsignedByte(buf.readerIndex()) : 1;
      if (sectionCount > 0 && (first & 1) == 0 && validBits(first >> 1)) {
         readFlaggedBiomes(buf, sectionCount, uniform, biomes);
      } else {
         int[] previous = null;

         for (int i = 0; i < sectionCount && buf.isReadable() && (buf.getUnsignedByte(buf.readerIndex()) & 1) == 1; i++) {
            previous = readBiome(buf, previous, uniform, i);
            biomes.add(previous);
         }

         while (buf.isReadable() && (buf.getUnsignedByte(buf.readerIndex()) & 1) == 1) {
            previous = readBiome(buf, previous, uniform, biomes.size());
         }
      }

      if (buf.isReadable()) {
         buf.skipBytes(buf.readUnsignedByte());
      }

      try {
         return new BedrockChunkCodec.LevelChunkData(subChunks, biomes, readBlockEntities(buf), null);
      } catch (RuntimeException var10) {
         return new BedrockChunkCodec.LevelChunkData(subChunks, biomes, List.of(), String.valueOf(var10.getCause() != null ? var10.getCause() : var10));
      }
   }

   private static void readFlaggedBiomes(ByteBuf buf, int sectionCount, BedrockChunkCodec.UniformStorages uniform, List<int[]> biomes) {
      int limit = Math.max(sectionCount, 64);
      int[] ends = new int[limit + 1];
      ends[0] = buf.readerIndex();
      List<int[]> read = new ArrayList<>();
      boolean clean = readFlagged(buf, sectionCount, uniform, read, ends) == sectionCount && cleanTail(buf, ends[sectionCount]);
      int count = -1;
      if (clean) {
         count = sectionCount;
      } else {
         readFlagged(buf, limit, uniform, read, ends);

         for (int candidate = read.size(); candidate > 0; candidate--) {
            if (cleanTail(buf, ends[candidate]) && (count < 0 || Math.abs(candidate - sectionCount) < Math.abs(count - sectionCount))) {
               count = candidate;
            }
         }
      }

      if (count < 0) {
         count = Math.min(read.size(), sectionCount);
      }

      buf.readerIndex(ends[count]);

      for (int i = 0; i < count; i++) {
         ChunkFormatStats.SESSION.biomeStorage(buf.getUnsignedByte(ends[i]));
         if (i < sectionCount) {
            biomes.add(read.get(i));
         }
      }
   }

   private static int readFlagged(ByteBuf buf, int upTo, BedrockChunkCodec.UniformStorages uniform, List<int[]> read, int[] ends) {
      buf.readerIndex(ends[read.size()]);

      for (int[] previous = read.isEmpty() ? null : read.getLast(); read.size() < upTo && buf.isReadable(); ends[read.size()] = buf.readerIndex()) {
         int start = buf.readerIndex();
         int bits = buf.getUnsignedByte(start) >> 1;
         if (!validBits(bits) && (bits != 127 || previous == null)) {
            break;
         }

         try {
            previous = readStorage(buf, previous, uniform, null, false);
         } catch (RuntimeException var9) {
            break;
         }

         read.add(previous);
      }

      return read.size();
   }

   private static boolean cleanTail(ByteBuf buf, int at) {
      ByteBuf tail = buf.slice(at, buf.writerIndex() - at);
      if (!tail.isReadable()) {
         return true;
      } else {
         int border = tail.readUnsignedByte();
         if (border > tail.readableBytes()) {
            return false;
         } else {
            tail.skipBytes(border);

            try {
               readBlockEntities(tail);
               return true;
            } catch (RuntimeException var5) {
               return false;
            }
         }
      }
   }

   private static int[] readBiome(ByteBuf buf, int[] previous, BedrockChunkCodec.UniformStorages uniform, int index) {
      int start = buf.readerIndex();
      ChunkFormatStats.SESSION.biomeStorage(buf.getUnsignedByte(start));

      try {
         return readStorage(buf, previous, uniform, null, false);
      } catch (RuntimeException var6) {
         throw BedrockChunkCodec.FormatException.of(var6, -1, -1, index, -1, start, buf.readerIndex());
      }
   }

   static long nbtReadLimit(int readable) {
      return Math.max(65536L, (long)readable << 4);
   }

   public static List<NbtMap> readBlockEntities(ByteBuf buf) {
      List<NbtMap> blockEntities = new ArrayList<>();

      try {
         try (NBTInputStream in = NbtUtils.createNetworkReader(new ByteBufInputStream(buf), nbtReadLimit(buf.readableBytes()))) {
            while (buf.isReadable()) {
               if (buf.getUnsignedByte(buf.readerIndex()) == 0) {
                  buf.skipBytes(1);
               } else {
                  blockEntities.add((NbtMap)in.readTag());
               }
            }
         }

         return blockEntities;
      } catch (IOException var7) {
         throw new IllegalArgumentException("Malformed block entity NBT", var7);
      }
   }

   static int readZigZagVarInt(ByteBuf buf) {
      int raw = readUnsignedVarInt(buf);
      return raw >>> 1 ^ -(raw & 1);
   }

   static int readUnsignedVarInt(ByteBuf buf) {
      int value = 0;

      for (int shift = 0; shift < 35; shift += 7) {
         byte b = buf.readByte();
         value |= (b & 127) << shift;
         if ((b & 128) == 0) {
            return value;
         }
      }

      throw new IllegalArgumentException("VarInt too long");
   }

   @FunctionalInterface
   public interface BlockStateResolver {
      int networkId(NbtMap var1);
   }

   public static final class FormatException extends IllegalArgumentException {
      private final int subChunk;
      private final int storage;
      private final int biome;
      private final int version;
      private final int elementStart;
      private final int failedAt;

      FormatException(String message, @Nullable Throwable cause, int subChunk, int storage, int biome, int version, int elementStart, int failedAt) {
         super(message, cause);
         this.subChunk = subChunk;
         this.storage = storage;
         this.biome = biome;
         this.version = version;
         this.elementStart = elementStart;
         this.failedAt = failedAt;
      }

      static BedrockChunkCodec.FormatException of(RuntimeException e, int subChunk, int storage, int biome, int version, int elementStart, int failedAt) {
         return e instanceof BedrockChunkCodec.FormatException located
            ? new BedrockChunkCodec.FormatException(
               located.getMessage(),
               located.getCause(),
               subChunk >= 0 ? subChunk : located.subChunk,
               storage >= 0 ? storage : located.storage,
               biome >= 0 ? biome : located.biome,
               version >= 0 ? version : located.version,
               located.elementStart >= 0 ? located.elementStart : elementStart,
               located.failedAt >= 0 ? located.failedAt : failedAt
            )
            : new BedrockChunkCodec.FormatException(String.valueOf(e.getMessage()), e, subChunk, storage, biome, version, elementStart, failedAt);
      }

      public int subChunk() {
         return this.subChunk;
      }

      public int storage() {
         return this.storage;
      }

      public int biome() {
         return this.biome;
      }

      public int version() {
         return this.version;
      }

      public int elementStart() {
         return this.elementStart;
      }

      public int failedAt() {
         return this.failedAt;
      }

      public String kind() {
         Throwable cause = this.getCause();
         return (cause != null ? cause : this).getClass().getSimpleName();
      }
   }

   public record LevelChunkData(List<BedrockChunkCodec.SubChunk> subChunks, List<int[]> biomes, List<NbtMap> blockEntities, String blockEntityProblem) {
   }

   public record SubChunk(Integer sectionY, List<int[]> layers) {
   }

   public static final class UniformStorages {
      private final int[] values = new int[8];
      private final int[][] arrays = new int[8][];
      private int size;

      int[] of(int value) {
         for (int i = 0; i < this.size; i++) {
            if (this.values[i] == value) {
               return this.arrays[i];
            }
         }

         int[] filled = new int[4096];
         Arrays.fill(filled, value);
         if (this.size < this.values.length) {
            this.values[this.size] = value;
            this.arrays[this.size++] = filled;
         }

         return filled;
      }
   }
}
