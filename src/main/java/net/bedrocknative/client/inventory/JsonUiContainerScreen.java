package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.inventory.ContainerScreenAccessor;
import net.bedrocknative.client.ui.ItemSource;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.client.ui.MenuNotice;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.ContainerData;
import net.bedrocknative.ui.ContainerScreens;
import net.bedrocknative.ui.ContainerSlots;
import net.bedrocknative.ui.PaperDoll;
import net.bedrocknative.ui.RecipeBookData;
import net.bedrocknative.ui.TradeData;
import net.bedrocknative.ui.UiBinding;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDataSource;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiDrawList;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiTreeDump;
import net.bedrocknative.ui.UiType;
import net.bedrocknative.ui.UiValue;
import net.bedrocknative.ui.WorkstationData;
import net.bedrocknative.world.item.BedrockItemTags;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.recipe.RecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

public final class JsonUiContainerScreen
   extends AbstractContainerScreen<AbstractContainerMenu>
   implements ContainerData.Controller,
   ItemSource,
   JsonUiHost,
   JsonUiSlots {
   private static final int STORAGE_STEPS = 1000;
   private static final float DOLL_OFFSET_Y = 0.0625F;
   private final JsonUiScreen ui;
   private final ContainerData data;
   private final String screen;
   private final ContainerSlots slots;
   private final List<List<UiControl>> cells;
   private final ItemStack[] shown;
   private ItemStack shownCarried = ItemStack.EMPTY;
   private long cellsVersion = -1L;
   @Nullable
   private UiControl cursorIcon;
   private boolean uiPressed;
   private boolean opened;
   private double mouseX = Double.NaN;
   private double mouseY = Double.NaN;
   private double hitX = Double.NaN;
   private double hitY = Double.NaN;
   @Nullable
   private UiDrawList hitLayout;
   @Nullable
   private Slot hit;
   @Nullable
   private final JsonUiWorkstation station;
   @Nullable
   private final JsonUiRecipeBook recipeBook;
   private final MenuNotice notice = new MenuNotice();
   @Nullable
   private JsonUiTrading trading;
   private final Set<String> loggedCellMappings = new HashSet<>();
   @Nullable
   private String serverMenu;
   private long firstFrameAt = -1L;
   private boolean serverMenuNoted;
   private static final int NOTED_SLOTS = 54;
   private static final long NOTE_AFTER_MILLIS = 1000L;
   private static final JsonUiContainerScreen.Described[] DESCRIBED = new JsonUiContainerScreen.Described[64];
   private static int describedNext;

   private JsonUiContainerScreen(
      AbstractContainerMenu menu, Inventory inventory, Component title, JsonUiScreen ui, ContainerData data, String screen, @Nullable RecipeBookData book
   ) {
      super(menu, inventory, title);
      this.ui = ui;
      this.data = data;
      this.screen = screen;
      this.slots = data.slots();
      this.cells = new ArrayList<>(menu.slots.size());

      for (int i = 0; i < menu.slots.size(); i++) {
         this.cells.add(new ArrayList<>(1));
      }

      this.shown = new ItemStack[menu.slots.size()];
      Arrays.fill(this.shown, ItemStack.EMPTY);
      this.station = data.station() instanceof WorkstationData workstation ? new JsonUiWorkstation(Minecraft.getInstance(), menu, workstation) : null;
      this.recipeBook = JsonUiRecipeBook.of(book, menu, this.slots.kind(), this::exit);
      data.controller(this);
      ui.items(this);
   }

   @Nullable
   static JsonUiContainerScreen create(
      MenuType<?> type,
      Minecraft minecraft,
      int containerId,
      Component title,
      ContainerScreens.Choice choice,
      @Nullable String customName,
      @Nullable Workstations trades,
      @Nullable Long owner
   ) {
      return minecraft.player == null
         ? null
         : create(minecraft, type.create(containerId, minecraft.player.getInventory()), title, choice, customName, trades, owner);
   }

   @Nullable
   static JsonUiContainerScreen inventory(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      return player == null
         ? null
         : create(minecraft, player.inventoryMenu, Component.translatable("container.crafting"), ContainerScreens.inventory(), null, null, null);
   }

   @Nullable
   private static JsonUiContainerScreen create(
      Minecraft minecraft,
      AbstractContainerMenu menu,
      Component title,
      ContainerScreens.Choice choice,
      @Nullable String customName,
      @Nullable Workstations trades,
      @Nullable Long owner
   ) {
      if (minecraft.player != null && fits(menu, choice.slots())) {
         boolean trading = choice.slots().kind() == ContainerSlots.Kind.MERCHANT;
         Workstations.TradeView view = trades == null ? null : trades.tradeView();
         if (!trading || view != null && view.containerId() == menu.containerId) {
            if (trading && view.offers().size() > 24) {
               BedrockNative.LOGGER.info("JSON UI leaves a trade of {} offers to Java's screen (more than {})", view.offers().size(), 24);
               return null;
            } else {
               UiDefinitionSet definitions = JsonUi.definitions();
               if (definitions == null) {
                  return null;
               } else {
                  ContainerData data = new ContainerData(choice.slots());
                  data.owner(owner);
                  TradeData tradeData = null;
                  if (trading) {
                     tradeData = new TradeData(BedrockText::lookupKey);
                     data.station(tradeData);
                     JsonUiTrading.fill(tradeData, view, trades.traderExperience(view.trader()));
                  } else {
                     data.station(WorkstationData.of(choice.slots().kind()));
                  }

                  RecipeBookData book = JsonUiRecipeBook.data(choice.slots().kind(), data);
                  Map<String, UiValue> variables = customName != null
                     ? ContainerData.variables(customName, false)
                     : ContainerData.variables(choice.titleKey(), true);
                  JsonUiScreen ui = JsonUiScreen.create(definitions, choice.screen(), variables, (UiDataSource)(book != null ? book : data));
                  if (ui != null && JsonUi.use(choice.parityKey(), ui.instance())) {
                     ui.instance().problems().log("container " + choice.screen());
                     String titled = customName != null ? customName : choice.titleKey();
                     ui.diagnostics("container " + choice.screen(), choice.screen() + " " + (titled.length() > 64 ? titled.substring(0, 64) : titled));
                     if (ui.instance().cutShort()) {
                        BedrockNative.LOGGER.warn("JSON UI container screen {} hit the engine's limits; the Java screen shows it", choice.screen());
                        return null;
                     } else {
                        JsonUiContainerScreen screen = new JsonUiContainerScreen(menu, minecraft.player.getInventory(), title, ui, data, choice.screen(), book);
                        if (tradeData != null && menu instanceof MerchantMenu merchant) {
                           screen.trading = new JsonUiTrading(minecraft, screen, merchant, tradeData, trades, view);
                        }

                        if (customName != null || owner != null) {
                           screen.serverMenu = customName != null ? "\"" + UiTreeDump.escape(customName) + "\"" : "by default (actor " + owner + ")";
                        }

                        return screen;
                     }
                  } else {
                     return null;
                  }
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean fits(AbstractContainerMenu menu, ContainerSlots slots) {
      boolean kind = switch (slots.kind()) {
         case CHEST -> menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
         case INVENTORY -> menu instanceof InventoryMenu;
         case CRAFTING_TABLE -> menu instanceof CraftingMenu;
         case FURNACE -> menu instanceof AbstractFurnaceMenu;
         case BREWING_STAND -> menu instanceof BrewingStandMenu;
         case ANVIL -> menu instanceof AnvilMenu;
         case ENCHANTING -> menu instanceof EnchantmentMenu;
         case GRINDSTONE -> menu instanceof GrindstoneMenu;
         case STONECUTTER -> menu instanceof StonecutterMenu;
         case LOOM -> menu instanceof LoomMenu;
         case SMITHING -> menu instanceof SmithingMenu;
         case CARTOGRAPHY -> menu instanceof CartographyTableMenu;
         case BEACON -> menu instanceof BeaconMenu;
         case MERCHANT -> menu instanceof MerchantMenu;
      };
      return kind && menu.slots.size() == slots.menuSize();
   }

   public static boolean showsInventory(@Nullable Screen screen) {
      return screen instanceof JsonUiContainerScreen json && json.getMenu() instanceof InventoryMenu;
   }

   @Override
   public JsonUiScreen ui() {
      return this.ui;
   }

   public String screen() {
      return this.screen;
   }

   public ContainerData data() {
      return this.data;
   }

   void fallback(Runnable action) {
      this.ui.fallback(action);
   }

   protected void init() {
      super.init();
      this.ui.resize(this.width, this.height);
      if (!this.opened) {
         this.ui.event("screen.entrance_push");
         this.opened = true;
         if (this.station != null) {
            this.station.opened();
         }

         if (this.recipeBook != null) {
            this.recipeBook.opened();
         }
      }
   }

   public void removed() {
      super.removed();
      if (this.station != null) {
         this.station.removed();
      }
   }

   protected void containerTick() {
      LocalPlayer player = this.minecraft.player;
      if (this.menu instanceof InventoryMenu && player != null && player.hasInfiniteMaterials()) {
         this.minecraft
            .gui
            .setScreen(new CreativeModeInventoryScreen(player, player.connection.enabledFeatures(), (Boolean)this.minecraft.options.operatorItemsTab().get()));
      }

      if (this.station != null) {
         this.station.tick();
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.ui.input().focused() != null) {
         if (this.ui.keyPressed(event)) {
            return true;
         }

         if (!event.isEscape()) {
            return true;
         }
      }

      return super.keyPressed(event);
   }

   public boolean charTyped(CharacterEvent event) {
      return this.ui.input().focused() != null && this.ui.charTyped(event) || super.charTyped(event);
   }

   public boolean preeditUpdated(@Nullable PreeditEvent event) {
      return this.ui.input().focused() != null && this.ui.preeditUpdated(event) || super.preeditUpdated(event);
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.minecraft.gui.hud.extractDeferredSubtitles();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      if (!this.ui.failed()) {
         if ((int)this.mouseX != mouseX || (int)this.mouseY != mouseY || Double.isNaN(this.mouseX)) {
            this.mouseMoved(mouseX, mouseY);
         }

         this.hover(this.slotAt(mouseX, mouseY));
         this.refresh();
         this.noteServerMenu();
         this.ui.render(graphics);
         if (this.recipeBook != null) {
            this.recipeBook.drawGhost(graphics, this::cellCentre);
         }

         if (this.cursorIcon == null || !this.cursorIcon.visible()) {
            this.extractCarriedItem(graphics, mouseX, mouseY);
         }

         if (this.hoverTextShown()) {
            this.extractTooltip(graphics, mouseX, mouseY);
         }

         if (this.recipeBook != null && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot == null) {
               this.recipeTooltip(graphics, mouseX, mouseY);
            } else if (!this.hoveredSlot.hasItem() && !this.recipeBook.ghost(this.hoveredSlot.index).isEmpty()) {
               graphics.setTooltipForNextFrame(this.font, this.recipeBook.ghost(this.hoveredSlot.index), mouseX, mouseY);
            }
         }

         if (this.trading != null && this.menu.getCarried().isEmpty()) {
            this.trading.tooltip(graphics, this.font, mouseX, mouseY);
         }

         this.notice.render(graphics, this.width);
      }
   }

   private void noteServerMenu() {
      if (this.serverMenu != null && !this.serverMenuNoted && BedrockNative.LOGGER.isDebugEnabled()) {
         long now = System.currentTimeMillis();
         if (this.firstFrameAt < 0L) {
            this.firstFrameAt = now;
         } else if (now - this.firstFrameAt >= 1000L) {
            this.serverMenuNoted = true;
            List<String> items = new ArrayList<>();
            int own = Math.min(54, Math.max(0, this.menu.slots.size() - 36));

            for (int i = 0; i < own; i++) {
               ItemStack stack = ((Slot)this.menu.slots.get(i)).getItem();
               if (!stack.isEmpty()) {
                  items.add(
                     i
                        + ": "
                        + stack.getCount()
                        + " "
                        + BuiltInRegistries.ITEM.getKey(stack.getItem())
                        + " \""
                        + UiTreeDump.escape(describe(stack).hoverText())
                        + "\""
                  );
               }
            }

            List<String> packs = this.ui.instance().definitions().serverPacks(this.ui.instance().contributingPacks());
            BedrockNative.LOGGER
               .debug(
                  "JSON UI container {} titled {}: server packs in its screen {}; {} of {} slots hold items {}",
                  new Object[]{this.screen, this.serverMenu, packs.isEmpty() ? "none" : packs, items.size(), own, items}
               );
         }
      }
   }

   public boolean hoverTextShown() {
      Slot slot = this.hoveredSlot;
      return slot != null && ContainerData.showsHoverText(this.visibleCell(slot.index));
   }

   private void recipeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      UiControl control = this.ui.topmost(mouseX, mouseY);
      if (control != null && "recipe_book".equals(control.itemCollection())) {
         int index = control.itemIndex();
         if (this.recipeBook.head(index)) {
            graphics.setTooltipForNextFrame(this.font, this.recipeBook.name(index), mouseX, mouseY);
         } else {
            ItemStack stack = this.recipeBook.stack(index);
            if (!stack.isEmpty()) {
               graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
            }
         }
      }
   }

   @Nullable
   public JsonUiRecipeBook recipeBook() {
      return this.recipeBook;
   }

   private void hover(@Nullable Slot slot) {
      Slot before = this.hoveredSlot;
      this.hoveredSlot = slot;
      if (before != null && before != slot) {
         this.accessor().bedrocknative$onStopHovering(before);
      }
   }

   private ContainerScreenAccessor accessor() {
      return (ContainerScreenAccessor)this;
   }

   @Nullable
   @Override
   public Slot slotAt(double x, double y) {
      this.cells();
      UiDrawList layout = this.ui.instance().drawList();
      if (x == this.hitX && y == this.hitY && layout == this.hitLayout) {
         return this.hit;
      } else {
         UiControl control = this.ui.topmost(x, y);
         int javaSlot = control == null ? -1 : this.javaSlot(control);
         Slot slot = javaSlot < 0 ? null : this.menu.getSlot(javaSlot);
         this.hit = slot != null && slot.isActive() ? slot : null;
         this.hitX = x;
         this.hitY = y;
         this.hitLayout = layout;
         return this.hit;
      }
   }

   @Nullable
   public float[] cellCentre(int javaSlot) {
      UiControl cell = this.visibleCell(javaSlot);
      return cell == null ? null : JsonUiScreen.toGui(cell.x() + cell.width() / 2.0F, cell.y() + cell.height() / 2.0F);
   }

   public int cellCount() {
      this.cells();
      int count = 0;

      for (int javaSlot = 0; javaSlot < this.cells.size(); javaSlot++) {
         if (this.visibleCell(javaSlot) != null) {
            count++;
         }
      }

      return count;
   }

   @Nullable
   private UiControl visibleCell(int javaSlot) {
      this.cells();
      if (javaSlot >= 0 && javaSlot < this.cells.size()) {
         for (UiControl cell : this.cells.get(javaSlot)) {
            if (cell.visible()) {
               return cell;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   public float[] outsidePoint() {
      float[][] candidates = new float[][]{{2.0F, 2.0F}, {this.width - 3, 2.0F}, {2.0F, this.height - 3}, {this.width - 3, this.height - 3}};

      for (float[] point : candidates) {
         if (this.slotAt(point[0], point[1]) == null && ContainerData.outside(this.ui.topmost(point[0], point[1]))) {
            return point;
         }
      }

      return null;
   }

   public List<String> labels() {
      List<String> texts = new ArrayList<>();
      UiControl root = this.ui.instance().root();
      if (root != null) {
         root.walk(control -> {
            if (control.type() == UiType.LABEL && control.visible() && !control.text().isEmpty()) {
               texts.add(control.text());
            }
         });
      }

      return texts;
   }

   public List<String> trace() {
      this.cells();
      List<String> lines = new ArrayList<>();
      lines.add("screen " + this.screen + ": " + this.menu.slots.size() + " Java slots, " + this.cellCount() + " with a visible cell");

      for (int javaSlot = 0; javaSlot < this.menu.slots.size(); javaSlot++) {
         if (this.visibleCell(javaSlot) == null) {
            lines.add(
               "Java slot "
                  + javaSlot
                  + " has no visible cell ("
                  + this.cells.get(javaSlot).size()
                  + " cells, stack "
                  + this.menu.getSlot(javaSlot).getItem()
                  + ")"
            );
         }
      }

      BedrockRecipeBook recipes = BedrockRecipeBook.active();
      if (recipes != null) {
         List<RecipeBook.Entry> entries = recipes.book().entries();
         lines.add(
            "recipe book: "
               + entries.size()
               + " entries, "
               + entries.stream().filter(RecipeBook.Entry::craftable).count()
               + " craftable from "
               + recipes.book().sources().size()
               + " source items"
         );
      }

      lines.addAll(UiTreeDump.collections(this.ui.instance(), this.slots::size, "inventory".equals(System.getProperty("bedrocknative.jsonUiTrace")), 400));
      return lines;
   }

   private int javaSlot(UiControl control) {
      int javaSlot = this.slots.javaSlot(control.itemCollection(), control.itemIndex());
      return javaSlot >= 0 && javaSlot < this.cells.size() && !this.cells.get(javaSlot).isEmpty() ? javaSlot : -1;
   }

   private void cells() {
      UiScreenInstance instance = this.ui.instance();
      if (this.cellsVersion != instance.treeVersion()) {
         this.cellsVersion = instance.treeVersion();
         this.cells.forEach(List::clear);
         this.cursorIcon = null;
         UiControl root = instance.root();
         if (root != null) {
            root.walk(
               control -> {
                  UiControl item = item(control);
                  int javaSlot = item == null ? -1 : this.slots.javaSlot(item.itemCollection(), item.collectionIndex());
                  if (javaSlot >= 0 && javaSlot < this.cells.size()) {
                     List<UiControl> copies = this.cells.get(javaSlot);
                     int at = copies.indexOf(item);
                     if (control == item) {
                        copies.add(control);
                     } else if (at >= 0 && item.type() != UiType.INPUT_PANEL && control.type() == UiType.INPUT_PANEL) {
                        copies.set(at, control);
                     }
                  }

                  if (this.cursorIcon == null
                     && control.type() == UiType.CUSTOM
                     && "inventory_item_renderer".equals(control.renderer())
                     && showsCarried(control)) {
                     this.cursorIcon = control;
                  }
               }
            );
         }
      }
   }

   @Nullable
   private static UiControl item(UiControl control) {
      for (UiControl c = control; c != null; c = c.parent()) {
         if (c.collectionIndex() >= 0) {
            return c;
         }
      }

      return null;
   }

   private static boolean showsCarried(UiControl control) {
      for (UiBinding binding : control.bindings()) {
         if ("#inventory_selected_item".equals(binding.name())) {
            return true;
         }
      }

      return false;
   }

   private void refresh() {
      this.cells();
      ItemStack carried = this.menu.getCarried();
      boolean dragging = this.isQuickCrafting && this.quickCraftSlots.size() > 1 && !carried.isEmpty();

      for (int javaSlot = 0; javaSlot < this.cells.size(); javaSlot++) {
         List<UiControl> copies = this.cells.get(javaSlot);
         if (!copies.isEmpty()) {
            Slot slot = this.menu.getSlot(javaSlot);
            ItemStack stack = dragging ? this.preview(slot, carried) : slot.getItem();
            if (!ItemStack.matches(stack, this.shown[javaSlot])) {
               this.shown[javaSlot] = stack.copy();
               UiControl cell = copies.getFirst();
               this.data.slot(cell.itemCollection(), cell.itemIndex(), describe(stack));
            }
         }
      }

      ItemStack cursor = dragging ? carried.copyWithCount(this.accessor().bedrocknative$quickCraftingRemainder()) : carried;
      if (!ItemStack.matches(cursor, this.shownCarried)) {
         this.shownCarried = cursor.copy();
         this.data.carried(describe(cursor));
      }

      switch (this.menu) {
         case AbstractFurnaceMenu furnace:
            this.data.furnace(furnace.getBurnProgress(), furnace.getLitProgress(), furnace.isLit());
            break;
         case BrewingStandMenu brewing:
            this.data.brewing(brewing.getBrewingTicks(), brewing.getTotalBrewingTicks(), brewing.getFuel(), brewing.getTotalFuel());
            break;
         default:
      }

      if (this.station != null) {
         this.station.read();
      }

      if (this.recipeBook != null) {
         this.recipeBook.read();
      }

      if (this.trading != null) {
         this.trading.read();
      }
   }

   private ItemStack preview(Slot slot, ItemStack carried) {
      ItemStack stack = slot.getItem();
      if (this.quickCraftSlots.contains(slot) && AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && this.menu.canDragTo(slot)) {
         int max = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
         int carry = stack.isEmpty() ? 0 : stack.getCount();
         int type = this.accessor().bedrocknative$quickCraftingType();
         return carried.copyWithCount(Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots.size(), type, carried) + carry, max));
      } else {
         return stack;
      }
   }

   public static ContainerData.Slot describe(ItemStack stack) {
      if (stack.isEmpty()) {
         return ContainerData.Slot.EMPTY;
      } else {
         int durabilityTotal = 0;
         int durabilityCurrent = 0;
         if (stack.isDamageableItem()) {
            durabilityTotal = stack.getMaxDamage();
            durabilityCurrent = durabilityTotal - stack.getDamageValue();
         }

         int storageTotal = 0;
         int storageCurrent = 0;
         if (stack.getItem() instanceof BundleItem) {
            storageTotal = 1000;
            storageCurrent = Math.round(BundleItem.getFullnessDisplay(stack) * 1000.0F);
         }

         CustomData data = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         Object name = stack.get(DataComponents.CUSTOM_NAME);
         ContainerData.Slot cached = cachedSlot(stack, stack.getCount(), stack.getDamageValue(), data, name, storageCurrent);
         if (cached != null) {
            return cached;
         } else {
            int lock = BedrockItemTags.bedrockData(stack).getByteOr("item_lock", (byte)0);
            String raw = BedrockItemTags.rawHoverText(stack);
            ContainerData.Slot slot = new ContainerData.Slot(
               stack.getCount(), durabilityTotal, durabilityCurrent, storageTotal, storageCurrent, raw != null ? raw : stack.getHoverName().getString(), lock
            );
            rememberSlot(stack, stack.getCount(), stack.getDamageValue(), data, name, storageCurrent, slot);
            return slot;
         }
      }
   }

   @Nullable
   private static ContainerData.Slot cachedSlot(ItemStack stack, int count, int damage, @Nullable CustomData data, @Nullable Object name, int storage) {
      synchronized (DESCRIBED) {
         for (JsonUiContainerScreen.Described entry : DESCRIBED) {
            if (entry != null
               && entry.stack == stack
               && entry.count == count
               && entry.damage == damage
               && entry.data == data
               && entry.name == name
               && entry.storage == storage) {
               return entry.slot;
            }
         }

         return null;
      }
   }

   private static void rememberSlot(
      ItemStack stack, int count, int damage, @Nullable CustomData data, @Nullable Object name, int storage, ContainerData.Slot slot
   ) {
      synchronized (DESCRIBED) {
         DESCRIBED[describedNext++ & DESCRIBED.length - 1] = new JsonUiContainerScreen.Described(stack, count, damage, data, name, storage, slot);
      }
   }

   @Override
   public ItemStack stack(UiControl renderer) {
      if (renderer == this.cursorIcon) {
         return this.shownCarried;
      } else if (this.recipeBook != null && "recipe_book".equals(renderer.itemCollection())) {
         return this.recipeBook.stack(renderer.itemIndex());
      } else {
         int javaSlot = this.javaSlot(renderer);
         if (javaSlot >= 0) {
            return this.shown[javaSlot];
         } else if (this.trading != null) {
            return this.trading.stack(renderer);
         } else if (this.station != null) {
            return this.station.stack(renderer);
         } else {
            UiValue idAux = this.recipeBook != null ? renderer.bag("#item_id_aux") : null;
            return idAux != null && idAux.isNumber() ? ItemMappings.fromIdAux((int)idAux.asNumber()) : ItemStack.EMPTY;
         }
      }
   }

   @Nullable
   public JsonUiTrading trading() {
      return this.trading;
   }

   void quickMove(int javaSlot) {
      if (javaSlot >= 0 && javaSlot < this.menu.slots.size()) {
         this.slotClicked(this.menu.getSlot(javaSlot), javaSlot, 0, ContainerInput.QUICK_MOVE);
      }
   }

   @Override
   public int seed(UiControl renderer) {
      return Math.max(0, this.javaSlot(renderer));
   }

   @Override
   public boolean draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      if (this.station != null && this.station.draw(graphics, renderer)) {
         return true;
      } else {
         LocalPlayer player = this.minecraft.player;
         if ("live_player_renderer".equals(renderer.renderer()) && player != null && !(renderer.h() <= 0.0F)) {
            PaperDoll.Box box = PaperDoll.of(renderer.x(), renderer.y(), renderer.w(), renderer.h());
            float gazeX = Double.isNaN(this.mouseX) ? (box.x0() + box.x1()) / 2.0F : (float)this.mouseX;
            float gazeY = Double.isNaN(this.mouseY) ? (box.y0() + box.y1()) / 2.0F : (float)this.mouseY;
            InventoryScreen.extractEntityInInventoryFollowsMouse(
               graphics,
               Math.round(box.x0()),
               Math.round(box.y0()),
               Math.round(box.x1()),
               Math.round(box.y1()),
               Math.round(box.scale()),
               0.0625F,
               gazeX,
               gazeY,
               player
            );
            return true;
         } else {
            return false;
         }
      }
   }

   protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
      return this.slotAt(mx, my) == null && ContainerData.outside(this.ui.topmost(mx, my));
   }

   public void mouseMoved(double x, double y) {
      this.mouseX = x;
      this.mouseY = y;
      this.ui.mouseMoved(x, y);
      this.hover(this.slotAt(x, y));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (this.recipeBook != null && this.menu.getCarried().isEmpty()) {
         UiControl control = this.ui.topmost(event.x(), event.y());
         if (control != null && "recipe_book".equals(control.itemCollection())) {
            if (this.ui.input().focused() != null) {
               this.ui.input().focus(null);
            }

            return this.recipeBook.click(control.itemIndex(), event.button(), event.hasShiftDown()) || super.mouseClicked(event, doubleClick);
         }
      }

      UiControl control = this.ui.topmost(event.x(), event.y());
      if (event.button() == 1 && control != null && this.javaSlot(control) < 0 && ContainerData.ownButton(control)) {
         this.uiPressed = true;
         this.ui.mouseClicked(event);
         return true;
      } else {
         boolean handled = super.mouseClicked(event, doubleClick);
         if (control != null && this.javaSlot(control) >= 0 && !this.ui.failed()) {
            this.otherCellMappings(control, event.button());
         }

         return handled;
      }
   }

   private void otherCellMappings(UiControl control, int mouseButton) {
      String from = mouseButton == 1 ? "button.menu_select" : (mouseButton == 3 ? "button.menu_secondary_select" : null);
      if (from != null) {
         List<String> fired = this.ui.input().fireOthers(control, from, ContainerData::slotButton);
         if (!fired.isEmpty() && this.loggedCellMappings.add(String.join(",", fired))) {
            BedrockNative.LOGGER.info("JSON UI container {}: a click on a slot cell also fired {}", this.screen, fired);
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.uiPressed && event.button() == 1) {
         this.uiPressed = false;
         this.ui.mouseReleased(event);
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.uiPressed ? this.ui.mouseDragged(event) : super.mouseDragged(event, dx, dy);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return super.mouseScrolled(x, y, scrollX, scrollY) || this.ui.mouseScrolled(x, y, scrollY);
   }

   @Override
   public void exit() {
      if (this.minecraft.gui.screen() == this) {
         BedrockNative.LOGGER.debug("JSON UI container {} closed by its close button", this.screen);
         this.onClose();
      }
   }

   @Override
   public void help() {
      this.notice.unavailable("menu.howToPlay");
   }

   public MenuNotice notice() {
      return this.notice;
   }

   private record Described(ItemStack stack, int count, int damage, @Nullable CustomData data, @Nullable Object name, int storage, ContainerData.Slot slot) {
   }
}
