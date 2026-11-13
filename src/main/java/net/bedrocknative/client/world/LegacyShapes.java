package net.bedrocknative.client.world;

import net.bedrocknative.world.block.BlockMappings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class LegacyShapes {
   private static final int SET_FLAGS = 18;

   private LegacyShapes() {
   }

   static boolean shaped(BlockState state) {
      Block block = state.getBlock();
      return block instanceof StairBlock || block instanceof CrossCollisionBlock || block instanceof TripWireBlock;
   }

   private static BlockState derive(ClientLevel level, BlockPos pos, BlockState state) {
      for (Direction direction : Plane.HORIZONTAL) {
         BlockPos neighbour = pos.relative(direction);
         state = state.updateShape(level, level, pos, direction, neighbour, level.getBlockState(neighbour), level.getRandom());
      }

      return state;
   }

   static void chunkLoaded(ClientLevel level, LevelChunk chunk) {
      if (BlockMappings.get().shapelessSeen()) {
         ChunkPos pos = chunk.getPos();
         LevelChunkSection[] sections = chunk.getSections();
         MutableBlockPos cursor = new MutableBlockPos();

         for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.hasOnlyAir() && section.maybeHas(LegacyShapes::shaped)) {
               int minY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));

               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (shaped(state)) {
                           BlockState derived = derive(level, cursor.set(pos.getMinBlockX() + x, minY + y, pos.getMinBlockZ() + z), state);
                           if (derived != state) {
                              section.setBlockState(x, y, z, derived, false);
                           }
                        }
                     }
                  }
               }
            }
         }

         for (Direction direction : Plane.HORIZONTAL) {
            LevelChunk neighbour = level.getChunkSource().getChunk(pos.x() + direction.getStepX(), pos.z() + direction.getStepZ(), false);
            if (neighbour != null) {
               edge(level, neighbour, direction.getOpposite());
            }
         }
      }
   }

   private static void edge(ClientLevel level, LevelChunk chunk, Direction side) {
      LevelChunkSection[] sections = chunk.getSections();
      ChunkPos pos = chunk.getPos();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int index = 0; index < sections.length; index++) {
         LevelChunkSection section = sections[index];
         if (!section.hasOnlyAir() && section.maybeHas(LegacyShapes::shaped)) {
            int minY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));

            for (int i = 0; i < 16; i++) {
               int x = side == Direction.EAST ? 15 : (side == Direction.WEST ? 0 : i);
               int z = side == Direction.SOUTH ? 15 : (side == Direction.NORTH ? 0 : i);

               for (int y = 0; y < 16; y++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (shaped(state)) {
                     cursor.set(pos.getMinBlockX() + x, minY + y, pos.getMinBlockZ() + z);
                     BlockState derived = derive(level, cursor, state);
                     if (derived != state) {
                        level.setBlock(cursor.immutable(), derived, 18);
                     }
                  }
               }
            }
         }
      }
   }

   static void blockChanged(ClientLevel level, BlockPos pos) {
      if (BlockMappings.get().shapelessSeen()) {
         update(level, pos);

         for (Direction direction : Plane.HORIZONTAL) {
            update(level, pos.relative(direction));
         }
      }
   }

   private static void update(ClientLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (shaped(state)) {
         BlockState derived = derive(level, pos, state);
         if (derived != state) {
            level.setBlock(pos, derived, 18);
         }
      }
   }
}
