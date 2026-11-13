package net.bedrocknative.client.mixin.movement;

import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Player.class})
public abstract class PlayerMovementMixin {
   @Unique
   private static final double EDGE_STEP = 0.05;
   @Unique
   private static final double EDGE_PROBE = 0.568125F;
   @Unique
   private boolean bedrocknative$restoreFlying;

   @Inject(
      method = {"maybeBackOffFromEdge"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$sneakEdge(Vec3 delta, MoverType moverType, CallbackInfoReturnable<Vec3> cir) {
      if (BedrockMovement.appliesTo(this)) {
         Player self = (Player)this;
         if (!self.getAbilities().flying
            && !(delta.y > 0.0)
            && (moverType == MoverType.SELF || moverType == MoverType.PLAYER)
            && self.isShiftKeyDown()
            && (self.onGround() || self.fallDistance < 0.6 && !bedrocknative$canFall(self, 0.0, 0.0, 0.6 - self.fallDistance))) {
            double x = delta.x;
            double z = delta.z;

            while (x != 0.0 && bedrocknative$canFall(self, x, 0.0, 0.568125F)) {
               x = bedrocknative$towardsZero(x);
            }

            while (z != 0.0 && bedrocknative$canFall(self, 0.0, z, 0.568125F)) {
               z = bedrocknative$towardsZero(z);
            }

            while (x != 0.0 && z != 0.0 && bedrocknative$canFall(self, x, z, 0.568125F)) {
               x = bedrocknative$towardsZero(x);
               z = bedrocknative$towardsZero(z);
            }

            if (x == 0.0 && delta.x != 0.0 || z == 0.0 && delta.z != 0.0) {
               Vec3 velocity = self.getDeltaMovement();
               self.setDeltaMovement(x == 0.0 ? 0.0 : velocity.x, velocity.y, z == 0.0 ? 0.0 : velocity.z);
            }

            cir.setReturnValue(new Vec3(x, delta.y, z));
         } else {
            cir.setReturnValue(delta);
         }
      }
   }

   @Shadow
   protected abstract boolean canGlide();

   @Inject(
      method = {"travel"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$flyBeforeMove(Vec3 input, CallbackInfo ci) {
      Player self = (Player)this;
      if (BedrockMovement.appliesTo(self)) {
         Abilities abilities = self.getAbilities();
         boolean toggled = BedrockMovement.takeFlightToggle();
         this.bedrocknative$updateGlide(self);
         BedrockMovement.travelled(abilities.flying, toggled, self.isFallFlying());
         boolean up = ((LivingEntityAccessor)self).bedrocknative$isJumping();
         boolean down = BedrockMovement.sneakKey((LocalPlayer)self);
         if (abilities.flying && !self.isPassenger()) {
            float speed = abilities.getFlyingSpeed() * BedrockMovement.verticalFlySpeed();
            Vec3 movement = self.getDeltaMovement();
            double y = up && down
               ? 0.0
               : (
                  up
                     ? movement.y + speed * 3.0F
                     : (down ? movement.y - speed * 4.4F : (input.x == 0.0 && input.z == 0.0 && !self.isSpectator() ? movement.y * 0.375 : movement.y))
               );
            self.setDeltaMovement(movement.x, y, movement.z);
         }

         if (toggled) {
            this.bedrocknative$restoreFlying = true;
            abilities.flying = !abilities.flying;
         }

         this.bedrocknative$scaffolding(self, abilities.flying, up, down);
      }
   }

   @Unique
   private void bedrocknative$updateGlide(Player self) {
      if (BedrockMovement.takeGlideAfterFlight() && !self.onClimbable()) {
         self.tryToStartFallFlying();
      }

      if (self.isFallFlying() && !this.canGlide()) {
         self.stopFallFlying();
      }
   }

   @Unique
   private void bedrocknative$scaffolding(Player self, boolean flying, boolean up, boolean down) {
      BedrockMovement.setScaffoldDescend(false);
      Vec3 movement = self.getDeltaMovement();
      if (up && !flying && BedrockMovement.scaffoldingAt(self, 0)) {
         BedrockMovement.cancelJump();
         self.setDeltaMovement(movement.x, 0.15, movement.z);
      } else if (down && !up && (BedrockMovement.scaffoldingAt(self, 0) || BedrockMovement.scaffoldingAt(self, 1))) {
         self.setDeltaMovement(movement.x, -0.15, movement.z);
         BedrockMovement.setScaffoldDescend(true);
      }
   }

   @Inject(
      method = {"travel"},
      at = {@At("RETURN")}
   )
   private void bedrocknative$flyAfterMove(Vec3 input, CallbackInfo ci) {
      Player self = (Player)this;
      Abilities abilities = self.getAbilities();
      boolean movedFlying = abilities.flying;
      if (this.bedrocknative$restoreFlying) {
         this.bedrocknative$restoreFlying = false;
         abilities.flying = !abilities.flying;
         if (!abilities.flying) {
            BedrockMovement.endedFlight();
         }
      }

      if (BedrockMovement.appliesTo(self) && (movedFlying || abilities.flying) && !self.isPassenger()) {
         boolean idle = input.x == 0.0 && input.z == 0.0;
         float damping = abilities.flying && idle ? (self.isSpectator() ? 0.75F : 0.375F) : 1.0F;
         Vec3 movement = self.getDeltaMovement();
         self.setDeltaMovement(movement.x * damping, movedFlying && self.verticalCollision ? 0.0 : movement.y, movement.z * damping);
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
         opcode = 181,
         shift = Shift.AFTER
      )}
   )
   private void bedrocknative$noClip(CallbackInfo ci) {
      Player self = (Player)this;
      if (BedrockMovement.appliesTo(self)) {
         self.noPhysics = self.noPhysics || BedrockMovement.noClip() || !BedrockMovement.collision();
      }
   }

   @Inject(
      method = {"canPlayerFitWithinBlocksAndEntitiesWhen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$fitsWithFloatBox(Pose pose, CallbackInfoReturnable<Boolean> cir) {
      Player self = (Player)this;
      if (BedrockMovement.appliesTo(self)) {
         AABB box = BedrockMovement.floatBox(self.getDimensions(pose).makeBoundingBox(self.position())).deflate(1.0E-7);
         cir.setReturnValue(self.level().noCollision(self, box));
      }
   }

   @Unique
   private static double bedrocknative$towardsZero(double value) {
      return Math.abs(value) <= 0.05 ? 0.0 : value - Math.signum(value) * 0.05;
   }

   @Unique
   private static boolean bedrocknative$canFall(Player player, double dx, double dz, double depth) {
      AABB box = player.getBoundingBox().deflate(0.025, 0.0, 0.025);
      return player.level().noCollision(player, new AABB(box.minX + dx, box.minY - depth, box.minZ + dz, box.maxX + dx, box.minY, box.maxZ + dz));
   }
}
