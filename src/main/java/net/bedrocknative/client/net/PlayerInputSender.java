package net.bedrocknative.client.net;

import java.util.Set;
import net.bedrocknative.client.camera.BedrockCamera;
import net.bedrocknative.client.world.BedrockSleeping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

final class PlayerInputSender {
   private static final long FIRST_INPUT_TICK = 6L;
   private static final int INPUTS_WITHOUT_HEAD_YAW = 2;
   private static final Set<PlayerAuthInputData> JUMP_FLAGS = Set.of(
      PlayerAuthInputData.JUMP_DOWN, PlayerAuthInputData.JUMPING, PlayerAuthInputData.WANT_UP, PlayerAuthInputData.JUMP_CURRENT_RAW
   );
   private static final Set<PlayerAuthInputData> SNEAK_FLAGS = Set.of(
      PlayerAuthInputData.SNEAK_DOWN, PlayerAuthInputData.SNEAKING, PlayerAuthInputData.WANT_DOWN, PlayerAuthInputData.SNEAK_CURRENT_RAW
   );
   static final int STANDING_SPRINT_TICKS = 35;
   static volatile PlayerAuthInputPacket lastBuilt;
   private long tick = 6L;
   private int sent;
   private Input previous = Input.EMPTY;
   private boolean previousSprinting;
   private boolean previousSwimming;
   private boolean previousCrawling;
   private boolean previousGliding;
   private boolean groundBeforeRiding;
   private boolean wallBeforeRiding;
   private boolean teleportToAcknowledge;
   private boolean sprintFromRest;
   private int standingSprintTicks;
   private Boolean flightToggle;
   private ClientPlayMode playMode = ClientPlayMode.NORMAL;
   private boolean cameraReady;

   void skipTick() {
      if (this.sent > 0) {
         this.tick++;
      }
   }

   void levelShown() {
      this.playMode = ClientPlayMode.SCREEN;
   }

   void screenShown() {
      this.playMode = ClientPlayMode.SCREEN;
      this.cameraReady = true;
   }

   void acknowledgeTeleportNextTick() {
      this.teleportToAcknowledge = true;
   }

   boolean teleportToAcknowledge() {
      return this.teleportToAcknowledge;
   }

   int sent() {
      return this.sent;
   }

   void flightToggled(boolean flying) {
      this.flightToggle = flying;
   }

   boolean standingSprint(boolean sprintKey, boolean sprintKeyBefore, boolean moving) {
      if (moving) {
         this.sprintFromRest = false;
         this.standingSprintTicks = 0;
         return false;
      } else if (sprintKey) {
         if (!sprintKeyBefore) {
            this.sprintFromRest = true;
         }

         this.standingSprintTicks = this.sprintFromRest ? 35 : 0;
         return false;
      } else if (this.standingSprintTicks > 0 && this.standingSprintTicks-- > 0) {
         return true;
      } else {
         this.sprintFromRest = false;
         return false;
      }
   }

   PlayerAuthInputPacket build(LocalPlayer player, boolean jumped) {
      if (!this.cameraReady) {
         return this.joining(player);
      } else {
         Input keys = BedrockSleeping.asleep() ? Input.EMPTY : player.input.keyPresses;
         PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
         Set<PlayerAuthInputData> flags = packet.getInputData();
         boolean moving = keys.forward() || keys.backward() || keys.left() || keys.right();
         flags.add(PlayerAuthInputData.BLOCK_BREAKING_DELAY_ENABLED);
         if (!player.isPassenger()) {
            this.groundBeforeRiding = player.onGround();
            this.wallBeforeRiding = player.horizontalCollision;
         }

         if (player.isPassenger() ? this.groundBeforeRiding : player.onGround()) {
            flags.add(PlayerAuthInputData.VERTICAL_COLLISION);
         }

         if (player.isPassenger() ? this.wallBeforeRiding : player.horizontalCollision) {
            flags.add(PlayerAuthInputData.HORIZONTAL_COLLISION);
         }

         Vec2 turned = BedrockCamera.cameraRelativeControls() ? player.input.getMoveVector() : null;
         if (turned != null ? turned.y > 0.1F : keys.forward()) {
            flags.add(PlayerAuthInputData.UP);
         }

         if (turned != null ? turned.y < -0.1F : keys.backward()) {
            flags.add(PlayerAuthInputData.DOWN);
         }

         if (turned != null ? turned.x > 0.1F : keys.left()) {
            flags.add(PlayerAuthInputData.LEFT);
         }

         if (turned != null ? turned.x < -0.1F : keys.right()) {
            flags.add(PlayerAuthInputData.RIGHT);
         }

         if (keys.jump()) {
            flags.addAll(JUMP_FLAGS);
         }

         if (jumped) {
            flags.add(PlayerAuthInputData.START_JUMPING);
         }

         if (keys.shift()) {
            flags.addAll(SNEAK_FLAGS);
         }

         boolean sprintKey = keys.sprint();
         if (sprintKey) {
            flags.add(PlayerAuthInputData.SPRINT_DOWN);
         }

         boolean latched = this.standingSprint(sprintKey, this.previous.sprint(), moving);
         if (sprintKey || latched) {
            flags.add(PlayerAuthInputData.SPRINTING);
         }

         if (player.isSwimming() != this.previousSwimming) {
            flags.add(player.isSwimming() ? PlayerAuthInputData.START_SWIMMING : PlayerAuthInputData.STOP_SWIMMING);
         }

         if (player.isVisuallyCrawling() != this.previousCrawling) {
            flags.add(player.isVisuallyCrawling() ? PlayerAuthInputData.START_CRAWLING : PlayerAuthInputData.STOP_CRAWLING);
         }

         if (player.isFallFlying() != this.previousGliding) {
            flags.add(player.isFallFlying() ? PlayerAuthInputData.START_GLIDING : PlayerAuthInputData.STOP_GLIDING);
         }

         if (player.isSprinting() != this.previousSprinting) {
            flags.add(player.isSprinting() ? PlayerAuthInputData.START_SPRINTING : PlayerAuthInputData.STOP_SPRINTING);
         }

         if (keys.jump() != this.previous.jump()) {
            flags.add(keys.jump() ? PlayerAuthInputData.JUMP_PRESSED_RAW : PlayerAuthInputData.JUMP_RELEASED_RAW);
         }

         if (keys.shift() != this.previous.shift()) {
            flags.add(keys.shift() ? PlayerAuthInputData.SNEAK_PRESSED_RAW : PlayerAuthInputData.SNEAK_RELEASED_RAW);
            flags.add(keys.shift() ? PlayerAuthInputData.START_SNEAKING : PlayerAuthInputData.STOP_SNEAKING);
         }

         if (this.flightToggle != null) {
            flags.add(this.flightToggle ? PlayerAuthInputData.START_FLYING : PlayerAuthInputData.STOP_FLYING);
            this.flightToggle = null;
         }

         if (this.teleportToAcknowledge) {
            flags.add(PlayerAuthInputData.HANDLE_TELEPORT);
            this.teleportToAcknowledge = false;
         }

         float pitch = Mth.clamp(player.getXRot(), -90.0F, 90.0F);
         float yaw = Mth.wrapDegrees(player.getYRot());
         float headYaw = Mth.wrapDegrees(player.getYHeadRot());
         Vec3 position = player.position();
         Vec3 velocity = player.getDeltaMovement();
         packet.setRotation(Vector3f.from(pitch, yaw, headYaw));
         packet.setPosition(Vector3f.from(position.x, position.y + 1.62001F, position.z));
         packet.setMotion(moveVector(player, keys, keys.shift()));
         packet.setRawMoveVector(moveVector(player, keys, false));
         packet.setAnalogMoveVector(Vector2f.ZERO);
         packet.setCameraOrientation(cameraOrientation(yaw, pitch));
         packet.setInteractRotation(Vector2f.from(pitch, player.getYRot()));
         packet.setDelta(Vector3f.from(velocity.x, velocity.y, velocity.z));
         this.finish(packet);
         this.previous = keys;
         this.previousSprinting = player.isSprinting();
         this.previousSwimming = player.isSwimming();
         this.previousCrawling = player.isVisuallyCrawling();
         this.previousGliding = player.isFallFlying();
         return packet;
      }
   }

   private PlayerAuthInputPacket joining(LocalPlayer player) {
      PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
      packet.getInputData().add(PlayerAuthInputData.BLOCK_BREAKING_DELAY_ENABLED);
      if (this.teleportToAcknowledge) {
         packet.getInputData().add(PlayerAuthInputData.HANDLE_TELEPORT);
         this.teleportToAcknowledge = false;
      }

      Vec3 position = player.position();
      packet.setRotation(Vector3f.from(0.0F, 0.0F, this.sent < 2 ? 0.0F : Mth.wrapDegrees(player.getYRot())));
      packet.setPosition(Vector3f.from(position.x, position.y + 1.62001F, position.z));
      packet.setMotion(Vector2f.ZERO);
      packet.setRawMoveVector(Vector2f.ZERO);
      packet.setAnalogMoveVector(Vector2f.ZERO);
      packet.setCameraOrientation(Vector3f.ZERO);
      packet.setInteractRotation(Vector2f.ZERO);
      packet.setDelta(Vector3f.ZERO);
      this.finish(packet);
      return packet;
   }

   private void finish(PlayerAuthInputPacket packet) {
      packet.setInputMode(InputMode.MOUSE);
      packet.setPlayMode(this.playMode);
      packet.setInputInteractionModel(InputInteractionModel.TOUCH);
      packet.setTick(this.tick++);
      this.sent++;
   }

   private static Vector2f moveVector(LocalPlayer player, Input keys, boolean sneaking) {
      if (BedrockCamera.cameraRelativeControls()) {
         Vec2 move = player.input.getMoveVector();
         float scale = sneaking ? 0.3F : 1.0F;
         return Vector2f.from(move.x * scale, move.y * scale);
      } else {
         float x = keys.left() ? 1.0F : (keys.right() ? -1.0F : 0.0F);
         float z = keys.forward() ? 1.0F : (keys.backward() ? -1.0F : 0.0F);
         float scale = (x != 0.0F && z != 0.0F ? (float)(1.0 / Math.sqrt(2.0)) : 1.0F) * (sneaking ? 0.3F : 1.0F);
         return Vector2f.from(x * scale, z * scale);
      }
   }

   private static Vector3f cameraOrientation(float yaw, float pitch) {
      float yawRad = (float)Math.toRadians(yaw);
      float pitchRad = (float)Math.toRadians(pitch);
      return Vector3f.from(
         -Mth.clamp(Mth.sin(yawRad) * Mth.cos(pitchRad), -1.0F, 1.0F),
         -Mth.clamp(Mth.sin(pitchRad), -1.0F, 1.0F),
         Mth.clamp(Mth.cos(yawRad) * Mth.cos(pitchRad), -1.0F, 1.0F)
      );
   }
}
