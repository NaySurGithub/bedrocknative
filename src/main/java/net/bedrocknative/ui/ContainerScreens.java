package net.bedrocknative.ui;

import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.jspecify.annotations.Nullable;

public final class ContainerScreens {
   public static final String SMALL_CHEST = "chest.small_chest_screen";
   public static final String LARGE_CHEST = "chest.large_chest_screen";
   public static final String INVENTORY = "crafting.inventory_screen";
   public static final String CRAFTING_TABLE = "crafting.crafting_screen";
   public static final String FURNACE = "furnace.furnace_screen";
   public static final String BLAST_FURNACE = "blast_furnace.blast_furnace_screen";
   public static final String SMOKER = "smoker.smoker_screen";
   public static final String BREWING_STAND = "brewing_stand.brewing_stand_screen";
   public static final String ANVIL_SCREEN = "anvil.anvil_screen";
   public static final String ENCHANTING_SCREEN = "enchanting.enchanting_screen";
   public static final String GRINDSTONE_SCREEN = "grindstone.grindstone_screen";
   public static final String STONECUTTER_SCREEN = "stonecutter.stonecutter_screen";
   public static final String LOOM_SCREEN = "loom.loom_screen";
   public static final String SMITHING_SCREEN = "smithing_table.smithing_table_screen";
   public static final String CARTOGRAPHY_SCREEN = "cartography.cartography_screen";
   public static final String BEACON_SCREEN = "beacon.beacon_screen";
   public static final String TRADE_SCREEN = "trade2.trade_screen";
   public static final String CHEST = "chest";
   public static final String REDSTONE = "redstone";
   public static final String CRAFTING = "inventory";
   public static final String FURNACES = "furnace";
   public static final String BREWING = "brewing_stand";
   public static final String CREATIVE = "creative";
   public static final String ANVIL = "anvil";
   public static final String ENCHANTING = "enchanting";
   public static final String GRINDSTONE = "grindstone";
   public static final String STONECUTTER = "stonecutter";
   public static final String LOOM = "loom";
   public static final String SMITHING = "smithing_table";
   public static final String CARTOGRAPHY = "cartography";
   public static final String BEACON = "beacon";
   public static final String TRADING = "trading";

   private ContainerScreens() {
   }

   public static ContainerScreens.@Nullable Choice of(ContainerType type, int size, ContainerScreens.Block block) {
      return switch (type) {
         case CONTAINER -> {
            switch (block) {
               case ENDER_CHEST:
                  yield chest("chest.ender_chest_screen", "container.enderchest", padded(size));
               case BARREL:
                  yield size == 27 ? chest("chest.barrel_screen", "container.barrel", 27) : null;
               case SHULKER_BOX:
                  yield size == 27 ? chest("chest.shulker_box_screen", "container.shulkerbox", 27) : null;
               default:
                  yield chest(size);
            }
         }
         case MINECART_CHEST -> size == 27 ? chest("chest.small_chest_screen", "entity.chest_minecart.name", 27) : null;
         case CHEST_BOAT -> size == 27 ? chest("chest.small_chest_screen", "container.chest", 27) : null;
         case HOPPER -> new ContainerScreens.Choice("redstone.hopper_screen", "redstone", "container.hopper", ContainerSlots.chest(5));
         case MINECART_HOPPER -> new ContainerScreens.Choice("redstone.hopper_screen", "redstone", "entity.hopper_minecart.name", ContainerSlots.chest(5));
         case DISPENSER -> new ContainerScreens.Choice("redstone.dispenser_screen", "redstone", "container.dispenser", ContainerSlots.chest(9));
         case DROPPER -> new ContainerScreens.Choice("redstone.dropper_screen", "redstone", "container.dropper", ContainerSlots.chest(9));
         case WORKBENCH -> new ContainerScreens.Choice("crafting.crafting_screen", "inventory", "container.crafting", ContainerSlots.craftingTable());
         case FURNACE -> new ContainerScreens.Choice("furnace.furnace_screen", "furnace", "container.furnace", ContainerSlots.furnace());
         case BLAST_FURNACE -> new ContainerScreens.Choice("blast_furnace.blast_furnace_screen", "furnace", "tile.blast_furnace.name", ContainerSlots.furnace());
         case SMOKER -> new ContainerScreens.Choice("smoker.smoker_screen", "furnace", "tile.smoker.name", ContainerSlots.furnace());
         case BREWING_STAND -> new ContainerScreens.Choice(
            "brewing_stand.brewing_stand_screen", "brewing_stand", "container.brewing", ContainerSlots.brewingStand()
         );
         case ANVIL -> new ContainerScreens.Choice("anvil.anvil_screen", "anvil", "container.repair", ContainerSlots.anvil());
         case ENCHANTMENT -> new ContainerScreens.Choice("enchanting.enchanting_screen", "enchanting", "container.enchant", ContainerSlots.enchanting());
         case GRINDSTONE -> new ContainerScreens.Choice(
            "grindstone.grindstone_screen", "grindstone", "container.repairAndDisenchant", ContainerSlots.grindstone()
         );
         case STONECUTTER -> new ContainerScreens.Choice("stonecutter.stonecutter_screen", "stonecutter", "container.stonecutter", ContainerSlots.stonecutter());
         case LOOM -> new ContainerScreens.Choice("loom.loom_screen", "loom", "container.loom", ContainerSlots.loom());
         case SMITHING_TABLE -> new ContainerScreens.Choice(
            "smithing_table.smithing_table_screen", "smithing_table", "container.smithing_table.upgrade", ContainerSlots.smithing()
         );
         case CARTOGRAPHY -> new ContainerScreens.Choice(
            "cartography.cartography_screen", "cartography", "tile.cartography_table.name", ContainerSlots.cartography()
         );
         case BEACON -> new ContainerScreens.Choice("beacon.beacon_screen", "beacon", "tile.beacon.name", ContainerSlots.beacon());
         default -> null;
      };
   }

   public static ContainerScreens.Choice inventory() {
      return new ContainerScreens.Choice("crafting.inventory_screen", "inventory", "container.crafting", ContainerSlots.inventory());
   }

   public static ContainerScreens.@Nullable Choice trade(boolean newTradingUi) {
      return newTradingUi ? new ContainerScreens.Choice("trade2.trade_screen", "trading", "entity.villager.name", ContainerSlots.merchant()) : null;
   }

   private static ContainerScreens.@Nullable Choice chest(int size) {
      return switch (size) {
         case 27 -> chest("chest.small_chest_screen", "container.chest", 27);
         case 54 -> chest("chest.large_chest_screen", "container.chestDouble", 54);
         default -> size > 54 ? chest("chest.large_chest_screen", "container.chestDouble", padded(size)) : null;
      };
   }

   static int padded(int size) {
      int slots = Math.max(9, Math.min(216, size));
      return (slots + 8) / 9 * 9;
   }

   private static ContainerScreens.Choice chest(String screen, String titleKey, int size) {
      return new ContainerScreens.Choice(screen, "chest", titleKey, ContainerSlots.chest(size));
   }

   public enum Block {
      CHEST,
      ENDER_CHEST,
      BARREL,
      SHULKER_BOX,
      OTHER;
   }

   public record Choice(String screen, String parityKey, String titleKey, ContainerSlots slots) {
   }
}
