package net.bedrocknative.client.net;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.BedrockRecipeBook;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.inventory.JsonUiRecipeBook;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.ui.RecipeBookData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiType;
import net.bedrocknative.world.recipe.BookRecipe;
import net.bedrocknative.world.recipe.RecipeBook;
import net.bedrocknative.world.state.InventoryOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryLayout;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.jspecify.annotations.Nullable;

final class DevRecipeBookTest {
   private static final long STEP = 700L;
   private static final int SHIFT = 1;
   private final Minecraft minecraft;
   private final StackRequests requests;
   private int checks;
   private int passed;

   DevRecipeBookTest(Minecraft minecraft, StackRequests requests) {
      this.minecraft = minecraft;
      this.requests = requests;
   }

   void run() {
      for (int i = 0; JsonUi.mode() != JsonUi.Mode.OFF && i < 40 && !this.onClient(() -> JsonUi.definitions() != null); i++) {
         sleep(500L);
      }

      BedrockRecipeBook book = BedrockRecipeBook.active();
      log("switch {}, JSON UI mode {}, inventory drawn by JSON UI {}", BedrockRecipeBook.enabled(), JsonUi.mode(), JsonUi.drawn("inventory"));
      this.check("the recipe book is on (always, unless -PbedrockRecipeBook=off)", book != null);
      if (book == null) {
         this.done();
      } else {
         this.unlocks(book);
         if (JsonUi.drawn("inventory")) {
            this.inventory(book);
            this.craftingTable();
         } else {
            this.javaFallback(book);
         }

         this.done();
      }
   }

   private void javaFallback(BedrockRecipeBook book) {
      this.commands("give @s oak_log 2");
      sleep(1000L);
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(1500L);
      this.run(
         () -> {
            Screen screen = this.minecraft.gui.screen();
            log(
               "inventory screen {} (JSON UI mode {}): Java's book fed with {} recipes, {} collections",
               screen == null ? "none" : screen.getClass().getSimpleName(),
               JsonUi.mode(),
               book.javaRecipes(),
               this.minecraft.player.getRecipeBook().getCollections().size()
            );
            this.check("Java's screen opens", screen instanceof InventoryScreen);
            this.check("Java's book holds the server's recipes", book.javaRecipes() > 0 && !this.minecraft.player.getRecipeBook().getCollections().isEmpty());
         }
      );
      BedrockRecipeBook active = BedrockRecipeBook.active();
      int planks = this.onClient(
         () -> {
            for (RecipeBook.Entry entry : active.book().entries()) {
               for (BookRecipe recipe : entry.recipes()) {
                  if (entry.result().getDefinition().getIdentifier().equals("minecraft:oak_planks")
                     && recipe.units().size() == 1
                     && recipe.units().getFirst().ingredient().getDescriptor() instanceof DefaultDescriptor log
                     && log.getItemId() != null
                     && "minecraft:oak_log".equals(log.getItemId().getIdentifier())) {
                     return recipe.netId();
                  }
               }
            }

            return -1;
         }
      );
      this.check("the oak planks recipe (from a log) is in the book", planks > 0);
      if (planks > 0) {
         this.step("Java's book places oak planks (one craft)", () -> this.onClient(() -> {
            this.minecraft.gameMode.handlePlaceRecipe(this.minecraft.player.containerMenu.containerId, new RecipeDisplayId(planks), false);
            return true;
         }));
         this.run(() -> {
            ItemStack grid = this.minecraft.player.inventoryMenu.getSlot(1).getItem();
            ItemStack result = this.minecraft.player.inventoryMenu.getSlot(0).getItem();
            log("grid after Java's book: {}, result {}", grid, result);
            this.check("the log is in the grid and the output shows 4 planks", grid.getCount() == 1 && result.getCount() == 4);
         });
         this.screenshot("java-placed");
         this.step(
            "shift-click Java's output",
            () -> this.onClient(
               () -> {
                  this.minecraft
                     .gameMode
                     .handleContainerInput(this.minecraft.player.containerMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
                  return true;
               }
            )
         );
         this.run(() -> this.check("4 oak planks taken from Java's output", this.count("oak_planks") == 4));
         this.commands(hasItem("oak_planks", 4));
      }

      this.run(() -> this.minecraft.player.closeContainer());
      sleep(700L);
   }

   private void unlocks(BedrockRecipeBook book) {
      this.commands("gamemode survival", "clear @s", "recipe take @s *");
      sleep(1500L);
      this.run(
         () -> {
            int entries = book.book().entries().size();
            log(
               "after /recipe take *: unlocks known {}, {} unlocked ids, {} book entries (always-unlocked recipes only)",
               book.unlocks().known(),
               book.unlocks().size(),
               entries
            );
            this.check("the server unlocks recipes (UnlockedRecipes arrived)", book.unlocks().known());
            this.check("only always-unlocked recipes are left in the book", entries > 0 && entries < 20);
         }
      );
      int toasts = this.onClient(book::toasts);
      this.commands("recipe give @s minecraft:oak_planks", "recipe give @s minecraft:stick");
      sleep(1500L);
      this.run(() -> {
         this.check("oak planks unlocked (NEWLY_UNLOCKED)", book.unlocks().isUnlocked("minecraft:oak_planks"));
         this.check("sticks unlocked (NEWLY_UNLOCKED)", book.unlocks().isUnlocked("minecraft:stick"));
         this.check("a 'New recipe unlocked!' toast showed (" + (book.toasts() - toasts) + ")", book.toasts() > toasts);
      });
      this.screenshot("toast");
   }

   private void inventory(BedrockRecipeBook book) {
      this.commands("give @s oak_log 1");
      sleep(1000L);
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(1500L);
      boolean open = this.onClient(
         () -> {
            Screen screen = this.minecraft.gui.screen();
            JsonUiRecipeBook part = screen instanceof JsonUiContainerScreen json ? json.recipeBook() : null;
            log("inventory screen {}, recipe book part {}", screen == null ? "none" : screen.getClass().getSimpleName(), part != null);
            if (part == null) {
               return false;
            } else {
               JsonUiContainerScreen jsonx = (JsonUiContainerScreen)screen;
               RecipeBookData data = part.data();
               log("the server's inventory options: {}", this.options());
               this.check(
                  "the inventory opens on the server's layout (" + data.layout() + ", BDS: the recipe book)",
                  data.layout() == RecipeBookData.Layout.RECIPE_BOOK
               );
               this.check("the book shows " + part.cells().size() + " cells", visible(jsonx, "recipe_book") && !part.cells().isEmpty());
               this.check("no unresolved JSON UI bases", jsonx.ui().instance().problems().count("unresolved-base") == 0);
               return true;
            }
         }
      );
      this.screenshot("open");
      if (!open) {
         this.run(() -> this.minecraft.player.closeContainer());
      } else {
         this.search("oak planks");
         this.run(
            () -> {
               JsonUiRecipeBook part = this.part();
               List<String> names = part == null ? List.of() : part.cells().stream().map(RecipeBook.Cell::name).toList();
               log("search 'oak planks': {}", names);
               this.check(
                  "the search lists oak planks and only matching names",
                  names.stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).contains("oak planks"))
                     && names.stream().allMatch(name -> name.toLowerCase(Locale.ROOT).contains("oak planks"))
               );
            }
         );
         this.screenshot("search");
         this.step("auto-craft oak planks (shift-click, one log)", () -> this.clickCell("minecraft:oak_planks", true));
         this.run(() -> {
            this.check("4 oak planks after the auto-craft", this.count("oak_planks") == 4);
            this.check("the log was used", this.count("oak_log") == 0);
         });
         this.commands(hasItem("oak_planks", 4), hasItem("oak_log", 0));
         this.search("crafting table");
         this.step("auto-craft a crafting table (shift-click, four planks)", () -> this.clickCell("minecraft:crafting_table", true));
         this.run(() -> {
            this.check("a crafting table after the auto-craft", this.count("crafting_table") == 1);
            this.check("the four planks were used", this.count("oak_planks") == 0);
         });
         this.commands(hasItem("crafting_table", 1), hasItem("oak_planks", 0));
         this.screenshot("crafted");
         this.search("stick");
         int before = StackRequests.acceptedTotal() + StackRequests.rejectedTotal() + this.requests.pendingCount();
         this.clickCell("minecraft:stick", false);
         sleep(700L);
         this.run(
            () -> {
               JsonUiRecipeBook part = this.part();
               this.check("a red recipe's click shows its ghost in the grid", part != null && part.ghostShown());
               this.check(
                  "a red recipe's click sends nothing", StackRequests.acceptedTotal() + StackRequests.rejectedTotal() + this.requests.pendingCount() == before
               );
            }
         );
         this.screenshot("ghost");
         this.commands("give @s oak_log 1");
         sleep(1000L);
         this.search("oak planks");
         this.step("click oak planks: the log goes into the grid", () -> this.clickCell("minecraft:oak_planks", false));
         this.run(
            () -> {
               ItemStack grid = this.minecraft.player.inventoryMenu.getSlot(1).getItem();
               ItemStack result = this.minecraft.player.inventoryMenu.getSlot(0).getItem();
               log("grid after the click: {}, result {}", grid, result);
               this.check("the log is in the grid", BuiltInRegistries.ITEM.getKey(grid.getItem()).getPath().equals("oak_log"));
               this.check(
                  "the output shows 4 oak planks", result.getCount() == 4 && BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().equals("oak_planks")
               );
            }
         );
         this.screenshot("placed");
         this.step("shift-click the output: the planks into the inventory", this::takeOutput);
         this.run(() -> this.check("4 oak planks taken from the output", this.count("oak_planks") == 4));
         this.commands(hasItem("oak_planks", 4));
         this.layoutToggle("survival_layout_toggle_panel");
         sleep(700L);
         this.run(
            () -> {
               JsonUiRecipeBook part = this.part();
               this.check(
                  "the survival layout toggle hides the book",
                  part != null
                     && part.data().layout() == RecipeBookData.Layout.SURVIVAL
                     && this.minecraft.gui.screen() instanceof JsonUiContainerScreen json
                     && !visible(json, "recipe_book")
               );
               this.check(
                  "SetPlayerInventoryOptions sent: the inventory only (" + this.options().layout() + ")",
                  this.options().layout() == InventoryLayout.INVENTORY_ONLY
               );
            }
         );
         this.screenshot("survival");
         this.layoutToggle("recipe_book_layout_toggle_panel");
         sleep(700L);
         this.run(
            () -> this.check(
               "SetPlayerInventoryOptions sent: the recipe book again (" + this.options().layout() + ")", this.options().layout() == InventoryLayout.DEFAULT
            )
         );
         this.run(() -> this.minecraft.player.closeContainer());
         sleep(700L);
      }
   }

   private void craftingTable() {
      BlockPos[] table = new BlockPos[1];
      this.run(() -> table[0] = this.minecraft.player.blockPosition().offset(2, 0, -1));
      this.commands(
         "clear @s",
         "recipe give @s *",
         "give @s oak_planks 8",
         "setblock " + table[0].getX() + " " + table[0].getY() + " " + table[0].getZ() + " crafting_table"
      );
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
      boolean open = this.onClient(() -> {
         JsonUiRecipeBook part = this.part();
         this.check("the crafting table shows the recipe book", part != null);
         return part != null;
      });
      if (open) {
         this.search("chest");
         this.screenshot("table");
         this.step("auto-craft a chest on the crafting table (shift-click, eight planks)", () -> this.clickCell("minecraft:chest", true));
         this.run(() -> this.check("a chest after the auto-craft", this.count("chest") == 1));
         this.commands("give @s oak_planks 8");
         sleep(1000L);
         this.search("crafting table");
         this.step("auto-craft two crafting tables (shift-click, eight planks)", () -> this.clickCell("minecraft:crafting_table", true));
         this.run(() -> this.check("two crafting tables after the auto-craft", this.count("crafting_table") == 2));
         this.commands(hasItem("crafting_table", 2), hasItem("oak_planks", 0));
         this.extraResults("cake", "cake", "bucket", 3, "give @s milk_bucket 3", "give @s sugar 2", "give @s egg 1", "give @s wheat 3");
         this.extraResults("honey block", "honey_block", "glass_bottle", 4, "give @s honey_bottle 4");
      }

      this.run(() -> this.minecraft.player.closeContainer());
      sleep(700L);
      this.commands(hasItem("chest", 1), "setblock " + table[0].getX() + " " + table[0].getY() + " " + table[0].getZ() + " air");
   }

   private void extraResults(String search, String made, String container, int containers, String... gives) {
      this.commands(gives);
      sleep(1000L);
      this.search(search);
      this.step("auto-craft " + made + " (shift-click, a recipe with more than one result)", () -> this.clickCell("minecraft:" + made, true));
      this.run(() -> {
         this.check(made + " after the auto-craft", this.count(made) == 1);
         log("{} came back as the second result: {} of {}", container, this.count(container), containers);
      });
      this.commands(hasItem(made, 1), hasItem(container, containers));
   }

   @Nullable
   private JsonUiRecipeBook part() {
      return this.minecraft.gui.screen() instanceof JsonUiContainerScreen json ? json.recipeBook() : null;
   }

   private static boolean visible(JsonUiContainerScreen json, String name) {
      UiControl control = json.ui().instance().find(name);
      return control != null && control.visible();
   }

   private void search(String text) {
      this.run(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen json) {
            UiControl var4 = first(json.ui().instance().find("search_tab_holder"), UiType.TOGGLE);
            JsonUiRecipeBook part = json.recipeBook();
            if (var4 != null && part != null && part.data().tab() != RecipeBookData.Tab.SEARCH) {
               press(json, centre(var4), 1, 0);
            }
         }
      });
      sleep(300L);
      this.run(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen json) {
            UiControl panel = json.ui().instance().find("search_and_filter_panel");
            UiControl box = panel == null ? null : first(panel, UiType.EDIT_BOX);
            if (box == null) {
               this.check("the search box is there", false);
            } else {
               press(json, centre(box), 1, 0);
               int length = json.ui().input().text(box).length();

               for (int i = 0; i < length; i++) {
                  json.keyPressed(new KeyEvent(42, 8, 0));
               }

               text.codePoints().forEach(codePoint -> json.charTyped(new CharacterEvent(codePoint)));
               json.ui().input().focus(null);
            }
         }
      });
      sleep(700L);
   }

   private boolean clickCell(String result, boolean shift) {
      int index = this.onClient(() -> {
         JsonUiRecipeBook part = this.part();
         if (part == null) {
            return -1;
         } else {
            List<RecipeBook.Cell> cells = part.cells();

            for (int i = 0; i < cells.size(); i++) {
               RecipeBook.Entry entry = cells.get(i).entry();
               if (entry != null && entry.result().getDefinition().getIdentifier().equals(result)) {
                  return i;
               }
            }

            return -1;
         }
      });
      if (index < 0) {
         this.check("a cell makes " + result, false);
         return false;
      } else {
         boolean[] scrolled = new boolean[1];
         this.run(() -> {
            if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen json) {
               UiControl grid = grid(json);
               scrolled[0] = grid != null && json.ui().input().revealItem(grid, index);
            }
         });
         sleep(scrolled[0] ? 400L : 50L);
         return this.onClient(
            () -> {
               if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen json) {
                  UiControl grid = grid(json);
                  UiControl cell = grid == null ? null : grid.gridItem(index);
                  if (cell == null) {
                     this.check("the cell of " + result + " is in view", false);
                     return false;
                  } else {
                     JsonUiRecipeBook part = this.part();
                     RecipeBook.Entry entry = part == null ? null : part.cells().get(index).entry();
                     log(
                        "{} {} (cell {}, craftable {} x{}, recipe {} net id {}, ingredients {})",
                        shift ? "shift-click" : "click",
                        result,
                        index,
                        entry != null && entry.craftable(),
                        entry == null ? 0 : entry.times(),
                        entry == null ? "-" : entry.recipe().id(),
                        entry == null ? 0 : entry.recipe().netId(),
                        entry == null ? "-" : entry.recipe().ingredients()
                     );
                     press(json, centre(cell), 1, shift ? 1 : 0);
                     return true;
                  }
               } else {
                  return false;
               }
            }
         );
      }
   }

   @Nullable
   private static UiControl grid(JsonUiContainerScreen json) {
      UiControl[] found = new UiControl[]{null};
      UiControl root = json.ui().instance().root();
      if (root != null) {
         root.walk(control -> {
            if (found[0] == null && control.type() == UiType.GRID && "recipe_book".equals(control.collectionName()) && control.visible()) {
               found[0] = control;
            }
         });
      }

      return found[0];
   }

   private boolean takeOutput() {
      return this.onClient(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen json) {
            float[] at = json.cellCentre(0);
            if (at == null) {
               this.check("the output cell is shown", false);
               return false;
            } else {
               press(json, at, 1, 1);
               return true;
            }
         } else {
            return false;
         }
      });
   }

   private boolean layoutToggle(String panel) {
      return this.onClient(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen json) {
            UiControl holder = json.ui().instance().find(panel);
            UiControl toggle = holder == null ? null : first(holder, UiType.TOGGLE);
            if (toggle == null) {
               this.check("a toggle in " + panel, false);
               return false;
            } else {
               press(json, centre(toggle), 1, 0);
               return true;
            }
         } else {
            return false;
         }
      });
   }

   @Nullable
   private static UiControl first(@Nullable UiControl from, UiType type) {
      if (from == null) {
         return null;
      } else {
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
   }

   private static void press(JsonUiContainerScreen screen, float[] at, int button, int modifiers) {
      MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(button, modifiers));
      screen.mouseMoved(at[0], at[1]);
      screen.mouseClicked(event, false);
      screen.mouseReleased(event);
   }

   private static float[] centre(UiControl control) {
      return new float[]{control.x() + control.width() / 2.0F, control.y() + control.height() / 2.0F};
   }

   private void step(String what, Supplier<Boolean> action) {
      int accepted = StackRequests.acceptedTotal();
      int rejected = StackRequests.rejectedTotal();
      boolean made = action.get();
      sleep(1200L);
      this.run(() -> {
         int ok = StackRequests.acceptedTotal() - accepted;
         int refused = StackRequests.rejectedTotal() - rejected;
         log("{}: requests accepted {}, rejected {}, pending {}", what, ok, refused, this.requests.pendingCount());
         this.check(what + " (accepted " + ok + ", rejected " + refused + ")", made && ok > 0 && refused == 0);
      });
   }

   private void check(String what, boolean pass) {
      this.checks++;
      if (pass) {
         this.passed++;
      }

      log("{}: {}", what, pass ? "PASS" : "FAIL");
   }

   private void done() {
      this.run(
         () -> log(
            "done, {} of {} checks passed; requests accepted {}, rejected {}",
            this.passed,
            this.checks,
            StackRequests.acceptedTotal(),
            StackRequests.rejectedTotal()
         )
      );
   }

   private InventoryOptions options() {
      BedrockExtras extras = BedrockExtras.active();
      return extras == null ? InventoryOptions.DEFAULT : extras.inventoryOptions();
   }

   private int count(String path) {
      int total = 0;
      Inventory inventory = this.minecraft.player.getInventory();

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(path)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private static String hasItem(String item, int quantity) {
      return "testfor @s[hasitem={item=" + item + ",quantity=" + quantity + "}]";
   }

   private void screenshot(String name) {
      this.run(
         () -> Screenshot.grab(
            this.minecraft.gameDirectory,
            "recipe-book-" + name + (this.minecraft.gui.screen() instanceof JsonUiContainerScreen ? "-jsonui" : "") + ".png",
            this.minecraft.gameRenderer.mainRenderTarget(),
            1,
            message -> {}
         )
      );
      sleep(300L);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> {
            log("command /{}", command);
            this.minecraft.getConnection().sendCommand(command);
         });
         sleep(500L);
      }
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
      BedrockNative.LOGGER.info("Dev recipe book test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
