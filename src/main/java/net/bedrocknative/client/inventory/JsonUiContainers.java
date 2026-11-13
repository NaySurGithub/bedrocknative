package net.bedrocknative.client.inventory;

import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.ui.ContainerScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import org.jspecify.annotations.Nullable;

public final class JsonUiContainers {
   private static JsonUiContainers.@Nullable Offer offer;
   private static boolean failed;
   private static boolean creativeFailed;

   private JsonUiContainers() {
   }

   public static void reset() {
      failed = false;
      creativeFailed = false;
   }

   static void opening(int containerId, ContainerScreens.@Nullable Choice choice, @Nullable String customName, @Nullable Long owner, Runnable open) {
      opening(containerId, choice, customName, null, owner, open);
   }

   static void opening(int containerId, ContainerScreens.@Nullable Choice choice, @Nullable String customName, @Nullable Workstations trades, Runnable open) {
      opening(containerId, choice, customName, trades, null, open);
   }

   private static void opening(
      int containerId,
      ContainerScreens.@Nullable Choice choice,
      @Nullable String customName,
      @Nullable Workstations trades,
      @Nullable Long owner,
      Runnable open
   ) {
      offer = choice != null && !failed && JsonUi.offered(choice.parityKey())
         ? new JsonUiContainers.Offer(containerId, choice, customName, trades, owner)
         : null;

      try {
         open.run();
      } finally {
         offer = null;
      }
   }

   public static boolean open(MenuType<?> type, Minecraft minecraft, int containerId, Component title) {
      JsonUiContainers.Offer current = offer;
      offer = null;
      if (current != null && current.containerId() == containerId && minecraft.player != null) {
         JsonUiContainerScreen screen;
         try {
            screen = JsonUiContainerScreen.create(
               type, minecraft, containerId, title, current.choice(), current.customName(), current.trades(), current.owner()
            );
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.warn("JSON UI couldn't build container screen {}; the Java screen shows it", current.choice().screen(), e);
            return false;
         }

         if (screen == null) {
            return false;
         }

         screen.fallback(() -> fallBack(minecraft, screen));
         minecraft.player.containerMenu = screen.getMenu();
         minecraft.gui.setScreen(screen);
         return true;
      } else {
         return false;
      }
   }

   public static Screen inventory(Screen screen) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (screen.getClass() == CreativeModeInventoryScreen.class) {
         return creative(minecraft, screen);
      }

      if (screen.getClass() == InventoryScreen.class
         && !failed
         && JsonUi.offered("inventory")
         && BedrockMovement.active()
         && player != null
         && !player.hasInfiniteMaterials()) {
         JsonUiContainerScreen json;
         try {
            json = JsonUiContainerScreen.inventory(minecraft);
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.warn("JSON UI couldn't build the inventory screen; the Java screen shows it", e);
            return screen;
         }

         if (json == null) {
            return screen;
         }

         json.fallback(() -> fallBack(minecraft, json));
         return json;
      } else {
         return screen;
      }
   }

   private static Screen creative(Minecraft minecraft, Screen screen) {
      LocalPlayer player = minecraft.player;
      if (!creativeFailed && JsonUi.offered("creative") && BedrockMovement.active() && player != null && player.hasInfiniteMaterials()) {
         JsonUiCreativeScreen json;
         try {
            json = JsonUiCreativeScreen.create(minecraft, player, player.connection.enabledFeatures(), minecraft.options.operatorItemsTab().get());
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.warn("JSON UI couldn't build the creative inventory; the Java screen shows it", e);
            json = null;
         }

         if (json == null) {
            player.containerMenu = ((CreativeModeInventoryScreen)screen).getMenu();
            return screen;
         } else {
            JsonUiCreativeScreen shown = json;
            shown.fallback(
               () -> {
                  creativeFailed = true;
                  if (minecraft.gui.screen() == shown && minecraft.player != null) {
                     minecraft.gui
                        .setScreen(
                           new CreativeModeInventoryScreen(
                              minecraft.player, minecraft.player.connection.enabledFeatures(), minecraft.options.operatorItemsTab().get()
                           )
                        );
                  }
               }
            );
            return json;
         }
      } else {
         return screen;
      }
   }

   private static void fallBack(Minecraft minecraft, JsonUiContainerScreen broken) {
      failed = true;
      if (minecraft.player != null && minecraft.gui.screen() == broken && minecraft.player.containerMenu == broken.getMenu()) {
         Screen java = javaScreen(broken.getMenu(), minecraft.player.getInventory(), broken.getTitle());
         if (java != null) {
            minecraft.gui.setScreen(java);
         } else {
            minecraft.player.closeContainer();
         }
      }
   }

   private static @Nullable Screen javaScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
      return switch (menu) {
         case ChestMenu chest -> new ContainerScreen(chest, inventory, title);
         case ShulkerBoxMenu shulker -> new ShulkerBoxScreen(shulker, inventory, title);
         case HopperMenu hopper -> new HopperScreen(hopper, inventory, title);
         case DispenserMenu dispenser -> new DispenserScreen(dispenser, inventory, title);
         case InventoryMenu ignored -> new InventoryScreen(inventory.player);
         case CraftingMenu crafting -> new CraftingScreen(crafting, inventory, title);
         case FurnaceMenu furnace -> new FurnaceScreen(furnace, inventory, title);
         case BlastFurnaceMenu blast -> new BlastFurnaceScreen(blast, inventory, title);
         case SmokerMenu smoker -> new SmokerScreen(smoker, inventory, title);
         case BrewingStandMenu brewing -> new BrewingStandScreen(brewing, inventory, title);
         case AnvilMenu anvil -> new AnvilScreen(anvil, inventory, title);
         case EnchantmentMenu enchanting -> new EnchantmentScreen(enchanting, inventory, title);
         case GrindstoneMenu grindstone -> new GrindstoneScreen(grindstone, inventory, title);
         case StonecutterMenu cutter -> new StonecutterScreen(cutter, inventory, title);
         case LoomMenu loom -> new LoomScreen(loom, inventory, title);
         case SmithingMenu smithing -> new SmithingScreen(smithing, inventory, title);
         case CartographyTableMenu cartography -> new CartographyTableScreen(cartography, inventory, title);
         case BeaconMenu beacon -> new BeaconScreen(beacon, inventory, title);
         case MerchantMenu merchant -> new MerchantScreen(merchant, inventory, title);
         default -> null;
      };
   }

   private record Offer(int containerId, ContainerScreens.Choice choice, @Nullable String customName, @Nullable Workstations trades, @Nullable Long owner) {
   }
}
