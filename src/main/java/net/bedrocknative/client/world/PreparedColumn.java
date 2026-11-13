package net.bedrocknative.client.world;

import java.util.List;
import java.util.Map;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.cloudburstmc.nbt.NbtMap;
import org.jspecify.annotations.Nullable;

public record PreparedColumn(
   PalettedContainerFactory factory,
   Map<Integer, LevelChunkSection> sections,
   boolean replaceAll,
   List<NbtMap> blockEntities,
   long @Nullable [][] heightmaps,
   int @Nullable [] blockEntityBlocks,
   boolean fullColumn
) {
   static boolean empty(PreparedColumn column) {
      return column.sections().isEmpty() && column.blockEntities().isEmpty() && !column.replaceAll();
   }
}
