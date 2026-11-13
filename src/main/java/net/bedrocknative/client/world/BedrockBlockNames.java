package net.bedrocknative.client.world;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.bedrocknative.world.block.ContainerNames;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.cloudburstmc.nbt.NbtMap;
import org.jspecify.annotations.Nullable;

public final class BedrockBlockNames {
   private static final Map<BlockEntity, String> NAMES = Collections.synchronizedMap(new WeakHashMap<>());

   private BedrockBlockNames() {
   }

   static void record(ClientLevel level, BlockPos pos, NbtMap tag) {
      BlockEntity entity = level.getBlockEntity(pos);
      if (entity != null) {
         String name = ContainerNames.customName(tag);
         if (name == null) {
            NAMES.remove(entity);
         } else {
            NAMES.put(entity, name);
         }
      }
   }

   public static @Nullable String customNameAt(ClientLevel level, BlockPos pos) {
      String opened = nameAt(level, pos);
      String partner = null;
      BlockState state = level.getBlockState(pos);
      if (opened == null && state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
         partner = nameAt(level, ChestBlock.getConnectedBlockPos(pos, state));
      }

      return ContainerNames.pick(opened, partner);
   }

   private static @Nullable String nameAt(ClientLevel level, BlockPos pos) {
      BlockEntity entity = level.getBlockEntity(pos);
      return entity == null ? null : NAMES.get(entity);
   }
}
