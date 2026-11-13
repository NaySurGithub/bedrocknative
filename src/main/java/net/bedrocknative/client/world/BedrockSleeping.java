package net.bedrocknative.client.world;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockSleeping {
   public static final int PLAYER_SLEEP = 2;
   static final int PENDING_TICKS = 100;
   static final int LEAVE_TIMEOUT_TICKS = 40;
   private static volatile boolean asleep;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private volatile long runtimeId;
   @Nullable
   private Vector3i lastBed;
   private boolean sleepFlagged;
   @Nullable
   private BlockPos pending;
   private int pendingTicks;
   private int leaveIn = -1;

   public BedrockSleeping(Minecraft minecraft, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.send = send;
   }

   public static BedrockSleeping.Decision decide(@Nullable Byte flags, @Nullable Vector3i bed, boolean sleeping) {
      if (flags == null) {
         return new BedrockSleeping.Decision(BedrockSleeping.Change.NONE, null);
      } else if ((flags & 2) != 0) {
         return bed == null ? new BedrockSleeping.Decision(BedrockSleeping.Change.NONE, null) : new BedrockSleeping.Decision(BedrockSleeping.Change.SLEEP, bed);
      } else {
         return new BedrockSleeping.Decision(sleeping ? BedrockSleeping.Change.WAKE : BedrockSleeping.Change.NONE, null);
      }
   }

   @Nullable
   static Byte combine(@Nullable Byte playerFlags, @Nullable Boolean sleepingFlag) {
      return !Boolean.TRUE.equals(sleepingFlag) ? playerFlags : (byte)((playerFlags == null ? 0 : playerFlags) | 2);
   }

   public static boolean asleep() {
      return asleep;
   }

   public void start(long runtimeId) {
      this.runtimeId = runtimeId;
      this.reset();
   }

   public void reset() {
      asleep = false;
      this.sleepFlagged = false;
      this.lastBed = null;
      this.pending = null;
      this.leaveIn = -1;
   }

   public void onLocalData(EntityDataMap data) {
      Vector3i bed = data.get(EntityDataTypes.BED_POSITION);
      boolean sleeping = asleep || this.pending != null;
      Map<EntityFlag, Boolean> actorFlags = data.getFlags();
      Byte flags = combine(data.get(EntityDataTypes.PLAYER_FLAGS), actorFlags == null ? null : actorFlags.get(EntityFlag.SLEEPING));
      if (flags != null) {
         this.sleepFlagged = (flags & 2) != 0;
      } else if (bed != null && this.sleepFlagged && !sleeping) {
         flags = (byte)2;
      }

      BedrockSleeping.Decision decision = decide(flags, bed != null ? bed : this.lastBed, sleeping);
      if (bed != null) {
         this.lastBed = bed;
      }

      switch (decision.change()) {
         case NONE:
         default:
            break;
         case SLEEP:
            if (!sleeping) {
               this.sleep(new BlockPos(decision.bed().getX(), decision.bed().getY(), decision.bed().getZ()));
            }
            break;
         case WAKE:
            this.wake();
      }
   }

   public void onWakeUp() {
      this.wake();
   }

   private void sleep(BlockPos clicked) {
      this.pending = clicked;
      this.pendingTicks = 100;
      asleep = true;
      this.leaveIn = -1;
      this.tryStart();
   }

   static BlockPos headOf(BlockState state, BlockPos pos) {
      return state.getBlock() instanceof AbstractBedBlock && state.getValue(AbstractBedBlock.PART) == BedPart.FOOT
         ? pos.relative((Direction)state.getValue(HorizontalDirectionalBlock.FACING))
         : pos;
   }

   private void tryStart() {
      LocalPlayer player = this.minecraft.player;
      ClientLevel level = this.minecraft.level;
      BlockPos bed = this.pending;
      if (player != null && level != null && bed != null) {
         BlockState state = level.getBlockState(bed);
         if (state.getBlock() instanceof AbstractBedBlock) {
            if (player.startSleeping(headOf(state, bed))) {
               this.pending = null;
            }
         }
      }
   }

   private void wake() {
      this.sleepFlagged = false;
      this.pending = null;
      this.leaveIn = -1;
      asleep = false;
      LocalPlayer player = this.minecraft.player;
      if (player != null && player.isSleeping()) {
         player.stopSleepInBed(false, false);
      }
   }

   public void tick() {
      LocalPlayer player = this.minecraft.player;
      if (this.pending != null) {
         this.tryStart();
         if (this.pending != null && --this.pendingTicks <= 0) {
            this.pending = null;
            asleep = false;
         }
      } else if (asleep && (player == null || !player.isSleeping())) {
         asleep = false;
         this.leaveIn = -1;
      }

      if (this.leaveIn > 0 && --this.leaveIn == 0) {
         this.wake();
      }
   }

   public void leaveBed() {
      LocalPlayer player = this.minecraft.player;
      if (player != null && (player.isSleeping() || this.pending != null)) {
         BlockPos where = player.getSleepingPos().orElse(this.pending != null ? this.pending : player.blockPosition());
         this.send.accept(leaveBedPacket(this.runtimeId, where.getX(), where.getY(), where.getZ()));
         this.leaveIn = 40;
      }
   }

   public static PlayerActionPacket leaveBedPacket(long runtimeId, int x, int y, int z) {
      PlayerActionPacket packet = new PlayerActionPacket();
      packet.setRuntimeEntityId(runtimeId);
      packet.setAction(PlayerActionType.STOP_SLEEP);
      packet.setBlockPosition(Vector3i.from(x, y, z));
      packet.setResultPosition(Vector3i.ZERO);
      packet.setFace(0);
      return packet;
   }

   public static enum Change {
      NONE,
      SLEEP,
      WAKE;
   }

   public record Decision(BedrockSleeping.Change change, @Nullable Vector3i bed) {
   }
}
