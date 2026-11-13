package net.bedrocknative.client.movement;

import net.bedrocknative.client.mixin.movement.AbstractBoatAccessor;
import net.bedrocknative.movement.BoatPhysics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class BoatPrediction implements VehiclePrediction {
   private final AbstractBoat boat;
   private final BoatPhysics.State state = new BoatPhysics.State();
   private final BoatPrediction.Frame[] frames = new BoatPrediction.Frame[64];
   private long lastTick = -1L;
   private BoatPrediction.@Nullable Frame pending;
   private Vec3 moved = Vec3.ZERO;

   BoatPrediction(AbstractBoat boat) {
      this.boat = boat;
      BedrockVehicles.stopInterpolating(boat);
      this.state.yaw = boat.getYRot();
   }

   @Override
   public Entity vehicle() {
      return this.boat;
   }

   @Override
   public void tick(LocalPlayer player) {
      BoatPhysics.Controls controls = controls(player.input.keyPresses);
      this.step(controls);
      this.pending = new BoatPrediction.Frame(-1L, controls, this.boat.position(), this.state.copy(), this.moved, this.boat.onGround());
      this.placeRider(player, this.state.angularVelocity);
   }

   static BoatPhysics.Controls controls(Input keys) {
      return new BoatPhysics.Controls(
         keys.left() && !keys.right(), keys.right() && !keys.left(), keys.forward() && !keys.backward(), keys.backward() && !keys.forward()
      );
   }

   private void step(BoatPhysics.Controls controls) {
      BoatPhysics.beforeMove(this.state, controls, BoatSurroundings.of(this.boat));
      Vec3 move = new Vec3(this.state.vx, this.state.vy, this.state.vz);
      this.moved = move;
      this.boat.setYRot(this.state.yaw);
      Vec3 from = this.boat.position();
      this.boat.setDeltaMovement(move);
      this.boat.move(MoverType.SELF, move);
      Vec3 after = this.boat.position();
      BoatPhysics.afterMove(
         this.state,
         BoatPhysics.isBlocked(from.x, move.x, after.x),
         BoatPhysics.isBlocked(from.y, move.y, after.y),
         BoatPhysics.isBlocked(from.z, move.z, after.z)
      );
      this.boat.setDeltaMovement(this.state.vx, this.state.vy, this.state.vz);
      this.boat.setPaddleState(this.state.leftPaddling(controls), this.state.rightPaddling(controls));
   }

   private void placeRider(LocalPlayer player, float turn) {
      if (this.boat.hasPassenger(player)) {
         AbstractBoatAccessor accessor = (AbstractBoatAccessor)this.boat;
         accessor.bedrocknative$setDeltaRotation(turn);
         this.boat.positionRider(player);
         accessor.bedrocknative$setDeltaRotation(0.0F);
      }
   }

   @Override
   public void record(long tick) {
      BoatPrediction.Frame frame = this.pending;
      this.pending = null;
      if (frame != null) {
         this.frames[VehiclePrediction.index(tick)] = new BoatPrediction.Frame(
            tick, frame.controls(), frame.position(), frame.state(), frame.moved(), frame.onGround()
         );
         this.lastTick = tick;
      }
   }

   @Override
   public BedrockVehicles.Correction correct(
      LocalPlayer player, long tick, Vec3 networkPosition, Vec3 delta, float pitch, float yaw, @Nullable Float angularVelocity, boolean onGround
   ) {
      BoatPrediction.Frame frame = this.frames[VehiclePrediction.index(tick)];
      boolean known = frame != null && frame.tick() == tick && tick <= this.lastTick;
      BedrockVehicles.Correction correction = new BedrockVehicles.Correction(
         tick,
         networkPosition,
         delta,
         yaw,
         angularVelocity == null ? Float.NaN : angularVelocity,
         onGround,
         known ? network(frame.position()) : null,
         known ? frame.moved() : null,
         known ? Mth.wrapDegrees(frame.state().yaw + 90.0F) : Float.NaN,
         known ? frame.state().angularVelocity : Float.NaN
      );
      BoatPhysics.State corrected = known ? frame.state().copy() : this.state.copy();
      float acceleration = corrected.vy - corrected.vyPrev;
      corrected.vx = (float)delta.x;
      corrected.vy = (float)delta.y;
      corrected.vz = (float)delta.z;
      corrected.vyPrev = corrected.vy - acceleration;
      if (onGround && corrected.vy < 0.0F) {
         corrected.vy = 0.0F;
      }

      if (!Float.isNaN(yaw)) {
         corrected.yaw = yaw - 90.0F;
      }

      if (angularVelocity != null) {
         corrected.angularVelocity = angularVelocity;
      }

      Vec3 bottom = new Vec3(networkPosition.x, (float)(networkPosition.y - 0.375), networkPosition.z);
      this.boat.setPos(bottom);
      this.boat.setYRot(corrected.yaw);
      this.boat.setOnGround(onGround);
      this.state.set(corrected);
      this.moved = delta;
      if (known) {
         this.frames[VehiclePrediction.index(tick)] = new BoatPrediction.Frame(tick, frame.controls(), bottom, corrected.copy(), delta, onGround);
         this.replayAfter(tick);
      }

      this.boat.setDeltaMovement(this.state.vx, this.state.vy, this.state.vz);
      this.placeRider(player, 0.0F);
      return correction;
   }

   private void replayAfter(long tick) {
      for (long t = tick + 1L; t <= this.lastTick; t++) {
         BoatPrediction.Frame frame = this.frames[VehiclePrediction.index(t)];
         if (frame == null || frame.tick() != t) {
            break;
         }

         this.step(frame.controls());
         this.frames[VehiclePrediction.index(t)] = new BoatPrediction.Frame(
            t, frame.controls(), this.boat.position(), this.state.copy(), this.moved, this.boat.onGround()
         );
      }
   }

   @Override
   public Vec3 networkPosition() {
      return network(this.boat.position());
   }

   private static Vec3 network(Vec3 bottom) {
      return new Vec3(bottom.x, (float)(bottom.y + 0.375), bottom.z);
   }

   @Override
   public Vec3 reportedDelta() {
      return this.moved;
   }

   @Override
   public float bedrockPitch() {
      return this.boat.getXRot();
   }

   @Override
   public float bedrockYaw() {
      return Mth.wrapDegrees(this.boat.getYRot() + 90.0F);
   }

   @Override
   public String describe() {
      return "boat "
         + BoatSurroundings.describe(this.boat)
         + ", yaw "
         + this.boat.getYRot()
         + ", turning "
         + this.state.angularVelocity
         + ", row time "
         + this.state.leftRowTime
         + "/"
         + this.state.rightRowTime;
   }

   private record Frame(long tick, BoatPhysics.Controls controls, Vec3 position, BoatPhysics.State state, Vec3 moved, boolean onGround) {
   }
}
