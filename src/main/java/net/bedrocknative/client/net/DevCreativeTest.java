package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.bedrocknative.client.inventory.Crafting;
import net.bedrocknative.client.inventory.JsonUiCreativeScreen;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.ui.CreativeData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiType;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Type;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.CraftingRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.InvalidDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.jspecify.annotations.Nullable;

final class DevCreativeTest {
   private static final long STEP = 700L;
   private static final int PICKER_HOTBAR = 45;
   private static final String RUBY = "bedrocknative:ruby";
   private static final String RUBY_BLOCK = "bedrocknative:ruby_block";
   private static final int SHIFT = 1;
   private final Minecraft minecraft;
   private final StackRequests requests;
   private final boolean bds;
   private final String server;
   private final boolean jsonUi = JsonUi.drawn("creative");
   private int jsonUiClicks;
   private int jsonUiMisses;

   DevCreativeTest(Minecraft minecraft, StackRequests requests, boolean bds) {
      this.minecraft = minecraft;
      this.requests = requests;
      this.bds = bds;
      this.server = bds ? "bds" : "prisma";
   }

   void run() {
      for (int i = 0; this.jsonUi && i < 40 && !this.onClient(() -> JsonUi.definitions() != null); i++) {
         sleep(500L);
      }

      String previousMode = this.onClient(() -> this.minecraft.gameMode.getPlayerMode().getName());
      if (this.bds) {
         this.commands("gamemode creative", "clear @s");
      } else {
         this.command("gamemode creative");
      }

      sleep(2000L);
      this.run(
         () -> this.minecraft
            .gui
            .setScreen(
               new CreativeModeInventoryScreen(
                  this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), (Boolean)this.minecraft.options.operatorItemsTab().get()
               )
            )
      );
      sleep(1500L);
      this.run(
         () -> {
            Screen screen = this.minecraft.gui.screen();
            if (this.jsonUi) {
               boolean pass = screen instanceof JsonUiCreativeScreen json
                  && json.layout() == CreativeData.Layout.CREATIVE
                  && json.grid() != null
                  && json.ui().instance().problems().count("unresolved-base") == 0;
               log(
                  "JSON UI: creative screen {}, problems {}: {}",
                  screen == null ? "nothing" : screen.getClass().getSimpleName(),
                  screen instanceof JsonUiCreativeScreen jsonx ? jsonx.ui().instance().problems() : "-",
                  pass ? "PASS" : "FAIL"
               );
            }
         }
      );
      this.categories();
      this.take();
      this.search();
      this.layouts();
      if (this.jsonUi) {
         log(
            "JSON UI: {} clicks, tabs and keys through the JSON UI, {} missed: {}",
            this.jsonUiClicks,
            this.jsonUiMisses,
            this.jsonUiClicks > 0 && this.jsonUiMisses == 0 ? "PASS" : "FAIL"
         );
      }

      this.run(() -> this.minecraft.player.closeContainer());
      sleep(700L);
      if (this.bds) {
         this.craftRubies();
      } else {
         this.craftOnPrisma();
         this.command("gamemode " + previousMode);
      }

      log("stack requests accepted {}, rejected {}", StackRequests.acceptedTotal(), StackRequests.rejectedTotal());
      log("done");
   }

   private void categories() {
      List<CreativeItemData> contents = this.requests.creative().contents();
      List<CreativeItemGroup> groups = this.requests.creative().groups();
      this.run(
         () -> {
            int lastGroup = -1;
            boolean grouped = true;

            for (CreativeItemData entry : contents) {
               grouped &= entry.getGroupId() >= lastGroup;
               lastGroup = Math.max(lastGroup, entry.getGroupId());
            }

            log(
               "CreativeContent: {} entries, {} groups, entries sorted by group: {}; Java tabs shown: {}",
               contents.size(),
               groups.size(),
               grouped,
               CreativeModeTabs.tabs().stream().map(tab -> tab.getDisplayName().getString()).toList()
            );
         }
      );

      for (CreativeItemCategory category : BedrockCreativeTabs.categories()) {
         this.showTab(category);
         this.run(
            () -> {
               List<CreativeItemData> inContentOrder = new ArrayList<>();

               for (CreativeItemData entry : contents) {
                  int group = entry.getGroupId();
                  CreativeItemCategory of = group >= 0 && group < groups.size() ? groups.get(group).getCategory() : CreativeItemCategory.ITEMS;
                  if (of == category && !ItemMappings.toJava(entry.getItem()).isEmpty()) {
                     inContentOrder.add(entry);
                  }
               }

               List<CreativeItemData> expected = new ArrayList<>(inContentOrder);
               expected.sort(
                  Comparator.comparingInt(entryx -> entryx.getGroupId() >= 0 && entryx.getGroupId() < groups.size() ? entryx.getGroupId() : Integer.MAX_VALUE)
               );
               if (!expected.equals(inContentOrder)) {
                  log(
                     "category {}: the server sends {} entries after the rest of their group (content order differs from group order)",
                     category,
                     IntStream.range(0, expected.size()).filter(i -> expected.get(i) != inContentOrder.get(i)).count()
                  );
               }

               List<ItemStack> shown = this.picker().items;
               List<CreativeItemData> listed = new ArrayList<>();
               int headers = 0;
               int loose = 0;

               for (ItemStack stack : shown) {
                  BedrockCreativeTabs.Group group = BedrockCreativeTabs.group(stack);
                  if (group != null) {
                     headers++;
                     if (!BedrockCreativeTabs.isOpen(group)) {
                        group.entries().forEach(entryx -> listed.add(BedrockCreativeTabs.entry(entryx)));
                     }
                  } else {
                     CreativeItemData entryx = BedrockCreativeTabs.entry(stack);
                     listed.add(entryx);
                     loose++;
                  }
               }

               int mismatch = -1;

               for (int i = 0; i < Math.max(expected.size(), listed.size()); i++) {
                  if (i >= expected.size() || i >= listed.size() || listed.get(i) == null || expected.get(i).getNetId() != listed.get(i).getNetId()) {
                     mismatch = i;
                     break;
                  }
               }

               long custom = listed.stream().filter(entryx -> !identifier(entryx).startsWith("minecraft:")).count();
               log(
                  "category {} ('{}'): {} slots ({} group headers, {} loose), {} entries listed, {} in CreativeContent, {} server-defined; order {}",
                  category,
                  this.selectedTab().getDisplayName().getString(),
                  shown.size(),
                  headers,
                  loose,
                  listed.size(),
                  expected.size(),
                  custom,
                  mismatch < 0
                     ? "matches"
                     : "differs at "
                        + mismatch
                        + ": "
                        + describe(mismatch < expected.size() ? expected.get(mismatch) : null)
                        + " expected, "
                        + describe(mismatch < listed.size() ? listed.get(mismatch) : null)
                        + " listed"
               );
               if (this.jsonUi && this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
                  UiControl grid = json.grid();
                  long marked = json.data()
                     .cells()
                     .stream()
                     .filter(cell -> cell.kind() == CreativeData.Kind.HEAD_CLOSED || cell.kind() == CreativeData.Kind.HEAD_OPEN)
                     .count();
                  String label = this.selectedTab().getDisplayName().getString();
                  boolean pass = grid != null
                     && grid.gridCount() == shown.size()
                     && json.data().cells().size() == shown.size()
                     && marked == headers
                     && json.data().tab() != CreativeData.Tab.SEARCH;
                  log(
                     "JSON UI: category {} grid {} cells ({} heads marked) of {} slots, tab '{}': {}",
                     category,
                     grid == null ? -1 : grid.gridCount(),
                     marked,
                     shown.size(),
                     label,
                     pass ? "PASS" : "FAIL"
                  );
               }
            }
         );
         this.screenshot(category.name().toLowerCase(Locale.ROOT));
      }
   }

   private void layouts() {
      if (this.jsonUi) {
         this.toggle("survival_layout_toggle_panel", "the survival layout");
         this.run(
            () -> {
               if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
                  boolean pass = json.layout() == CreativeData.Layout.SURVIVAL
                     && this.selectedTab().getType() == Type.INVENTORY
                     && json.cellCentre("inventory_items", 0) != null
                     && json.cellCentre("armor_items", 0) != null;
                  log("JSON UI: survival layout shows the inventory (Java tab {}): {}", this.selectedTab().getType(), pass ? "PASS" : "FAIL");
               }
            }
         );
         this.screenshot("survival");
         this.toggle("creative_layout_toggle_panel", "the creative layout");
         this.run(() -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               boolean pass = json.layout() == CreativeData.Layout.CREATIVE && this.selectedTab().getType() != Type.INVENTORY && json.grid() != null;
               log("JSON UI: back to the creative layout (Java tab '{}'): {}", this.selectedTab().getDisplayName().getString(), pass ? "PASS" : "FAIL");
            }
         });
         this.both();
      }
   }

   private void both() {
      this.toggle("recipe_book_layout_toggle_panel", "the both layout (middle button)");
      this.run(
         () -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               boolean cells = json.cellCentre("inventory_items", 26) != null
                  && json.cellCentre("armor_items", 3) != null
                  && json.cellCentre("offhand_items", 0) != null
                  && json.cellCentre("hotbar_items", 8) != null;
               boolean pass = json.layout() == CreativeData.Layout.BOTH
                  && this.selectedTab().getType() != Type.INVENTORY
                  && json.grid() != null
                  && cells
                  && json.ui().instance().problems().count("unresolved-base") == 0;
               log(
                  "JSON UI: both layout shows the grid ({} cells) and the inventory cells (Java tab '{}', problems {}): {}",
                  json.grid() == null ? -1 : json.grid().gridCount(),
                  this.selectedTab().getDisplayName().getString(),
                  json.ui().instance().problems(),
                  pass ? "PASS" : "FAIL"
               );
            }
         }
      );
      this.screenshot("both");
      if (this.bds) {
         this.bothTake();
      }

      this.toggle("creative_layout_toggle_panel", "the creative layout again");
      this.run(
         () -> log(
            "JSON UI: back to the creative layout from the both layout: {}",
            this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.layout() == CreativeData.Layout.CREATIVE && json.grid() != null
               ? "PASS"
               : "FAIL"
         )
      );
   }

   private void bothTake() {
      int accepted = StackRequests.acceptedTotal();
      int rejected = StackRequests.rejectedTotal();
      List<String> checks = new ArrayList<>();
      this.showTab(CreativeItemCategory.NATURE);
      DevCreativeTest.Taken first = this.find(
         CreativeItemCategory.NATURE, entry -> this.loose(entry) && identifier(entry).startsWith("minecraft:"), "a loose vanilla entry"
      );
      DevCreativeTest.Taken second = first == null
         ? null
         : this.find(
            CreativeItemCategory.NATURE,
            entry -> this.loose(entry) && identifier(entry).startsWith("minecraft:") && entry != first.entry(),
            "another loose vanilla entry"
         );
      DevCreativeTest.Taken third = second == null
         ? null
         : this.find(
            CreativeItemCategory.NATURE,
            entry -> this.loose(entry) && identifier(entry).startsWith("minecraft:") && entry != first.entry() && entry != second.entry(),
            "a third loose vanilla entry"
         );
      if (third == null) {
         log("JSON UI: FAIL, the nature tab has fewer than three loose vanilla entries");
      } else {
         this.clickPicker(first.index(), 0, ContainerInput.PICKUP, "both layout: pick up " + first);
         this.clickInventoryCell("inventory_items", 5, "put it into inventory cell 5");
         this.command(this.verifyAt(first, "slot.inventory", 5, () -> this.minecraft.player.getInventory().getItem(14)));
         this.clickPicker(second.index(), 0, ContainerInput.PICKUP, "both layout: pick up " + second);
         this.clickInventoryCell("hotbar_items", 8, "put it into hotbar cell 8");
         checks.add(this.verifyAt(second, "slot.hotbar", 8, () -> this.minecraft.player.getInventory().getItem(8)));
         this.clickPicker(third.index(), 6, ContainerInput.SWAP, "both layout: number key 7 on " + third);
         checks.add(this.verifyAt(third, "slot.hotbar", 6, () -> this.minecraft.player.getInventory().getItem(6)));
         int free = this.onClient(() -> {
            for (int slot = 0; slot < 9; slot++) {
               if (this.minecraft.player.getInventory().getItem(slot).isEmpty()) {
                  return slot;
               }
            }

            return -1;
         });
         this.clickInventoryCell("inventory_items", 5, ContainerInput.QUICK_MOVE, "shift-click inventory cell 5 (first free hotbar slot " + free + ")");
         if (free >= 0) {
            checks.add(this.verifyAt(first, "slot.hotbar", free, () -> this.minecraft.player.getInventory().getItem(free)));
         }

         DevCreativeTest.Taken helmet = this.findAnywhere("minecraft:iron_helmet");
         if (helmet != null) {
            this.clickPicker(helmet.index(), 0, ContainerInput.PICKUP, "both layout: pick up " + helmet);
            this.clickInventoryCell("armor_items", 0, "put it on the head cell");
            checks.add("testfor @s[hasitem={item=minecraft:iron_helmet,location=slot.armor.head,quantity=1}]");
         }

         sleep(1500L);
         log(
            "both layout: requests accepted {}, rejected {}: {}",
            StackRequests.acceptedTotal() - accepted,
            StackRequests.rejectedTotal() - rejected,
            StackRequests.rejectedTotal() == rejected && StackRequests.acceptedTotal() > accepted ? "PASS" : "FAIL"
         );
         this.run(() -> this.minecraft.player.closeContainer());
         sleep(700L);
         this.commands(checks.stream().filter(check -> check != null).toArray(String[]::new));
         this.commands("clear @s");
         this.run(
            () -> this.minecraft
               .gui
               .setScreen(
                  new CreativeModeInventoryScreen(
                     this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), (Boolean)this.minecraft.options.operatorItemsTab().get()
                  )
               )
         );
         sleep(1500L);
         this.run(
            () -> log(
               "JSON UI: the screen opens in the both layout again: {}",
               this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.layout() == CreativeData.Layout.BOTH && json.grid() != null
                  ? "PASS"
                  : "FAIL"
            )
         );
      }
   }

   private void clickInventoryCell(String collection, int index, String what) {
      this.clickInventoryCell(collection, index, ContainerInput.PICKUP, what);
   }

   private void clickInventoryCell(String collection, int index, ContainerInput input, String what) {
      this.run(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
            this.jsonUiClicks++;
            float[] at = json.cellCentre(collection, index);
            Slot under = at == null ? null : json.slotAt(at[0], at[1]);
            if (under != null && this.minecraft.player.inventoryMenu.getSlot(under.index) == under) {
               press(json, at, 1, input == ContainerInput.QUICK_MOVE ? 1 : 0);
               log("creative click: {} through the JSON UI: cursor {}", what, ((ItemPickerMenu)json.getMenu()).getCarried());
            } else {
               this.jsonUiMisses++;
               log("JSON UI: FAIL, {} cell {} is not under the mouse for '{}'", collection, index, what);
            }
         }
      });
      sleep(700L);
   }

   @Nullable
   private String verifyAt(DevCreativeTest.Taken taken, String location, int slot, Supplier<ItemStack> java) {
      return this.onClient(
         () -> {
            ItemStack shown = java.get();
            CreativeItemData last = this.requests.creative().lastCreated();
            log("took {}: {} {} shows {}; last CRAFT_CREATIVE {}", taken, location, slot, shown, describe(last));
            ItemData item = taken.entry().getItem();
            String data = item.getDamage() != 0 ? ",data=" + item.getDamage() : "";
            return "testfor @s[hasitem={item="
               + identifier(taken.entry())
               + data
               + ",location="
               + location
               + ",slot="
               + slot
               + ",quantity="
               + Math.max(1, shown.getCount())
               + "}]";
         }
      );
   }

   private void toggle(String panel, String what) {
      this.run(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
            this.jsonUiClicks++;
            UiControl holder = json.ui().instance().find(panel);
            UiControl toggle = holder == null ? null : first(holder, UiType.TOGGLE);
            if (toggle != null && toggle.visible()) {
               press(json, centre(toggle), 1, 0);
               log("JSON UI: clicked {}", what);
            } else {
               this.jsonUiMisses++;
               log("JSON UI: FAIL, no toggle in {} for {}", panel, what);
            }
         }
      });
      sleep(700L);
   }

   @Nullable
   private static UiControl first(UiControl from, UiType type) {
      ArrayDeque<UiControl> queue = new ArrayDeque<>();
      queue.add(from);

      while (!queue.isEmpty()) {
         UiControl control = queue.poll();
         if (control.type() == type && control.visible()) {
            return control;
         }

         queue.addAll(control.children());
      }

      return null;
   }

   private static void press(JsonUiCreativeScreen screen, float[] at, int button, int modifiers) {
      MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(button, modifiers));
      screen.mouseMoved(at[0], at[1]);
      screen.mouseClicked(event, false);
      screen.mouseReleased(event);
   }

   private static float[] centre(UiControl control) {
      return JsonUiScreen.toGui(control.x() + control.width() / 2.0F, control.y() + control.height() / 2.0F);
   }

   private boolean jsonUiPicker(int index, int button, ContainerInput input, String what) {
      boolean[] scrolled = new boolean[1];
      this.run(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.grid() != null) {
            scrolled[0] = json.ui().input().revealItem(json.grid(), index);
         }
      });
      sleep(scrolled[0] ? 400L : 50L);
      return this.onClient(
         () -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               this.jsonUiClicks++;
               UiControl grid = json.grid();
               UiControl cell = grid == null ? null : grid.gridItem(index);
               float[] at = cell == null ? null : centre(cell);
               Slot under = at == null ? null : json.slotAt(at[0], at[1]);
               if (under != null && index < json.items().size() && under.getItem() == json.items().get(index)) {
                  switch (input) {
                     case PICKUP:
                        press(json, at, button == 1 ? 3 : 1, 0);
                        break;
                     case QUICK_MOVE:
                        press(json, at, 1, 1);
                        break;
                     case CLONE:
                        press(json, at, 2, 0);
                        break;
                     case SWAP:
                        json.mouseMoved(at[0], at[1]);
                        KeyMapping key = this.minecraft.options.keyHotbarSlots[button];
                        json.keyPressed(new KeyEvent(InputConstants.getKey(key.saveString()).getValue(), 0, 0));
                        break;
                     default:
                        this.jsonUiMisses++;
                        log("JSON UI: FAIL, '{}' ({}) can't be made on the JSON UI", what, input);
                        return false;
                  }

                  log(
                     "creative click: {} ({} {}) through the JSON UI: cursor {}, last CRAFT_CREATIVE {}",
                     what,
                     input,
                     button,
                     ((ItemPickerMenu)json.getMenu()).getCarried(),
                     describe(this.requests.creative().lastCreated())
                  );
                  return true;
               } else {
                  this.jsonUiMisses++;
                  log("JSON UI: FAIL, creative cell {} is not under the mouse for '{}'", index, what);
                  return false;
               }
            } else {
               return false;
            }
         }
      );
   }

   private boolean jsonUiHotbar(int hotbar, String what) {
      return this.onClient(
         () -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               this.jsonUiClicks++;
               float[] at = json.cellCentre("hotbar_items", hotbar);
               Slot under = at == null ? null : json.slotAt(at[0], at[1]);
               if (under != null && under.index == 45 + hotbar) {
                  press(json, at, 1, 0);
                  log(
                     "creative click: {} through the JSON UI: cursor {}, last CRAFT_CREATIVE {}",
                     what,
                     ((ItemPickerMenu)json.getMenu()).getCarried(),
                     describe(this.requests.creative().lastCreated())
                  );
                  return true;
               } else {
                  this.jsonUiMisses++;
                  log("JSON UI: FAIL, hotbar cell {} is not under the mouse for '{}'", hotbar, what);
                  return false;
               }
            } else {
               return false;
            }
         }
      );
   }

   private void take() {
      List<Integer> free = this.onClient(() -> {
         List<Integer> slots = new ArrayList<>();

         for (int slotx = 0; slotx < 9; slotx++) {
            if (this.minecraft.player.getInventory().getItem(slotx).isEmpty()) {
               slots.add(slotx);
            }
         }

         return slots;
      });
      log("free hotbar slots {}", free);
      if (free.size() < 4) {
         log("not enough free hotbar slots; taking nothing");
      } else {
         List<String> checks = new ArrayList<>();
         int accepted = StackRequests.acceptedTotal();
         int rejected = StackRequests.rejectedTotal();
         this.showTab(CreativeItemCategory.NATURE);
         DevCreativeTest.Taken vanilla = this.find(
            CreativeItemCategory.NATURE, entry -> this.loose(entry) && identifier(entry).startsWith("minecraft:"), "a loose vanilla entry"
         );
         if (vanilla != null) {
            this.clickPicker(vanilla.index(), free.get(0), ContainerInput.SWAP, "hotbar key " + (free.get(0) + 1) + " on " + vanilla);
            checks.add(this.verify(vanilla, free.get(0)));
         }

         this.showTab(CreativeItemCategory.CONSTRUCTION);
         BedrockCreativeTabs.Group group = this.onClient(() -> {
            for (ItemStack stack : this.picker().items) {
               BedrockCreativeTabs.Group each = BedrockCreativeTabs.group(stack);
               if (each != null && each.entries().size() >= 3 && identifier(BedrockCreativeTabs.entry(each.entries().get(1))).startsWith("minecraft:")) {
                  return each;
               }
            }

            return null;
         });
         if (group != null) {
            this.openGroup(group);
            this.screenshot("group-open");
            DevCreativeTest.Taken inside = this.onClient(() -> this.taken(this.picker().items.indexOf(group.entries().get(1))));
            this.clickPicker(inside.index(), 0, ContainerInput.PICKUP, "pick up " + inside + " (second entry of group '" + group.name() + "')");
            this.clickHotbar(free.get(1), "put it into hotbar " + free.get(1));
            checks.add(this.verify(inside, free.get(1)));
         }

         String customItem = this.bds ? "bedrocknative:ruby" : this.pickCustom(false, "prisma:ruby_ingot");
         String customBlock = this.bds ? "bedrocknative:ruby_block" : this.pickCustom(true, "prisma:meteorite_ore");
         DevCreativeTest.Taken item = this.findAnywhere(customItem);
         if (item != null) {
            this.clickPicker(item.index(), 2, ContainerInput.CLONE, "middle-click " + item);
            this.clickHotbar(free.get(2), "put the stack into hotbar " + free.get(2));
            checks.add(this.verify(item, free.get(2)));
         }

         DevCreativeTest.Taken block = this.findAnywhere(customBlock);
         if (block != null) {
            this.clickPicker(block.index(), 0, ContainerInput.QUICK_MOVE, "shift-click " + block);
            this.clickHotbar(free.get(3), "put the stack into hotbar " + free.get(3));
            checks.add(this.verify(block, free.get(3)));
            this.screenshot("taken");
         }

         if (vanilla != null) {
            int before = StackRequests.acceptedTotal() + StackRequests.rejectedTotal();
            this.showTab(CreativeItemCategory.NATURE);
            this.clickPicker(vanilla.index(), 0, ContainerInput.PICKUP, "pick up " + vanilla);
            this.clickPicker(vanilla.index() + 1, 0, ContainerInput.PICKUP, "click another creative slot with it: Java destroys the carried stack");
            sleep(700L);
            log(
               "destroying a carried creative stack sent {} requests, cursor now {}",
               StackRequests.acceptedTotal() + StackRequests.rejectedTotal() - before,
               this.onClient(() -> this.minecraft.player.containerMenu.getCarried())
            );
         }

         sleep(1500L);
         log("taking from creative: requests accepted {}, rejected {}", StackRequests.acceptedTotal() - accepted, StackRequests.rejectedTotal() - rejected);
         this.run(
            () -> {
               for (int slotx : free.subList(0, 4)) {
                  ItemData server = this.requests.inventory().item(slotx);
                  log(
                     "hotbar {} on the server: {} x{} (Java shows {})",
                     slotx,
                     server.isNull() ? "empty" : server.getDefinition().getIdentifier(),
                     server.getCount(),
                     this.minecraft.player.getInventory().getItem(slotx)
                  );
               }
            }
         );
         if (!this.bds) {
            int before = StackRequests.acceptedTotal();

            for (int slot : free.subList(0, 4)) {
               this.clickHotbar(slot, "pick hotbar " + slot + " up again");
               this.destroyCarried();
            }

            this.run(() -> this.minecraft.player.closeContainer());
            sleep(1500L);
            this.run(
               () -> log(
                  "destroyed the taken stacks: requests accepted {}, rejected {}; hotbar on the server {}",
                  StackRequests.acceptedTotal() - before,
                  StackRequests.rejectedTotal(),
                  free.subList(0, 4)
                     .stream()
                     .map(slotx -> this.requests.inventory().item(slotx))
                     .map(server -> server.isNull() ? "empty" : server.getDefinition().getIdentifier())
                     .toList()
               )
            );
            this.run(
               () -> this.minecraft
                  .gui
                  .setScreen(
                     new CreativeModeInventoryScreen(
                        this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), (Boolean)this.minecraft.options.operatorItemsTab().get()
                     )
                  )
            );
            sleep(1500L);
         }

         if (this.bds) {
            this.run(() -> this.minecraft.player.closeContainer());
            sleep(700L);
            this.commands(checks.stream().filter(check -> check != null).toArray(String[]::new));
            this.run(
               () -> this.minecraft
                  .gui
                  .setScreen(
                     new CreativeModeInventoryScreen(
                        this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), (Boolean)this.minecraft.options.operatorItemsTab().get()
                     )
                  )
            );
            sleep(1500L);
         }
      }
   }

   private void search() {
      String name = this.onClient(
         () -> {
            for (CreativeItemCategory category : BedrockCreativeTabs.categories()) {
               DevCreativeTest.Taken item = findIn(
                  BedrockCreativeTabs.entries(category), entry -> identifier(entry).equals(this.bds ? "bedrocknative:ruby" : "prisma:ruby_ingot")
               );
               if (item != null) {
                  return item.stack().getHoverName().getString();
               }
            }

            return "ruby";
         }
      );
      String typed = name.toLowerCase(Locale.ROOT);
      if (this.jsonUi && this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen)) {
         this.toggle("search_tab_holder", "the search tab");
         this.run(() -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               this.jsonUiClicks++;
               if (json.ui().input().focused() == null) {
                  this.jsonUiMisses++;
                  log("JSON UI: FAIL, the search box has no focus after the search tab opened");
               }

               typed.codePoints().forEach(codePoint -> json.charTyped(new CharacterEvent(codePoint)));
            }
         });
      } else {
         this.run(() -> {
            this.selectTab(CreativeModeTabs.searchTab());

            try {
               EditBox box = (EditBox)field("searchBox").get(this.screen());
               box.setValue(typed);
               method("refreshSearchResults").invoke(this.screen());
            } catch (ReflectiveOperationException var3) {
               throw new IllegalStateException(var3);
            }
         });
      }

      sleep(1000L);
      this.run(
         () -> {
            List<String> found = this.picker().items.stream().map(stack -> {
               CreativeItemData entry = BedrockCreativeTabs.entry(stack);
               return entry == null ? "?" + stack : identifier(entry);
            }).toList();
            log(
               "search '{}': {} results {}; search tab holds {} entries",
               name,
               found.size(),
               found.stream().limit(12L).toList(),
               CreativeModeTabs.searchTab().getDisplayItems().size()
            );
            if (this.jsonUi && this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               UiControl box = json.searchBox();
               String shown = box == null ? "-" : json.ui().input().text(box);
               UiControl grid = json.grid();
               boolean pass = shown.equals(typed) && grid != null && grid.gridCount() == found.size() && !found.isEmpty();
               log(
                  "JSON UI: search box '{}', grid {} cells for {} results: {}",
                  shown,
                  grid == null ? -1 : grid.gridCount(),
                  found.size(),
                  pass ? "PASS" : "FAIL"
               );
            }
         }
      );
      this.screenshot("search");
      if (this.jsonUi && this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen)) {
         this.run(() -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               UiControl box = json.searchBox();
               if (box != null) {
                  json.ui().input().setText(box, "");
               }
            }
         });
         sleep(1000L);
         this.run(
            () -> {
               if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.grid() != null) {
                  UiControl grid = json.grid();
                  boolean pass = grid.gridCount() == this.picker().items.size() && grid.generated().size() < 400 && this.picker().items.size() > 400;
                  log(
                     "JSON UI: empty search lists {} entries, {} cells made, {} controls: {}",
                     grid.gridCount(),
                     grid.generated().size(),
                     json.ui().instance().controlCount(),
                     pass ? "PASS" : "FAIL"
                  );
               }
            }
         );
         this.screenshot("search-all");
      }
   }

   private void craftRubies() {
      this.run(
         () -> {
            CustomItem ruby = ItemMappings.custom("bedrocknative:ruby");
            log(
               "ruby item tags {}; recipes {}",
               ruby == null ? "no definition" : ruby.tags(),
               this.requests
                  .crafting()
                  .shaped()
                  .stream()
                  .filter(Crafting::serverDefined)
                  .map(recipe -> recipe.getId() + " " + recipe.getIngredients().getFirst().getDescriptor())
                  .toList()
            );
         }
      );
      this.commands("gamemode survival", "clear @s", "give @s bedrocknative:ruby 8");
      sleep(2000L);
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(700L);
      this.clickIdentifier("bedrocknative:ruby", 0, "pick up 8 rubies");

      for (int slot = 1; slot <= 4; slot++) {
         this.click(slot, 1, ContainerInput.PICKUP, "one ruby into 2x2 slot " + slot);
      }

      this.clickFirstEmpty(9, "put the other 4 rubies down");
      sleep(700L);
      this.reportResult("2x2 shaped");
      this.click(0, 0, ContainerInput.PICKUP, "take the ruby block");
      this.clickFirstEmpty(9, "put the block down");
      sleep(700L);
      this.commands(hasItem("bedrocknative:ruby_block", 1), hasItem("bedrocknative:ruby", 4));
      this.clickIdentifier("bedrocknative:ruby_block", 0, "pick up the ruby block");
      this.click(1, 0, ContainerInput.PICKUP, "put it into 2x2 slot 1");
      sleep(700L);
      this.reportResult("2x2 shapeless");
      this.click(0, 0, ContainerInput.QUICK_MOVE, "shift-click the 4 rubies out");
      sleep(700L);
      this.run(() -> this.minecraft.player.closeContainer());
      sleep(700L);
      this.commands(hasItem("bedrocknative:ruby_block", 0), hasItem("bedrocknative:ruby", 8));
      this.craftCorner();
      BlockPos[] table = new BlockPos[1];
      this.run(() -> table[0] = this.minecraft.player.blockPosition().offset(2, 0, -1));
      String at = table[0].getX() + " " + table[0].getY() + " " + table[0].getZ();
      this.command("setblock " + at + " crafting_table");
      sleep(1000L);
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(table[0])));
      sleep(300L);
      this.run(
         () -> this.minecraft
            .gameMode
            .useItemOn(
               this.minecraft.player,
               InteractionHand.MAIN_HAND,
               new BlockHitResult(Vec3.atCenterOf(table[0]).add(-0.5, 0.0, 0.0), Direction.WEST, table[0], false)
            )
      );
      sleep(1500L);
      this.clickIdentifier("bedrocknative:ruby", 0, "pick up the 8 rubies");

      for (int slot : List.of(1, 2, 4, 5)) {
         this.click(slot, 1, ContainerInput.PICKUP, "one ruby into 3x3 slot " + slot);
      }

      this.clickFirstEmpty(10, "put the other 4 rubies down");
      sleep(700L);
      this.reportResult("3x3 shaped");
      this.screenshot("table-shaped");
      this.click(0, 0, ContainerInput.QUICK_MOVE, "shift-click the ruby block out");
      sleep(700L);
      this.commands(hasItem("bedrocknative:ruby_block", 1), hasItem("bedrocknative:ruby", 4));
      this.clickIdentifier("bedrocknative:ruby_block", 0, "pick up the ruby block");
      this.click(9, 0, ContainerInput.PICKUP, "put it into 3x3 slot 9");
      sleep(700L);
      this.reportResult("3x3 shapeless");
      this.screenshot("table-shapeless");
      this.click(0, 0, ContainerInput.PICKUP, "take the 4 rubies");
      this.clickFirstEmpty(10, "put them down");
      sleep(700L);
      this.run(() -> this.minecraft.player.closeContainer());
      sleep(700L);
      this.commands(hasItem("bedrocknative:ruby_block", 0), hasItem("bedrocknative:ruby", 8), "setblock " + at + " air");
   }

   private void craftCorner() {
      this.run(
         () -> log(
            "corner recipe: {}",
            this.requests
               .crafting()
               .shaped()
               .stream()
               .filter(recipe -> recipe.getId().equals("bedrocknative:ruby_corner"))
               .map(recipe -> "assume symmetry " + recipe.isAssumeSymetry() + ", net id " + recipe.getNetId())
               .toList()
         )
      );
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(700L);
      this.clickIdentifier("bedrocknative:ruby", 0, "pick up 8 rubies");

      for (int slot : List.of(2, 3, 4)) {
         this.click(slot, 1, ContainerInput.PICKUP, "one ruby into 2x2 slot " + slot);
      }

      this.clickFirstEmpty(9, "put the other 5 rubies down");
      sleep(700L);
      this.reportResult("2x2 mirrored corner (expect nothing)");
      this.click(2, 0, ContainerInput.PICKUP, "take the top right ruby");
      this.click(1, 0, ContainerInput.PICKUP, "put it top left");
      sleep(700L);
      this.reportResult("2x2 corner");
      this.click(0, 0, ContainerInput.PICKUP, "take the emerald");
      this.clickFirstEmpty(9, "put it down");
      sleep(700L);
      this.run(() -> this.minecraft.player.closeContainer());
      sleep(700L);
      this.commands(hasItem("minecraft:emerald", 1), hasItem("bedrocknative:ruby", 5), "clear @s", "give @s bedrocknative:ruby 8");
      sleep(2000L);
   }

   private void craftOnPrisma() {
      Crafting crafting = this.requests.crafting();
      CraftingRecipeData recipe = this.onClient(
         () -> {
            List<CraftingRecipeData> all = new ArrayList<>(crafting.shaped());
            all.addAll(crafting.shapeless());

            for (CraftingRecipeData each : all) {
               boolean fits = each instanceof ShapedRecipeData shaped ? shaped.getWidth() <= 2 && shaped.getHeight() <= 2 : each.getIngredients().size() <= 4;
               boolean exact = each.getIngredients()
                  .stream()
                  .allMatch(
                     ingredient -> ingredient.getDescriptor() instanceof InvalidDescriptor
                        || ingredient.getDescriptor() instanceof DefaultDescriptor && ingredient.getCount() <= 1
                  );
               if (fits && exact && Crafting.serverDefined(each)) {
                  return each;
               }
            }

            return null;
         }
      );
      if (recipe == null) {
         log(
            "no server-defined recipe fits the 2x2 grid; nothing crafted. The server-defined ones: {}",
            this.onClient(
               () -> {
                  List<CraftingRecipeData> all = new ArrayList<>(crafting.shaped());
                  all.addAll(crafting.shapeless());
                  return all.stream()
                     .filter(Crafting::serverDefined)
                     .map(
                        each -> (each instanceof ShapedRecipeData shaped ? shaped.getWidth() + "x" + shaped.getHeight() : "shapeless")
                           + " "
                           + each.getIngredients().stream().map(ingredient -> {
                              return ingredient.getCount() + " " + switch (ingredient.getDescriptor()) {
                                 case DefaultDescriptor exact -> exact.getItemId() == null ? "?" : exact.getItemId().getIdentifier();
                                 case InvalidDescriptor empty -> "empty";
                                 default -> ingredient.getDescriptor().getType().name();
                              };
                           }).toList()
                           + " -> "
                           + each.getResults().stream().map(result -> result.getCount() + " " + result.getDefinition().getIdentifier()).toList()
                     )
                     .toList();
               }
            )
         );
      } else {
         String player = this.onClient(() -> this.minecraft.player.getGameProfile().name());
         List<String> given = new ArrayList<>();

         for (ItemDescriptorWithCount ingredient : recipe.getIngredients()) {
            String item = item(ingredient);
            if (item != null) {
               given.add(item);
            }
         }

         String result = recipe.getResults().getFirst().getDefinition().getIdentifier();
         log("Prisma recipe {} ({}): {} -> {} x{}", recipe.getId(), recipe.getClass().getSimpleName(), given, result, recipe.getResults().getFirst().getCount());
         this.command("gamemode survival");

         for (String item : given.stream().distinct().toList()) {
            this.command("give " + player + " " + item + " " + given.stream().filter(item::equals).count());
         }

         sleep(2000L);
         this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
         sleep(700L);
         int width = recipe instanceof ShapedRecipeData shaped ? shaped.getWidth() : 2;
         List<ItemDescriptorWithCount> ingredients = recipe.getIngredients();

         for (int i = 0; i < ingredients.size(); i++) {
            String item = item(ingredients.get(i));
            if (item != null) {
               int gridSlot = 1 + i / width * 2 + i % width;
               this.clickIdentifier(item, 0, "pick up " + item);
               this.click(gridSlot, 1, ContainerInput.PICKUP, "one into 2x2 slot " + gridSlot);
               this.clickFirstEmpty(9, "put the rest down");
            }
         }

         sleep(700L);
         this.reportResult("Prisma 2x2");
         this.screenshot("prisma-craft");
         int accepted = StackRequests.acceptedTotal();
         this.click(0, 0, ContainerInput.PICKUP, "take the result");
         this.clickFirstEmpty(9, "put it down");
         sleep(1500L);
         log(
            "Prisma craft: requests accepted {}, rejected {}; inventory holds {}",
            StackRequests.acceptedTotal() - accepted,
            StackRequests.rejectedTotal(),
            this.onClient(() -> this.describeInventory())
         );
         this.run(() -> this.minecraft.player.closeContainer());
         sleep(700L);

         for (String item : given.stream().distinct().toList()) {
            this.command("clear " + player + " " + item);
         }

         this.command("clear " + player + " " + result);
      }
   }

   @Nullable
   private static String item(ItemDescriptorWithCount ingredient) {
      return ingredient.getDescriptor() instanceof DefaultDescriptor exact && exact.getItemId() != null && exact.getItemId().getRuntimeId() != 0
         ? exact.getItemId().getIdentifier()
         : null;
   }

   private void destroyCarried() {
      int index = this.onClient(() -> {
         List<ItemStack> items = this.picker().items;
         ItemStack carried = this.picker().getCarried();

         for (int i = 0; i < items.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(items.get(i), carried) && !BedrockCreativeTabs.isHeader(items.get(i))) {
               return i;
            }
         }

         return 0;
      });
      this.clickPicker(index, 0, ContainerInput.PICKUP, "drop the carried stack onto the creative grid: Java destroys it");
   }

   private void clickPicker(int index, int button, ContainerInput input, String what) {
      if (this.jsonUi && this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen)) {
         this.jsonUiPicker(index, button, input, what);
         sleep(700L);
      } else {
         this.clickSlot(() -> this.scrollTo(index), button, input, what);
      }
   }

   private void clickHotbar(int hotbar, String what) {
      if (this.jsonUi && this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen)) {
         this.jsonUiHotbar(hotbar, what);
         sleep(700L);
      } else {
         this.clickSlot(() -> 45 + hotbar, 0, ContainerInput.PICKUP, what);
      }
   }

   private void clickSlot(Supplier<Integer> slotIndex, int button, ContainerInput input, String what) {
      this.run(
         () -> {
            ItemPickerMenu menu = this.picker();
            int index = slotIndex.get();

            try {
               method("slotClicked", Slot.class, int.class, int.class, ContainerInput.class).invoke(this.screen(), menu.slots.get(index), index, button, input);
            } catch (ReflectiveOperationException var8) {
               throw new IllegalStateException(var8);
            }

            log(
               "creative click: {} ({} {}): cursor {}, last CRAFT_CREATIVE {}",
               what,
               input,
               button,
               menu.getCarried(),
               describe(this.requests.creative().lastCreated())
            );
         }
      );
      sleep(700L);
   }

   private int scrollTo(int index) {
      ItemPickerMenu menu = this.picker();
      int rows = (menu.items.size() + 8) / 9 - 5;
      int row = index / 9;
      float scroll = rows <= 0 ? 0.0F : Math.min(1.0F, (float)row / rows);
      menu.scrollTo(scroll);

      try {
         field("scrollOffs").setFloat(this.screen(), scroll);
      } catch (ReflectiveOperationException var7) {
         throw new IllegalStateException(var7);
      }

      int top = rows <= 0 ? 0 : Math.max((int)(scroll * rows + 0.5F), 0);
      return index - top * 9;
   }

   private void openGroup(BedrockCreativeTabs.Group group) {
      int index = this.onClient(() -> this.picker().items.indexOf(group.header()));
      this.clickPicker(index, 0, ContainerInput.PICKUP, "open group '" + group.name() + "' (" + group.entries().size() + " entries)");
      this.run(() -> log("group '{}' open {}, tab now shows {} slots", group.name(), BedrockCreativeTabs.isOpen(group), this.picker().items.size()));
   }

   @Nullable
   private DevCreativeTest.Taken find(CreativeItemCategory category, Predicate<CreativeItemData> test, String what) {
      DevCreativeTest.Taken found = this.onClient(() -> {
         List<ItemStack> items = this.picker().items;

         for (int i = 0; i < items.size(); i++) {
            CreativeItemData entry = BedrockCreativeTabs.entry(items.get(i));
            if (entry != null && test.test(entry)) {
               return new DevCreativeTest.Taken(i, items.get(i), entry);
            }
         }

         return null;
      });
      log("{} in {}: {}", what, category, found);
      return found;
   }

   @Nullable
   private DevCreativeTest.Taken findAnywhere(String identifier) {
      for (CreativeItemCategory category : BedrockCreativeTabs.categories()) {
         DevCreativeTest.Taken entry = this.onClient(() -> findIn(BedrockCreativeTabs.entries(category), each -> identifier(each).equals(identifier)));
         if (entry != null) {
            this.showTab(category);
            BedrockCreativeTabs.Group group = this.onClient(
               () -> this.picker()
                  .items
                  .stream()
                  .map(BedrockCreativeTabs::group)
                  .filter(each -> each != null && each.entries().contains(entry.stack()))
                  .findFirst()
                  .orElse(null)
            );
            if (group != null && !this.onClient(() -> BedrockCreativeTabs.isOpen(group))) {
               this.openGroup(group);
            }

            return this.find(category, each -> each == entry.entry(), identifier);
         }
      }

      log("{} is in no creative category", identifier);
      return null;
   }

   private String pickCustom(boolean block, String preferred) {
      String chosen = this.onClient(() -> {
         String first = null;

         for (CreativeItemCategory category : BedrockCreativeTabs.categories()) {
            for (ItemStack stack : BedrockCreativeTabs.entries(category)) {
               CustomItem custom = ItemMappings.custom(stack);
               if (custom != null) {
                  if (custom.identifier().equals(preferred)) {
                     return preferred;
                  }

                  if (first == null && custom.block() != null == block) {
                     first = custom.identifier();
                  }
               }
            }
         }

         return first != null ? first : preferred;
      });
      log("custom {} to take: {}", block ? "block" : "item", chosen);
      return chosen;
   }

   @Nullable
   private static DevCreativeTest.Taken findIn(List<ItemStack> stacks, Predicate<CreativeItemData> test) {
      for (int i = 0; i < stacks.size(); i++) {
         CreativeItemData entry = BedrockCreativeTabs.entry(stacks.get(i));
         if (entry != null && test.test(entry)) {
            return new DevCreativeTest.Taken(i, stacks.get(i), entry);
         }
      }

      return null;
   }

   private DevCreativeTest.Taken taken(int index) {
      ItemStack stack = (ItemStack)this.picker().items.get(index);
      return new DevCreativeTest.Taken(index, stack, BedrockCreativeTabs.entry(stack));
   }

   @Nullable
   private String verify(DevCreativeTest.Taken taken, int hotbar) {
      return this.onClient(
         () -> {
            CreativeItemData last = this.requests.creative().lastCreated();
            ItemStack java = this.minecraft.player.getInventory().getItem(hotbar);
            log(
               "took {}: CRAFT_CREATIVE named {} -> {}; hotbar {} shows {}",
               taken,
               describe(last),
               last != null && last.getNetId() == taken.entry().getNetId() ? "exactly that entry" : "ANOTHER ENTRY",
               hotbar,
               java
            );
            ItemData item = taken.entry().getItem();
            String data = item.getDamage() != 0 ? ",data=" + item.getDamage() : "";
            return "testfor @s[hasitem={item="
               + identifier(taken.entry())
               + data
               + ",location=slot.hotbar,slot="
               + hotbar
               + ",quantity="
               + java.getCount()
               + "}]";
         }
      );
   }

   private boolean loose(CreativeItemData entry) {
      List<CreativeItemGroup> groups = this.requests.creative().groups();
      return entry.getGroupId() < 0 || entry.getGroupId() >= groups.size() || groups.get(entry.getGroupId()).getName().isEmpty();
   }

   private void showTab(CreativeItemCategory category) {
      if (this.jsonUi && this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen)) {
         this.toggle(category.name().toLowerCase(Locale.ROOT) + "_tab_panel", "the " + category + " tab");
      } else {
         this.run(() -> this.selectTab(BedrockCreativeTabs.javaTab(category)));
         sleep(700L);
      }
   }

   private void selectTab(CreativeModeTab tab) {
      try {
         method("selectTab", CreativeModeTab.class).invoke(this.screen(), tab);
      } catch (ReflectiveOperationException var3) {
         throw new IllegalStateException(var3);
      }
   }

   private CreativeModeTab selectedTab() {
      try {
         return (CreativeModeTab)field("selectedTab").get(null);
      } catch (ReflectiveOperationException var2) {
         throw new IllegalStateException(var2);
      }
   }

   private CreativeModeInventoryScreen screen() {
      Screen screen = this.minecraft.gui.screen();
      if (screen instanceof CreativeModeInventoryScreen creative) {
         return creative;
      } else {
         throw new IllegalStateException("The creative screen is not open: " + screen);
      }
   }

   private ItemPickerMenu picker() {
      return (ItemPickerMenu)this.screen().getMenu();
   }

   private static Method method(String name, Class<?>... parameters) throws NoSuchMethodException {
      Method method = CreativeModeInventoryScreen.class.getDeclaredMethod(name, parameters);
      method.setAccessible(true);
      return method;
   }

   private static Field field(String name) throws NoSuchFieldException {
      Field field = CreativeModeInventoryScreen.class.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   private void reportResult(String what) {
      this.run(
         () -> {
            ItemStack result = this.minecraft.player.containerMenu.getSlot(0).getItem();
            CustomItem custom = ItemMappings.custom(result);
            log(
               "{}: the result slot shows {} x{} ({})",
               what,
               custom != null ? custom.identifier() : BuiltInRegistries.ITEM.getKey(result.getItem()),
               result.getCount(),
               result.getHoverName().getString()
            );
         }
      );
   }

   private void clickIdentifier(String identifier, int button, String what) {
      this.run(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = menu instanceof CraftingMenu ? 10 : 9; slot < menu.slots.size(); slot++) {
            if (is(menu.getSlot(slot).getItem(), identifier)) {
               this.clickNow(slot, button, ContainerInput.PICKUP, what);
               return;
            }
         }

         log("no {} in the open menu for '{}'", identifier, what);
      });
      sleep(700L);
   }

   private static boolean is(ItemStack stack, String identifier) {
      if (stack.isEmpty()) {
         return false;
      } else {
         CustomItem custom = ItemMappings.custom(stack);
         return custom != null ? custom.identifier().equals(identifier) : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(identifier);
      }
   }

   private void clickFirstEmpty(int from, String what) {
      this.run(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = from; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
               this.clickNow(slot, 0, ContainerInput.PICKUP, what);
               return;
            }
         }
      });
      sleep(700L);
   }

   private void click(int slot, int button, ContainerInput input, String what) {
      this.run(() -> this.clickNow(slot, button, input, what));
      sleep(700L);
   }

   private void clickNow(int slot, int button, ContainerInput input, String what) {
      LocalPlayer player = this.minecraft.player;
      AbstractContainerMenu menu = player.containerMenu;
      this.minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
      log(
         "click: {} (slot {} {} {}): slot now {}, cursor {}; requests accepted {}, rejected {}",
         what,
         slot,
         input,
         button,
         slot >= 0 ? menu.getSlot(slot).getItem() : "-",
         menu.getCarried(),
         StackRequests.acceptedTotal(),
         StackRequests.rejectedTotal()
      );
   }

   private String describeInventory() {
      List<String> items = new ArrayList<>();
      Inventory inventory = this.minecraft.player.getInventory();

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!stack.isEmpty()) {
            CustomItem custom = ItemMappings.custom(stack);
            items.add(slot + "=" + stack.getCount() + "x" + (custom != null ? custom.identifier() : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()));
         }
      }

      return items.toString();
   }

   private static String hasItem(String item, int quantity) {
      return "testfor @s[hasitem={item=" + item + ",quantity=" + quantity + "}]";
   }

   private static String identifier(@Nullable CreativeItemData entry) {
      return entry == null ? "none" : entry.getItem().getDefinition().getIdentifier();
   }

   private static String describe(@Nullable CreativeItemData entry) {
      return entry == null ? "none" : identifier(entry) + ":" + entry.getItem().getDamage() + " (net id " + entry.getNetId() + ")";
   }

   private void screenshot(String name) {
      this.run(
         () -> Screenshot.grab(
            this.minecraft.gameDirectory,
            "creative-" + this.server + "-" + name + (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen ? "-jsonui" : "") + ".png",
            this.minecraft.gameRenderer.mainRenderTarget(),
            1,
            message -> {}
         )
      );
      sleep(300L);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
      }
   }

   private void command(String command) {
      this.run(() -> {
         log("command /{}", command);
         this.minecraft.getConnection().sendCommand(command);
      });
      sleep(this.bds ? 500L : 1200L);
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev creative test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private record Taken(int index, ItemStack stack, CreativeItemData entry) {
      @Override
      public String toString() {
         return DevCreativeTest.identifier(this.entry) + " (net id " + this.entry.getNetId() + ", slot " + this.index + ")";
      }
   }
}
