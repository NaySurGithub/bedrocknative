package net.bedrocknative.protocol;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.common.util.VarInts;

final class NamedLevelSoundEventSerializer implements BedrockPacketSerializer<NamedLevelSoundEventPacket> {
   static final NamedLevelSoundEventSerializer INSTANCE = new NamedLevelSoundEventSerializer();

   private NamedLevelSoundEventSerializer() {
   }

   public void serialize(ByteBuf buffer, BedrockCodecHelper helper, NamedLevelSoundEventPacket packet) {
      helper.writeString(buffer, NamedLevelSoundEventPacket.name(packet));
      helper.writeVector3f(buffer, packet.getPosition());
      VarInts.writeInt(buffer, packet.getExtraData());
      helper.writeString(buffer, packet.getIdentifier());
      buffer.writeBoolean(packet.isBabySound());
      buffer.writeBoolean(packet.isRelativeVolumeDisabled());
      buffer.writeLongLE(packet.getEntityUniqueId());
      helper.writeOptionalNull(buffer, packet.getFireAtPosition(), helper::writeVector3f);
   }

   public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, NamedLevelSoundEventPacket packet) {
      String name = helper.readString(buffer);
      packet.setSoundName(name);
      packet.setSound(SoundEvent.fromName(name));
      packet.setPosition(helper.readVector3f(buffer));
      packet.setExtraData(VarInts.readInt(buffer));
      packet.setIdentifier(helper.readString(buffer));
      packet.setBabySound(buffer.readBoolean());
      packet.setRelativeVolumeDisabled(buffer.readBoolean());
      packet.setEntityUniqueId(buffer.readLongLE());
      packet.setFireAtPosition(helper.readOptional(buffer, null, helper::readVector3f));
   }
}
