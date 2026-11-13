package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.ui.ContainerData;
import net.bedrocknative.ui.ContainerSlots;
import net.bedrocknative.ui.RecipeBookData;
import net.bedrocknative.world.recipe.BookRecipe;
import net.bedrocknative.world.recipe.CraftPlanner;
import net.bedrocknative.world.recipe.RecipeBook;
import net.bedrocknative.world.state.InventoryOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SlotDisplay.Empty;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryLayout;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabLeft;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public final class JsonUiRecipeBook implements RecipeBookData.Actions {
   private static final int GHOST_FADE = -1718908021;
   private static final long GHOST_CYCLE_MS = 1000L;
   private final BedrockRecipeBook session;
   private final RecipeBook book;
   private final RecipeBookData data;
   private final AbstractContainerMenu menu;
   private final boolean table;
   private final Runnable exit;
   private List<RecipeBook.Cell> cells = List.of();
   private int cellsVersion = -1;
   private @Nullable ItemStack[] shownStacks = new ItemStack[0];
   private final ItemData[] sourceItems = new ItemData[45];
   private List<List<ItemStack>> ghost = List.of();
   private List<ItemStack> ghostResult = List.of();

   private JsonUiRecipeBook(BedrockRecipeBook session, RecipeBookData data, AbstractContainerMenu menu, boolean table, Runnable exit) {
      this.session = session;
      this.book = session.book();
      this.data = data;
      this.menu = menu;
      this.table = table;
      this.exit = exit;
      data.actions(this);
   }

   static @Nullable RecipeBookData data(ContainerSlots.Kind kind, ContainerData slots) {
      return (kind == ContainerSlots.Kind.INVENTORY || kind == ContainerSlots.Kind.CRAFTING_TABLE) && BedrockRecipeBook.active() != null
         ? new RecipeBookData(slots)
         : null;
   }

   static @Nullable JsonUiRecipeBook of(@Nullable RecipeBookData data, AbstractContainerMenu menu, ContainerSlots.Kind kind, Runnable exit) {
      BedrockRecipeBook session = BedrockRecipeBook.active();
      return data != null && session != null ? new JsonUiRecipeBook(session, data, menu, kind == ContainerSlots.Kind.CRAFTING_TABLE, exit) : null;
   }

   private int gridSize() {
      return this.table ? 3 : 2;
   }

   void opened() {
      InventoryOptions options = this.session.options();
      InventoryLayout layout = this.table && options.craftingLayout() != InventoryLayout.NONE ? options.craftingLayout() : options.layout();
      this.data.layout(layout == InventoryLayout.INVENTORY_ONLY ? RecipeBookData.Layout.SURVIVAL : RecipeBookData.Layout.RECIPE_BOOK);
      RecipeBookData.Tab tab = tab(options.leftTab());
      this.data.tab(tab);
      this.data.filtering(options.filtering());
      this.data.search("");
      this.book.station("crafting_table", this.gridSize());
      this.book.tab(RecipeBook.Tab.valueOf(tab.name()));
      this.book.filtering(options.filtering());
      this.book.search("");
      this.book.select(null);
      this.cellsVersion = -1;
      this.clearGhost();
   }

   private static RecipeBookData.Tab tab(@Nullable InventoryTabLeft left) {
      return switch (left == null ? InventoryTabLeft.RECIPE_SEARCH : left) {
         case RECIPE_CONSTRUCTION -> RecipeBookData.Tab.CONSTRUCTION;
         case RECIPE_EQUIPMENT -> RecipeBookData.Tab.EQUIPMENT;
         case RECIPE_ITEMS -> RecipeBookData.Tab.ITEMS;
         case RECIPE_NATURE -> RecipeBookData.Tab.NATURE;
         default -> RecipeBookData.Tab.SEARCH;
      };
   }

   private static InventoryTabLeft left(RecipeBookData.Tab tab) {
      return switch (tab) {
         case CONSTRUCTION -> InventoryTabLeft.RECIPE_CONSTRUCTION;
         case EQUIPMENT -> InventoryTabLeft.RECIPE_EQUIPMENT;
         case ITEMS -> InventoryTabLeft.RECIPE_ITEMS;
         case NATURE -> InventoryTabLeft.RECIPE_NATURE;
         case SEARCH -> InventoryTabLeft.RECIPE_SEARCH;
      };
   }

   void read() {
      StackRequests requests = this.session.requests();
      List<BedrockSlot> grid = StackRequests.gridSlots(this.gridSize());
      if (!this.ghost.isEmpty() && gridFilled(requests, grid)) {
         this.clearGhost();
      }

      if (this.data.layout() != RecipeBookData.Layout.SURVIVAL) {
         this.readSources(requests, grid);
         if (this.book.version() != this.cellsVersion) {
            this.cellsVersion = this.book.version();
            this.cells = this.book.cells();
            this.shownStacks = new ItemStack[this.cells.size()];
            List<RecipeBookData.Cell> shownCells = new ArrayList<>(this.cells.size());

            for (RecipeBook.Cell cell : this.cells) {
               RecipeBook.Entry entry = cell.entry();
               int items = entry == null ? 0 : entry.craftableItems();
               shownCells.add(
                  new RecipeBookData.Cell(cell.name(), kind(cell.kind()), cell.craftable(), items > 1 ? Integer.toString(items) : "", cell.selected())
               );
            }

            this.data.cells(shownCells);
            this.data.needsCraftingTable(!this.table && this.book.needsCraftingTable());
         }
      }
   }

   private static boolean gridFilled(StackRequests requests, List<BedrockSlot> grid) {
      for (BedrockSlot slot : grid) {
         if (!requests.inventory().get(slot).isNull()) {
            return true;
         }
      }

      return false;
   }

   private void readSources(StackRequests requests, List<BedrockSlot> grid) {
      List<BedrockSlot> player = RecipeBookCrafts.playerSlots();
      int size = player.size() + grid.size();
      int index = 0;

      for (BedrockSlot slot : player) {
         this.sourceItems[index++] = requests.inventory().get(slot);
      }

      for (BedrockSlot slot : grid) {
         this.sourceItems[index++] = requests.inventory().get(slot);
      }

      List<ItemData> held = this.book.sources();
      boolean same = held.size() == size;

      for (int i = 0; same && i < size; i++) {
         same = this.sourceItems[i].equals(held.get(i));
      }

      if (!same) {
         this.book.sources(Arrays.asList(Arrays.copyOf(this.sourceItems, size)));
      }
   }

   private static RecipeBookData.Kind kind(RecipeBook.Kind kind) {
      return RecipeBookData.Kind.valueOf(kind.name());
   }

   public RecipeBookData data() {
      return this.data;
   }

   public List<RecipeBook.Cell> cells() {
      return this.cells;
   }

   public ItemStack stack(int index) {
      if (index >= 0 && index < this.cells.size() && index < this.shownStacks.length) {
         ItemStack stack = this.shownStacks[index];
         if (stack == null) {
            stack = this.session.stack(this.cells.get(index).icon());
            this.shownStacks[index] = stack;
         }

         return stack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   boolean head(int index) {
      return index >= 0 && index < this.cells.size() && this.cells.get(index).entry() == null;
   }

   Component name(int index) {
      return Component.literal(index >= 0 && index < this.cells.size() ? this.cells.get(index).name() : "");
   }

   public boolean click(int index, int button, boolean shift) {
      if (index >= 0 && index < this.cells.size() && (button == 1 || button == 3)) {
         RecipeBook.Cell cell = this.cells.get(index);
         RecipeBook.Entry entry = cell.entry();
         if (entry == null) {
            this.book.toggle(cell.group());
            return true;
         }

         this.book.select(entry);
         this.clearGhost();
         BookRecipe recipe = entry.recipe();
         StackRequests requests = this.session.requests();
         if (!entry.fits()) {
            BedrockNative.LOGGER.info("Recipe book: {} needs a crafting table", recipe.id());
            return true;
         }

         if (!entry.craftable()) {
            BedrockNative.LOGGER.debug("Recipe book: {} chosen, the items are missing", recipe.id());
            this.showGhost(recipe);
            return true;
         }

         if (shift && button == 1) {
            int crafts = RecipeBookCrafts.autoCraft(
               requests, recipe, this.gridSize(), Integer.MAX_VALUE, requests.crafting()::matches, BedrockRecipeBook::maxStack
            );
            BedrockNative.LOGGER.info("Recipe book: auto-craft of {} (net id {}) x{}", new Object[]{recipe.id(), recipe.netId(), crafts});
         } else {
            boolean placed = RecipeBookCrafts.place(requests, recipe, this.gridSize(), requests.crafting()::matches, BedrockRecipeBook::maxStack);
            BedrockNative.LOGGER.info("Recipe book: {} {} into the grid", recipe.id(), placed ? "placed" : "not placed");
         }

         requests.inventory().showMenu(this.menu);
         return true;
      } else {
         return false;
      }
   }

   private void showGhost(BookRecipe recipe) {
      int[][] cells = CraftPlanner.cells(recipe, this.gridSize());
      RecipeDisplay display = JavaRecipeBookFeed.display(recipe);
      Minecraft minecraft = Minecraft.getInstance();
      if (cells != null && display != null && minecraft.level != null) {
         ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
         List<List<ItemStack>> shown = new ArrayList<>();

         for (int i = 0; i < this.gridSize() * this.gridSize(); i++) {
            shown.add(List.of());
         }

         List<BookRecipe.Unit> units = recipe.units();

         List<SlotDisplay> slots = switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
            case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
            default -> List.of();
         };
         int item = 0;

         for (int u = 0; u < units.size(); u++) {
            for (int cell : cells[u]) {
               SlotDisplay slot = recipe.shaped() ? slots.get(units.get(u).index()) : (item < slots.size() ? slots.get(item) : Empty.INSTANCE);
               item++;
               shown.set(cell, slot.resolveForStacks(context));
            }
         }

         this.ghost = List.copyOf(shown);
         this.ghostResult = display.result().resolveForStacks(context);
      }
   }

   private void clearGhost() {
      this.ghost = List.of();
      this.ghostResult = List.of();
   }

   public boolean ghostShown() {
      return !this.ghost.isEmpty();
   }

   ItemStack ghost(int javaSlot) {
      List<ItemStack> items = javaSlot == 0 ? this.ghostResult : (javaSlot >= 1 && javaSlot <= this.ghost.size() ? this.ghost.get(javaSlot - 1) : List.of());
      return items.isEmpty() ? ItemStack.EMPTY : items.get((int)(Util.getMillis() / 1000L % items.size()));
   }

   void drawGhost(GuiGraphicsExtractor graphics, IntFunction<float @Nullable []> centre) {
      if (!this.ghost.isEmpty()) {
         for (int javaSlot = 0; javaSlot <= this.ghost.size(); javaSlot++) {
            ItemStack stack = this.ghost(javaSlot);
            float[] at = !stack.isEmpty() && this.menu.getSlot(javaSlot).getItem().isEmpty() ? centre.apply(javaSlot) : null;
            if (at != null) {
               int x = Math.round(at[0]) - 8;
               int y = Math.round(at[1]) - 8;
               graphics.fakeItem(stack, x, y);
               graphics.fill(x, y, x + 16, y + 16, -1718908021);
            }
         }
      }
   }

   @Override
   public void tab(RecipeBookData.Tab tab) {
      this.data.tab(tab);
      this.book.tab(RecipeBook.Tab.valueOf(tab.name()));
      InventoryOptions options = this.session.options();
      this.session.options(new InventoryOptions(left(tab), options.rightTab(), options.filtering(), options.layout(), options.craftingLayout()));
   }

   @Override
   public void layout(RecipeBookData.Layout layout) {
      this.data.layout(layout);
      InventoryLayout chosen = layout == RecipeBookData.Layout.SURVIVAL ? InventoryLayout.INVENTORY_ONLY : InventoryLayout.DEFAULT;
      InventoryOptions options = this.session.options();
      this.session
         .options(
            this.table
               ? new InventoryOptions(options.leftTab(), options.rightTab(), options.filtering(), options.layout(), chosen)
               : new InventoryOptions(options.leftTab(), options.rightTab(), options.filtering(), chosen, options.craftingLayout())
         );
   }

   @Override
   public void filtering(boolean on) {
      this.data.filtering(on);
      this.book.filtering(on);
      InventoryOptions options = this.session.options();
      this.session.options(new InventoryOptions(options.leftTab(), options.rightTab(), on, options.layout(), options.craftingLayout()));
   }

   @Override
   public void search(String text) {
      this.data.search(text);
      this.book.search(text);
   }

   @Override
   public void exit() {
      this.exit.run();
   }
}
