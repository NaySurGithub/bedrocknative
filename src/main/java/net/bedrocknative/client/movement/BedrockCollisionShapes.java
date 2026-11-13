package net.bedrocknative.client.movement;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class BedrockCollisionShapes {
   private static final double DOOR = 0.1825;
   private static final VoxelShape NONE = Shapes.empty();
   private static final VoxelShape CHEST = Shapes.box(0.025, 0.0, 0.025, 0.975, 0.95, 0.975);
   private static final VoxelShape INSET_FULL_HEIGHT = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 1.0, 0.9375);
   private static final VoxelShape SCAFFOLDING_TOP = Shapes.or(
      Shapes.box(0.0, 0.875, 0.0, 1.0, 1.0, 1.0),
      Shapes.box(0.0, 0.0, 0.0, 0.125, 1.0, 0.125),
      Shapes.box(0.875, 0.0, 0.0, 1.0, 1.0, 0.125),
      Shapes.box(0.0, 0.0, 0.875, 0.125, 1.0, 1.0),
      Shapes.box(0.875, 0.0, 0.875, 1.0, 1.0, 1.0)
   );
   private static final VoxelShape CAULDRON = Shapes.or(
      Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
      Shapes.box(0.0, 0.0, 0.0, 0.125, 1.0, 1.0),
      Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.125),
      Shapes.box(0.875, 0.0, 0.0, 1.0, 1.0, 1.0),
      Shapes.box(0.0, 0.0, 0.875, 1.0, 1.0, 1.0)
   );
   private static final VoxelShape BREWING_STAND = Shapes.or(
      Shapes.box(0.025, 0.0, 0.025, 0.975, 0.125, 0.975), Shapes.box(0.4375, 0.0, 0.4375, 0.5625, 0.875, 0.5625)
   );
   private static final VoxelShape JAVA = Shapes.box(0.0, 0.0, 0.0, 0.5, 0.5, 0.5);
   private static final Map<BlockState, VoxelShape> CACHE = new ConcurrentHashMap<>();
   private static final Set<String> PROBE = Set.of(
      Arrays.stream(System.getProperty("bedrocknative.collisionProbe", "").split(",")).map(String::trim).filter(name -> !name.isEmpty()).toArray(String[]::new)
   );

   private BedrockCollisionShapes() {
   }

   public static @Nullable VoxelShape get(BlockState state, BlockPos pos, CollisionContext context, VoxelShape javaShape) {
      if (!PROBE.isEmpty() && PROBE.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath())) {
         return NONE;
      }

      if (!(state.getBlock() instanceof ScaffoldingBlock)) {
         if (state.getBlock() instanceof BellBlock) {
            return state.getValue(BellBlock.ATTACHMENT) == BellAttachType.FLOOR ? clipTop(javaShape, 0.8125) : null;
         }

         VoxelShape shape = CACHE.computeIfAbsent(state, BedrockCollisionShapes::compute);
         return shape == JAVA ? null : shape;
      } else {
         return context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? SCAFFOLDING_TOP : NONE;
      }
   }

   private static VoxelShape compute(BlockState state) {
      return switch (state.getBlock()) {
         case ChestBlock ignored -> chest(state);
         case EnderChestBlock ignored -> CHEST;
         case HoneyBlock ignored -> INSET_FULL_HEIGHT;
         case BedBlock ignored -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5625, 1.0);
         case LecternBlock ignored -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.9, 1.0);
         case EndPortalFrameBlock ignored -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.8125, 1.0);
         case ConduitBlock ignored -> Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75);
         case TurtleEggBlock ignored -> Shapes.box(0.1875, 0.0, 0.1875, 0.75, 0.45, 0.75);
         case SeaPickleBlock ignored -> NONE;
         case AbstractCauldronBlock ignored -> CAULDRON;
         case BrewingStandBlock ignored -> BREWING_STAND;
         case GrindstoneBlock ignored -> state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.WALL ? wallGrindstone(state) : JAVA;
         case TrapDoorBlock ignored -> state.getValue(TrapDoorBlock.OPEN)
            ? side(state.getValue(TrapDoorBlock.FACING).getOpposite(), 0.1825)
            : (state.getValue(TrapDoorBlock.HALF) == Half.TOP ? side(Direction.UP, 0.1825) : side(Direction.DOWN, 0.1825));
         case DoorBlock ignored -> door(state);
         case LanternBlock ignored -> state.getValue(LanternBlock.HANGING)
            ? Shapes.box(0.3125, 0.125, 0.3125, 0.6875, 0.625, 0.6875)
            : Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.5, 0.6875);
         case AnvilBlock ignored -> state.getValue(AnvilBlock.FACING).getAxis() == Axis.X
            ? Shapes.box(0.0, 0.0, 0.125, 1.0, 1.0, 0.875)
            : Shapes.box(0.125, 0.0, 0.0, 0.875, 1.0, 1.0);
         case IronBarsBlock ignored -> bars(state);
         case WallBlock ignored -> wall(state);
         default -> state.is(Blocks.CACTUS) ? INSET_FULL_HEIGHT : (state.is(Blocks.DRAGON_EGG) ? Shapes.block() : JAVA);
      };
   }

   private static VoxelShape wallGrindstone(BlockState state) {
      return state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING).getAxis() == Axis.Z
         ? Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 1.0)
         : Shapes.box(0.0, 0.125, 0.125, 1.0, 0.875, 0.875);
   }

   private static VoxelShape chest(BlockState state) {
      Direction joined = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? null : ChestBlock.getConnectedDirection(state);
      return Shapes.box(
         joined == Direction.WEST ? 0.0 : 0.025,
         0.0,
         joined == Direction.NORTH ? 0.0 : 0.025,
         joined == Direction.EAST ? 1.0 : 0.975,
         0.95,
         joined == Direction.SOUTH ? 1.0 : 0.975
      );
   }

   private static VoxelShape door(BlockState state) {
      Direction facing = state.getValue(DoorBlock.FACING);
      if (state.getValue(DoorBlock.OPEN)) {
         facing = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT ? facing.getCounterClockWise() : facing.getClockWise();
      }

      return side(facing.getOpposite(), 0.1825);
   }

   private static VoxelShape bars(BlockState state) {
      boolean north = state.getValue(CrossCollisionBlock.NORTH);
      boolean east = state.getValue(CrossCollisionBlock.EAST);
      boolean south = state.getValue(CrossCollisionBlock.SOUTH);
      boolean west = state.getValue(CrossCollisionBlock.WEST);
      if (!north && !east && !south && !west) {
         return Shapes.box(0.4375, 0.0, 0.4375, 0.5625, 1.0, 0.5625);
      }

      VoxelShape shape = NONE;
      if (north) {
         shape = Shapes.or(shape, Shapes.box(0.4375, 0.0, 0.0, 0.5625, 1.0, 0.5));
      }

      if (south) {
         shape = Shapes.or(shape, Shapes.box(0.4375, 0.0, 0.5, 0.5625, 1.0, 1.0));
      }

      if (west) {
         shape = Shapes.or(shape, Shapes.box(0.0, 0.0, 0.4375, 0.5, 1.0, 0.5625));
      }

      if (east) {
         shape = Shapes.or(shape, Shapes.box(0.5, 0.0, 0.4375, 1.0, 1.0, 0.5625));
      }

      return shape;
   }

   private static VoxelShape wall(BlockState state) {
      boolean north = state.getValue(WallBlock.NORTH) != WallSide.NONE;
      boolean east = state.getValue(WallBlock.EAST) != WallSide.NONE;
      boolean south = state.getValue(WallBlock.SOUTH) != WallSide.NONE;
      boolean west = state.getValue(WallBlock.WEST) != WallSide.NONE;
      boolean straight = north && south && !east && !west || east && west && !north && !south;
      double inset = !state.getValue(WallBlock.UP) && straight ? 0.3125 : 0.25;
      return Shapes.box(west ? 0.0 : inset, 0.0, north ? 0.0 : inset, east ? 1.0 : 1.0 - inset, 1.5, south ? 1.0 : 1.0 - inset);
   }

   private static VoxelShape side(Direction side, double thickness) {
      return switch (side) {
         case DOWN -> Shapes.box(0.0, 0.0, 0.0, 1.0, thickness, 1.0);
         case UP -> Shapes.box(0.0, 1.0 - thickness, 0.0, 1.0, 1.0, 1.0);
         case NORTH -> Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, thickness);
         case SOUTH -> Shapes.box(0.0, 0.0, 1.0 - thickness, 1.0, 1.0, 1.0);
         case WEST -> Shapes.box(0.0, 0.0, 0.0, thickness, 1.0, 1.0);
         case EAST -> Shapes.box(1.0 - thickness, 0.0, 0.0, 1.0, 1.0, 1.0);
      };
   }

   private static VoxelShape clipTop(VoxelShape shape, double maxY) {
      return shape.isEmpty() ? shape : Shapes.join(shape, Shapes.box(0.0, 0.0, 0.0, 1.0, maxY, 1.0), BooleanOp.AND);
   }
}
