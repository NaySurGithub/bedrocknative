package net.bedrocknative.net.client;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource.Flag;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.jspecify.annotations.Nullable;

public final class DropPackets {
   public static final String ITEM_LOCK = "minecraft:item_lock";

   private DropPackets() {
   }

   @Nullable
   public static InventoryTransactionPacket hotbarDrop(int slot, ItemData before, ItemData after) {
      if (before != null && !before.isNull()) {
         ItemData kept = after != null && !after.isNull() ? after : ItemData.AIR;
         int dropped = before.getCount() - (kept.isNull() ? 0 : kept.getCount());
         if (dropped <= 0) {
            return null;
         } else {
            InventoryTransactionPacket drop = new InventoryTransactionPacket();
            drop.setTransactionType(InventoryTransactionType.NORMAL);
            drop.getActions().add(new InventoryActionData(InventorySource.fromContainerWindowId(0), slot, withoutStackId(before), withoutStackId(kept)));
            drop.getActions()
               .add(
                  new InventoryActionData(
                     InventorySource.fromWorldInteraction(Flag.DROP_ITEM), 0, ItemData.AIR, withoutStackId(before.toBuilder().count(dropped).build())
                  )
               );
            return drop;
         }
      } else {
         return null;
      }
   }

   public static boolean locked(@Nullable ItemData item) {
      if (item != null && !item.isNull()) {
         NbtMap tag = item.getTag();
         return tag != null && tag.getByte("minecraft:item_lock", (byte)0) != 0;
      } else {
         return false;
      }
   }

   private static ItemData withoutStackId(ItemData item) {
      return !item.isNull() && (item.isUsingNetId() || item.getNetId() != 0) ? item.toBuilder().usingNetId(false).netId(0).build() : item;
   }
}
