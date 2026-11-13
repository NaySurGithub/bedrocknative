package net.bedrocknative.client.world;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.cloudburstmc.nbt.NbtMap;

public final class BedrockJukeboxDiscs {
   private static final Set<Long> RECORDS = ConcurrentHashMap.newKeySet();

   private BedrockJukeboxDiscs() {
   }

   public static void clear() {
      RECORDS.clear();
   }

   public static boolean hasRecord(BlockPos pos) {
      return RECORDS.contains(pos.asLong());
   }

   public static boolean holdsRecord(NbtMap tag) {
      NbtMap item = tag.getCompound("RecordItem", NbtMap.EMPTY);
      String name = item.getString("Name", "");
      return !name.isEmpty() && !name.equals("minecraft:air") && item.getByte("Count", (byte)1) > 0;
   }

   public static BlockState adjust(BlockPos pos, BlockState state) {
      if (!state.is(Blocks.JUKEBOX)) {
         RECORDS.remove(pos.asLong());
         return state;
      } else {
         return (BlockState)state.setValue(BlockStateProperties.HAS_RECORD, hasRecord(pos));
      }
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      boolean has = holdsRecord(tag);
      if (has) {
         RECORDS.add(pos.asLong());
      } else {
         RECORDS.remove(pos.asLong());
      }

      BlockState state = level.getBlockState(pos);
      if (state.is(Blocks.JUKEBOX) && (Boolean)state.getValue(BlockStateProperties.HAS_RECORD) != has) {
         level.setServerVerifiedBlockState(pos, (BlockState)state.setValue(BlockStateProperties.HAS_RECORD, has), 19);
      }
   }
}
