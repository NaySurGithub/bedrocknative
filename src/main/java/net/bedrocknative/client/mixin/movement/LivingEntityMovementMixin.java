package net.bedrocknative.client.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMovementMixin {
   @Unique
   private static final float SOUL_SAND_ACCELERATION = 0.543991F;
   @Shadow
   protected boolean jumping;
   @Unique
   private boolean bedrocknative$sprintedLastTick;

   @Shadow
   protected abstract float getFlyingSpeed();

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
         ordinal = 0
      )}
   )
   private void bedrocknative$velocityThreshold(LivingEntity self, double x, double y, double z, Operation<Void> original) {
      if (BedrockMovement.simulates(self)) {
         Vec3 movement = self.getDeltaMovement();
         original.call(new Object[]{self, BedrockMovement.clearTiny(movement.x), BedrockMovement.clearTiny(movement.y), BedrockMovement.clearTiny(movement.z)});
      } else {
         original.call(new Object[]{self, x, y, z});
      }
   }

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;travelRidden(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V"
      )}
   )
   private void bedrocknative$ridesInTheRidersTick(LivingEntity self, Player controller, Vec3 input, Operation<Void> original) {
      if (!BedrockVehicles.predictable(self)) {
         original.call(new Object[]{self, controller, input});
      }
   }

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;applyEffectsFromBlocks()V"
      )}
   )
   private void bedrocknative$blockEffectsInTheRidersTick(LivingEntity self, Operation<Void> original) {
      if (!BedrockVehicles.predictable(self)) {
         original.call(new Object[]{self});
      }
   }

   @Inject(
      method = {"aiStep"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$floatVelocity(CallbackInfo ci) {
      LivingEntity self = (LivingEntity)this;
      this.bedrocknative$sprintedLastTick = self.isSprinting();
      if (BedrockMovement.appliesTo(self)) {
         BedrockMovement.tickGlideBoost();
         Vec3 movement = BedrockMovement.holdsStill(self) ? Vec3.ZERO : self.getDeltaMovement();
         self.setDeltaMovement((float)movement.x, (float)movement.y, (float)movement.z);
      }
   }

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"
      )}
   )
   private void bedrocknative$heldStill(LivingEntity self, Vec3 input, Operation<Void> original) {
      if (BedrockMovement.holdsStill(self)) {
         self.setDeltaMovement(Vec3.ZERO);
         if (self instanceof Player player) {
            BedrockMovement.travelled(player.getAbilities().flying, BedrockMovement.takeFlightToggle(), player.isFallFlying());
         }
      } else {
         original.call(new Object[]{self, input});
      }
   }

   @ModifyReturnValue(
      method = {"isImmobile"},
      at = {@At("RETURN")}
   )
   private boolean bedrocknative$serverImmobile(boolean original) {
      return original || BedrockMovement.immobile() && BedrockMovement.appliesTo(this);
   }

   @ModifyReturnValue(
      method = {"onClimbable"},
      at = {@At("RETURN")}
   )
   private boolean bedrocknative$scaffoldingNotClimbable(boolean original) {
      LivingEntity self = (LivingEntity)this;
      return original && (!BedrockMovement.appliesTo(self) || !self.getInBlockState().is(Blocks.SCAFFOLDING));
   }

   @WrapOperation(
      method = {"travelInAir"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"
      )}
   )
   private double bedrocknative$noGravityWhileDescending(LivingEntity self, Operation<Double> original) {
      return BedrockMovement.appliesTo(self) && BedrockMovement.scaffoldDescend() && !self.onGround() ? 0.0 : (Double)original.call(new Object[]{self});
   }

   @Inject(
      method = {"jumpFromGround"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$recordJump(CallbackInfo ci) {
      BedrockMovement.onJump(this);
   }

   @WrapOperation(
      method = {"jumpFromGround", "updateFallFlyingMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/Mth;sin(D)F"
      )}
   )
   private float bedrocknative$jumpSin(double angle, Operation<Float> original) {
      return BedrockMovement.appliesTo(this) ? (float)Math.sin(angle) : (Float)original.call(new Object[]{angle});
   }

   @WrapOperation(
      method = {"jumpFromGround"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/Mth;cos(D)F"
      )}
   )
   private float bedrocknative$jumpCos(double angle, Operation<Float> original) {
      return BedrockMovement.appliesTo(this) ? (float)Math.cos(angle) : (Float)original.call(new Object[]{angle});
   }

   @ModifyReturnValue(
      method = {"maxUpStep"},
      at = {@At("RETURN")}
   )
   private float bedrocknative$stepHeight(float original) {
      float vehicle = BedrockVehicles.stepHeight(this);
      return !Float.isNaN(vehicle) ? vehicle : (BedrockMovement.appliesTo(this) ? 0.5625F : original);
   }

   @Inject(
      method = {"getFrictionInfluencedSpeed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$groundSpeed(float friction, CallbackInfoReturnable<Float> cir) {
      LivingEntity self = (LivingEntity)this;
      if (BedrockMovement.appliesTo(self) && self.onGround() && self instanceof Player player && player.getAbilities().flying) {
         cir.setReturnValue(this.getFlyingSpeed());
      } else if (BedrockMovement.simulates(self) && self.onGround()) {
         float speed = self.getSpeed() * (0.21600002F / (friction * friction * friction));
         if (self.level().getBlockState(BedrockMovement.feetBlock(self)).is(Blocks.SOUL_SAND)) {
            float soulSpeed = (float)self.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY);
            speed *= 0.543991F + 0.45600897F * Math.clamp(soulSpeed, 0.0F, 1.0F);
         }

         cir.setReturnValue(speed);
      }
   }

   @WrapOperation(
      method = {"travelInAir"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/Block;getFriction()F"
      )}
   )
   private float bedrocknative$blockFriction(Block block, Operation<Float> original) {
      return BedrockMovement.simulates(this)
         ? BedrockMovement.friction((LivingEntity)this, block, (Float)original.call(new Object[]{block}))
         : (Float)original.call(new Object[]{block});
   }

   @Inject(
      method = {"handleOnClimbable"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$climb(Vec3 delta, CallbackInfoReturnable<Vec3> cir) {
      LivingEntity self = (LivingEntity)this;
      if (BedrockMovement.appliesTo(self)) {
         if (!self.onClimbable()) {
            cir.setReturnValue(delta);
         } else {
            self.resetFallDistance();
            boolean scaffolding = self.getInBlockState().is(Blocks.SCAFFOLDING);
            double y = !self.horizontalCollision && !this.jumping ? delta.y : (scaffolding ? 0.15 : 0.2);
            y = Math.max(y, -0.2);
            if (y < 0.0 && !scaffolding && self.isSuppressingSlidingDownLadder() && self instanceof Player) {
               y = 0.0;
            }

            cir.setReturnValue(new Vec3(delta.x, y, delta.z));
         }
      }
   }

   @WrapOperation(
      method = {"handleRelativeFrictionAndCalculateMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;onClimbable()Z"
      )}
   )
   private boolean bedrocknative$noJavaClimbBoost(LivingEntity self, Operation<Boolean> original) {
      return !BedrockMovement.appliesTo(self) && (Boolean)original.call(new Object[]{self});
   }

   @Inject(
      method = {"travelInAir"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$climbEndOfTick(Vec3 input, CallbackInfo ci) {
      LivingEntity self = (LivingEntity)this;
      if (BedrockMovement.appliesTo(self) && self.horizontalCollision && self.onClimbable()) {
         Vec3 movement = self.getDeltaMovement();
         self.setDeltaMovement(movement.x, self.getInBlockState().is(Blocks.SCAFFOLDING) ? 0.15 : 0.2, movement.z);
      }
   }

   @WrapOperation(
      method = {"updateFallFlyingMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private Vec3 bedrocknative$glideBoost(Vec3 movement, double x, double y, double z, Operation<Vec3> original) {
      LivingEntity self = (LivingEntity)this;
      if (BedrockMovement.appliesTo(self) && BedrockMovement.glideBoosting()) {
         Vec3 look = self.getLookAngle();
         movement = movement.add(
            look.x * 0.1 + (look.x * 1.5 - movement.x) * 0.5,
            look.y * 0.1 + (look.y * 1.5 - movement.y) * 0.5,
            look.z * 0.1 + (look.z * 1.5 - movement.z) * 0.5
         );
      }

      return (Vec3)original.call(new Object[]{movement, x, y, z});
   }

   @WrapOperation(
      method = {"travelInWater"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"
      )}
   )
   private boolean bedrocknative$stopSprintDrag(LivingEntity self, Operation<Boolean> original) {
      return (Boolean)original.call(new Object[]{self}) || BedrockMovement.appliesTo(self) && this.bedrocknative$sprintedLastTick;
   }

   @Inject(
      method = {"getFluidFallingAdjustedMovement"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$waterGravity(double baseGravity, boolean isFalling, Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
      LivingEntity self = (LivingEntity)this;
      if (BedrockMovement.appliesTo(self)) {
         cir.setReturnValue(baseGravity != 0.0 && !self.isSwimming() ? movement.subtract(0.0, baseGravity / 16.0, 0.0) : movement);
      }
   }
}
