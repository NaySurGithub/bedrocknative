package net.bedrocknative.client.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache.Storage;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public final class LightReference {
   private LightReference() {
   }

   public static LightReference.Result compare(ClientLevel level, int centerX, int centerZ, int radius) {
      long started = System.nanoTime();
      ClientChunkCache cache = level.getChunkSource();
      List<LevelChunk> loaded = new ArrayList<>();
      Storage storage = cache.storage;

      for (int i = 0; i < storage.chunks.length(); i++) {
         LevelChunk chunk = (LevelChunk)storage.chunks.get(i);
         if (chunk != null && ClientChunkCache.isValidChunk(chunk, chunk.getPos().x(), chunk.getPos().z())) {
            loaded.add(chunk);
         }
      }

      LightReference.Getter getter = new LightReference.Getter(level, loaded);
      LevelLightEngine reference = new LevelLightEngine(getter, true, level.dimensionType().hasSkyLight());

      for (LevelChunk chunk : loaded) {
         LevelChunkSection[] sections = chunk.getSections();

         for (int ix = 0; ix < sections.length; ix++) {
            reference.updateSectionStatus(SectionPos.of(chunk.getPos(), level.getSectionYFromSectionIndex(ix)), sections[ix].hasOnlyAir());
         }
      }

      for (LevelChunk chunk : loaded) {
         reference.setLightEnabled(chunk.getPos(), true);
         reference.propagateLightSources(chunk.getPos());
      }

      reference.runLightUpdates();
      LevelLightEngine live = cache.getLightEngine();
      int compared = 0;
      int skySections = 0;
      int blockSections = 0;
      long skyCells = 0L;
      long blockCells = 0L;
      String first = "";

      for (LevelChunk chunk : loaded) {
         ChunkPos pos = chunk.getPos();
         if (Math.abs(pos.x() - centerX) <= radius && Math.abs(pos.z() - centerZ) <= radius) {
            for (int sectionY = live.getMinLightSection(); sectionY < live.getMaxLightSection(); sectionY++) {
               SectionPos section = SectionPos.of(pos, sectionY);
               compared++;
               long sky = differing(live.getLayerListener(LightLayer.SKY), reference.getLayerListener(LightLayer.SKY), section);
               long block = differing(live.getLayerListener(LightLayer.BLOCK), reference.getLayerListener(LightLayer.BLOCK), section);
               if (sky > 0L) {
                  skySections++;
                  skyCells += sky;
               }

               if (block > 0L) {
                  blockSections++;
                  blockCells += block;
               }

               if (first.isEmpty() && (sky > 0L || block > 0L)) {
                  first = "section " + section.x() + " " + section.y() + " " + section.z() + " (" + (sky > 0L ? "sky" : "block") + ")";
               }
            }
         }
      }

      return new LightReference.Result(compared, skySections, blockSections, skyCells, blockCells, first, (System.nanoTime() - started) / 1000000.0);
   }

   private static long differing(LayerLightEventListener live, LayerLightEventListener reference, SectionPos section) {
      DataLayer a = live.getDataLayerData(section);
      DataLayer b = reference.getDataLayerData(section);
      if (a == null && b == null) {
         return 0L;
      } else {
         long count = 0L;

         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  int left = a == null ? 0 : a.get(x, y, z);
                  int right = b == null ? 0 : b.get(x, y, z);
                  if (left != right) {
                     count++;
                  }
               }
            }
         }

         return count;
      }
   }

   private record Getter(ClientLevel level, List<LevelChunk> chunks) implements LightChunkGetter {
      @Nullable
      public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
         for (LevelChunk chunk : this.chunks) {
            if (chunk.getPos().x() == chunkX && chunk.getPos().z() == chunkZ) {
               return chunk;
            }
         }

         return null;
      }

      public BlockGetter getLevel() {
         return this.level;
      }

      public void onLightUpdate(LightLayer layer, SectionPos pos) {
      }
   }

   public record Result(
      int sectionsCompared, int skySectionsDiffering, int blockSectionsDiffering, long skyCellsDiffering, long blockCellsDiffering, String first, double millis
   ) {
      public boolean equal() {
         return this.skySectionsDiffering == 0 && this.blockSectionsDiffering == 0;
      }

      public String describe() {
         return String.format(
            Locale.ROOT,
            "%d sections compared: sky %d sections (%d cells) and block %d sections (%d cells) differ from a full recompute%s; %.0f ms",
            this.sectionsCompared,
            this.skySectionsDiffering,
            this.skyCellsDiffering,
            this.blockSectionsDiffering,
            this.blockCellsDiffering,
            this.first.isEmpty() ? "" : "; first " + this.first,
            this.millis
         );
      }
   }
}
