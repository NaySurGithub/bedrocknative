package net.bedrocknative.client.movement;

import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

public final class BedrockMovement {
   public static final float STEP_HEIGHT = 0.5625F;
   public static final float USING_ITEM_INPUT = 0.122499995F;
   public static final float SNEAK_INPUT = 0.3F;
   public static final float FLY_ASCEND = 3.0F;
   public static final float FLY_DESCEND = 4.4F;
   public static final float FLY_IDLE_DAMPING = 0.375F;
   public static final float SPECTATOR_IDLE_DAMPING = 0.75F;
   private static volatile boolean active;
   private static boolean jumped;
   private static boolean replaying;
   private static int glideBoostTicks;
   private static boolean noClip;
   private static float verticalFlySpeed = 1.0F;
   private static boolean replaySneaking;
   private static boolean flewThisTick;
   private static boolean toggledFlightThisTick;
   private static boolean glidedThisTick;
   private static boolean flightToggle;
   private static boolean glideAfterFlight;
   private static boolean scaffoldDescend;
   private static volatile boolean immobile;
   private static volatile boolean collision = true;

   private BedrockMovement() {
   }

   public static float usingItemInput(ItemStack used) {
      float modifier = ItemMappings.movementModifier(used);
      return !Float.isNaN(modifier) ? modifier * modifier : (used.is(ItemTags.SPEARS) ? 1.0F : 0.122499995F);
   }

   public static boolean active() {
      return active;
   }

   public static void setActive(boolean value) {
      active = value;
      noClip = false;
      verticalFlySpeed = 1.0F;
      flightToggle = false;
      glideAfterFlight = false;
      scaffoldDescend = false;
      immobile = false;
      collision = true;
      jumped = false;
      glideBoostTicks = 0;
      BedrockDepenetration.reset();
      BedrockVehicles.reset();
      FreezeDiagnostics.reset();
   }

   public static boolean immobile() {
      return immobile;
   }

   public static void setImmobile(boolean value) {
      immobile = value;
   }

   public static boolean collision() {
      return collision;
   }

   public static void setCollision(boolean value) {
      collision = value;
   }

   public static boolean holdsStill(Object entity) {
      return immobile && appliesTo(entity);
   }

   public static boolean noClip() {
      return noClip;
   }

   public static void setNoClip(boolean value) {
      noClip = value;
   }

   public static float verticalFlySpeed() {
      return verticalFlySpeed;
   }

   public static void setVerticalFlySpeed(float value) {
      verticalFlySpeed = value;
   }

   public static boolean appliesTo(Object entity) {
      return active && entity instanceof LocalPlayer;
   }

   public static boolean simulates(Object entity) {
      return appliesTo(entity) || active && BedrockVehicles.predicts(entity);
   }

   public static double clearTiny(double value) {
      return Math.abs(value) < 1.0E-8 ? 0.0 : value;
   }

   public static BlockPos blockBelow(Entity entity) {
      return BlockPos.containing(entity.getX(), entity.getY() - 0.1, entity.getZ());
   }

   public static BlockPos feetBlock(Entity entity) {
      return BlockPos.containing(entity.getX(), entity.getY() - 0.001, entity.getZ());
   }

   public static boolean touchesWater(Entity entity) {
      AABB box = entity.getBoundingBox().deflate(0.001);
      double minY = box.minY + 0.4;
      double maxY = Math.max(minY, box.maxY - 0.4);
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
         for (int y = Mth.floor(minY); y <= Mth.floor(maxY); y++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
               FluidState fluid = entity.level().getFluidState(pos.set(x, y, z));
               if (fluid.is(FluidTags.WATER)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean scaffoldingAt(Entity entity, int below) {
      AABB box = entity.getBoundingBox().move(0.0, -below, 0.0);
      int y = Mth.floor(entity.getY()) - below;
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-7); x++) {
         for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-7); z++) {
            if (entity.level().getBlockState(pos.set(x, y, z)).is(Blocks.SCAFFOLDING) && box.intersects(x, y, z, x + 1, y + 1, z + 1)) {
               return true;
            }
         }
      }

      return false;
   }

   public static AABB floatBox(AABB box) {
      return new AABB((float)box.minX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ);
   }

   public static float friction(Entity entity, Block block, float javaFriction) {
      if (block instanceof CarrierBlock) {
         CustomBlockState custom = CarrierBlock.data(entity.level().getBlockState(blockBelow(entity)));
         return custom == null ? javaFriction : 1.0F - custom.friction();
      } else {
         return block instanceof HoneyBlock ? 0.8F : javaFriction;
      }
   }

   public static float friction(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof CarrierBlock) {
         CustomBlockState custom = CarrierBlock.data(state);
         return custom == null ? block.getFriction() : 1.0F - custom.friction();
      } else {
         return block instanceof HoneyBlock ? 0.8F : block.getFriction();
      }
   }

   public static void startGlideBoost(int duration) {
      glideBoostTicks = (duration != 0 || glideBoostTicks > 0) && duration != Integer.MAX_VALUE ? Math.max(1, duration / 2) : 1;
   }

   public static int glideBoostTicks() {
      return glideBoostTicks;
   }

   public static void setGlideBoostTicks(int ticks) {
      glideBoostTicks = ticks;
   }

   public static boolean glideBoosting() {
      return glideBoostTicks > 0;
   }

   public static void tickGlideBoost() {
      if (glideBoostTicks > 0) {
         glideBoostTicks--;
      }
   }

   public static void setReplaying(boolean value) {
      replaying = value;
   }

   public static void setReplaySneaking(boolean value) {
      replaySneaking = value;
   }

   public static boolean sneakKey(LocalPlayer player) {
      return replaying ? replaySneaking : player.isShiftKeyDown();
   }

   public static void toggleFlight() {
      flightToggle = true;
   }

   public static void setReplayFlightToggle(boolean value) {
      flightToggle = value;
   }

   public static boolean takeFlightToggle() {
      boolean value = flightToggle;
      flightToggle = false;
      return value;
   }

   public static void endedFlight() {
      if (!replaying) {
         glideAfterFlight = true;
      }
   }

   public static boolean takeGlideAfterFlight() {
      if (replaying) {
         return false;
      } else {
         boolean value = glideAfterFlight;
         glideAfterFlight = false;
         return value;
      }
   }

   public static void setScaffoldDescend(boolean value) {
      scaffoldDescend = value;
   }

   public static boolean scaffoldDescend() {
      return scaffoldDescend;
   }

   public static void travelled(boolean flying, boolean toggled, boolean gliding) {
      if (!replaying) {
         flewThisTick = flying;
         toggledFlightThisTick = toggled;
         glidedThisTick = gliding;
      }
   }

   public static boolean glidedThisTick() {
      return glidedThisTick;
   }

   public static boolean flewThisTick() {
      return flewThisTick;
   }

   public static boolean toggledFlightThisTick() {
      return toggledFlightThisTick;
   }

   public static void onJump(Object entity) {
      if (!replaying && appliesTo(entity)) {
         jumped = true;
      }
   }

   public static void cancelJump() {
      if (!replaying) {
         jumped = false;
      }
   }

   public static boolean takeJumped() {
      boolean value = jumped;
      jumped = false;
      return value;
   }
}
