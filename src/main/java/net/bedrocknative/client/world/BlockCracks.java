package net.bedrocknative.client.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;

public final class BlockCracks {
   private static final float FULL = 65535.0F;
   private final Minecraft minecraft;
   private final Map<BlockPos, BlockCracks.Crack> cracks = new HashMap<>();
   private int nextId = -1000;
   private int events;

   public BlockCracks(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof LevelEventPacket event
         && (
            event.getType() == LevelEvent.BLOCK_START_BREAK
               || event.getType() == LevelEvent.BLOCK_UPDATE_BREAK
               || event.getType() == LevelEvent.BLOCK_STOP_BREAK
         );
   }

   public void apply(BedrockPacket packet, BlockPos ownBreak) {
      ClientLevel level = this.minecraft.level;
      if (level != null && packet instanceof LevelEventPacket event) {
         this.events++;
         BlockPos pos = BlockPos.containing(event.getPosition().getX(), event.getPosition().getY(), event.getPosition().getZ());
         if (event.getType() != LevelEvent.BLOCK_STOP_BREAK && !pos.equals(ownBreak)) {
            if (event.getType() == LevelEvent.BLOCK_START_BREAK) {
               this.remove(level, pos);
               BlockCracks.Crack crack = new BlockCracks.Crack(this.nextId--, event.getData() / 65535.0F);
               this.cracks.put(pos, crack);
               level.destroyBlockProgress(crack.id, pos, 0);
            } else if (this.cracks.get(pos) instanceof BlockCracks.Crack crack) {
               crack.rate = event.getData() / 65535.0F;
            }
         } else {
            this.remove(level, pos);
         }
      }
   }

   public int eventCount() {
      return this.events;
   }

   public void tick() {
      ClientLevel level = this.minecraft.level;
      if (level == null) {
         this.cracks.clear();
      } else {
         Iterator<Entry<BlockPos, BlockCracks.Crack>> it = this.cracks.entrySet().iterator();

         while (it.hasNext()) {
            Entry<BlockPos, BlockCracks.Crack> entry = it.next();
            BlockCracks.Crack crack = entry.getValue();
            crack.progress = crack.progress + crack.rate;
            if (level.getBlockState(entry.getKey()).isAir()) {
               level.destroyBlockProgress(crack.id, entry.getKey(), -1);
               it.remove();
            } else {
               level.destroyBlockProgress(crack.id, entry.getKey(), Math.min(9, (int)(crack.progress * 10.0F)));
            }
         }
      }
   }

   private void remove(ClientLevel level, BlockPos pos) {
      BlockCracks.Crack crack = this.cracks.remove(pos);
      if (crack != null) {
         level.destroyBlockProgress(crack.id, pos, -1);
      }
   }

   private static final class Crack {
      final int id;
      float progress;
      float rate;

      Crack(int id, float rate) {
         this.id = id;
         this.rate = rate;
      }
   }
}
