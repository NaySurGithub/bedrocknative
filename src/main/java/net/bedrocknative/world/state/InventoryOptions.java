package net.bedrocknative.world.state;

import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryLayout;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabLeft;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabRight;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;

public record InventoryOptions(InventoryTabLeft leftTab, InventoryTabRight rightTab, boolean filtering, InventoryLayout layout, InventoryLayout craftingLayout) {
   public static final InventoryOptions DEFAULT = new InventoryOptions(
      InventoryTabLeft.RECIPE_SEARCH, InventoryTabRight.CRAFTING, false, InventoryLayout.DEFAULT, InventoryLayout.NONE
   );

   public static InventoryOptions of(SetPlayerInventoryOptionsPacket packet) {
      return new InventoryOptions(
         packet.getLeftTab() != null ? packet.getLeftTab() : DEFAULT.leftTab(),
         packet.getRightTab() != null ? packet.getRightTab() : DEFAULT.rightTab(),
         packet.isFiltering(),
         packet.getLayout() != null ? packet.getLayout() : DEFAULT.layout(),
         packet.getCraftingLayout() != null ? packet.getCraftingLayout() : DEFAULT.craftingLayout()
      );
   }

   public SetPlayerInventoryOptionsPacket toPacket() {
      SetPlayerInventoryOptionsPacket packet = new SetPlayerInventoryOptionsPacket();
      packet.setLeftTab(this.leftTab);
      packet.setRightTab(this.rightTab);
      packet.setFiltering(this.filtering);
      packet.setLayout(this.layout);
      packet.setCraftingLayout(this.craftingLayout);
      return packet;
   }
}
