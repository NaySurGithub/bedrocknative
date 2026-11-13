package net.bedrocknative.client.inventory;

import java.util.Objects;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BedrockChestMenu extends ChestMenu {
   static final int MAX_SLOTS = 216;
   public static final MenuType<BedrockChestMenu> TYPE = (MenuType<BedrockChestMenu>)Registry.register(
      BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath("bedrocknative", "bedrock_chest"), new MenuType(BedrockChestMenu::new, FeatureFlags.VANILLA_SET)
   );
   private static volatile int pending = -1;
   private final int realSize;

   private BedrockChestMenu(int containerId, Inventory inventory) {
      this(containerId, inventory, pending);
   }

   private BedrockChestMenu(int containerId, Inventory inventory, int size) {
      super(TYPE, containerId, inventory, new SimpleContainer(padded(size)), rows(padded(size)));
      this.realSize = Math.min(216, Math.max(9, size));
   }

   public static void expect(int size) {
      pending = size;
   }

   public static void register() {
      Objects.requireNonNull(TYPE, "menu type");
      MenuScreens.register(TYPE, (menu, inventory, title) -> new ContainerScreen(menu, inventory, title));
   }

   public static BedrockChestMenu of(int containerId, Inventory inventory, int size) {
      return new BedrockChestMenu(containerId, inventory, size);
   }

   static int padded(int size) {
      int slots = Math.max(9, Math.min(216, size));
      return (slots + 8) / 9 * 9;
   }

   static int real(int size) {
      return Math.min(216, Math.max(9, size));
   }

   private static int rows(int padded) {
      return padded / 9;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (slot != null && slot.hasItem()) {
         ItemStack stack = slot.getItem();
         ItemStack copy = stack.copy();
         int containerSlots = this.slots.size() - 36;
         if (index < containerSlots) {
            if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }
         } else if (!this.moveItemStackTo(stack, 0, this.realSize, false)) {
            return ItemStack.EMPTY;
         }

         if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
         } else {
            slot.onTake(player, stack);
            return copy;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }
}
