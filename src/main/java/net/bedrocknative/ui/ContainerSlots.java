package net.bedrocknative.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ContainerSlots {
   public static final String CONTAINER_ITEMS = "container_items";
   public static final String INVENTORY_ITEMS = "inventory_items";
   public static final String HOTBAR_ITEMS = "hotbar_items";
   public static final String ARMOR_ITEMS = "armor_items";
   public static final String OFFHAND_ITEMS = "offhand_items";
   public static final String CRAFTING_INPUT_ITEMS = "crafting_input_items";
   public static final String CRAFTING_OUTPUT_ITEMS = "crafting_output_items";
   public static final String FURNACE_INGREDIENT_ITEMS = "furnace_ingredient_items";
   public static final String FURNACE_FUEL_ITEMS = "furnace_fuel_items";
   public static final String FURNACE_OUTPUT_ITEMS = "furnace_output_items";
   public static final String BREWING_RESULT_ITEMS = "brewing_result_items";
   public static final String BREWING_INPUT_ITEM = "brewing_input_item";
   public static final String BREWING_FUEL_ITEM = "brewing_fuel_item";
   public static final String ANVIL_INPUT_ITEMS = "anvil_input_items";
   public static final String ANVIL_MATERIAL_ITEMS = "anvil_material_items";
   public static final String ANVIL_RESULT_ITEMS = "anvil_result_items";
   public static final String ENCHANTING_INPUT_ITEMS = "enchanting_input_items";
   public static final String ENCHANTING_LAPIS_ITEMS = "enchanting_lapis_items";
   public static final String GRINDSTONE_INPUT_ITEMS = "grindstone_input_items";
   public static final String GRINDSTONE_ADDITIONAL_ITEMS = "grindstone_additional_items";
   public static final String GRINDSTONE_RESULT_ITEMS = "grindstone_result_items";
   public static final String STONECUTTER_INPUT_ITEMS = "stonecutter_input_items";
   public static final String STONECUTTER_RESULT_ITEMS = "stonecutter_result_items";
   public static final String LOOM_INPUT_ITEMS = "loom_input_items";
   public static final String LOOM_DYE_ITEMS = "loom_dye_items";
   public static final String LOOM_MATERIAL_ITEMS = "loom_material_items";
   public static final String LOOM_RESULT_ITEMS = "loom_result_items";
   public static final String SMITHING_TEMPLATE_ITEMS = "smithing_table_template_items";
   public static final String SMITHING_INPUT_ITEMS = "smithing_table_input_items";
   public static final String SMITHING_MATERIAL_ITEMS = "smithing_table_material_items";
   public static final String SMITHING_RESULT_ITEMS = "smithing_table_result_items";
   public static final String CARTOGRAPHY_INPUT_ITEMS = "cartography_input_items";
   public static final String CARTOGRAPHY_ADDITIONAL_ITEMS = "cartography_additional_items";
   public static final String CARTOGRAPHY_RESULT_ITEMS = "cartography_result_items";
   public static final String BEACON_PAYMENT_ITEMS = "beacon_payment_items";
   public static final String TRADE_INGREDIENT_1_ITEMS = "trade2_ingredient1_item";
   public static final String TRADE_INGREDIENT_2_ITEMS = "trade2_ingredient2_item";
   public static final String TRADE_RESULT_ITEMS = "trade2_result_item";
   public static final int INVENTORY_SIZE = 27;
   public static final int HOTBAR_SIZE = 9;
   private final ContainerSlots.Kind kind;
   private final Map<String, int[]> collections;
   private final int menuSize;

   private ContainerSlots(ContainerSlots.Kind kind, Map<String, int[]> collections, int menuSize) {
      this.kind = kind;
      this.collections = Collections.unmodifiableMap(collections);
      this.menuSize = menuSize;
   }

   public static ContainerSlots chest(int own) {
      return new ContainerSlots.Builder().add("container_items", Math.max(0, own)).player().build(ContainerSlots.Kind.CHEST);
   }

   public static ContainerSlots inventory() {
      return new ContainerSlots.Builder()
         .add("crafting_output_items", 1)
         .add("crafting_input_items", 4)
         .add("armor_items", 4)
         .player()
         .add("offhand_items", 1)
         .build(ContainerSlots.Kind.INVENTORY);
   }

   public static ContainerSlots craftingTable() {
      return new ContainerSlots.Builder().add("crafting_output_items", 1).add("crafting_input_items", 9).player().build(ContainerSlots.Kind.CRAFTING_TABLE);
   }

   public static ContainerSlots furnace() {
      return new ContainerSlots.Builder()
         .add("furnace_ingredient_items", 1)
         .add("furnace_fuel_items", 1)
         .add("furnace_output_items", 1)
         .player()
         .build(ContainerSlots.Kind.FURNACE);
   }

   public static ContainerSlots brewingStand() {
      return new ContainerSlots.Builder()
         .add("brewing_result_items", 3)
         .add("brewing_input_item", 1)
         .add("brewing_fuel_item", 1)
         .player()
         .build(ContainerSlots.Kind.BREWING_STAND);
   }

   public static ContainerSlots creativeHotbar() {
      return new ContainerSlots.Builder().skip(45).add("hotbar_items", 9).build(ContainerSlots.Kind.INVENTORY);
   }

   public static ContainerSlots creativeInventory() {
      return new ContainerSlots.Builder().skip(5).add("armor_items", 4).player().add("offhand_items", 1).build(ContainerSlots.Kind.INVENTORY);
   }

   public static ContainerSlots anvil() {
      return new ContainerSlots.Builder()
         .add("anvil_input_items", 1)
         .add("anvil_material_items", 1)
         .add("anvil_result_items", 1)
         .player()
         .build(ContainerSlots.Kind.ANVIL);
   }

   public static ContainerSlots enchanting() {
      return new ContainerSlots.Builder().add("enchanting_input_items", 1).add("enchanting_lapis_items", 1).player().build(ContainerSlots.Kind.ENCHANTING);
   }

   public static ContainerSlots grindstone() {
      return new ContainerSlots.Builder()
         .add("grindstone_input_items", 1)
         .add("grindstone_additional_items", 1)
         .add("grindstone_result_items", 1)
         .player()
         .build(ContainerSlots.Kind.GRINDSTONE);
   }

   public static ContainerSlots stonecutter() {
      return new ContainerSlots.Builder().add("stonecutter_input_items", 1).add("stonecutter_result_items", 1).player().build(ContainerSlots.Kind.STONECUTTER);
   }

   public static ContainerSlots loom() {
      return new ContainerSlots.Builder()
         .add("loom_input_items", 1)
         .add("loom_dye_items", 1)
         .add("loom_material_items", 1)
         .add("loom_result_items", 1)
         .player()
         .build(ContainerSlots.Kind.LOOM);
   }

   public static ContainerSlots smithing() {
      return new ContainerSlots.Builder()
         .add("smithing_table_template_items", 1)
         .add("smithing_table_input_items", 1)
         .add("smithing_table_material_items", 1)
         .add("smithing_table_result_items", 1)
         .player()
         .build(ContainerSlots.Kind.SMITHING);
   }

   public static ContainerSlots cartography() {
      return new ContainerSlots.Builder()
         .add("cartography_input_items", 1)
         .add("cartography_additional_items", 1)
         .add("cartography_result_items", 1)
         .player()
         .build(ContainerSlots.Kind.CARTOGRAPHY);
   }

   public static ContainerSlots beacon() {
      return new ContainerSlots.Builder().add("beacon_payment_items", 1).player().build(ContainerSlots.Kind.BEACON);
   }

   public static ContainerSlots merchant() {
      return new ContainerSlots.Builder()
         .add("trade2_ingredient1_item", 1)
         .add("trade2_ingredient2_item", 1)
         .add("trade2_result_item", 1)
         .player()
         .build(ContainerSlots.Kind.MERCHANT);
   }

   public ContainerSlots.Kind kind() {
      return this.kind;
   }

   public int javaSlot(@Nullable String collection, int index) {
      int[] slots = collection == null ? null : this.collections.get(collection);
      return slots != null && index >= 0 && index < slots.length ? slots[index] : -1;
   }

   public int size(String collection) {
      int[] slots = this.collections.get(collection);
      return slots == null ? 0 : slots.length;
   }

   public Set<String> collections() {
      return this.collections.keySet();
   }

   public int menuSize() {
      return this.menuSize;
   }

   private static final class Builder {
      private final Map<String, int[]> collections = new LinkedHashMap<>();
      private int next;

      ContainerSlots.Builder add(String collection, int count) {
         int[] slots = new int[count];

         for (int i = 0; i < count; i++) {
            slots[i] = this.next++;
         }

         this.collections.put(collection, slots);
         return this;
      }

      ContainerSlots.Builder player() {
         return this.add("inventory_items", 27).add("hotbar_items", 9);
      }

      ContainerSlots.Builder skip(int count) {
         this.next += count;
         return this;
      }

      ContainerSlots build(ContainerSlots.Kind kind) {
         return new ContainerSlots(kind, this.collections, this.next);
      }
   }

   public enum Kind {
      CHEST,
      INVENTORY,
      CRAFTING_TABLE,
      FURNACE,
      BREWING_STAND,
      ANVIL,
      ENCHANTING,
      GRINDSTONE,
      STONECUTTER,
      LOOM,
      SMITHING,
      CARTOGRAPHY,
      BEACON,
      MERCHANT;
   }
}
