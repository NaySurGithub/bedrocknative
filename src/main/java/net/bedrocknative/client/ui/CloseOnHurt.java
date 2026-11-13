package net.bedrocknative.client.ui;

import net.bedrocknative.BedrockNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import org.jspecify.annotations.Nullable;

public final class CloseOnHurt {
   private CloseOnHurt() {
   }

   public static void playerHurt(Minecraft minecraft) {
      Screen screen = minecraft.gui.screen();
      LocalPlayer player = minecraft.player;
      if (screen != null && player != null && closes(screen)) {
         BedrockNative.LOGGER.debug("The player was hurt: closing {}", screen.getClass().getSimpleName());
         if (screen instanceof AbstractContainerScreen && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
         } else {
            screen.onClose();
         }
      }
   }

   private static boolean isPlayersInventory(Screen screen) {
      LocalPlayer player = Minecraft.getInstance().player;
      return screen instanceof AbstractContainerScreen<?> container && player != null && container.getMenu() == player.inventoryMenu;
   }

   public static boolean closes(Screen screen) {
      if (screen instanceof CreativeModeInventoryScreen || isPlayersInventory(screen)) {
         return false;
      } else if (screen instanceof ChatScreen || screen instanceof JsonUiChatScreen) {
         return false;
      } else if (screen instanceof JsonUiHost host) {
         return host.ui().closesOnPlayerHurt();
      } else {
         boolean bedrockCloses = switch (screen) {
            case InventoryScreen ignored -> false;
            case CreativeModeInventoryScreen ignoredx -> false;
            case AbstractMountInventoryScreen<?> ignoredxx -> false;
            case InBedChatScreen ignoredxxx -> false;
            case AbstractContainerScreen<?> ignoredxxxx -> true;
            case ChatScreen ignoredxxxxx -> true;
            case AbstractSignEditScreen ignoredxxxxxx -> true;
            case BookViewScreen ignoredxxxxxxx -> true;
            case BookEditScreen ignoredxxxxxxxx -> true;
            default -> false;
         };
         if (!bedrockCloses) {
            return false;
         } else {
            String key = key(screen);
            return key == null ? JsonUi.mode() != JsonUi.Mode.OFF : JsonUi.drawn(key);
         }
      }
   }

   @Nullable
   private static String key(Screen screen) {
      if (screen instanceof ChatScreen) {
         return "chat";
      } else if (screen instanceof AbstractContainerScreen<?> container) {
         AbstractContainerMenu menu = container.getMenu();

         return switch (menu) {
            case ChestMenu ignored -> "chest";
            case ShulkerBoxMenu ignoredx -> "chest";
            case HopperMenu ignoredxx -> "redstone";
            case DispenserMenu ignoredxxx -> "redstone";
            case CraftingMenu ignoredxxxx -> "inventory";
            case AbstractFurnaceMenu ignoredxxxxx -> "furnace";
            case BrewingStandMenu ignoredxxxxxx -> "brewing_stand";
            case AnvilMenu ignoredxxxxxxx -> "anvil";
            case EnchantmentMenu ignoredxxxxxxxx -> "enchanting";
            case GrindstoneMenu ignoredxxxxxxxxx -> "grindstone";
            case StonecutterMenu ignoredxxxxxxxxxx -> "stonecutter";
            case LoomMenu ignoredxxxxxxxxxxx -> "loom";
            case SmithingMenu ignoredxxxxxxxxxxxx -> "smithing_table";
            case CartographyTableMenu ignoredxxxxxxxxxxxxx -> "cartography";
            case BeaconMenu ignoredxxxxxxxxxxxxxx -> "beacon";
            case MerchantMenu ignoredxxxxxxxxxxxxxxx -> "trading";
            default -> null;
         };
      } else {
         return null;
      }
   }
}
