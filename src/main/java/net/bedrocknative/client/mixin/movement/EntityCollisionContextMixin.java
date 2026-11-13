package net.bedrocknative.client.mixin.movement;

import net.bedrocknative.client.movement.BedrockCollisionShapes;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EntityCollisionContext.class})
public abstract class EntityCollisionContextMixin {
   @Shadow
   @Final
   @Nullable
   private Entity entity;

   @Inject(
      method = {"getCollisionShape"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bedrocknative$bedrockShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
      if (BedrockMovement.simulates(this.entity)) {
         VoxelShape shape = BedrockCollisionShapes.get(state, pos, (CollisionContext)this, (VoxelShape)cir.getReturnValue());
         if (shape != null) {
            cir.setReturnValue(shape);
         }
      }
   }
}
