package net.bedrocknative.client.mixin.movement;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.bedrocknative.client.movement.BedrockDepenetration;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.bedrocknative.client.movement.ServerBoxed;
import net.bedrocknative.movement.ServerBox;
import net.bedrocknative.movement.StepHeights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMovementMixin implements ServerBoxed {
   @Unique
   private static final ImmutableList<Axis> BEDROCK_AXIS_ORDER = ImmutableList.of(Axis.Y, Axis.X, Axis.Z);
   @Shadow
   protected boolean wasTouchingWater;
   @Unique
   private @Nullable ServerBox bedrocknative$serverBox;

   @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$noEntityPush(double xa, double ya, double za, CallbackInfo ci) {
      if (BedrockMovement.appliesTo(this)) {
         ci.cancel();
      }
   }

   @Shadow
   protected abstract double getEffectiveGravity();

   @WrapOperation(
      method = "collideWithShapes",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/core/Direction;axisStepOrder(Lnet/minecraft/world/phys/Vec3;)Lcom/google/common/collect/ImmutableList;"
      )
   )
   private static ImmutableList<Axis> bedrocknative$axisOrder(Vec3 movement, Operation<ImmutableList<Axis>> original) {
      return BedrockMovement.active() ? BEDROCK_AXIS_ORDER : (ImmutableList)original.call(new Object[]{movement});
   }

   @WrapOperation(
      method = "collideWithShapes",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/shapes/Shapes;collide(Lnet/minecraft/core/Direction$Axis;Lnet/minecraft/world/phys/AABB;Ljava/lang/Iterable;D)D"
      )
   )
   private static double bedrocknative$clipTolerance(Axis axis, AABB box, Iterable<VoxelShape> shapes, double distance, Operation<Double> original) {
      if (BedrockMovement.active() && distance != 0.0) {
         double back = Math.copySign(0.001, distance);
         AABB shifted = BedrockMovement.floatBox(box).move(axis.choose(-back, 0.0, 0.0), axis.choose(0.0, -back, 0.0), axis.choose(0.0, 0.0, -back));
         return (Double)original.call(new Object[]{axis, shifted, shapes, distance + back}) - back;
      } else {
         return (Double)original.call(new Object[]{axis, box, shapes, distance});
      }
   }

   @ModifyVariable(method = "collide", at = @At("HEAD"), argsOnly = true)
   private Vec3 bedrocknative$depenetrate(Vec3 movement) {
      return BedrockMovement.appliesTo(this) ? BedrockDepenetration.apply((Entity)(Object)this, movement) : movement;
   }

   @WrapOperation(method = "collide", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;onGround()Z"))
   private boolean bedrocknative$flyingStep(Entity self, Operation<Boolean> original) {
      return (Boolean)original.call(new Object[]{self}) || BedrockMovement.appliesTo(self) && self instanceof Player player && player.getAbilities().flying;
   }

   @ModifyReturnValue(method = "collectCandidateStepUpHeights", at = @At("RETURN"))
   private static float[] bedrocknative$stepUpToFloat(float[] heights, AABB box, List<VoxelShape> shapes, float maxStep, float collided) {
      if (!BedrockMovement.active()) {
         return heights;
      }

      float[] raised = new float[heights.length];

      for (int i = 0; i < heights.length; i++) {
         raised[i] = StepHeights.raise(box.minY, heights[i], maxStep);
      }

      return raised;
   }

   @ModifyConstant(method = "move", constant = @Constant(doubleValue = 1.0E-7, ordinal = 1))
   private double bedrocknative$keepTinyMoves(double original) {
      return BedrockMovement.simulates(this) ? 0.0 : original;
   }

   @Inject(method = "move", at = @At("TAIL"))
   private void bedrocknative$floatPosition(MoverType moverType, Vec3 delta, CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (moverType == MoverType.SELF && BedrockMovement.simulates(self)) {
         self.setPos((float)self.getX(), (float)self.getY(), (float)self.getZ());
      }
   }

   @WrapOperation(method = {"getInputVector", "calculateViewVector"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
   private static float bedrocknative$inputSin(double angle, Operation<Float> original) {
      return BedrockMovement.active() ? (float)Math.sin(angle) : (Float)original.call(new Object[]{angle});
   }

   @WrapOperation(method = {"getInputVector", "calculateViewVector"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;cos(D)F"))
   private static float bedrocknative$inputCos(double angle, Operation<Float> original) {
      return BedrockMovement.active() ? (float)Math.cos(angle) : (Float)original.call(new Object[]{angle});
   }

   @ModifyReturnValue(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("RETURN"))
   private AABB bedrocknative$floatBoundingBox(AABB original) {
      if (BedrockMovement.active() && (Object)this instanceof AbstractBoat) {
         return BedrockMovement.floatBox(BedrockVehicles.boatBox(original));
      }

      if (BedrockMovement.simulates(this)) {
         return BedrockMovement.floatBox(original);
      }

      ServerBox box = this.bedrocknative$serverBox;
      return box != null && BedrockMovement.active() ? box.apply(original) : original;
   }

   @Override
   public @Nullable ServerBox bedrocknative$serverBox() {
      return this.bedrocknative$serverBox;
   }

   @Override
   public void bedrocknative$setServerBox(@Nullable ServerBox box) {
      this.bedrocknative$serverBox = box;
   }

   @ModifyReturnValue(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("RETURN"))
   private BlockPos bedrocknative$blockBelow(BlockPos original) {
      return BedrockMovement.simulates(this) ? BedrockMovement.blockBelow((Entity)(Object)this) : original;
   }

   @ModifyReturnValue(method = "getBlockSpeedFactor", at = @At("RETURN"))
   private float bedrocknative$noSpeedFactor(float original) {
      return BedrockMovement.simulates(this) ? 1.0F : original;
   }

   @WrapOperation(method = "getBlockJumpFactor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getJumpFactor()F"))
   private float bedrocknative$honeyJump(Block block, Operation<Float> original) {
      return BedrockMovement.appliesTo(this) && block instanceof HoneyBlock ? 0.6F : (Float)original.call(new Object[]{block});
   }

   @WrapOperation(
      method = "applyEffectsFromBlocks(Ljava/util/List;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/Block;stepOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;)V"
      )
   )
   private void bedrocknative$honeyStep(Block block, Level level, BlockPos pos, BlockState state, Entity entity, Operation<Void> original) {
      if (block instanceof HoneyBlock && BedrockMovement.appliesTo(entity)) {
         Vec3 movement = entity.getDeltaMovement();
         if (Math.abs(movement.y) < 0.1 && !entity.isSteppingCarefully()) {
            double scale = 0.4 + Math.abs(movement.y) * 0.2;
            entity.setDeltaMovement(movement.multiply(scale, 1.0, scale));
         }
      }

      original.call(new Object[]{block, level, pos, state, entity});
   }

   @Inject(method = "restituteMovementAfterCollisions", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$bounce(BlockState effectState, boolean xCollision, boolean zCollision, Vec3 movement, CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (BedrockMovement.appliesTo(self)
         && self.verticalCollision
         && self.verticalCollisionBelow
         && !self.isSuppressingBounce()
         && !effectState.is(BlockTags.SUPPRESSES_BOUNCE)) {
         double restitution = effectState.getBlock().getBounceRestitution();
         if (!(restitution <= 0.0)) {
            Vec3 current = self.getDeltaMovement();
            double falling = -current.y;
            double y = 0.0;
            if (falling >= 0.08) {
               double gravity = this.getEffectiveGravity();
               double tau = 0.0;
               if (gravity > 1.0E-7) {
                  double impact = Math.sqrt(falling * falling + 2.0 * gravity * Math.max(0.0, -movement.y));
                  tau = (impact - falling) / gravity;
               }

               y = restitution * falling + gravity * tau;
            }

            self.setDeltaMovement(xCollision ? 0.0 : current.x, y, zCollision ? 0.0 : current.z);
            ci.cancel();
         }
      }
   }

   @Inject(method = "updateFluidInteraction", at = @At("RETURN"))
   private void bedrocknative$waterContact(CallbackInfoReturnable<Boolean> cir) {
      if (BedrockMovement.appliesTo(this)) {
         this.wasTouchingWater = BedrockMovement.touchesWater((Entity)(Object)this);
      }
   }
}
