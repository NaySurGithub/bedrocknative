package net.bedrocknative.protocol;

import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;

public final class NamedLevelSoundEventPacket extends LevelSoundEventPacket {
   private String soundName = "";

   public String getSoundName() {
      return this.soundName;
   }

   public void setSoundName(String soundName) {
      this.soundName = soundName;
   }

   public static String name(LevelSoundEventPacket packet) {
      if (packet instanceof NamedLevelSoundEventPacket named && !named.soundName.isEmpty()) {
         return named.soundName;
      } else {
         SoundEvent sound = packet.getSound();
         return sound != null ? sound.getSerializeName() : "";
      }
   }

   @Override
   public String toString() {
      return super.toString() + "[" + this.soundName + "]";
   }
}
