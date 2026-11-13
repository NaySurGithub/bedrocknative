package net.bedrocknative.client.net;

import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.world.block.BlockUseRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jspecify.annotations.Nullable;

public final class BedrockBridging {
   private static final boolean ENABLED = !"off".equals(System.getProperty("bedrocknative.bridging"));
   private static final float MIN_PITCH = 15.0F;
   private static final double MAX_DROP = 1.3;
   private static final double MAX_BEHIND = 0.4;

   private BedrockBridging() {
   }

   public static @Nullable BlockHitResult target(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      ClientLevel level = minecraft.level;
      if (ENABLED
         && player != null
         && level != null
         && BedrockMovement.active()
         && minecraft.hitResult != null
         && minecraft.hitResult.getType() == Type.MISS
         && !player.isSpectator()
         && !(player.getXRot() < 15.0F)
         && player.getMainHandItem().getItem() instanceof BlockItem) {
         Direction facing = player.getDirection();
         BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.01, player.getZ());
         BlockHitResult hit = click(level, player, under, facing);
         if (hit == null && player.onGround()) {
            hit = click(level, player, player.getOnPos(), facing);
         }

         for (int down = 1; hit == null && down <= 2 && player.getY() - (under.getY() - down + 1) <= 1.3; down++) {
            hit = click(level, player, under.below(down), facing);
         }

         return hit;
      } else {
         return null;
      }
   }

   public static void tick(Minecraft minecraft) {
      if (minecraft.rightClickDelay > 0
         && minecraft.options.keyUse.isDown()
         && minecraft.gui.screen() == null
         && minecraft.player != null
         && !minecraft.player.isUsingItem()
         && target(minecraft) != null) {
         minecraft.rightClickDelay = 0;
      }
   }

   private static @Nullable BlockHitResult click(ClientLevel level, LocalPlayer player, BlockPos below, Direction facing) {
      BlockState state = level.getBlockState(below);
      if (state.isCollisionShapeFullBlock(level, below)
         && !state.hasBlockEntity()
         && BedrockBlockUse.rule(state) != BlockUseRules.Rule.BLOCK
         && level.getBlockState(below.relative(facing)).canBeReplaced()) {
         double yaw = Math.toRadians(player.getYRot());
         double ahead = (below.getX() + 0.5 - player.getX()) * -Math.sin(yaw) + (below.getZ() + 0.5 - player.getZ()) * Math.cos(yaw);
         if (ahead < -0.4) {
            return null;
         }

         Vec3 face = Vec3.atCenterOf(below).add(facing.getStepX() * 0.5, 0.0, facing.getStepZ() * 0.5);
         return new BlockHitResult(face, facing, below, false);
      } else {
         return null;
      }
   }
}
