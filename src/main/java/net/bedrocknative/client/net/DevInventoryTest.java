package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

final class DevInventoryTest {
   private static final long STEP = 600L;
   private static final int SHIFT = 1;
   private static final int CONTROL = 64;
   private final Minecraft minecraft;
   private final StackRequests requests;
   private int jsonUiClicks;
   private int jsonUiMisses;
   private int jsonUiAround;
   private int leftoverChecks;
   private final List<String> leftoverFailures = new ArrayList<>();

   DevInventoryTest(Minecraft minecraft, StackRequests requests) {
      this.minecraft = minecraft;
      this.requests = requests;
   }

   void run() {
      this.commands(
         "gamemode survival",
         "clear @s",
         "give @s dirt 16",
         "give @s stone 10",
         "give @s arrow 10",
         "give @s diamond_sword",
         "give @s oak_planks 8",
         "fill ~1 ~ ~-1 ~3 ~2 ~2 air",
         "setblock ~2 ~-1 ~ stone",
         "setblock ~2 ~-1 ~1 stone",
         "setblock ~2 ~-1 ~-1 stone",
         "setblock ~2 ~-1 ~2 stone"
      );
      sleep(2000L);
      this.awaitJsonUi();
      this.inventoryMoves();
      this.dataSwaps();
      this.chest();
      this.craftingTable();
      this.furnace();
      this.furnaceFamily();
      this.brewingStand();
      this.leftovers();
      this.creative();
      this.entities();
      this.command("fill ~1 ~ ~-1 ~3 ~2 ~2 air");
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER.info("Dev inventory test: done, requests accepted {}, rejected {}", this.requests.accepted(), this.requests.rejected())
         );
   }

   void runArmor() {
      this.commands("gamemode survival", "clear @s");
      sleep(1000L);
      this.armorOnBlocks();
      this.placeAgainstTakenBlock();
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev inventory test leftovers: {} checks, {} failed{}",
                  new Object[]{this.leftoverChecks, this.leftoverFailures.size(), this.leftoverFailures.isEmpty() ? "" : ": " + this.leftoverFailures}
               )
         );
      this.commands("clear @s");
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER.info("Dev inventory test: done, requests accepted {}, rejected {}", this.requests.accepted(), this.requests.rejected())
         );
   }

   private void inventoryMoves() {
      this.minecraft.execute(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(600L);
      this.checkScreen("inventory", "inventory", "crafting.inventory_screen", 46, "container.crafting", false);
      this.screenshot("inventory-player");
      this.jsonUiBegin();
      this.clickItem(Items.DIRT, 0, ContainerInput.PICKUP, "pick up 16 dirt");
      this.click(9, 1, ContainerInput.PICKUP, "right-click: one dirt into main slot 9");
      this.click(10, 0, ContainerInput.PICKUP, "put the other 15 into slot 10");
      this.click(10, 0, ContainerInput.QUICK_MOVE, "shift-click slot 10 into the hotbar");
      this.click(9, 8, ContainerInput.SWAP, "number key 9 on slot 9: swap with hotbar 8");
      this.clickItem(Items.DIRT, 1, ContainerInput.PICKUP, "right-click: take half of a dirt stack");
      this.click(20, 0, ContainerInput.PICKUP, "put it into slot 20");
      this.click(20, 0, ContainerInput.PICKUP, "pick slot 20 up again");
      this.click(20, 0, ContainerInput.PICKUP_ALL, "double-click: collect all dirt onto the cursor");
      this.click(20, 0, ContainerInput.PICKUP, "put the collected dirt into slot 20");
      this.click(20, 0, ContainerInput.THROW, "Q on slot 20: throw one dirt");
      this.clickItem(Items.ARROW, 0, ContainerInput.PICKUP, "pick up 10 arrows");
      this.click(-999, 1, ContainerInput.PICKUP, "right-click outside: drop one arrow");
      this.click(45, 0, ContainerInput.PICKUP, "put 9 arrows into the offhand");
      this.clickItem(Items.DIAMOND_SWORD, 0, ContainerInput.PICKUP, "pick up the sword");
      this.click(20, 0, ContainerInput.PICKUP, "click the dirt in slot 20 with the sword: swap them");
      this.click(30, 0, ContainerInput.PICKUP, "put the dirt into slot 30");
      this.craftInInventory();
      this.click(30, 0, ContainerInput.PICKUP, "pick up the 15 dirt from slot 30");
      this.drag(new int[]{21, 22, 23}, 0, "drag the dirt over main slots 21 to 23: 5 each");
      sleep(600L);
      this.minecraft.execute(() -> this.report("inventory after moves"));
      this.screenshot("inventory-after-moves");
      this.jsonUiReport("inventory", "inventory");
      sleep(600L);
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.verifyInventory();
      this.command("clear @s dirt 0 0");
      sleep(1000L);
   }

   private void dataSwaps() {
      List<Item> items = List.of(Items.TIPPED_ARROW, Items.POTION, Items.IRON_SWORD);
      this.commands("give @s arrow 5 22", "give @s arrow 3 26", "give @s potion 1 21", "give @s potion 1 14", "give @s iron_sword", "give @s iron_sword 1 100");
      sleep(1500L);
      List<ItemData> given = new ArrayList<>();
      List<ItemStack> shown = new ArrayList<>();
      this.minecraft.execute(() -> {
         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
            if (items.contains(stack.getItem())) {
               given.add(this.requests.inventory().item(slot));
               shown.add(stack.copy());
            }
         }

         BedrockNative.LOGGER.info("Dev test data swaps: given {}", given);
         this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
      });
      sleep(600L);
      this.jsonUiBegin();

      for (Item item : items) {
         String name = BuiltInRegistries.ITEM.getKey(item).getPath();
         this.clickItem(item, 0, ContainerInput.PICKUP, "pick up the first " + name);
         this.clickOther(item, "click the other " + name + " with it: Java swaps them");
         this.clickFirstEmpty(9, "put the cursor's " + name + " into the first empty slot");
      }

      sleep(600L);
      this.jsonUiReport("data swaps", "inventory");
      List<String> checks = new ArrayList<>();
      this.minecraft
         .execute(
            () -> {
               this.report("data swaps");
               this.minecraft.player.closeContainer();
               Inventory inventory = this.minecraft.player.getInventory();

               for (int slot = 0; slot < 36; slot++) {
                  ItemStack stack = inventory.getItem(slot);

                  for (int i = 0; i < shown.size() && items.contains(stack.getItem()); i++) {
                     if (ItemStack.isSameItemSameComponents(shown.get(i), stack)) {
                        ItemData bedrock = given.get(i);
                        String itemx = bedrock.getDefinition().getIdentifier().substring("minecraft:".length())
                           + (bedrock.getDamage() != 0 ? ",data=" + bedrock.getDamage() : "");
                        checks.add(
                           slot < 9 ? hasItem(itemx, "slot.hotbar", slot, stack.getCount()) : hasItem(itemx, "slot.inventory", slot - 9, stack.getCount())
                        );
                        break;
                     }
                  }
               }

               BedrockNative.LOGGER.info("Dev test data swaps: checking {} slots server-side", checks.size());
            }
         );
      sleep(600L);
      this.commands(checks.toArray(new String[0]));
      this.commands("clear @s arrow 22", "clear @s arrow 26", "clear @s potion", "clear @s iron_sword");
      sleep(600L);
   }

   private void craftInInventory() {
      this.minecraft.execute(() -> BedrockNative.LOGGER.info("Dev test: Java item tags bound: planks {}", new ItemStack(Items.OAK_PLANKS).is(ItemTags.PLANKS)));
      this.clickItem(Items.OAK_PLANKS, 0, ContainerInput.PICKUP, "pick up 8 planks");

      for (int slot = 1; slot <= 4; slot++) {
         this.click(slot, 1, ContainerInput.PICKUP, "one plank into crafting slot " + slot);
      }

      this.click(32, 0, ContainerInput.PICKUP, "put the other 4 planks into slot 32");
      sleep(600L);
      this.minecraft
         .execute(() -> BedrockNative.LOGGER.info("Dev test: 2x2 crafting result shows {}", this.minecraft.player.inventoryMenu.getSlot(0).getItem()));
      this.click(0, 0, ContainerInput.PICKUP, "take the crafting result");
      this.click(31, 0, ContainerInput.PICKUP, "put it into slot 31");
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test: 2x2 grid after crafting {}, result {}",
                  List.of(1, 2, 3, 4).stream().map(slotx -> this.minecraft.player.inventoryMenu.getSlot(slotx).getItem()).toList(),
                  this.minecraft.player.inventoryMenu.getSlot(0).getItem()
               )
         );
   }

   private void craftingTable() {
      BlockPos[] table = new BlockPos[1];
      this.minecraft.execute(() -> table[0] = this.minecraft.player.blockPosition().offset(2, 0, -1));
      sleep(100L);
      this.command("setblock " + at(table[0]) + " crafting_table");
      sleep(800L);
      this.open(table[0], "crafting table");
      this.checkScreen("crafting table", "inventory", "crafting.crafting_screen", 46, "container.crafting", true);
      this.jsonUiBegin();
      this.clickItem(Items.OAK_PLANKS, 0, ContainerInput.PICKUP, "pick up the planks");
      this.click(2, 1, ContainerInput.PICKUP, "one plank into the top middle");
      this.click(5, 1, ContainerInput.PICKUP, "one plank into the centre");
      sleep(600L);
      this.minecraft
         .execute(() -> BedrockNative.LOGGER.info("Dev test: 3x3 crafting result shows {}", this.minecraft.player.containerMenu.getSlot(0).getItem()));
      this.screenshot("inventory-crafting-table");
      this.click(0, 0, ContainerInput.QUICK_MOVE, "shift-click the sticks into the inventory");
      this.clickFirstEmpty(10, "put the other planks into the first empty inventory slot");
      sleep(600L);
      this.jsonUiReport("crafting table", "inventory");
      this.minecraft.execute(() -> {
         this.report("crafting table after crafting");
         this.minecraft.player.closeContainer();
      });
      sleep(600L);
      this.commands(hasItem("stick", null, -1, 4), hasItem("crafting_table", null, -1, 1), hasItem("oak_planks", null, -1, 2));
      sleep(600L);
   }

   private void chest() {
      BlockPos[] chest = new BlockPos[1];
      this.minecraft.execute(() -> chest[0] = this.minecraft.player.blockPosition().offset(2, 0, 0));
      sleep(100L);
      BlockPos pos = chest[0];
      this.commands(
         "setblock " + at(pos) + " chest",
         "replaceitem block " + at(pos) + " slot.container 0 diamond 3",
         "replaceitem block " + at(pos) + " slot.container 1 apple 5"
      );
      sleep(1000L);
      this.open(pos, "chest");
      this.checkScreen("chest", "chest", "chest.small_chest_screen", 63, "container.chest", true);
      this.screenshot("inventory-chest");
      sleep(600L);
      this.jsonUiBegin();
      this.click(0, 0, ContainerInput.QUICK_MOVE, "shift-click the diamonds into the inventory");
      this.click(1, 1, ContainerInput.PICKUP, "right-click the apples: take half");
      this.click(26, 0, ContainerInput.PICKUP, "put them into chest slot 26");
      this.clickItem(Items.STONE, 0, ContainerInput.PICKUP, "pick up the stone from the inventory part");
      this.click(5, 0, ContainerInput.PICKUP, "put it into chest slot 5");
      sleep(600L);
      this.jsonUiReport("chest", "chest");
      this.minecraft.execute(() -> {
         this.report("chest after moves");
         this.minecraft.player.closeContainer();
      });
      sleep(600L);
      this.commands(hasItem("diamond", null, -1, 3), hasItem("stone", null, -1, 0));
      sleep(600L);
      this.open(pos, "chest again (server's contents)");
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.command("setblock " + at(pos.east()) + " chest");
      sleep(600L);
      this.open(pos, "chest paired with a second one");
      this.checkScreen("double chest", "chest", "chest.large_chest_screen", 90, "container.chestDouble", true);
      this.screenshot("inventory-double-chest");
      sleep(600L);
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
   }

   private void furnace() {
      BlockPos[] furnace = new BlockPos[1];
      this.minecraft.execute(() -> furnace[0] = this.minecraft.player.blockPosition().offset(2, 0, 1));
      sleep(100L);
      BlockPos pos = furnace[0];
      this.commands(
         "setblock " + at(pos) + " furnace",
         "replaceitem block " + at(pos) + " slot.container 0 raw_iron 2",
         "replaceitem block " + at(pos) + " slot.container 1 coal 1"
      );
      sleep(1000L);
      this.open(pos, "furnace");
      this.checkScreen("furnace", "furnace", "furnace.furnace_screen", 39, "container.furnace", true);
      sleep(11000L);
      this.minecraft.execute(() -> this.report("furnace after 11 s"));
      this.checkProgress("furnace");
      this.screenshot("inventory-furnace");
      sleep(600L);
      this.jsonUiBegin();
      this.click(2, 0, ContainerInput.QUICK_MOVE, "shift-click the furnace result into the inventory");
      sleep(600L);
      this.jsonUiReport("furnace", "furnace");
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.command(hasItem("iron_ingot", null, -1, 1));
      sleep(600L);
   }

   private void furnaceFamily() {
      BlockPos[] at = new BlockPos[2];
      this.minecraft.execute(() -> {
         at[0] = this.minecraft.player.blockPosition().offset(2, 0, 2);
         at[1] = at[0].above();
      });
      sleep(100L);
      this.commands(
         "setblock " + at(at[0]) + " blast_furnace",
         "replaceitem block " + at(at[0]) + " slot.container 0 raw_iron 8",
         "replaceitem block " + at(at[0]) + " slot.container 1 coal 1",
         "setblock " + at(at[1]) + " smoker",
         "replaceitem block " + at(at[1]) + " slot.container 0 beef 8",
         "replaceitem block " + at(at[1]) + " slot.container 1 coal 1"
      );
      sleep(1000L);
      String[][] stations = new String[][]{
         {"blast furnace", "blast_furnace.blast_furnace_screen", "tile.blast_furnace.name", "inventory-blast-furnace"},
         {"smoker", "smoker.smoker_screen", "tile.smoker.name", "inventory-smoker"}
      };

      for (int i = 0; i < stations.length; i++) {
         String[] station = stations[i];
         this.open(at[i], station[0]);
         this.checkScreen(station[0], "furnace", station[1], 39, station[2], true);
         sleep(2500L);
         this.checkProgress(station[0]);
         this.screenshot(station[3]);
         sleep(600L);
         this.minecraft.execute(() -> this.minecraft.player.closeContainer());
         sleep(600L);
      }
   }

   private void brewingStand() {
      BlockPos[] stand = new BlockPos[1];
      this.minecraft.execute(() -> stand[0] = this.minecraft.player.blockPosition().offset(2, 1, 1));
      sleep(100L);
      BlockPos pos = stand[0];
      this.commands(
         "setblock " + at(pos) + " brewing_stand",
         "replaceitem block " + at(pos) + " slot.container 1 potion 1 0",
         "replaceitem block " + at(pos) + " slot.container 3 potion 1 0",
         "replaceitem block " + at(pos) + " slot.container 0 nether_wart 1",
         "replaceitem block " + at(pos) + " slot.container 4 blaze_powder 2"
      );
      sleep(1000L);
      this.open(pos, "brewing stand");
      this.checkScreen("brewing stand", "brewing_stand", "brewing_stand.brewing_stand_screen", 41, "container.brewing", true);
      sleep(3000L);
      int[] before = new int[1];
      this.minecraft.execute(() -> before[0] = this.minecraft.player.containerMenu instanceof BrewingStandMenu brewing ? brewing.getBrewingTicks() : -1);
      sleep(1000L);
      this.minecraft
         .execute(
            () -> {
               this.report("brewing stand after 4 s");
               int after = this.minecraft.player.containerMenu instanceof BrewingStandMenu brewing ? brewing.getBrewingTicks() : -1;
               BedrockNative.LOGGER
                  .info(
                     "Dev test: brewing stand brew time {} then {} a second later (counts down: {})",
                     new Object[]{before[0], after, after > 0 && after < before[0]}
                  );
            }
         );
      this.checkProgress("brewing stand");
      this.screenshot("inventory-brewing-stand");
      sleep(600L);
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
   }

   private void leftovers() {
      this.commands("clear @s", "gamemode survival");
      sleep(1000L);
      this.hotbarDrops();
      this.dragVariants();
      this.eatingAndBow();

      for (DevInventoryTest.Round round : List.of(
         new DevInventoryTest.Round("barrel", "barrel", 27, "chest", "chest.barrel_screen", "container.barrel", true),
         new DevInventoryTest.Round("shulker box", "undyed_shulker_box", 27, "chest", "chest.shulker_box_screen", "container.shulkerbox", true),
         new DevInventoryTest.Round("hopper", "hopper", 5, "redstone", "redstone.hopper_screen", "container.hopper", true),
         new DevInventoryTest.Round("dispenser", "dispenser", 9, "redstone", "redstone.dispenser_screen", "container.dispenser", true),
         new DevInventoryTest.Round("dropper", "dropper", 9, "redstone", "redstone.dropper_screen", "container.dropper", true),
         new DevInventoryTest.Round("ender chest", "ender_chest", 27, "chest", "chest.ender_chest_screen", "container.enderchest", false)
      )) {
         this.containerRound(round);
      }

      this.smeltingStation("blast furnace", "blast_furnace", Items.RAW_IRON, "raw_iron", Items.IRON_INGOT, "iron_ingot");
      this.smeltingStation("smoker", "smoker", Items.BEEF, "beef", Items.COOKED_BEEF, "cooked_beef");
      this.brewingMoves();
      this.armorOnBlocks();
      this.placeAgainstTakenBlock();
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev inventory test leftovers: {} checks, {} failed{}",
                  new Object[]{this.leftoverChecks, this.leftoverFailures.size(), this.leftoverFailures.isEmpty() ? "" : ": " + this.leftoverFailures}
               )
         );
      this.commands("clear @s");
   }

   private void check(String name, boolean ok, String detail) {
      this.leftoverChecks++;
      if (!ok) {
         this.leftoverFailures.add(name);
      }

      BedrockNative.LOGGER.info("Dev inventory test leftovers {}: {} ({})", new Object[]{ok ? "PASS" : "FAIL", name, detail});
   }

   private int droppedDirt() {
      return this.onClient(
         () -> this.minecraft
            .level
            .getEntities(
               this.minecraft.player,
               this.minecraft.player.getBoundingBox().inflate(6.0),
               entity -> entity instanceof ItemEntity item && item.getItem().is(Items.DIRT)
            )
            .stream()
            .mapToInt(entity -> ((ItemEntity)entity).getItem().getCount())
            .sum()
      );
   }

   private void pressDropKey() {
      this.minecraft.execute(() -> KeyMapping.click(InputConstants.getKey(this.minecraft.options.keyDrop.saveString())));
      sleep(1200L);
   }

   private void hotbarDrops() {
      this.commands("clear @s", "replaceitem entity @s slot.hotbar 0 dirt 10", "replaceitem entity @s slot.hotbar 4 dirt 1");
      this.minecraft.execute(() -> {
         this.minecraft.gui.setScreen(null);
         this.minecraft.player.getInventory().setSelectedSlot(0);
         this.minecraft.player.setXRot(-20.0F);
      });
      sleep(1000L);
      int before = this.droppedDirt();
      BedrockInteractions.lastDrop = "";
      this.pressDropKey();
      String sent = BedrockInteractions.lastDrop;
      int container = sent.indexOf("type=CONTAINER");
      int world = sent.indexOf("type=WORLD_INTERACTION");
      this.check("drop key sends the held slot first, the world second", container >= 0 && world > container, sent.isEmpty() ? "nothing sent" : sent);
      this.check("drop key: one dirt leaves hotbar 0", this.hotbarCount(0) == 9, "dirt " + this.hotbarCount(0) + " of 10 minus 1");
      this.check("drop key: an item entity appears", this.droppedDirt() - before == 1, this.droppedDirt() + " dirt on the ground, " + before + " before");
      this.commands(hasItem("dirt", "slot.hotbar", 0, 9), "kill @e[type=item,r=12]");
      this.minecraft.execute(() -> this.minecraft.gameMode.dropItem(this.minecraft.player, true));
      sleep(1200L);
      this.check("drop stack: hotbar 0 empty", this.hotbarCount(0) == 0, "dirt " + this.hotbarCount(0));
      this.check("drop stack: 9 dirt on the ground", this.droppedDirt() == 9, this.droppedDirt() + " dirt on the ground");
      this.commands(hasItem("dirt", "slot.hotbar", 0, 0), "kill @e[type=item,r=12]");
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(4));
      sleep(400L);
      this.pressDropKey();
      this.check(
         "drop key on hotbar 4: its last dirt leaves",
         this.hotbarCount(4) == 0 && this.droppedDirt() == 1,
         "dirt " + this.hotbarCount(4) + ", " + this.droppedDirt() + " on the ground"
      );
      this.commands(hasItem("dirt", null, -1, 0), "kill @e[type=item,r=12]");
      BedrockInteractions.lastDrop = "";
      this.pressDropKey();
      this.check("drop key with an empty hand sends nothing", BedrockInteractions.lastDrop.isEmpty(), BedrockInteractions.lastDrop);
      this.command("give @s dirt 3 0 {\"item_lock\":{\"mode\":\"lock_in_inventory\"}}");
      sleep(800L);
      int lockedSlot = this.onClient(() -> {
         for (int slot = 0; slot < 9; slot++) {
            if (this.minecraft.player.getInventory().getItem(slot).is(Items.DIRT)) {
               return slot;
            }
         }

         return -1;
      });
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(Math.max(0, lockedSlot)));
      sleep(400L);
      BedrockInteractions.lastDrop = "";
      this.pressDropKey();
      this.check(
         "drop key on an item-locked stack keeps it",
         lockedSlot >= 0 && lockedSlot < 9 && this.hotbarCount(lockedSlot) == 3 && BedrockInteractions.lastDrop.isEmpty() && this.droppedDirt() == 0,
         "slot "
            + lockedSlot
            + ", dirt "
            + (lockedSlot >= 0 ? this.hotbarCount(lockedSlot) : -1)
            + ", sent "
            + (BedrockInteractions.lastDrop.isEmpty() ? "nothing" : BedrockInteractions.lastDrop)
      );
      this.commands(hasItem("dirt", null, -1, 3));
      this.commands("clear @s", "gamemode creative", "replaceitem entity @s slot.hotbar 0 dirt 5");
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
      sleep(800L);
      this.pressDropKey();
      int left = this.hotbarCount(0);
      this.check("creative drop key: an item entity appears", this.droppedDirt() == 1, this.droppedDirt() + " dirt on the ground, " + left + " left in hand");
      this.commands(hasItem("dirt", "slot.hotbar", 0, left));
      this.commands("gamemode survival", "clear @s");
      this.minecraft.execute(() -> this.minecraft.player.setXRot(0.0F));
      sleep(600L);
   }

   private void dragVariants() {
      this.commands("give @s oak_planks 9", "give @s cobblestone 8");
      sleep(1000L);
      this.minecraft.execute(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(600L);
      this.jsonUiBegin();
      this.clickItem(Items.OAK_PLANKS, 0, ContainerInput.PICKUP, "pick up 9 planks");
      this.drag(new int[]{21, 22, 23}, 1, "right drag of the planks over main slots 21 to 23: one each");
      this.drag(new int[]{24, 25, 26, 27}, 0, "left drag of the 6 left planks over slots 24 to 27: one each, 2 stay on the cursor");
      this.click(28, 0, ContainerInput.PICKUP, "put the last 2 planks into slot 28");
      this.clickItem(Items.COBBLESTONE, 0, ContainerInput.PICKUP, "pick up 8 cobblestone");
      this.drag(new int[]{29, 30}, 0, "left drag of the 8 cobblestone over slots 29 and 30: 4 each");
      sleep(600L);
      List<String> slots = new ArrayList<>();
      this.minecraft
         .execute(
            () -> {
               AbstractContainerMenu menu = this.minecraft.player.containerMenu;

               for (int slot = 21; slot <= 30; slot++) {
                  ItemStack stack = menu.getSlot(slot).getItem();
                  slots.add(stack.isEmpty() ? "" : stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
               }

               this.check(
                  "drags: right drag one each, left drag spread evenly",
                  slots.equals(
                        List.of(
                           "1xoak_planks",
                           "1xoak_planks",
                           "1xoak_planks",
                           "1xoak_planks",
                           "1xoak_planks",
                           "1xoak_planks",
                           "1xoak_planks",
                           "2xoak_planks",
                           "4xcobblestone",
                           "4xcobblestone"
                        )
                     )
                     && menu.getCarried().isEmpty(),
                  slots + ", cursor " + menu.getCarried()
               );
            }
         );
      sleep(600L);
      this.jsonUiReport("drags", "inventory");
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.verifyInventory();
      this.commands("clear @s");
      sleep(600L);
   }

   private void armorOnBlocks() {
      for (DevInventoryTest.ArmorClick click : List.of(
         new DevInventoryTest.ArmorClick("anvil", "diamond_helmet", Items.DIAMOND_HELMET, "slot.armor.head", EquipmentSlot.HEAD, false, false),
         new DevInventoryTest.ArmorClick(
            "crafting_table", "diamond_chestplate", Items.DIAMOND_CHESTPLATE, "slot.armor.chest", EquipmentSlot.CHEST, false, false
         ),
         new DevInventoryTest.ArmorClick("chest", "diamond_chestplate", Items.DIAMOND_CHESTPLATE, "slot.armor.chest", EquipmentSlot.CHEST, false, false),
         new DevInventoryTest.ArmorClick("enchanting_table", "diamond_helmet", Items.DIAMOND_HELMET, "slot.armor.head", EquipmentSlot.HEAD, false, false),
         new DevInventoryTest.ArmorClick("furnace", "diamond_boots", Items.DIAMOND_BOOTS, "slot.armor.feet", EquipmentSlot.FEET, false, false),
         new DevInventoryTest.ArmorClick("stone", "diamond_helmet", Items.DIAMOND_HELMET, "slot.armor.head", EquipmentSlot.HEAD, false, true),
         new DevInventoryTest.ArmorClick("anvil", "diamond_helmet", Items.DIAMOND_HELMET, "slot.armor.head", EquipmentSlot.HEAD, true, true),
         new DevInventoryTest.ArmorClick("air", "diamond_helmet", Items.DIAMOND_HELMET, "slot.armor.head", EquipmentSlot.HEAD, false, true)
      )) {
         this.armorClick(click);
      }

      this.minecraft.execute(() -> this.minecraft.player.setXRot(0.0F));
   }

   private void armorClick(DevInventoryTest.ArmorClick click) {
      boolean air = "air".equals(click.block());
      BlockPos pos = this.besideThePlayer();
      this.commands("setblock " + at(pos) + " air", "clear @s", "give @s " + click.item());
      if (!air) {
         this.command("setblock " + at(pos) + " " + click.block());
      }

      sleep(1000L);
      this.minecraft.execute(() -> {
         this.minecraft.player.getInventory().setSelectedSlot(0);
         if (air) {
            this.minecraft.player.setXRot(-90.0F);
         } else {
            this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos).add(0.0, 0.25, 0.0));
         }

         if (click.sneak()) {
            this.minecraft.options.keyShift.setDown(true);
         }
      });
      sleep(click.sneak() ? 800L : 400L);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
      sleep(150L);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      sleep(1500L);
      String name = "armor: "
         + click.item()
         + (air ? " right-click in the air" : " right-click on " + click.block())
         + (click.sneak() ? " sneaking" : "")
         + (click.worn() ? " puts it on" : " keeps it in the hand");
      this.minecraft
         .execute(
            () -> {
               LocalPlayer player = this.minecraft.player;
               boolean screen = player.containerMenu != player.inventoryMenu;
               boolean worn = player.getItemBySlot(click.slot()).is(click.javaItem());
               boolean inHand = player.getInventory().getItem(0).is(click.javaItem());
               this.check(
                  name,
                  worn == click.worn() && inHand == !click.worn() && screen == !click.worn(),
                  "worn " + worn + ", in hand " + inHand + ", screen " + (screen ? player.containerMenu.getClass().getSimpleName() : "none")
               );
               player.closeContainer();
               this.minecraft.options.keyShift.setDown(false);
            }
         );
      sleep(600L);
      if (click.worn()) {
         this.commands(hasItem(click.item(), click.location(), 0, 1));
      } else {
         this.commands(hasItem(click.item(), "slot.hotbar", 0, 1), hasItem(click.item(), click.location(), 0, 0));
      }

      sleep(600L);
      this.commands("setblock " + at(pos) + " air", "clear @s");
      sleep(600L);
   }

   private void placeAgainstTakenBlock() {
      for (boolean sneak : new boolean[]{false, true}) {
         BlockPos pos = this.besideThePlayer();
         BlockPos above = pos.above();
         this.commands(
            "setblock " + at(above) + " air", "setblock " + at(pos) + " air", "clear @s", "give @s dirt 4", "setblock " + at(pos) + " crafting_table"
         );
         sleep(1000L);
         this.minecraft.execute(() -> {
            this.minecraft.player.getInventory().setSelectedSlot(0);
            if (sneak) {
               this.minecraft.options.keyShift.setDown(true);
            }
         });
         sleep(sneak ? 800L : 200L);
         this.minecraft.execute(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0)));
         sleep(300L);
         this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
         sleep(150L);
         this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
         sleep(1500L);
         String name = "place: dirt right-click on a crafting_table" + (sneak ? " sneaking places it" : " opens the table and places nothing");
         int left = sneak ? 3 : 4;
         this.minecraft
            .execute(
               () -> {
                  LocalPlayer player = this.minecraft.player;
                  boolean screen = player.containerMenu != player.inventoryMenu;
                  boolean placed = this.minecraft.level.getBlockState(above).is(Blocks.DIRT);
                  int inHand = player.getInventory().getItem(0).getCount();
                  this.check(
                     name,
                     placed == sneak && screen == !sneak && inHand == left,
                     "dirt on the table "
                        + placed
                        + ", screen "
                        + (screen ? player.containerMenu.getClass().getSimpleName() : "none")
                        + ", dirt in hand "
                        + inHand
                        + ", above the table "
                        + this.minecraft.level.getBlockState(above)
                  );
                  player.closeContainer();
                  this.minecraft.options.keyShift.setDown(false);
               }
            );
         sleep(600L);
         this.commands(hasItem("dirt", "slot.hotbar", 0, left));
         sleep(600L);
         this.commands("setblock " + at(above) + " air", "setblock " + at(pos) + " air", "clear @s");
         sleep(600L);
      }

      this.minecraft.execute(() -> this.minecraft.player.setXRot(0.0F));
   }

   private void useFor(long millis) {
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
      sleep(millis);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      sleep(1000L);
   }

   private int hotbarCount(int slot) {
      return this.onClient(() -> this.minecraft.player.getInventory().getItem(slot).getCount());
   }

   private void eatingAndBow() {
      this.commands("give @s golden_apple 3", "give @s bow", "give @s arrow 10");
      sleep(1000L);
      this.minecraft.execute(() -> {
         this.minecraft.player.getInventory().setSelectedSlot(0);
         this.minecraft.player.setXRot(-30.0F);
      });
      sleep(600L);
      this.useFor(2300L);
      this.check("eating completes", this.hotbarCount(0) == 2, "golden apples " + this.hotbarCount(0) + " of 3 minus 1");
      this.commands(hasItem("golden_apple", "slot.hotbar", 0, 2));
      this.useFor(600L);
      this.check("eating let go early keeps the apple", this.hotbarCount(0) == 2, "golden apples " + this.hotbarCount(0));
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
      sleep(600L);
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(2));
      sleep(400L);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      sleep(800L);
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
      sleep(600L);
      this.check("eating cancelled by changing slot keeps the apple", this.hotbarCount(0) == 2, "golden apples " + this.hotbarCount(0));
      this.commands(hasItem("golden_apple", "slot.hotbar", 0, 2));
      this.useFor(2300L);
      this.check("eating again completes", this.hotbarCount(0) == 1, "golden apples " + this.hotbarCount(0));
      this.commands(hasItem("golden_apple", "slot.hotbar", 0, 1));
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(1));
      sleep(600L);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
      sleep(1400L);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      List<Integer> arrowTimeline = new ArrayList<>();

      for (int i = 0; i < 8; i++) {
         sleep(500L);
         arrowTimeline.add(this.hotbarCount(2));
      }

      BedrockNative.LOGGER.info("Dev inventory test leftovers: arrows every 0.5 s after the full draw let go: {}", arrowTimeline);
      this.check("bow full draw takes an arrow", this.hotbarCount(2) == 9, "arrows " + this.hotbarCount(2) + " of 10 minus 1");
      this.commands(hasItem("arrow", "slot.hotbar", 2, 9));
      this.useFor(500L);
      sleep(1500L);
      this.check("bow half draw takes an arrow", this.hotbarCount(2) == 8, "arrows " + this.hotbarCount(2));
      this.commands(hasItem("arrow", "slot.hotbar", 2, 8));
      this.minecraft
         .execute(
            () -> {
               long arrows = this.minecraft
                  .level
                  .getEntities(this.minecraft.player, this.minecraft.player.getBoundingBox().inflate(80.0), entity -> entity.getType() == EntityTypes.ARROW)
                  .size();
               this.check("the arrows fly", arrows >= 1L, arrows + " arrow entities within 80 blocks");
            }
         );
      this.commands("kill @e[type=arrow]", "clear @s");
      sleep(600L);
   }

   private ItemStack menuSlot(int slot) {
      return this.onClient(() -> this.minecraft.player.containerMenu.getSlot(slot).getItem().copy());
   }

   private List<String> containerSlots(int size) {
      return this.onClient(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;
         List<String> list = new ArrayList<>();

         for (int slot = 0; slot < size && slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            list.add(stack.isEmpty() ? "" : stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
         }

         return list;
      });
   }

   private void clickPlayerItem(Item item, int button, ContainerInput input, String what) {
      this.minecraft.execute(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = menu.slots.size() - 36; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().is(item)) {
               this.clickNow(slot, button, input, what);
               return;
            }
         }

         BedrockNative.LOGGER.warn("Dev test: no {} in the player's part of the open menu for '{}'", item, what);
      });
      sleep(600L);
   }

   private BlockPos besideThePlayer() {
      BlockPos[] at = new BlockPos[1];
      this.minecraft.execute(() -> at[0] = this.minecraft.player.blockPosition().offset(1, 0, 0));
      sleep(100L);
      return at[0];
   }

   private void checkContainerScreen(DevInventoryTest.Round round) {
      this.minecraft
         .execute(
            () -> {
               AbstractContainerMenu menu = this.minecraft.player.containerMenu;
               Screen screen = this.minecraft.gui.screen();
               this.check(
                  round.name() + " menu", menu.slots.size() == round.size() + 36, menu.getClass().getSimpleName() + " with " + menu.slots.size() + " slots"
               );
               if (JsonUi.drawn(round.key())) {
                  if (screen instanceof JsonUiContainerScreen json) {
                     this.check(
                        round.name() + " JSON UI screen",
                        json.screen().equals(round.element()) && json.cellCount() == round.size() + 36 && json.labels().contains(text(round.titleKey())),
                        json.screen() + ", " + json.cellCount() + " cells, labels " + json.labels()
                     );
                  } else {
                     this.check(round.name() + " JSON UI screen", false, "shows " + (screen == null ? "nothing" : screen.getClass().getSimpleName()));
                  }
               }
            }
         );
      sleep(200L);
   }

   private void containerRound(DevInventoryTest.Round round) {
      BlockPos pos = this.besideThePlayer();
      int last = round.size() - 1;
      this.commands("setblock " + at(pos) + " air", "clear @s", "give @s gold_ingot 12", "give @s emerald 4", "setblock " + at(pos) + " " + round.block());
      if (round.fill()) {
         this.commands(
            "replaceitem block " + at(pos) + " slot.container 0 diamond 3",
            "replaceitem block " + at(pos) + " slot.container 1 apple 5",
            "replaceitem block " + at(pos) + " slot.container 2 cobblestone 20",
            "replaceitem block " + at(pos) + " slot.container 3 cobblestone 20"
         );
      }

      sleep(1000L);
      this.open(pos, round.name());
      this.checkContainerScreen(round);
      this.jsonUiBegin();
      List<String> expected = new ArrayList<>();

      for (int i = 0; i < round.size(); i++) {
         expected.add("");
      }

      if (round.fill()) {
         this.click(0, 0, ContainerInput.QUICK_MOVE, "shift-click the diamonds into the inventory");
         this.click(1, 1, ContainerInput.PICKUP, "right-click the apples: take half");
         this.click(last, 0, ContainerInput.PICKUP, "put them into the last slot");
         this.click(2, 0, ContainerInput.PICKUP, "pick up the first cobblestone stack");
         this.click(2, 0, ContainerInput.PICKUP_ALL, "double-click: collect both cobblestone stacks onto the cursor");
         this.click(2, 0, ContainerInput.PICKUP, "put the 40 cobblestone into slot 2");
         this.clickPlayerItem(Items.GOLD_INGOT, 0, ContainerInput.QUICK_MOVE, "shift-click the gold ingots into the container: first empty slot");
         this.click(1, 0, ContainerInput.SWAP, "number key 1 on slot 1: swap the 2 apples with the empty hotbar 0");
         expected.set(0, "12xgold_ingot");
         expected.set(2, "40xcobblestone");
         expected.set(last, "3xapple");
      } else {
         this.clickPlayerItem(Items.GOLD_INGOT, 0, ContainerInput.QUICK_MOVE, "shift-click the gold ingots into the ender chest");
         this.clickPlayerItem(Items.EMERALD, 0, ContainerInput.QUICK_MOVE, "shift-click the emeralds in");
         this.click(1, 0, ContainerInput.PICKUP, "pick the emeralds up");
         this.click(5, 0, ContainerInput.PICKUP, "put them into slot 5");
         expected.set(0, "12xgold_ingot");
         expected.set(5, "4xemerald");
      }

      sleep(2000L);
      List<String> shown = this.containerSlots(round.size());
      this.check(round.name() + " slots after the round", shown.equals(expected), shown + (shown.equals(expected) ? "" : ", expected " + expected));
      this.jsonUiReport(round.name(), round.key());
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      if (round.fill()) {
         this.commands(
            hasItem("diamond", null, -1, 3), hasItem("apple", "slot.hotbar", 0, 2), hasItem("gold_ingot", null, -1, 0), hasItem("cobblestone", null, -1, 0)
         );
      } else {
         this.commands(hasItem("gold_ingot", null, -1, 0), hasItem("emerald", null, -1, 0));
      }

      sleep(600L);
      this.open(pos, round.name() + " again (the server's contents)");
      List<String> server = this.containerSlots(round.size());
      this.check(round.name() + " server contents", server.equals(shown), server + (server.equals(shown) ? "" : ", client had " + shown));
      if (!round.fill()) {
         this.click(0, 0, ContainerInput.QUICK_MOVE, "shift-click the gold ingots back out");
         this.click(5, 0, ContainerInput.QUICK_MOVE, "shift-click the emeralds back out");
         sleep(600L);
         this.commands(hasItem("gold_ingot", null, -1, 12), hasItem("emerald", null, -1, 4));
      }

      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.commands("setblock " + at(pos) + " air", "clear @s");
      sleep(600L);
   }

   private void smeltingStation(String name, String block, Item ingredient, String ingredientId, Item result, String resultId) {
      BlockPos pos = this.besideThePlayer();
      this.commands("setblock " + at(pos) + " air", "clear @s", "setblock " + at(pos) + " " + block, "give @s " + ingredientId + " 8", "give @s coal 4");
      sleep(1000L);
      this.open(pos, name);
      this.check(
         name + " menu",
         this.onClient(() -> this.minecraft.player.containerMenu instanceof AbstractFurnaceMenu),
         this.onClient(() -> this.minecraft.player.containerMenu.getClass().getSimpleName())
      );
      this.clickPlayerItem(ingredient, 0, ContainerInput.QUICK_MOVE, "shift-click the " + ingredientId + ": input slot");
      this.clickPlayerItem(Items.COAL, 0, ContainerInput.QUICK_MOVE, "shift-click the coal: fuel slot");
      ItemStack input = this.menuSlot(0);
      ItemStack fuel = this.menuSlot(1);
      this.check(
         name + " shift-click fills input and fuel",
         input.is(ingredient) && input.getCount() == 8 && fuel.is(Items.COAL) && fuel.getCount() >= 3,
         "input " + input + ", fuel " + fuel
      );
      int cooked = 0;

      for (int i = 0; i < 24 && cooked == 0; i++) {
         sleep(500L);
         ItemStack out = this.menuSlot(2);
         cooked = out.is(result) ? out.getCount() : 0;
      }

      this.check(name + " cooks", cooked > 0, cooked + " " + resultId + " in the result slot");
      if (cooked > 0) {
         this.click(2, 0, ContainerInput.QUICK_MOVE, "shift-click the result into the inventory");
         sleep(600L);
      }

      int taken = cooked;
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.commands(hasItem(resultId, null, -1, taken));
      sleep(600L);
      this.commands("setblock " + at(pos) + " air", "clear @s");
      sleep(600L);
   }

   private void brewingMoves() {
      BlockPos pos = this.besideThePlayer();
      this.commands(
         "setblock " + at(pos) + " air",
         "clear @s",
         "setblock " + at(pos) + " brewing_stand",
         "give @s potion 3 0",
         "give @s nether_wart 4",
         "give @s blaze_powder 4",
         "give @s dirt 4"
      );
      sleep(1000L);
      this.open(pos, "brewing stand (moves)");
      this.check(
         "brewing stand menu",
         this.onClient(() -> this.minecraft.player.containerMenu instanceof BrewingStandMenu),
         this.onClient(() -> this.minecraft.player.containerMenu.getClass().getSimpleName())
      );
      this.jsonUiBegin();

      for (int i = 0; i < 3; i++) {
         this.clickPlayerItem(Items.POTION, 0, ContainerInput.QUICK_MOVE, "shift-click a water bottle: a bottle slot");
      }

      this.clickPlayerItem(Items.NETHER_WART, 0, ContainerInput.QUICK_MOVE, "shift-click the nether wart: the ingredient slot");
      this.clickPlayerItem(Items.BLAZE_POWDER, 0, ContainerInput.QUICK_MOVE, "shift-click the blaze powder: the fuel slot");
      this.clickPlayerItem(Items.DIRT, 0, ContainerInput.QUICK_MOVE, "shift-click dirt: goes nowhere");
      sleep(2000L);
      List<String> shown = this.containerSlots(5);
      boolean bottles = shown.subList(0, 3).stream().allMatch(s -> s.equals("1xpotion"));
      this.check("brewing stand shift-clicks", bottles && shown.get(3).equals("4xnether_wart") && shown.get(4).endsWith("xblaze_powder"), shown.toString());
      this.jsonUiReport("brewing stand moves", "brewing_stand");
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.commands(hasItem("potion", null, -1, 0), hasItem("nether_wart", null, -1, 0), hasItem("blaze_powder", null, -1, 0), hasItem("dirt", null, -1, 4));
      sleep(600L);
      this.open(pos, "brewing stand again (the server's contents)");
      List<String> server = this.containerSlots(5);
      this.check("brewing stand server contents", server.equals(shown), server + (server.equals(shown) ? "" : ", client had " + shown));
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(600L);
      this.commands("setblock " + at(pos) + " air", "clear @s");
      sleep(600L);
   }

   private void creative() {
      this.command("gamemode creative");
      sleep(1500L);
      this.creativeSlot(40, new ItemStack(Items.EMERALD, 64), "64 emeralds into hotbar 4");
      this.creativeSlot(40, ItemStack.EMPTY, "pick the emeralds up again");
      this.creativeSlot(21, new ItemStack(Items.EMERALD, 64), "put them into main slot 21");
      this.creativeSlot(22, new ItemStack(Items.GOLDEN_APPLE, 3), "3 golden apples into slot 22");
      this.creativeSlot(22, new ItemStack(Items.GOLDEN_APPLE, 1), "reduce them to one");
      this.creativeSlot(23, PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.HEALING).copyWithCount(5), "5 arrows of healing into slot 23");
      this.creativeSlot(23, PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON), "replace them with 1 arrow of poison");
      this.creativeSlot(24, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), "a potion of swiftness into slot 24");
      this.creativeSlot(24, PotionContents.createItemStack(Items.POTION, Potions.HEALING), "replace it with a potion of healing");
      this.creativeSlot(45, new ItemStack(Items.DIRT, 4), "4 dirt into the offhand: Bedrock's offhand refuses dirt");
      this.minecraft.execute(() -> BedrockNative.LOGGER.info("Dev test creative: offhand after the dirt shows {}", this.minecraft.player.getOffhandItem()));
      this.creativeSlot(45, new ItemStack(Items.TOTEM_OF_UNDYING), "a totem into the offhand");
      this.minecraft.execute(() -> this.minecraft.gameMode.handleCreativeModeItemDrop(new ItemStack(Items.GOLD_INGOT, 5)));
      sleep(1000L);
      this.minecraft
         .execute(
            () -> {
               this.report("creative");
               long gold = this.minecraft
                  .level
                  .getEntities(
                     this.minecraft.player,
                     this.minecraft.player.getBoundingBox().inflate(6.0),
                     entity -> entity instanceof ItemEntity item && item.getItem().is(Items.GOLD_INGOT)
                  )
                  .size();
               BedrockNative.LOGGER.info("Dev test: {} gold ingot item entities near the player after the creative drop", gold);
            }
         );
      this.commands(
         hasItem("emerald", "slot.inventory", 12, 64),
         hasItem("emerald", "slot.hotbar", 4, 0),
         hasItem("golden_apple", "slot.inventory", 13, 1),
         hasItem("arrow,data=26", "slot.inventory", 14, 1),
         hasItem("arrow,data=22", null, -1, 0),
         hasItem("potion,data=21", "slot.inventory", 15, 1),
         hasItem("potion,data=14", null, -1, 0),
         hasItem("dirt", "slot.weapon.offhand", 0, 0),
         hasItem("totem_of_undying", "slot.weapon.offhand", 0, 1)
      );
      this.command("replaceitem entity @s slot.weapon.offhand 0 air");
      sleep(1000L);
      this.command("gamemode survival");
      sleep(1000L);
   }

   private void entities() {
      this.command("summon pig ~-2 ~ ~");
      this.command("summon arrow ~1 ~3 ~1");
      sleep(1500L);
      Entity[] pig = new Entity[1];
      this.minecraft
         .execute(
            () -> {
               for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                  if (entity instanceof LivingEntity
                     && entity.getType() == EntityTypes.PIG
                     && (pig[0] == null || entity.distanceToSqr(this.minecraft.player) < pig[0].distanceToSqr(this.minecraft.player))) {
                     pig[0] = entity;
                  }
               }

               if (pig[0] != null) {
                  this.minecraft.player.lookAt(Anchor.EYES, pig[0].getBoundingBox().getCenter());
               }
            }
         );
      sleep(200L);
      this.minecraft.execute(() -> {
         if (pig[0] != null) {
            BedrockNative.LOGGER.info("Dev test: pig health before the hit {}", ((LivingEntity)pig[0]).getHealth());
            this.minecraft.gameMode.attack(this.minecraft.player, pig[0]);
            this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getAttackAnimation(), false);
         }
      });
      sleep(1500L);
      this.minecraft
         .execute(
            () -> {
               if (pig[0] != null) {
                  BedrockNative.LOGGER
                     .info("Dev test: pig health after the hit {} of {}", ((LivingEntity)pig[0]).getHealth(), ((LivingEntity)pig[0]).getMaxHealth());
               }

               for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                  if (!(entity instanceof LivingEntity) && entity.distanceTo(this.minecraft.player) < 16.0F) {
                     BedrockNative.LOGGER
                        .info(
                           "Dev test: {} custom name {}, visible {}, shows name {}",
                           new Object[]{entity.typeHolder().getRegisteredName(), entity.getCustomName(), entity.isCustomNameVisible(), entity.shouldShowName()}
                        );
                  }
               }

               Screenshot.grab(this.minecraft, false);
               BedrockNative.LOGGER
                  .info(
                     "Dev test: stack requests accepted {}, rejected {}, pending {}",
                     new Object[]{this.requests.accepted(), this.requests.rejected(), this.requests.pendingCount()}
                  );
            }
         );
   }

   private void open(BlockPos pos, String what) {
      this.minecraft.execute(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos)));
      sleep(300L);
      this.minecraft
         .execute(
            () -> this.minecraft
               .gameMode
               .useItemOn(
                  this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(-0.5, 0.0, 0.0), Direction.WEST, pos, false)
               )
         );
      sleep(1500L);
      this.minecraft.execute(() -> this.report("opened " + what));
   }

   private void creativeSlot(int slot, ItemStack stack, String what) {
      this.minecraft.execute(() -> {
         this.minecraft.player.inventoryMenu.getSlot(slot).set(stack.copy());
         this.minecraft.gameMode.handleCreativeModeItemAdd(stack.copy(), slot);
      });
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info("Dev test creative: {}; requests accepted {}, rejected {}", new Object[]{what, this.requests.accepted(), this.requests.rejected()})
         );
   }

   private void clickItem(Item item, int button, ContainerInput input, String what) {
      this.minecraft.execute(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = 0; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().is(item)) {
               this.clickNow(slot, button, input, what);
               return;
            }
         }

         BedrockNative.LOGGER.warn("Dev test: no {} in the open menu for '{}'", item, what);
      });
      sleep(600L);
   }

   private void clickOther(Item item, String what) {
      this.minecraft.execute(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = 0; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.is(item) && !ItemStack.isSameItemSameComponents(stack, menu.getCarried())) {
               this.clickNow(slot, 0, ContainerInput.PICKUP, what);
               return;
            }
         }

         BedrockNative.LOGGER.warn("Dev test: no other {} in the open menu for '{}'", item, what);
      });
      sleep(600L);
   }

   private void clickFirstEmpty(int from, String what) {
      this.minecraft.execute(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = from; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
               this.clickNow(slot, 0, ContainerInput.PICKUP, what);
               return;
            }
         }
      });
      sleep(600L);
   }

   private void click(int slot, int button, ContainerInput input, String what) {
      this.minecraft.execute(() -> this.clickNow(slot, button, input, what));
      sleep(600L);
   }

   private void clickNow(int slot, int button, ContainerInput input, String what) {
      LocalPlayer player = this.minecraft.player;
      AbstractContainerMenu menu = player.containerMenu;
      boolean viaJsonUi = false;
      if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen screen && screen.getMenu() == menu) {
         viaJsonUi = this.jsonUiClick(screen, slot, button, input, what);
      }

      if (!viaJsonUi) {
         this.minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
      }

      BedrockNative.LOGGER
         .info(
            "Dev test click: {} (slot {} {} {}{}): slot now {}, cursor {}; requests accepted {}, rejected {}",
            new Object[]{
               what,
               slot,
               input,
               button,
               viaJsonUi ? " through the JSON UI" : "",
               slot >= 0 ? menu.getSlot(slot).getItem() : "-",
               menu.getCarried(),
               this.requests.accepted(),
               this.requests.rejected()
            }
         );
   }

   private boolean jsonUiClick(JsonUiContainerScreen screen, int slot, int button, ContainerInput input, String what) {
      this.jsonUiClicks++;
      if (slot != -999 || input != ContainerInput.PICKUP || button != 0 && button != 1) {
         float[] at = screen.cellCentre(slot);
         Slot under = at == null ? null : screen.slotAt(at[0], at[1]);
         if (under != null && under.index == slot) {
            switch (input) {
               case PICKUP:
                  if (button != 0 && button != 1) {
                     return this.around(what);
                  }

                  mouse(screen, at, button == 0 ? 1 : 3, 0, false);
                  break;
               case QUICK_MOVE:
                  mouse(screen, at, 1, 1, false);
                  break;
               case PICKUP_ALL:
                  mouse(screen, at, 1, 0, true);
                  break;
               case SWAP:
                  KeyMapping key = button == 40
                     ? this.minecraft.options.keySwapOffhand
                     : (button >= 0 && button < 9 ? this.minecraft.options.keyHotbarSlots[button] : null);
                  if (key == null) {
                     return this.around(what);
                  }

                  screen.mouseMoved(at[0], at[1]);
                  screen.keyPressed(new KeyEvent(InputConstants.getKey(key.saveString()).getValue(), 0, 0));
                  break;
               case THROW:
                  screen.mouseMoved(at[0], at[1]);
                  screen.keyPressed(new KeyEvent(InputConstants.getKey(this.minecraft.options.keyDrop.saveString()).getValue(), 0, button == 1 ? 64 : 0));
                  break;
               default:
                  return this.around(what);
            }

            return true;
         } else {
            return this.missed(what, "no cell of slot " + slot + " under the mouse (found " + (under == null ? "none" : under.index) + ")");
         }
      } else {
         float[] at = screen.outsidePoint();
         if (at == null) {
            return this.missed(what, "no place outside the panels");
         }

         mouse(screen, at, button == 0 ? 1 : 3, 0, false);
         return true;
      }
   }

   private static void mouse(JsonUiContainerScreen screen, float[] at, int mouseButton, int modifiers, boolean doubleClick) {
      MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(mouseButton, modifiers));
      screen.mouseMoved(at[0], at[1]);
      screen.mouseClicked(event, doubleClick);
      screen.mouseReleased(event);
   }

   private boolean missed(String what, String why) {
      this.jsonUiMisses++;
      BedrockNative.LOGGER.warn("Dev inventory test JSON UI: FAIL, {} for '{}'", why, what);
      return false;
   }

   private boolean around(String what) {
      this.jsonUiAround++;
      BedrockNative.LOGGER.warn("Dev inventory test JSON UI: '{}' can't be made on the JSON UI, sent to the menu directly", what);
      return false;
   }

   private void drag(int[] slots, int mouseButton, String what) {
      this.minecraft
         .execute(
            () -> {
               LocalPlayer player = this.minecraft.player;
               AbstractContainerMenu menu = player.containerMenu;
               boolean viaJsonUi = this.minecraft.gui.screen() instanceof JsonUiContainerScreen screen
                  && screen.getMenu() == menu
                  && this.jsonUiDrag(screen, slots, mouseButton, what);
               if (!viaJsonUi) {
                  this.minecraft
                     .gameMode
                     .handleContainerInput(menu.containerId, -999, AbstractContainerMenu.getQuickcraftMask(0, mouseButton), ContainerInput.QUICK_CRAFT, player);

                  for (int slot : slots) {
                     this.minecraft
                        .gameMode
                        .handleContainerInput(
                           menu.containerId, slot, AbstractContainerMenu.getQuickcraftMask(1, mouseButton), ContainerInput.QUICK_CRAFT, player
                        );
                  }

                  this.minecraft
                     .gameMode
                     .handleContainerInput(menu.containerId, -999, AbstractContainerMenu.getQuickcraftMask(2, mouseButton), ContainerInput.QUICK_CRAFT, player);
               }

               List<String> stacks = new ArrayList<>();

               for (int slot : slots) {
                  stacks.add(slot + "=" + menu.getSlot(slot).getItem());
               }

               BedrockNative.LOGGER
                  .info(
                     "Dev test drag: {} (slots {}{}): {}, cursor {}; requests accepted {}, rejected {}",
                     new Object[]{
                        what,
                        Arrays.toString(slots),
                        viaJsonUi ? " through the JSON UI" : "",
                        stacks,
                        menu.getCarried(),
                        this.requests.accepted(),
                        this.requests.rejected()
                     }
                  );
            }
         );
      sleep(600L);
   }

   private boolean jsonUiDrag(JsonUiContainerScreen screen, int[] slots, int mouseButton, String what) {
      this.jsonUiClicks++;
      List<float[]> points = new ArrayList<>();

      for (int slot : slots) {
         float[] at = screen.cellCentre(slot);
         Slot under = at == null ? null : screen.slotAt(at[0], at[1]);
         if (under == null || under.index != slot) {
            return this.missed(what, "no cell of slot " + slot + " under the mouse");
         }

         points.add(at);
      }

      MouseButtonInfo left = new MouseButtonInfo(mouseButton == 0 ? 1 : 3, 0);
      float[] first = points.getFirst();
      screen.mouseMoved(first[0], first[1]);
      screen.mouseClicked(new MouseButtonEvent(first[0], first[1], left), false);

      for (float[] at : points) {
         screen.mouseMoved(at[0], at[1]);
         screen.mouseDragged(new MouseButtonEvent(at[0], at[1], left), 0.0, 0.0);
      }

      float[] last = points.getLast();
      screen.mouseReleased(new MouseButtonEvent(last[0], last[1], left));
      return true;
   }

   private void jsonUiBegin() {
      this.minecraft.execute(() -> {
         this.jsonUiClicks = 0;
         this.jsonUiMisses = 0;
         this.jsonUiAround = 0;
      });
   }

   private void jsonUiReport(String what, String key) {
      this.minecraft
         .execute(
            () -> {
               if (JsonUi.drawn(key)) {
                  BedrockNative.LOGGER
                     .info(
                        "Dev inventory test JSON UI: {}: {} clicks, drags and keys through the JSON UI slots, {} missed, {} around it: {}",
                        new Object[]{
                           what,
                           this.jsonUiClicks,
                           this.jsonUiMisses,
                           this.jsonUiAround,
                           this.jsonUiClicks > 0 && this.jsonUiMisses == 0 && this.jsonUiAround == 0 ? "PASS" : "FAIL"
                        }
                     );
               }
            }
         );
   }

   private void awaitJsonUi() {
      for (int i = 0; JsonUi.mode() != JsonUi.Mode.OFF && i < 40 && !this.onClient(() -> JsonUi.definitions() != null); i++) {
         sleep(500L);
      }
   }

   private void checkScreen(String what, String key, String element, int cells, String titleKey, boolean inventoryLabel) {
      this.minecraft
         .execute(
            () -> {
               Screen screen = this.minecraft.gui.screen();
               String shown = screen == null ? "nothing" : screen.getClass().getSimpleName();
               if (!JsonUi.drawn(key)) {
                  BedrockNative.LOGGER
                     .info("Dev inventory test: JSON UI mode {} leaves {} to Java, {} shows {}", new Object[]{JsonUi.mode(), key, what, shown});
               } else if (!(screen instanceof JsonUiContainerScreen json)) {
                  BedrockNative.LOGGER.warn("Dev inventory test JSON UI: {} shows {} instead of {}: FAIL", new Object[]{what, shown, element});
               } else {
                  List labels = json.labels();
                  boolean pass = json.screen().equals(element)
                     && json.cellCount() == cells
                     && labels.contains(text(titleKey))
                     && labels.contains(text("container.inventory")) == inventoryLabel;
                  BedrockNative.LOGGER
                     .info(
                        "Dev inventory test JSON UI: {} screen {}, {} slot cells, labels {}, problems {}: {}",
                        new Object[]{what, json.screen(), json.cellCount(), labels, json.ui().instance().problems(), pass ? "PASS" : "FAIL"}
                     );
               }
            }
         );
      sleep(200L);
   }

   private void checkProgress(String what) {
      this.minecraft
         .execute(
            () -> {
               AbstractContainerMenu menu = this.minecraft.player.containerMenu;
               Screen screen = this.minecraft.gui.screen();
               switch (menu) {
                  case AbstractFurnaceMenu furnace:
                     float cooked = furnace.getBurnProgress();
                     float fuel = furnace.isLit() ? furnace.getLitProgress() : 0.0F;
                     BedrockNative.LOGGER.info("Dev test: {} lit {}, fuel left {}, cooked {}", new Object[]{what, furnace.isLit(), fuel, cooked});
                     if (JsonUi.drawn("furnace") && screen instanceof JsonUiContainerScreen json) {
                        float arrow = shown(json, "furnace_arrow_full_image", true);
                        float flame = shown(json, "flame_full_image", false);
                        boolean pass = furnace.isLit() && fuel > 0.0F && Math.abs(arrow - cooked) < 0.1F && Math.abs(flame - fuel) < 0.15F;
                        BedrockNative.LOGGER
                           .info(
                              "Dev inventory test JSON UI: {} progress: arrow {} (menu {}), flame {} (menu {}): {}",
                              new Object[]{what, arrow, cooked, flame, fuel, pass ? "PASS" : "FAIL"}
                           );
                     }
                     break;
                  case BrewingStandMenu brewing:
                     int total = brewing.getTotalBrewingTicks();
                     float brewed = brewing.getBrewingTicks() > 0 && total > 0 ? 1.0F - (float)brewing.getBrewingTicks() / total : 0.0F;
                     float fuelx = brewing.getTotalFuel() > 0 ? (float)brewing.getFuel() / brewing.getTotalFuel() : 0.0F;
                     BedrockNative.LOGGER
                        .info(
                           "Dev test: {} brew time {} of {}, fuel {} of {}",
                           new Object[]{what, brewing.getBrewingTicks(), total, brewing.getFuel(), brewing.getTotalFuel()}
                        );
                     if (JsonUi.drawn("brewing_stand") && screen instanceof JsonUiContainerScreen json) {
                        float arrow = shown(json, "brewing_arrow_full_image", false);
                        float bar = shown(json, "brewing_fuel_bar_full_image", true);
                        boolean pass = brewing.getBrewingTicks() > 0 && Math.abs(arrow - brewed) < 0.1F && Math.abs(bar - fuelx) < 0.1F;
                        BedrockNative.LOGGER
                           .info(
                              "Dev inventory test JSON UI: {} progress: arrow {} (menu {}), fuel bar {} (menu {}): {}",
                              new Object[]{what, arrow, brewed, bar, fuelx, pass ? "PASS" : "FAIL"}
                           );
                     }
                     break;
                  default:
                     BedrockNative.LOGGER.warn("Dev test: {} shows {}, not a furnace or brewing stand", what, menu.getClass().getSimpleName());
               }

               if (JsonUi.drawn(menu instanceof BrewingStandMenu ? "brewing_stand" : "furnace") && !(screen instanceof JsonUiContainerScreen)) {
                  BedrockNative.LOGGER.warn("Dev inventory test JSON UI: {} progress: no JSON UI screen: FAIL", what);
               }
            }
         );
      sleep(200L);
   }

   private static float shown(JsonUiContainerScreen screen, String name, boolean horizontal) {
      UiControl control = screen.ui().instance().find(name);
      float size = control == null ? 0.0F : (horizontal ? control.width() : control.height());
      if (size <= 0.0F) {
         return Float.NaN;
      }

      float from = Float.MAX_VALUE;
      float to = -Float.MAX_VALUE;

      for (UiDrawCommand command : screen.ui().instance().drawList().commands()) {
         if (command instanceof UiDrawCommand.Image image && image.control() == control) {
            for (UiDrawCommand.Quad quad : image.quads()) {
               if (quad.w() > 0.0F && quad.h() > 0.0F) {
                  from = Math.min(from, horizontal ? quad.x() : quad.y());
                  to = Math.max(to, horizontal ? quad.x() + quad.w() : quad.y() + quad.h());
               }
            }
         }
      }

      return to < from ? 0.0F : (to - from) / size;
   }

   private static String text(String key) {
      String text = BedrockText.lookupKey(key);
      return text == null ? key : text;
   }

   private void screenshot(String name) {
      this.minecraft
         .execute(
            () -> Screenshot.grab(
               this.minecraft.gameDirectory,
               name + (this.minecraft.gui.screen() instanceof JsonUiContainerScreen ? "-jsonui" : "") + ".png",
               this.minecraft.gameRenderer.mainRenderTarget(),
               1,
               message -> {}
            )
         );
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void report(String when) {
      AbstractContainerMenu menu = this.minecraft.player.containerMenu;
      List<String> items = new ArrayList<>();

      for (int slot = 0; slot < menu.slots.size(); slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty()) {
            items.add(slot + "=" + stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
         }
      }

      BedrockNative.LOGGER
         .info(
            "Dev test menu ({}): {} id {} with {} slots, cursor {}: {}; requests accepted {}, rejected {}, pending {}",
            new Object[]{
               when,
               menu.getClass().getSimpleName(),
               menu.containerId,
               menu.slots.size(),
               menu.getCarried(),
               items,
               this.requests.accepted(),
               this.requests.rejected(),
               this.requests.pendingCount()
            }
         );
   }

   private void verifyInventory() {
      List<String> checks = new ArrayList<>();
      this.minecraft.execute(() -> {
         Inventory inventory = this.minecraft.player.getInventory();

         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
               String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               checks.add(slot < 9 ? hasItem(item, "slot.hotbar", slot, stack.getCount()) : hasItem(item, "slot.inventory", slot - 9, stack.getCount()));
            }
         }

         ItemStack offhand = this.minecraft.player.getOffhandItem();
         if (!offhand.isEmpty()) {
            checks.add(hasItem(BuiltInRegistries.ITEM.getKey(offhand.getItem()).getPath(), "slot.weapon.offhand", 0, offhand.getCount()));
         }

         BedrockNative.LOGGER.info("Dev test: checking {} slots server-side", checks.size());
      });
      sleep(200L);
      this.commands(checks.toArray(new String[0]));
   }

   private static String hasItem(String item, String location, int slot, int quantity) {
      String where = location == null ? "" : ",location=" + location + ",slot=" + slot;
      return "testfor @s[hasitem={item=" + item + where + ",quantity=" + quantity + "}]";
   }

   private static String at(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
      }
   }

   private void command(String command) {
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test command: /{}", command);
         this.minecraft.getConnection().sendCommand(command);
      });
      sleep(400L);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
      }
   }

   private record ArmorClick(String block, String item, Item javaItem, String location, EquipmentSlot slot, boolean sneak, boolean worn) {
   }

   private record Round(String name, String block, int size, String key, String element, String titleKey, boolean fill) {
   }
}
