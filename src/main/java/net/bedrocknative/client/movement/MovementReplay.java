package net.bedrocknative.client.movement;

import net.bedrocknative.client.mixin.movement.EntityAccessor;
import net.bedrocknative.client.mixin.movement.LivingEntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class MovementReplay {
   static final int HISTORY = 64;
   private final MovementReplay.Frame[] frames = new MovementReplay.Frame[64];
   private final Vec3[] positions = new Vec3[64];
   private final Vec3[] velocities = new Vec3[64];
   private final boolean[] grounds = new boolean[64];
   private final int[] glideBoosts = new int[64];
   private final byte[] penetrations = new byte[64];
   private long lastTick = -1L;

   public void record(long tick, LocalPlayer player, boolean jumped) {
      int index = index(tick);
      this.frames[index] = new MovementReplay.Frame(
         tick,
         new Vec3(player.xxa, player.yya, player.zza),
         ((LivingEntityAccessor)player).bedrocknative$isJumping(),
         jumped,
         player.isSprinting(),
         player.isShiftKeyDown(),
         BedrockMovement.flewThisTick(),
         BedrockMovement.toggledFlightThisTick(),
         BedrockMovement.glidedThisTick(),
         player.getYRot(),
         player.getXRot(),
         BedrockMovement.holdsStill(player)
      );
      this.store(index, player);
      this.lastTick = tick;
   }

   public boolean rewind(LocalPlayer player, long tick, Vec3 position, Vec3 velocity, boolean onGround) {
      if (!this.known(tick)) {
         return false;
      } else {
         player.setPos(position);
         player.setDeltaMovement(velocity);
         player.setOnGround(onGround);
         BedrockMovement.setGlideBoostTicks(this.glideBoosts[index(tick)]);
         BedrockDepenetration.restore(this.penetrations[index(tick)]);
         this.store(index(tick), player);
         this.replayAfter(player, tick);
         return true;
      }
   }

   public boolean setMotion(LocalPlayer player, long tick, Vec3 motion) {
      if (!this.known(tick)) {
         return false;
      } else {
         int index = index(tick);
         return this.rewind(player, tick, this.positions[index], motion, this.grounds[index]);
      }
   }

   public boolean startGlideBoost(LocalPlayer player, long tick, int duration) {
      if (!this.known(tick)) {
         return false;
      } else {
         int index = index(tick);
         BedrockMovement.setGlideBoostTicks(this.glideBoosts[index]);
         BedrockMovement.startGlideBoost(duration);
         this.glideBoosts[index] = BedrockMovement.glideBoostTicks();
         this.rewind(player, tick, this.positions[index], this.velocities[index], this.grounds[index]);
         return true;
      }
   }

   public boolean setFlyingAfter(LocalPlayer player, long tick, boolean flying) {
      if (!this.known(tick)) {
         return false;
      } else {
         for (long t = tick + 1L; t <= this.lastTick; t++) {
            MovementReplay.Frame frame = this.frames[index(t)];
            if (frame == null || frame.tick() != t) {
               break;
            }

            this.frames[index(t)] = new MovementReplay.Frame(
               t,
               frame.input(),
               frame.jumping(),
               frame.jumped(),
               frame.sprinting(),
               frame.sneaking(),
               flying,
               false,
               frame.gliding(),
               frame.yRot(),
               frame.xRot(),
               frame.held()
            );
         }

         this.resimulateAfter(player, tick);
         return true;
      }
   }

   public void resimulateAfter(LocalPlayer player, long tick) {
      if (this.known(tick)) {
         int index = index(tick);
         this.rewind(player, tick, this.positions[index], this.velocities[index], this.grounds[index]);
      }
   }

   private void replayAfter(LocalPlayer player, long tick) {
      float yRot = player.getYRot();
      float xRot = player.getXRot();
      boolean sprinting = player.isSprinting();
      boolean jumping = ((LivingEntityAccessor)player).bedrocknative$isJumping();
      boolean flying = player.getAbilities().flying;
      BedrockMovement.setReplaying(true);

      try {
         for (long t = tick + 1L; t <= this.lastTick; t++) {
            int index = index(t);
            MovementReplay.Frame frame = this.frames[index];
            if (frame == null || frame.tick() != t) {
               break;
            }

            step(player, frame);
            this.store(index, player);
         }
      } finally {
         BedrockMovement.setReplaying(false);
         player.setYRot(yRot);
         player.setXRot(xRot);
         player.setSprinting(sprinting);
         player.getAbilities().flying = flying;
         ((LivingEntityAccessor)player).bedrocknative$setJumping(jumping);
      }
   }

   private static void step(LocalPlayer player, MovementReplay.Frame frame) {
      LivingEntityAccessor living = (LivingEntityAccessor)player;
      player.setYRot(frame.yRot());
      player.setXRot(frame.xRot());
      player.setSprinting(frame.sprinting());
      living.bedrocknative$setJumping(frame.jumping());
      player.getAbilities().flying = frame.flying();
      if (frame.gliding()) {
         player.startFallFlying();
      } else if (player.isFallFlying()) {
         player.stopFallFlying();
      }

      BedrockMovement.setReplaySneaking(frame.sneaking());
      BedrockMovement.setReplayFlightToggle(frame.toggledFlight());
      if (frame.held()) {
         BedrockMovement.setReplayFlightToggle(false);
         player.setDeltaMovement(Vec3.ZERO);
         BedrockMovement.tickGlideBoost();
      } else {
         ((EntityAccessor)player).bedrocknative$updateFluidInteraction();
         Vec3 velocity = player.getDeltaMovement();
         player.setDeltaMovement(BedrockMovement.clearTiny(velocity.x), BedrockMovement.clearTiny(velocity.y), BedrockMovement.clearTiny(velocity.z));
         if (!frame.jumping() || !player.isAffectedByFluids() || !player.isInWater() && !player.isInLava()) {
            if (frame.jumped() && player.onGround()) {
               player.jumpFromGround();
            }
         } else {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.04F, 0.0));
         }

         player.travel(frame.input());
         ((EntityAccessor)player).bedrocknative$applyEffectsFromBlocks();
         velocity = player.getDeltaMovement();
         player.setDeltaMovement((float)velocity.x, (float)velocity.y, (float)velocity.z);
         BedrockMovement.tickGlideBoost();
      }
   }

   private boolean known(long tick) {
      MovementReplay.Frame frame = this.frames[index(tick)];
      return tick > 0L && tick <= this.lastTick && frame != null && frame.tick() == tick;
   }

   private void store(int index, LocalPlayer player) {
      this.positions[index] = player.position();
      this.velocities[index] = player.getDeltaMovement();
      this.grounds[index] = player.onGround();
      this.glideBoosts[index] = BedrockMovement.glideBoostTicks();
      this.penetrations[index] = BedrockDepenetration.state();
   }

   private static int index(long tick) {
      return (int)Math.floorMod(tick, 64L);
   }

   private record Frame(
      long tick,
      Vec3 input,
      boolean jumping,
      boolean jumped,
      boolean sprinting,
      boolean sneaking,
      boolean flying,
      boolean toggledFlight,
      boolean gliding,
      float yRot,
      float xRot,
      boolean held
   ) {
   }
}
