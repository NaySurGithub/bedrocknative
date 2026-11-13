package net.bedrocknative.client.inventory;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.ToggleCrafterSlotRequestPacket;

public final class BedrockCrafters {
   static final int SLOTS = 9;
   private static final int MAX_CRAFTERS = 512;
   private static final Map<BlockPos, Integer> MASKS = new HashMap<>();

   private BedrockCrafters() {
   }

   static int maskOf(NbtMap tag) {
      return tag.get("disabled_slots") instanceof Number number ? number.intValue() & 511 : 0;
   }

   public static void record(BlockPos pos, NbtMap tag) {
      if (MASKS.size() >= 512 && !MASKS.containsKey(pos)) {
         MASKS.remove(MASKS.keySet().iterator().next());
      }

      MASKS.put(pos.immutable(), maskOf(tag));
   }

   static int mask(BlockPos pos) {
      return MASKS.getOrDefault(pos, 0);
   }

   static void toggled(BlockPos pos, int slot, boolean disabled) {
      int mask = mask(pos);
      MASKS.put(pos.immutable(), disabled ? mask | 1 << slot : mask & ~(1 << slot));
   }

   public static void reset() {
      MASKS.clear();
   }

   static ToggleCrafterSlotRequestPacket request(BlockPos pos, int slot, boolean enabledAfter) {
      ToggleCrafterSlotRequestPacket packet = new ToggleCrafterSlotRequestPacket();
      packet.setBlockPosition(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()));
      packet.setSlot((byte)slot);
      packet.setDisabled(!enabledAfter);
      return packet;
   }
}
