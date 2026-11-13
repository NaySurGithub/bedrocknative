package net.bedrocknative.world.chunk;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

public final class ChunkFormatStats {
   public static final ChunkFormatStats SESSION = new ChunkFormatStats();
   private final AtomicIntegerArray subChunkVersions = new AtomicIntegerArray(256);
   private final AtomicInteger runtimeStorages = new AtomicInteger();
   private final AtomicInteger persistentStorages = new AtomicInteger();
   private final AtomicInteger persistentEntries = new AtomicInteger();
   private final AtomicInteger littleEndianEntries = new AtomicInteger();
   private final AtomicInteger uncountedEntries = new AtomicInteger();
   private final AtomicInteger uncountedPalettes = new AtomicInteger();
   private final AtomicInteger biomeStorages = new AtomicInteger();
   private final AtomicInteger biomeFlagged = new AtomicInteger();
   private final AtomicInteger biomeCopies = new AtomicInteger();

   public void reset() {
      for (int i = 0; i < this.subChunkVersions.length(); i++) {
         this.subChunkVersions.set(i, 0);
      }

      this.runtimeStorages.set(0);
      this.persistentStorages.set(0);
      this.persistentEntries.set(0);
      this.littleEndianEntries.set(0);
      this.uncountedEntries.set(0);
      this.uncountedPalettes.set(0);
      this.biomeStorages.set(0);
      this.biomeFlagged.set(0);
      this.biomeCopies.set(0);
   }

   void subChunkVersion(int version) {
      this.subChunkVersions.incrementAndGet(version & 0xFF);
   }

   void blockStorage(boolean persistent) {
      (persistent ? this.persistentStorages : this.runtimeStorages).incrementAndGet();
   }

   void persistentEntries(int entries, int littleEndian, int uncounted) {
      this.persistentEntries.addAndGet(entries);
      if (littleEndian > 0) {
         this.littleEndianEntries.addAndGet(littleEndian);
      }

      if (uncounted > 0) {
         this.uncountedEntries.addAndGet(uncounted);
         this.uncountedPalettes.incrementAndGet();
      }
   }

   void biomeStorage(int header) {
      this.biomeStorages.incrementAndGet();
      if (header >> 1 == 127) {
         this.biomeCopies.incrementAndGet();
      } else if ((header & 1) == 0) {
         this.biomeFlagged.incrementAndGet();
      }
   }

   public int subChunks(int version) {
      return this.subChunkVersions.get(version & 0xFF);
   }

   public int runtimeStorages() {
      return this.runtimeStorages.get();
   }

   public int persistentStorages() {
      return this.persistentStorages.get();
   }

   public int persistentEntries() {
      return this.persistentEntries.get();
   }

   public int littleEndianEntries() {
      return this.littleEndianEntries.get();
   }

   public int uncountedEntries() {
      return this.uncountedEntries.get();
   }

   public int biomeStorages() {
      return this.biomeStorages.get();
   }

   public int flaggedBiomeStorages() {
      return this.biomeFlagged.get();
   }

   public String summary() {
      StringJoiner versions = new StringJoiner(", ", "{", "}");

      for (int i = 0; i < this.subChunkVersions.length(); i++) {
         int count = this.subChunkVersions.get(i);
         if (count > 0) {
            versions.add(i + "=" + count);
         }
      }

      return String.format(
         "sub-chunk versions %s, block storages %d runtime, %d persistent (%d palette entries, %d in little-endian NBT, %d past the count in %d palettes), biome storages %d (%d with the persistent flag, %d copy the previous)",
         versions,
         this.runtimeStorages.get(),
         this.persistentStorages.get(),
         this.persistentEntries.get(),
         this.littleEndianEntries.get(),
         this.uncountedEntries.get(),
         this.uncountedPalettes.get(),
         this.biomeStorages.get(),
         this.biomeFlagged.get(),
         this.biomeCopies.get()
      );
   }
}
