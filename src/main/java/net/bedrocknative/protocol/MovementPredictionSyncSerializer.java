package net.bedrocknative.protocol;

import io.netty.buffer.ByteBuf;
import java.math.BigInteger;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v975.serializer.MovementPredictionSyncSerializer_v975;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MovementPredictionSyncPacket;
import org.cloudburstmc.protocol.common.util.TypeMap;
import org.cloudburstmc.protocol.common.util.VarInts;

final class MovementPredictionSyncSerializer extends MovementPredictionSyncSerializer_v975 {
   private static final int MAX_FLAG_BITS = 256;
   private final TypeMap<EntityFlag> entityFlags;

   MovementPredictionSyncSerializer(TypeMap<EntityFlag> entityFlags) {
      this.entityFlags = entityFlags;
   }

   @Override
   public void serialize(ByteBuf buffer, BedrockCodecHelper helper, MovementPredictionSyncPacket packet) {
      BigInteger flags = BigInteger.ZERO;

      for (EntityFlag flag : packet.getFlags()) {
         flags = flags.setBit(this.entityFlags.getId(flag));
      }

      VarInts.writeUnsignedBigVarInt(buffer, flags);
      helper.writeVector3f(buffer, packet.getBoundingBox());
      buffer.writeFloatLE(packet.getSpeed());
      buffer.writeFloatLE(packet.getUnderwaterSpeed());
      buffer.writeFloatLE(packet.getLavaSpeed());
      buffer.writeFloatLE(packet.getJumpStrength());
      buffer.writeFloatLE(packet.getHealth());
      buffer.writeFloatLE(packet.getHunger());
      buffer.writeFloatLE(packet.getUnknown1());
      buffer.writeFloatLE(packet.getUnknown2());
      buffer.writeFloatLE(packet.getUnknown3());
      VarInts.writeLong(buffer, packet.getUniqueEntityId());
      buffer.writeBoolean(packet.isFlying());
   }

   @Override
   public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, MovementPredictionSyncPacket packet) {
      BigInteger flags = VarInts.readUnsignedBigVarInt(buffer, 256);

      for (int id = 0; id < flags.bitLength(); id++) {
         EntityFlag flag = flags.testBit(id) ? this.entityFlags.getTypeUnsafe(id) : null;
         if (flag != null) {
            packet.getFlags().add(flag);
         }
      }

      packet.setBoundingBox(helper.readVector3f(buffer));
      packet.setSpeed(buffer.readFloatLE());
      packet.setUnderwaterSpeed(buffer.readFloatLE());
      packet.setLavaSpeed(buffer.readFloatLE());
      packet.setJumpStrength(buffer.readFloatLE());
      packet.setHealth(buffer.readFloatLE());
      packet.setHunger(buffer.readFloatLE());
      packet.setUnknown1(buffer.readFloatLE());
      packet.setUnknown2(buffer.readFloatLE());
      packet.setUnknown3(buffer.readFloatLE());
      packet.setUniqueEntityId(VarInts.readLong(buffer));
      packet.setFlying(buffer.readBoolean());
   }
}
