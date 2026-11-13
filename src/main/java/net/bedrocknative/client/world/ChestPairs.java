package net.bedrocknative.client.world;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.jspecify.annotations.Nullable;

public final class ChestPairs {
   private static final Map<Object, BlockPos> CLAIMS = Collections.synchronizedMap(new WeakHashMap<>());

   private ChestPairs() {
   }

   @Nullable
   public static BlockPos partnerOf(BlockPos pos, NbtMap tag) {
      return tag.containsKey("pairx", NbtType.INT) && tag.containsKey("pairz", NbtType.INT)
         ? new BlockPos(tag.getInt("pairx"), pos.getY(), tag.getInt("pairz"))
         : null;
   }

   public static void claim(ChestPairs.Blocks blocks, BlockPos pos, @Nullable BlockPos partner) {
      Object owner = blocks.owner(pos);
      if (owner != null) {
         if (partner == null) {
            CLAIMS.remove(owner);
         } else {
            CLAIMS.put(owner, partner.immutable());
         }
      }

      blockChanged(blocks, pos);
   }

   public static void blockChanged(ChestPairs.Blocks blocks, BlockPos pos) {
      refresh(blocks, pos);

      for (Direction side : Plane.HORIZONTAL) {
         refresh(blocks, pos.relative(side));
      }
   }

   static ChestType type(ChestPairs.Blocks blocks, BlockPos pos, BlockState state) {
      Object owner = blocks.owner(pos);
      BlockPos partner = owner == null ? null : CLAIMS.get(owner);
      if (partner != null && partner.getY() == pos.getY()) {
         int dx = partner.getX() - pos.getX();
         int dz = partner.getZ() - pos.getZ();
         if (Math.abs(dx) + Math.abs(dz) != 1) {
            return ChestType.SINGLE;
         } else {
            Object partnerOwner = blocks.owner(partner);
            if (partnerOwner != null && pos.equals(CLAIMS.get(partnerOwner))) {
               BlockState other = blocks.state(partner);
               if (state.getBlock() instanceof ChestBlock chest
                  && chest.chestCanConnectTo(other)
                  && other.hasProperty(ChestBlock.FACING)
                  && other.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                  Direction facing = (Direction)state.getValue(ChestBlock.FACING);
                  Direction towards = Direction.getNearest(dx, 0, dz, null);
                  if (towards == facing.getClockWise()) {
                     return ChestType.LEFT;
                  } else {
                     return towards == facing.getCounterClockWise() ? ChestType.RIGHT : ChestType.SINGLE;
                  }
               } else {
                  return ChestType.SINGLE;
               }
            } else {
               return ChestType.SINGLE;
            }
         }
      } else {
         return ChestType.SINGLE;
      }
   }

   private static void refresh(ChestPairs.Blocks blocks, BlockPos pos) {
      BlockState state = blocks.state(pos);
      if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
         ChestType type = type(blocks, pos, state);
         if (state.getValue(ChestBlock.TYPE) != type) {
            blocks.set(pos, (BlockState)state.setValue(ChestBlock.TYPE, type));
         }
      }
   }

   public interface Blocks {
      BlockState state(BlockPos var1);

      @Nullable
      Object owner(BlockPos var1);

      void set(BlockPos var1, BlockState var2);
   }
}
