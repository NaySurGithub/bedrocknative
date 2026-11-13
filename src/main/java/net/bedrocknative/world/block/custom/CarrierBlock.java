package net.bedrocknative.world.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class CarrierBlock extends Block {
   public static final int POOL = 65536;
   public static final IntegerProperty SLOT = IntegerProperty.create("slot", 0, 65535);
   private static volatile boolean registering = true;

   CarrierBlock(Properties properties) {
      super(properties);
   }

   static void registered() {
      registering = false;
   }

   public boolean hasDynamicShape() {
      return registering || super.hasDynamicShape();
   }

   @Nullable
   public static CustomBlockState data(BlockState state) {
      return state.getBlock() instanceof CarrierBlock ? CustomBlockSet.current().data((Integer)state.getValue(SLOT)) : null;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{SLOT});
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      CustomBlockState data = data(state);
      return data == null ? Shapes.block() : data.selectionShape();
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      CustomBlockState data = data(state);
      VoxelShape shape = data == null ? Shapes.block() : data.collisionShape();
      float top = CustomBlocks.collisionTop(pos);
      return top > 0.0F && top < 1.0F ? Shapes.join(shape, Shapes.box(0.0, 0.0, 0.0, 1.0, top, 1.0), BooleanOp.AND) : shape;
   }

   protected VoxelShape getOcclusionShape(BlockState state) {
      CustomBlockState data = data(state);
      return data != null && !data.occludes() ? Shapes.empty() : Shapes.block();
   }

   protected int getLightDampening(BlockState state) {
      CustomBlockState data = data(state);
      return data == null ? 15 : data.lightDampening();
   }

   protected boolean propagatesSkylightDown(BlockState state) {
      CustomBlockState data = data(state);
      return data != null && data.lightDampening() == 0;
   }

   protected boolean useShapeForLightOcclusion(BlockState state) {
      return false;
   }

   protected SoundType getSoundType(BlockState state) {
      return CustomBlocks.soundType((Integer)state.getValue(SLOT));
   }

   protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
      CustomBlockState data = data(state);
      if (data == null) {
         return super.getDestroyProgress(state, player, level, pos);
      } else {
         float time = data.destroyTime();
         if (time < 0.0F) {
            return 0.0F;
         } else {
            return time == 0.0F ? 1.0F : player.getDestroySpeed(state) / time / 30.0F;
         }
      }
   }
}
