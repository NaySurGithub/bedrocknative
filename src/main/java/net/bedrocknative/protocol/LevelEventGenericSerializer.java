package net.bedrocknative.protocol;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v361.serializer.LevelEventGenericSerializer_v361;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.common.util.TypeMap;
import org.cloudburstmc.protocol.common.util.VarInts;

final class LevelEventGenericSerializer extends LevelEventGenericSerializer_v361 {
   LevelEventGenericSerializer(TypeMap<LevelEventType> typeMap) {
      super(typeMap);
   }

   @Override
   public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, LevelEventGenericPacket packet) {
      packet.setType(this.typeMap.getTypeUnsafe(VarInts.readInt(buffer)));
      packet.setTag(helper.readTagValue(buffer, NbtType.COMPOUND));
   }
}
