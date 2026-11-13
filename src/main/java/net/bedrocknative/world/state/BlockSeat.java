package net.bedrocknative.world.state;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PassengerOfBlockArguments;
import org.cloudburstmc.protocol.bedrock.data.PassengerOfBlockArguments.EmoteType;

public record BlockSeat(Vector3f position, float rotation, float rotationLimit, EmoteType emote) {
   public float clampYaw(float yaw) {
      if (this.rotationLimit <= 0.0F) {
         return yaw;
      } else {
         float delta = (yaw - this.rotation + 540.0F) % 360.0F - 180.0F;
         if (Math.abs(delta) <= this.rotationLimit) {
            return yaw;
         } else {
            float clamped = this.rotation + Math.copySign(this.rotationLimit, delta);
            return (clamped % 360.0F + 360.0F) % 360.0F;
         }
      }
   }

   public static BlockSeat of(PassengerOfBlockArguments arguments) {
      Vector3i block = arguments.getBlockPos();
      Vector3f offset = arguments.getOffset();
      return new BlockSeat(
         Vector3f.from(block.getX() + offset.getX(), block.getY() + offset.getY(), block.getZ() + offset.getZ()),
         arguments.getRotation(),
         arguments.getRotationLimit(),
         arguments.getEmoteType()
      );
   }
}
