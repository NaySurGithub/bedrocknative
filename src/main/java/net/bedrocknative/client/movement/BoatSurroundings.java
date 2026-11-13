package net.bedrocknative.client.movement;

import net.bedrocknative.movement.BoatPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class BoatSurroundings {
   private static final float WATER_SURFACE_LIFT = 0.11111111F;
   private static final float MIN_WATER_DEPTH = 0.25F;

   private BoatSurroundings() {
   }

   static BoatPhysics.Surroundings of(Entity boat) {
      Level level = boat.level();
      AABB box = boat.getBoundingBox();
      double surfaceAbove = surfaceAbove(level, box);
      if (!Double.isNaN(surfaceAbove)) {
         return BoatPhysics.Surroundings.water(BoatPhysics.Medium.UNDER_WATER, (float)(surfaceAbove - box.minY));
      } else {
         double waterLevel = waterLevel(level, box);
         if (waterLevel - box.minY >= 0.25) {
            return BoatPhysics.Surroundings.water(BoatPhysics.Medium.WATER, (float)(waterLevel - box.minY));
         } else {
            float friction = groundFriction(level, box);
            return friction > 0.0F ? BoatPhysics.Surroundings.land(friction) : BoatPhysics.Surroundings.air();
         }
      }
   }

   private static double surfaceAbove(Level level, AABB box) {
      double top = box.maxY + 0.001;
      int x0 = Mth.floor(box.minX);
      int x1 = Mth.ceil(box.maxX);
      int z0 = Mth.floor(box.minZ);
      int z1 = Mth.ceil(box.maxZ);
      MutableBlockPos pos = new MutableBlockPos();
      boolean under = false;

      for (int x = x0; x < x1 && !under; x++) {
         for (int y = Mth.floor(box.maxY); y < Mth.ceil(top) && !under; y++) {
            for (int z = z0; z < z1; z++) {
               FluidState fluid = level.getFluidState(pos.set(x, y, z));
               if (fluid.is(FluidTags.WATER) && top < y + fluid.getHeight(level, pos)) {
                  under = true;
                  break;
               }
            }
         }
      }

      if (!under) {
         return Double.NaN;
      } else {
         for (int y = Mth.floor(box.maxY); y < Mth.floor(box.maxY) + 32; y++) {
            float height = 0.0F;

            for (int x = x0; x < x1; x++) {
               for (int zx = z0; zx < z1; zx++) {
                  FluidState fluid = level.getFluidState(pos.set(x, y, zx));
                  if (fluid.is(FluidTags.WATER)) {
                     height = Math.max(height, fluid.getHeight(level, pos));
                  }
               }
            }

            if (height < 1.0F) {
               return y + height;
            }
         }

         return Mth.floor(box.maxY) + 32;
      }
   }

   private static double waterLevel(Level level, AABB box) {
      int x0 = Mth.floor(box.minX);
      int x1 = Mth.ceil(box.maxX);
      int y0 = Mth.floor(box.minY);
      int y1 = Mth.ceil(box.minY + 0.001);
      int z0 = Mth.floor(box.minZ);
      int z1 = Mth.ceil(box.maxZ);
      double surface = Double.NEGATIVE_INFINITY;
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = x0; x < x1; x++) {
         for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
               FluidState fluid = level.getFluidState(pos.set(x, y, z));
               if (fluid.is(FluidTags.WATER)) {
                  surface = Math.max(surface, (double)(y + Math.min(1.0F, fluid.getHeight(level, pos) + 0.11111111F)));
               }
            }
         }
      }

      return surface;
   }

   static float groundFriction(Level level, AABB bb) {
      BlockPos below = BlockPos.containing((bb.minX + bb.maxX) / 2.0, bb.minY - 0.001, (bb.minZ + bb.maxZ) / 2.0);
      BlockState under = level.getBlockState(below);
      return !(under.getBlock() instanceof LilyPadBlock) && !under.getCollisionShape(level, below).isEmpty()
         ? BedrockMovement.friction(under)
         : averageFriction(level, bb);
   }

   private static float averageFriction(Level level, AABB bb) {
      AABB box = new AABB(bb.minX, bb.minY - 0.001, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
      int x0 = Mth.floor(box.minX) - 1;
      int x1 = Mth.ceil(box.maxX) + 1;
      int y0 = Mth.floor(box.minY) - 1;
      int y1 = Mth.ceil(box.maxY) + 1;
      int z0 = Mth.floor(box.minZ) - 1;
      int z1 = Mth.ceil(box.maxZ) + 1;
      VoxelShape shape = Shapes.create(box);
      float friction = 0.0F;
      int count = 0;
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = x0; x < x1; x++) {
         for (int z = z0; z < z1; z++) {
            int edges = (x != x0 && x != x1 - 1 ? 0 : 1) + (z != z0 && z != z1 - 1 ? 0 : 1);
            if (edges != 2) {
               for (int y = y0; y < y1; y++) {
                  if (edges <= 0 || y != y0 && y != y1 - 1) {
                     BlockState state = level.getBlockState(pos.set(x, y, z));
                     if (!(state.getBlock() instanceof LilyPadBlock)
                        && Shapes.joinIsNotEmpty(state.getCollisionShape(level, pos).move(pos), shape, BooleanOp.AND)) {
                        friction += BedrockMovement.friction(state);
                        count++;
                     }
                  }
               }
            }
         }
      }

      return count == 0 ? 0.0F : friction / count;
   }

   static String describe(Entity boat) {
      BoatPhysics.Surroundings around = of(boat);
      return around.medium()
         + (
            around.medium() == BoatPhysics.Medium.LAND
               ? " friction " + around.friction()
               : (around.medium() == BoatPhysics.Medium.AIR ? "" : " depth " + around.depth())
         )
         + " ("
         + BoatPhysics.friction(around)
         + ")";
   }
}
