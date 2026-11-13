package net.bedrocknative.client.movement;

import net.bedrocknative.client.mixin.movement.AbstractHorseAccessor;
import net.bedrocknative.client.mixin.movement.EntityAccessor;
import net.bedrocknative.client.mixin.movement.LivingEntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class HorsePrediction implements VehiclePrediction {
   private static final float PLAYER_INPUT_SCALE = 0.98F;
   private final AbstractHorse horse;
   private final HorsePrediction.Frame[] frames = new HorsePrediction.Frame[64];
   private long lastTick = -1L;
   @Nullable
   private HorsePrediction.Frame pending;

   HorsePrediction(AbstractHorse horse) {
      this.horse = horse;
      BedrockVehicles.stopInterpolating(horse);
   }

   @Override
   public Entity vehicle() {
      return this.horse;
   }

   @Override
   public void tick(LocalPlayer player) {
      HorsePrediction.Input input = new HorsePrediction.Input(
         player.xxa / 0.98F, player.zza / 0.98F, player.getYRot(), player.getXRot(), ((AbstractHorseAccessor)this.horse).bedrocknative$playerJumpPendingScale()
      );
      float strafe = player.xxa;
      float forward = player.zza;
      this.step(player, input);
      player.xxa = strafe;
      player.zza = forward;
      this.pending = this.snapshot(-1L, input);
      this.horse.positionRider(player);
   }

   private void step(LocalPlayer player, HorsePrediction.Input input) {
      player.xxa = input.strafe();
      player.zza = input.forward();
      player.setYRot(input.yRot());
      player.setXRot(input.xRot());
      ((AbstractHorseAccessor)this.horse).bedrocknative$setPlayerJumpPendingScale(input.jumpScale());
      Vec3 velocity = this.horse.getDeltaMovement();
      this.horse.setDeltaMovement(BedrockMovement.clearTiny(velocity.x), BedrockMovement.clearTiny(velocity.y), BedrockMovement.clearTiny(velocity.z));
      ((LivingEntityAccessor)this.horse).bedrocknative$travelRidden(player, new Vec3(this.horse.xxa, this.horse.yya, this.horse.zza));
      ((EntityAccessor)this.horse).bedrocknative$applyEffectsFromBlocks();
      velocity = this.horse.getDeltaMovement();
      this.horse.setDeltaMovement((float)velocity.x, (float)velocity.y, (float)velocity.z);
   }

   private HorsePrediction.Frame snapshot(long tick, HorsePrediction.Input input) {
      return new HorsePrediction.Frame(
         tick, input, this.horse.position(), this.horse.getDeltaMovement(), this.horse.getYRot(), this.horse.getXRot(), this.horse.onGround()
      );
   }

   @Override
   public void record(long tick) {
      HorsePrediction.Frame frame = this.pending;
      this.pending = null;
      if (frame != null) {
         this.frames[VehiclePrediction.index(tick)] = new HorsePrediction.Frame(
            tick, frame.input(), frame.position(), frame.velocity(), frame.yaw(), frame.pitch(), frame.onGround()
         );
         this.lastTick = tick;
      }
   }

   @Override
   public BedrockVehicles.Correction correct(
      LocalPlayer player, long tick, Vec3 networkPosition, Vec3 delta, float pitch, float yaw, @Nullable Float angularVelocity, boolean onGround
   ) {
      HorsePrediction.Frame frame = this.frames[VehiclePrediction.index(tick)];
      boolean known = frame != null && frame.tick() == tick && tick <= this.lastTick;
      BedrockVehicles.Correction correction = new BedrockVehicles.Correction(
         tick,
         networkPosition,
         delta,
         yaw,
         Float.NaN,
         onGround,
         known ? frame.position() : null,
         known ? frame.velocity() : null,
         known ? Mth.wrapDegrees(frame.yaw()) : Float.NaN,
         Float.NaN
      );
      this.horse.setPos(networkPosition);
      this.horse.setDeltaMovement(delta);
      this.horse.setOnGround(onGround);
      if (!Float.isNaN(yaw)) {
         this.horse.setYRot(yaw);
         this.horse.setXRot(pitch);
      }

      if (known) {
         this.frames[VehiclePrediction.index(tick)] = new HorsePrediction.Frame(
            tick, frame.input(), networkPosition, delta, this.horse.getYRot(), this.horse.getXRot(), onGround
         );
         this.replayAfter(player, tick);
      }

      this.horse.positionRider(player);
      return correction;
   }

   private void replayAfter(LocalPlayer player, long tick) {
      float strafe = player.xxa;
      float forward = player.zza;
      float yRot = player.getYRot();
      float xRot = player.getXRot();
      float jumpScale = ((AbstractHorseAccessor)this.horse).bedrocknative$playerJumpPendingScale();

      try {
         for (long t = tick + 1L; t <= this.lastTick; t++) {
            HorsePrediction.Frame frame = this.frames[VehiclePrediction.index(t)];
            if (frame == null || frame.tick() != t) {
               break;
            }

            this.step(player, frame.input());
            this.frames[VehiclePrediction.index(t)] = this.snapshot(t, frame.input());
         }
      } finally {
         player.xxa = strafe;
         player.zza = forward;
         player.setYRot(yRot);
         player.setXRot(xRot);
         ((AbstractHorseAccessor)this.horse).bedrocknative$setPlayerJumpPendingScale(jumpScale);
      }
   }

   @Override
   public Vec3 networkPosition() {
      return this.horse.position();
   }

   @Override
   public Vec3 reportedDelta() {
      return this.horse.getDeltaMovement();
   }

   @Override
   public float bedrockPitch() {
      return this.horse.getXRot();
   }

   @Override
   public float bedrockYaw() {
      return Mth.wrapDegrees(this.horse.getYRot());
   }

   @Override
   public String describe() {
      return this.horse.typeHolder().getRegisteredName()
         + " on ground "
         + this.horse.onGround()
         + ", speed "
         + this.horse.getSpeed()
         + ", jump strength "
         + this.horse.getAttributeValue(Attributes.JUMP_STRENGTH);
   }

   private record Frame(long tick, HorsePrediction.Input input, Vec3 position, Vec3 velocity, float yaw, float pitch, boolean onGround) {
   }

   private record Input(float strafe, float forward, float yRot, float xRot, float jumpScale) {
   }
}
