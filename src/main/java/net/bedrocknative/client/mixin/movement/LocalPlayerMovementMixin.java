package net.bedrocknative.client.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.bedrocknative.movement.GroundContact;
import net.bedrocknative.movement.SprintRules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LocalPlayer.class})
public abstract class LocalPlayerMovementMixin extends AbstractClientPlayer {
   @Shadow
   public ClientInput input;
   @Shadow
   private boolean crouching;
   @Unique
   private Vec3 bedrocknative$lastIntended = Vec3.ZERO;
   @Unique
   private Vec3 bedrocknative$lastMoved = Vec3.ZERO;
   @Unique
   private int bedrocknative$slowTicks;
   @Unique
   private boolean bedrocknative$wasShiftDown;
   @Unique
   private boolean bedrocknative$wasOnGround;

   private LocalPlayerMovementMixin(ClientLevel level, GameProfile profile) {
      super(level, profile);
   }

   @Shadow
   protected abstract boolean isSprintingPossible(boolean var1);

   @Inject(
      method = {"aiStep"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$rememberShift(CallbackInfo ci) {
      this.bedrocknative$wasShiftDown = this.input.keyPresses.shift();
   }

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;tick()V",
         shift = Shift.AFTER
      )}
   )
   private void bedrocknative$crouchThisTick(CallbackInfo ci) {
      if (BedrockMovement.active()) {
         this.crouching = !this.getAbilities().flying
            && !this.isSwimming()
            && !this.isPassenger()
            && this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)
            && (this.isShiftKeyDown() || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));
      }
   }

   @Inject(
      method = {"modifyInput"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockInput(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
      if (BedrockMovement.active()) {
         boolean stoppedSneaking = this.bedrocknative$wasShiftDown && !this.input.keyPresses.shift() && !this.getAbilities().flying;
         boolean slowed = (this.crouching || stoppedSneaking || this.isVisuallyCrawling() || this.isFallFlying()) && !this.isInWater() && !this.isInLava();
         this.bedrocknative$slowTicks = slowed ? this.bedrocknative$slowTicks + 1 : 0;
         if (input.lengthSquared() == 0.0F) {
            cir.setReturnValue(input);
         } else {
            Vec2 result = input.scale(0.98F);
            if (slowed) {
               float sneak = this.bedrocknative$slowTicks > 2 ? (float)this.getAttributeValue(Attributes.SNEAKING_SPEED) : 0.3F;
               result = result.scale(Math.clamp(sneak, 0.0F, 1.0F));
            }

            if (this.isUsingItem() && !this.isPassenger()) {
               result = result.scale(BedrockMovement.usingItemInput(this.getUseItem()));
            }

            cir.setReturnValue(result);
         }
      }
   }

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"
      )}
   )
   private void bedrocknative$noJavaFlyVertical(LocalPlayer self, Vec3 movement, Operation<Void> original) {
      if (!BedrockMovement.active()) {
         original.call(new Object[]{self, movement});
      }
   }

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;onUpdateAbilities()V"
      )}
   )
   private void bedrocknative$flightToggled(CallbackInfo ci) {
      if (BedrockMovement.active()) {
         BedrockMovement.toggleFlight();
      }
   }

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z",
         ordinal = 1
      )}
   )
   private boolean bedrocknative$noLandingStop(LocalPlayer self, Operation<Boolean> original) {
      return !BedrockMovement.active() && (Boolean)original.call(new Object[]{self});
   }

   @Inject(
      method = {"move"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$beforeMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
      if (moverType == MoverType.SELF) {
         this.bedrocknative$lastIntended = delta;
         this.bedrocknative$lastMoved = this.position();
         this.bedrocknative$wasOnGround = this.onGround();
      }
   }

   @Inject(
      method = {"move"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$afterMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
      if (moverType == MoverType.SELF) {
         this.bedrocknative$lastMoved = this.position().subtract(this.bedrocknative$lastMoved);
         if (BedrockMovement.active() && GroundContact.keepsGround(this.getAbilities().flying, this.bedrocknative$wasOnGround, this.verticalCollision, delta.y)
            )
          {
            this.setOnGround(true);
         }
      }
   }

   @Inject(
      method = {"rideTick"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$vehicleTick(CallbackInfo ci) {
      if (BedrockMovement.active()) {
         BedrockVehicles.tick((LocalPlayer)this);
      }
   }

   @Inject(
      method = {"shouldStopSwimSprinting"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$swimSprintStop(CallbackInfoReturnable<Boolean> cir) {
      if (BedrockMovement.active()) {
         cir.setReturnValue(!this.isSprintingPossible(true) || !this.isInWater() || !this.input.hasForwardImpulse() && !this.input.keyPresses.shift());
      }
   }

   @Inject(
      method = {"canStartSprinting"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$noSprintStartAfterSneak(CallbackInfoReturnable<Boolean> cir) {
      if (BedrockMovement.active()
         && SprintRules.sneakBlocksSprintStart(this.bedrocknative$wasShiftDown, this.getAbilities().flying, this.isInWater() || this.isInLava())) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"shouldStopRunSprinting"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$sprintStop(CallbackInfoReturnable<Boolean> cir) {
      if (BedrockMovement.active()) {
         Vec3 intended = this.bedrocknative$lastIntended;
         Vec3 moved = this.bedrocknative$lastMoved;
         boolean wall = Math.abs(intended.z) > Math.abs(intended.x) && Math.abs(moved.z) < 5.0E-5
            || Math.abs(intended.x) > Math.abs(intended.z) && Math.abs(moved.x) < 5.0E-5;
         cir.setReturnValue(
            !this.isSprintingPossible(this.getAbilities().flying)
               || !this.input.hasForwardImpulse()
               || wall
               || SprintRules.sneakEndsSprint(this.input.keyPresses.shift(), this.getAbilities().flying, this.isInWater() || this.isInLava())
         );
      }
   }
}
