package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

public record ContainerLayout(MenuType<?> menu, List<BedrockSlot> slots) {
   private static final int PLAYER_SLOTS = 36;
   public static final ContainerLayout PLAYER = playerInventory();
   public static final BedrockSlot ANVIL_INPUT = BedrockSlot.ui(1, ContainerSlotType.ANVIL_INPUT);
   public static final BedrockSlot ANVIL_MATERIAL = BedrockSlot.ui(2, ContainerSlotType.ANVIL_MATERIAL);
   public static final BedrockSlot STONECUTTER_INPUT = BedrockSlot.ui(3, ContainerSlotType.STONECUTTER_INPUT);
   public static final BedrockSlot TRADE2_INGREDIENT_1 = BedrockSlot.ui(4, ContainerSlotType.TRADE2_INGREDIENT_1);
   public static final BedrockSlot TRADE2_INGREDIENT_2 = BedrockSlot.ui(5, ContainerSlotType.TRADE2_INGREDIENT_2);
   public static final BedrockSlot TRADE_INGREDIENT_1 = BedrockSlot.ui(6, ContainerSlotType.TRADE_INGREDIENT_1);
   public static final BedrockSlot TRADE_INGREDIENT_2 = BedrockSlot.ui(7, ContainerSlotType.TRADE_INGREDIENT_2);
   public static final BedrockSlot LOOM_INPUT = BedrockSlot.ui(9, ContainerSlotType.LOOM_INPUT);
   public static final BedrockSlot LOOM_DYE = BedrockSlot.ui(10, ContainerSlotType.LOOM_DYE);
   public static final BedrockSlot LOOM_MATERIAL = BedrockSlot.ui(11, ContainerSlotType.LOOM_MATERIAL);
   public static final BedrockSlot CARTOGRAPHY_INPUT = BedrockSlot.ui(12, ContainerSlotType.CARTOGRAPHY_INPUT);
   public static final BedrockSlot CARTOGRAPHY_ADDITIONAL = BedrockSlot.ui(13, ContainerSlotType.CARTOGRAPHY_ADDITIONAL);
   public static final BedrockSlot ENCHANTING_INPUT = BedrockSlot.ui(14, ContainerSlotType.ENCHANTING_INPUT);
   public static final BedrockSlot ENCHANTING_MATERIAL = BedrockSlot.ui(15, ContainerSlotType.ENCHANTING_MATERIAL);
   public static final BedrockSlot GRINDSTONE_INPUT = BedrockSlot.ui(16, ContainerSlotType.GRINDSTONE_INPUT);
   public static final BedrockSlot GRINDSTONE_ADDITIONAL = BedrockSlot.ui(17, ContainerSlotType.GRINDSTONE_ADDITIONAL);
   public static final BedrockSlot BEACON_PAYMENT = BedrockSlot.ui(27, ContainerSlotType.BEACON_PAYMENT);
   public static final BedrockSlot SMITHING_INPUT = BedrockSlot.ui(51, ContainerSlotType.SMITHING_TABLE_INPUT);
   public static final BedrockSlot SMITHING_MATERIAL = BedrockSlot.ui(52, ContainerSlotType.SMITHING_TABLE_MATERIAL);
   public static final BedrockSlot SMITHING_TEMPLATE = BedrockSlot.ui(53, ContainerSlotType.SMITHING_TABLE_TEMPLATE);

   public static ContainerLayout trade(boolean newUi) {
      return new ContainerLayout(
         MenuType.MERCHANT,
         newUi
            ? List.of(TRADE2_INGREDIENT_1, TRADE2_INGREDIENT_2, BedrockSlot.CREATED_OUTPUT)
            : List.of(TRADE_INGREDIENT_1, TRADE_INGREDIENT_2, BedrockSlot.CREATED_OUTPUT)
      );
   }

   public List<BedrockSlot> uiInputs() {
      return this.slots
         .stream()
         .filter(
            slot -> slot.container() == 124
               && slot.type() != ContainerSlotType.CRAFTING_INPUT
               && !slot.equals(BedrockSlot.CREATED_OUTPUT)
               && !slot.equals(BedrockSlot.CURSOR)
         )
         .toList();
   }

   public BedrockSlot slot(int javaSlot, int menuSize) {
      if (this == PLAYER) {
         return javaSlot >= 0 && javaSlot < this.slots.size() ? this.slots.get(javaSlot) : null;
      } else {
         int own = this.menu == MenuType.CRAFTER_3x3 ? this.slots.size() : menuSize - 36;
         if (javaSlot >= own && javaSlot < own + 36) {
            int player = javaSlot - own;
            return BedrockSlot.player(player < 27 ? player + 9 : player - 27);
         } else {
            return javaSlot >= 0 && javaSlot < this.slots.size() ? this.slots.get(javaSlot) : null;
         }
      }
   }

   public int javaSlot(BedrockSlot slot) {
      return this.slots.indexOf(slot);
   }

   public static ContainerLayout of(ContainerType type, int id, int size, Block block) {
      return switch (type) {
         case CONTAINER, MINECART_CHEST, CHEST_BOAT -> {
            ContainerSlotType name = block instanceof BarrelBlock
               ? ContainerSlotType.BARREL
               : (block instanceof ShulkerBoxBlock ? ContainerSlotType.SHULKER_BOX : ContainerSlotType.LEVEL_ENTITY);
            MenuType<?> menu = block instanceof ShulkerBoxBlock && size == 27 ? MenuType.SHULKER_BOX : generic(size);
            if (menu == null && type == ContainerType.CONTAINER && size > 54) {
               menu = BedrockChestMenu.TYPE;
            }

            yield menu == null ? null : sequential(menu, id, name, size);
         }
         case DISPENSER, DROPPER -> sequential(MenuType.GENERIC_3x3, id, ContainerSlotType.LEVEL_ENTITY, 9);
         case HOPPER, MINECART_HOPPER -> sequential(MenuType.HOPPER, id, ContainerSlotType.LEVEL_ENTITY, 5);
         case FURNACE -> furnace(MenuType.FURNACE, id, ContainerSlotType.FURNACE_INGREDIENT);
         case BLAST_FURNACE -> furnace(MenuType.BLAST_FURNACE, id, ContainerSlotType.BLAST_FURNACE_INGREDIENT);
         case SMOKER -> furnace(MenuType.SMOKER, id, ContainerSlotType.SMOKER_INGREDIENT);
         case BREWING_STAND -> new ContainerLayout(
            MenuType.BREWING_STAND,
            List.of(
               new BedrockSlot(id, 1, ContainerSlotType.BREWING_RESULT),
               new BedrockSlot(id, 2, ContainerSlotType.BREWING_RESULT),
               new BedrockSlot(id, 3, ContainerSlotType.BREWING_RESULT),
               new BedrockSlot(id, 0, ContainerSlotType.BREWING_INPUT),
               new BedrockSlot(id, 4, ContainerSlotType.BREWING_FUEL)
            )
         );
         case WORKBENCH -> {
            List<BedrockSlot> slots = new ArrayList<>(List.of(BedrockSlot.CREATED_OUTPUT));

            for (int slot = 0; slot < 9; slot++) {
               slots.add(BedrockSlot.ui(32 + slot, ContainerSlotType.CRAFTING_INPUT));
            }

            yield new ContainerLayout(MenuType.CRAFTING, slots);
         }
         case ANVIL -> new ContainerLayout(MenuType.ANVIL, List.of(ANVIL_INPUT, ANVIL_MATERIAL, BedrockSlot.CREATED_OUTPUT));
         case ENCHANTMENT -> new ContainerLayout(MenuType.ENCHANTMENT, List.of(ENCHANTING_INPUT, ENCHANTING_MATERIAL));
         case GRINDSTONE -> new ContainerLayout(MenuType.GRINDSTONE, List.of(GRINDSTONE_INPUT, GRINDSTONE_ADDITIONAL, BedrockSlot.CREATED_OUTPUT));
         case STONECUTTER -> new ContainerLayout(MenuType.STONECUTTER, List.of(STONECUTTER_INPUT, BedrockSlot.CREATED_OUTPUT));
         case CARTOGRAPHY -> new ContainerLayout(MenuType.CARTOGRAPHY_TABLE, List.of(CARTOGRAPHY_INPUT, CARTOGRAPHY_ADDITIONAL, BedrockSlot.CREATED_OUTPUT));
         case LOOM -> new ContainerLayout(MenuType.LOOM, List.of(LOOM_INPUT, LOOM_DYE, LOOM_MATERIAL, BedrockSlot.CREATED_OUTPUT));
         case SMITHING_TABLE -> new ContainerLayout(
            MenuType.SMITHING, List.of(SMITHING_TEMPLATE, SMITHING_INPUT, SMITHING_MATERIAL, BedrockSlot.CREATED_OUTPUT)
         );
         case BEACON -> new ContainerLayout(MenuType.BEACON, List.of(BEACON_PAYMENT));
         case CRAFTER -> crafter(id);
         default -> null;
      };
   }

   private static ContainerLayout crafter(int id) {
      List<BedrockSlot> slots = new ArrayList<>(9);

      for (int slot = 0; slot < 9; slot++) {
         slots.add(new BedrockSlot(id, slot, ContainerSlotType.CRAFTER_BLOCK_CONTAINER));
      }

      return new ContainerLayout(MenuType.CRAFTER_3x3, slots);
   }

   public static boolean sizedByContent(ContainerType type) {
      return type == ContainerType.CONTAINER || type == ContainerType.MINECART_CHEST || type == ContainerType.CHEST_BOAT || type == ContainerType.HORSE;
   }

   private static MenuType<?> generic(int size) {
      return switch (size) {
         case 9 -> MenuType.GENERIC_9x1;
         case 18 -> MenuType.GENERIC_9x2;
         case 27 -> MenuType.GENERIC_9x3;
         case 36 -> MenuType.GENERIC_9x4;
         case 45 -> MenuType.GENERIC_9x5;
         case 54 -> MenuType.GENERIC_9x6;
         default -> null;
      };
   }

   private static ContainerLayout sequential(MenuType<?> menu, int id, ContainerSlotType name, int size) {
      List<BedrockSlot> slots = new ArrayList<>(size);

      for (int slot = 0; slot < size; slot++) {
         slots.add(new BedrockSlot(id, slot, name));
      }

      return new ContainerLayout(menu, slots);
   }

   private static ContainerLayout furnace(MenuType<?> menu, int id, ContainerSlotType ingredient) {
      return new ContainerLayout(
         menu,
         List.of(
            new BedrockSlot(id, 0, ingredient),
            new BedrockSlot(id, 1, ContainerSlotType.FURNACE_FUEL),
            new BedrockSlot(id, 2, ContainerSlotType.FURNACE_RESULT)
         )
      );
   }

   private static ContainerLayout playerInventory() {
      List<BedrockSlot> slots = new ArrayList<>();
      slots.add(BedrockSlot.CREATED_OUTPUT);

      for (int slot = 0; slot < 4; slot++) {
         slots.add(BedrockSlot.ui(28 + slot, ContainerSlotType.CRAFTING_INPUT));
      }

      for (int slot = 0; slot < 4; slot++) {
         slots.add(BedrockSlot.armor(slot));
      }

      for (int slot = 9; slot < 36; slot++) {
         slots.add(BedrockSlot.player(slot));
      }

      for (int slot = 0; slot < 9; slot++) {
         slots.add(BedrockSlot.player(slot));
      }

      slots.add(BedrockSlot.offhand());
      if (slots.size() != 46) {
         throw new IllegalStateException("Inventory menu layout has " + slots.size() + " slots");
      } else {
         return new ContainerLayout(null, slots);
      }
   }
}
