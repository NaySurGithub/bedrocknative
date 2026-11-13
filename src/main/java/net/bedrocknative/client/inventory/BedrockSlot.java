package net.bedrocknative.client.inventory;

import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;

public record BedrockSlot(int container, int slot, ContainerSlotType type) {
   public static final int UI = 124;
   public static final int UI_SIZE = 54;
   public static final int CREATED_OUTPUT_SLOT = 50;
   public static final BedrockSlot CURSOR = new BedrockSlot(124, 0, ContainerSlotType.CURSOR);
   public static final BedrockSlot CREATED_OUTPUT = ui(50, ContainerSlotType.CREATED_OUTPUT);
   private static final int OFFHAND_REQUEST_SLOT = 1;

   public static BedrockSlot player(int slot) {
      return new BedrockSlot(0, slot, slot < 9 ? ContainerSlotType.HOTBAR : ContainerSlotType.INVENTORY);
   }

   public static BedrockSlot armor(int slot) {
      return new BedrockSlot(120, slot, ContainerSlotType.ARMOR);
   }

   public static BedrockSlot offhand() {
      return new BedrockSlot(119, 0, ContainerSlotType.OFFHAND);
   }

   public static BedrockSlot ui(int slot, ContainerSlotType type) {
      return new BedrockSlot(124, slot, type);
   }

   public static int containerOf(ContainerSlotType type, int openContainer) {
      return switch (type) {
         case HOTBAR, INVENTORY, HOTBAR_AND_INVENTORY -> 0;
         case ARMOR -> 120;
         case OFFHAND -> 119;
         case CURSOR, CRAFTING_INPUT, CREATED_OUTPUT, ANVIL_INPUT, ANVIL_MATERIAL, ANVIL_RESULT, ENCHANTING_INPUT, ENCHANTING_MATERIAL, GRINDSTONE_INPUT, GRINDSTONE_ADDITIONAL, GRINDSTONE_RESULT, STONECUTTER_INPUT, STONECUTTER_RESULT, CARTOGRAPHY_INPUT, CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_RESULT, LOOM_INPUT, LOOM_DYE, LOOM_MATERIAL, LOOM_RESULT, SMITHING_TABLE_INPUT, SMITHING_TABLE_MATERIAL, SMITHING_TABLE_RESULT, SMITHING_TABLE_TEMPLATE, BEACON_PAYMENT, TRADE_INGREDIENT_1, TRADE_INGREDIENT_2, TRADE_RESULT, TRADE2_INGREDIENT_1, TRADE2_INGREDIENT_2, TRADE2_RESULT -> 124;
         default -> openContainer;
      };
   }

   public boolean isPlayer() {
      return this.container == 0;
   }

   public BedrockSlot named(ContainerSlotType name) {
      return new BedrockSlot(this.container, this.slot, name);
   }

   public ItemStackRequestSlotData request(int stackNetworkId) {
      return new ItemStackRequestSlotData(this.type, requestSlot(this.type, this.slot), stackNetworkId, new FullContainerName(this.type, null));
   }

   private static int requestSlot(ContainerSlotType type, int slot) {
      return type == ContainerSlotType.OFFHAND ? 1 : slot;
   }

   public static BedrockSlot fromResponse(ContainerSlotType type, int slot, int openContainer) {
      return new BedrockSlot(containerOf(type, openContainer), type == ContainerSlotType.OFFHAND ? 0 : slot, type);
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof BedrockSlot that && that.container == this.container && that.slot == this.slot;
   }

   @Override
   public int hashCode() {
      return this.container * 31 + this.slot;
   }
}
