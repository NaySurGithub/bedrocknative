package net.bedrocknative.client.world;

import java.util.LinkedHashMap;
import java.util.Map;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.RecordStartedPacket;

public final class BedrockJukeboxes {
   private static final int MAX_RECORDS = 256;
   private static final int CHECK_EVERY_TICKS = 10;
   private final Minecraft minecraft;
   private final Map<Long, BlockPos> records = new LinkedHashMap<>();
   private int ticks;

   public BedrockJukeboxes(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public void onRecordStarted(RecordStartedPacket packet) {
      Vector3i at = packet.getBlockPos();
      if (at != null) {
         if (this.records.size() >= 256) {
            this.records.remove(this.records.keySet().iterator().next());
         }

         this.records.put(packet.getServerSoundHandle(), new BlockPos(at.getX(), at.getY(), at.getZ()));
      }
   }

   public void tick() {
      ClientLevel level = this.minecraft.level;
      if (!this.records.isEmpty() && level != null && ++this.ticks % 10 == 0) {
         this.records.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getValue();
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).is(Blocks.JUKEBOX)) {
               BedrockSoundEngine.stopHandle(entry.getKey());
               return true;
            } else {
               return false;
            }
         });
      }
   }

   public int count() {
      return this.records.size();
   }

   public void clear() {
      this.records.clear();
   }
}
