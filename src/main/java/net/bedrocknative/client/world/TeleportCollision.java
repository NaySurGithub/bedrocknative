package net.bedrocknative.client.world;

import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class TeleportCollision {
   private TeleportCollision() {
   }

   public static void infer(ClientLevel level, double x, double feetY, double z) {
      if (level != null) {
         BlockPos pos = BlockPos.containing(x, feetY, z);
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof CarrierBlock) {
            double top = feetY - pos.getY();
            if (top > 0.0 && top < 1.0) {
               CustomBlocks.limitCollisionTop(pos, (float)top);
            }
         }
      }
   }
}
