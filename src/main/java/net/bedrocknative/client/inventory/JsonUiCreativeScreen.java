package net.bedrocknative.client.inventory;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.inventory.ContainerScreenAccessor;
import net.bedrocknative.client.mixin.item.CreativeScreenAccessor;
import net.bedrocknative.client.ui.ItemSource;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.client.ui.MenuNotice;
import net.bedrocknative.ui.ContainerData;
import net.bedrocknative.ui.ContainerSlots;
import net.bedrocknative.ui.CreativeData;
import net.bedrocknative.ui.PaperDoll;
import net.bedrocknative.ui.UiBinding;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Type;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.jspecify.annotations.Nullable;

public final class JsonUiCreativeScreen extends CreativeModeInventoryScreen implements ItemSource, JsonUiHost, JsonUiSlots, CreativeData.Actions {
   private static final int AWAY = -100000;
   private static final float DOLL_OFFSET_Y = 0.0625F;
   private static final ContainerSlots HOTBAR = ContainerSlots.creativeHotbar();
   private static final ContainerSlots INVENTORY = ContainerSlots.creativeInventory();
   private static final Map<CreativeData.Tab, CreativeItemCategory> CATEGORIES = Map.of(
      CreativeData.Tab.CONSTRUCTION,
      CreativeItemCategory.CONSTRUCTION,
      CreativeData.Tab.EQUIPMENT,
      CreativeItemCategory.EQUIPMENT,
      CreativeData.Tab.ITEMS,
      CreativeItemCategory.ITEMS,
      CreativeData.Tab.NATURE,
      CreativeItemCategory.NATURE
   );
   private static boolean bothChosen;
   private final JsonUiScreen ui;
   private final CreativeData data;
   private final ContainerData slots;
   private final MenuNotice notice = new MenuNotice();
   private CreativeData.Layout layout;
   private CreativeData.Tab lastTab;
   private List<ItemStack> shownItems;
   private ItemStack[] shownSlots;
   private CreativeData.@Nullable Layout shownLayout;
   private ItemStack shownCarried;
   private @Nullable UiControl cursorIcon;
   private long cursorVersion;
   private boolean uiPressed;
   private boolean opened;
   private boolean skipChar;
   private boolean focusSearch;
   private double mouseX;
   private double mouseY;

   private JsonUiCreativeScreen(LocalPlayer player, FeatureFlagSet features, boolean operatorTab, JsonUiScreen ui, CreativeData data) {
      super(player, features, operatorTab);
      this.layout = bothChosen ? CreativeData.Layout.BOTH : CreativeData.Layout.CREATIVE;
      this.lastTab = CreativeData.Tab.CONSTRUCTION;
      this.shownItems = List.of();
      this.shownSlots = new ItemStack[0];
      this.shownCarried = ItemStack.EMPTY;
      this.cursorVersion = -1L;
      this.mouseX = Double.NaN;
      this.mouseY = Double.NaN;
      this.ui = ui;
      this.data = data;
      this.slots = data.slots();
      data.actions(this);
      ui.items(this);
   }

   static @Nullable JsonUiCreativeScreen create(Minecraft minecraft, LocalPlayer player, FeatureFlagSet features, boolean operatorTab) {
      UiDefinitionSet definitions = JsonUi.definitions();
      if (definitions != null && BedrockCreativeTabs.active()) {
         CreativeData data = new CreativeData(new ContainerData(INVENTORY));
         JsonUiScreen ui = JsonUiScreen.create(definitions, "crafting.inventory_screen", CreativeData.variables("container.crafting"), data);
         if (ui != null && JsonUi.use("creative", ui.instance())) {
            ui.instance().layoutRules(UiScreenInstance.bedrockLayout());
            ui.instance().problems().log("creative inventory");
            return new JsonUiCreativeScreen(player, features, operatorTab, ui, data);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Override
   public JsonUiScreen ui() {
      return this.ui;
   }

   public CreativeData data() {
      return this.data;
   }

   public List<ItemStack> items() {
      return this.getMenu().items;
   }

   public CreativeData.Layout layout() {
      return this.layout;
   }

   void fallback(Runnable action) {
      this.ui.fallback(action);
   }

   private CreativeScreenAccessor creative() {
      return (CreativeScreenAccessor)(Object)this;
   }

   private ContainerScreenAccessor container() {
      return (ContainerScreenAccessor)(Object)this;
   }

   @Override
   protected void init() {
      super.init();
      LocalPlayer player = this.minecraft.player;
      if (player != null && player.hasInfiniteMaterials()) {
         this.leftPos = -100000;
         this.topPos = -100000;
         CreativeModeTab selected = CreativeScreenAccessor.bedrocknative$selectedTab();
         if (selected.getType() == Type.INVENTORY) {
            this.layout = CreativeData.Layout.SURVIVAL;
         } else if (tabOf(selected) == null) {
            this.creative().bedrocknative$selectTab(javaTab(CreativeData.Tab.CONSTRUCTION));
         }

         this.hideJavaSearch();
         this.ui.resize(this.width, this.height);
         if (!this.opened) {
            this.opened = true;
            this.ui.event("screen.entrance_push");
         }
      }
   }

   private void hideJavaSearch() {
      EditBox box = this.creative().bedrocknative$searchBox();
      if (box != null) {
         box.setVisible(false);
         box.setCanLoseFocus(true);
         box.setFocused(false);
      }

      this.setFocused(null);
   }

   private static CreativeData.@Nullable Tab tabOf(CreativeModeTab javaTab) {
      if (javaTab.getType() == Type.SEARCH) {
         return CreativeData.Tab.SEARCH;
      }

      for (Entry<CreativeData.Tab, CreativeItemCategory> category : CATEGORIES.entrySet()) {
         if (BedrockCreativeTabs.javaTab(category.getValue()) == javaTab) {
            return category.getKey();
         }
      }

      return null;
   }

   private static CreativeModeTab javaTab(CreativeData.Tab tab) {
      if (tab == CreativeData.Tab.SEARCH) {
         return CreativeModeTabs.searchTab();
      }

      CreativeModeTab javaTab = BedrockCreativeTabs.javaTab(CATEGORIES.get(tab));
      return javaTab != null ? javaTab : CreativeModeTabs.getDefaultTab();
   }

   private static @Nullable CreativeModeTab inventoryTab() {
      for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
         if (tab.getType() == Type.INVENTORY) {
            return tab;
         }
      }

      return null;
   }

   @Override
   public void tab(CreativeData.Tab tab) {
      if (this.layout != CreativeData.Layout.SURVIVAL) {
         this.creative().bedrocknative$selectTab(javaTab(tab));
         this.hideJavaSearch();
         this.lastTab = tab;
         this.focusSearch = false;
         if (tab == CreativeData.Tab.SEARCH) {
            UiControl box = this.searchBox();
            if (box != null) {
               this.ui.input().focus(box);
            } else {
               this.focusSearch = true;
            }
         }
      }
   }

   @Override
   public void layout(CreativeData.Layout chosen) {
      if (chosen != this.layout) {
         this.focusSearch = false;
         if (chosen == CreativeData.Layout.SURVIVAL) {
            CreativeModeTab inventory = inventoryTab();
            if (inventory == null) {
               return;
            }

            CreativeData.Tab current = tabOf(CreativeScreenAccessor.bedrocknative$selectedTab());
            this.lastTab = current != null ? current : this.lastTab;
            this.creative().bedrocknative$selectTab(inventory);
         } else if (this.layout == CreativeData.Layout.SURVIVAL) {
            this.creative().bedrocknative$selectTab(javaTab(this.lastTab));
         }

         this.ui.input().focus(null);
         this.focusSearch = chosen != CreativeData.Layout.SURVIVAL && this.data.tab() == CreativeData.Tab.SEARCH;
         this.hideJavaSearch();
         this.layout = chosen;
         bothChosen = chosen == CreativeData.Layout.BOTH;
      }
   }

   @Override
   public void search(String text) {
      EditBox box = this.creative().bedrocknative$searchBox();
      if (box != null && CreativeScreenAccessor.bedrocknative$selectedTab().getType() == Type.SEARCH) {
         box.setValue(text);
         this.creative().bedrocknative$refreshSearchResults();
      }
   }

   @Override
   public void exit() {
      if (this.minecraft.gui.screen() == this) {
         BedrockNative.LOGGER.debug("JSON UI creative inventory closed by its close button");
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

   public @Nullable UiControl searchBox() {
      UiControl[] found = new UiControl[]{null};
      UiControl root = this.ui.instance().root();
      if (root != null) {
         root.walk(control -> {
            if (found[0] == null && control.type() == UiType.EDIT_BOX && control.visible()) {
               found[0] = control;
            }
         });
      }

      return found[0];
   }

   @Override
   public @Nullable Slot slotAt(double x, double y) {
      UiControl control = this.ui.topmost(x, y);
      if (control == null) {
         return null;
      }

      String collection = control.itemCollection();
      int index = control.itemIndex();
      if (this.layout != CreativeData.Layout.SURVIVAL) {
         if ("recipe_book".equals(collection)) {
            List<ItemStack> items = this.getMenu().items;
            return index >= 0 && index < items.size() ? this.picker(items.get(index)) : null;
         }

         if (fires(control, "button.destroy_selection")) {
            return this.picker(ItemStack.EMPTY);
         }
      }

      return this.slotOf(slotsOf(this.layout).javaSlot(collection, index));
   }

   private @Nullable Slot slotOf(int javaSlot) {
      AbstractContainerMenu menu = this.layout == CreativeData.Layout.BOTH && this.minecraft.player != null
         ? this.minecraft.player.inventoryMenu
         : this.getMenu();
      return javaSlot >= 0 && javaSlot < menu.slots.size() ? menu.getSlot(javaSlot) : null;
   }

   private Slot picker(ItemStack stack) {
      CreativeScreenAccessor.bedrocknative$container().setItem(0, stack);
      return this.getMenu().getSlot(0);
   }

   private static boolean fires(UiControl control, String buttonId) {
      for (JsonObject mapping : control.buttonMappings()) {
         if (mapping.has("to_button_id") && buttonId.equals(mapping.get("to_button_id").getAsString())) {
            return true;
         }
      }

      return false;
   }

   private static ContainerSlots slotsOf(CreativeData.Layout layout) {
      return layout == CreativeData.Layout.CREATIVE ? HOTBAR : INVENTORY;
   }

   public float @Nullable [] cellCentre(String collection, int index) {
      UiControl[] found = new UiControl[]{null};
      UiControl root = this.ui.instance().root();
      if (root != null) {
         root.walk(
            control -> {
               if (found[0] == null
                  && control.visible()
                  && control.collectionIndex() == index
                  && collection.equals(control.itemCollection())
                  && (control.type() == UiType.INPUT_PANEL || control.parent() != null && control.parent().type() == UiType.GRID)) {
                  found[0] = control;
               }
            }
         );
      }

      UiControl cell = found[0];
      return cell == null ? null : JsonUiScreen.toGui(cell.x() + cell.width() / 2.0F, cell.y() + cell.height() / 2.0F);
   }

   public @Nullable UiControl grid() {
      UiControl[] found = new UiControl[]{null};
      UiControl root = this.ui.instance().root();
      if (root != null) {
         root.walk(control -> {
            if (found[0] == null && control.type() == UiType.GRID && "recipe_book".equals(control.collectionName()) && control.visible()) {
               found[0] = control;
            }
         });
      }

      return found[0];
   }

   private void refresh() {
      CreativeModeTab selected = CreativeScreenAccessor.bedrocknative$selectedTab();
      CreativeData.Tab tab = tabOf(selected);
      if (tab != null) {
         Component name = tab == CreativeData.Tab.SEARCH ? BedrockCreativeTabs.searchName() : BedrockCreativeTabs.name(selected);
         this.data.tab(tab, name == null ? selected.getDisplayName().getString() : name.getString());
      }

      this.data.layout(this.layout);
      EditBox box = this.creative().bedrocknative$searchBox();
      if (box != null) {
         this.data.search(box.getValue());
      }

      List<ItemStack> items = this.getMenu().items;
      if (!this.sameItems(items)) {
         this.shownItems = new ArrayList<>(items);
         List<CreativeData.Cell> cells = new ArrayList<>(items.size());
         boolean flat = tab == CreativeData.Tab.SEARCH;

         for (ItemStack stack : items) {
            BedrockCreativeTabs.Group group = flat ? null : BedrockCreativeTabs.group(stack);
            CreativeData.Kind kind = group != null
               ? (BedrockCreativeTabs.isOpen(group) ? CreativeData.Kind.HEAD_OPEN : CreativeData.Kind.HEAD_CLOSED)
               : (!flat && BedrockCreativeTabs.inOpenGroup(stack) ? CreativeData.Kind.GROUP_ENTRY : CreativeData.Kind.ENTRY);
            cells.add(new CreativeData.Cell(stack.isEmpty() ? "" : stack.getHoverName().getString(), kind));
         }

         this.data.cells(cells);
      }

      ContainerSlots map = slotsOf(this.layout);
      if (this.layout != this.shownLayout) {
         this.shownLayout = this.layout;
         this.shownSlots = new ItemStack[map.collections().stream().mapToInt(map::size).sum()];
      }

      int cell = 0;

      for (String collection : map.collections()) {
         for (int index = 0; index < map.size(collection); cell++) {
            Slot slot = this.slotOf(map.javaSlot(collection, index));
            ItemStack stack = slot != null ? slot.getItem() : ItemStack.EMPTY;
            ItemStack seen = this.shownSlots[cell];
            if (seen == null || !ItemStack.matches(stack, seen)) {
               this.shownSlots[cell] = stack.copy();
               this.slots.slot(collection, index, JsonUiContainerScreen.describe(stack));
            }

            index++;
         }
      }

      ItemStack carried = this.getMenu().getCarried();
      if (!ItemStack.matches(carried, this.shownCarried)) {
         this.shownCarried = carried.copy();
         this.slots.carried(JsonUiContainerScreen.describe(carried));
      }
   }

   private boolean sameItems(List<ItemStack> items) {
      if (items.size() != this.shownItems.size()) {
         return false;
      }

      for (int i = 0; i < items.size(); i++) {
         if (items.get(i) != this.shownItems.get(i)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public ItemStack stack(UiControl renderer) {
      if (renderer == this.cursorIcon()) {
         return this.shownCarried;
      } else {
         String collection = renderer.itemCollection();
         int index = renderer.itemIndex();
         if (!"recipe_book".equals(collection)) {
            Slot slot = this.slotOf(slotsOf(this.layout).javaSlot(collection, index));
            return slot != null ? slot.getItem() : ItemStack.EMPTY;
         } else {
            List<ItemStack> items = this.getMenu().items;
            return index >= 0 && index < items.size() ? items.get(index) : ItemStack.EMPTY;
         }
      }
   }

   @Override
   public int seed(UiControl renderer) {
      return Math.max(0, renderer.itemIndex());
   }

   private @Nullable UiControl cursorIcon() {
      long version = this.ui.instance().treeVersion();
      if (version != this.cursorVersion) {
         this.cursorVersion = version;
         this.cursorIcon = null;
         UiControl root = this.ui.instance().root();
         if (root != null) {
            root.walk(control -> {
               if (this.cursorIcon == null && control.type() == UiType.CUSTOM && "inventory_item_renderer".equals(control.renderer())) {
                  for (UiBinding binding : control.bindings()) {
                     if ("#inventory_selected_item".equals(binding.name())) {
                        this.cursorIcon = control;
                     }
                  }
               }
            });
         }
      }

      return this.cursorIcon;
   }

   @Override
   public boolean draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
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

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.minecraft.gui.hud.extractDeferredSubtitles();
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      if (!this.ui.failed()) {
         if ((int)this.mouseX != mouseX || (int)this.mouseY != mouseY || Double.isNaN(this.mouseX)) {
            this.mouseMoved(mouseX, mouseY);
         }

         this.hover(this.slotAt(mouseX, mouseY));
         this.refresh();
         this.ui.render(graphics);
         if (this.focusSearch) {
            UiControl box = this.searchBox();
            if (box != null) {
               this.focusSearch = false;
               this.ui.input().focus(box);
            }
         }

         UiControl icon = this.cursorIcon();
         if (icon == null || !icon.visible()) {
            this.extractCarriedItem(graphics, mouseX, mouseY);
         }

         this.extractTooltip(graphics, mouseX, mouseY);
         this.notice.render(graphics, this.width);
      }
   }

   private void hover(@Nullable Slot slot) {
      Slot before = this.hoveredSlot;
      this.hoveredSlot = slot;
      if (before != null && before != slot) {
         this.container().bedrocknative$onStopHovering(before);
      }
   }

   @Override
   protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
      boolean outside = this.slotAt(mx, my) == null && ContainerData.outside(this.ui.topmost(mx, my));
      this.creative().bedrocknative$hasClickedOutside(outside);
      return outside;
   }

   @Override
   protected void slotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput input) {
      LocalPlayer player = this.minecraft.player;
      boolean inventorySide = slot != null
         && player != null
         && slot.index >= 0
         && slot.index < player.inventoryMenu.slots.size()
         && player.inventoryMenu.getSlot(slot.index) == slot;
      if (this.layout == CreativeData.Layout.BOTH && player != null && (inventorySide || input == ContainerInput.QUICK_CRAFT)) {
         ContainerInput made = slotId == -999 && input == ContainerInput.PICKUP ? ContainerInput.THROW : input;
         if ((made != ContainerInput.THROW || player.canDropItems()) && (slot == null || slot.mayPickup(player))) {
            AbstractContainerMenu inventory = player.inventoryMenu;
            if (made == ContainerInput.THROW && slot != null && slot.hasItem()) {
               ItemStack dropped = slot.remove(buttonNum == 0 ? 1 : slot.getItem().getMaxStackSize());
               player.drop(dropped, true, Prediction.PREDICTED);
               this.minecraft.gameMode.handleCreativeModeItemDrop(dropped);
               this.minecraft.gameMode.handleCreativeModeItemAdd(slot.getItem(), slot.index);
            } else if (made == ContainerInput.THROW && slotId == -999 && !inventory.getCarried().isEmpty()) {
               player.drop(inventory.getCarried(), true, Prediction.PREDICTED);
               this.minecraft.gameMode.handleCreativeModeItemDrop(inventory.getCarried());
               inventory.setCarried(ItemStack.EMPTY);
            } else {
               inventory.clicked(slot == null ? slotId : slot.index, buttonNum, made, player);
               inventory.broadcastChanges();
            }
         }
      } else {
         super.slotClicked(slot, slotId, buttonNum, input);
      }
   }

   @Override
   public void mouseMoved(double x, double y) {
      this.mouseX = x;
      this.mouseY = y;
      this.ui.mouseMoved(x, y);
      this.hover(this.slotAt(x, y));
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 1) {
         UiControl control = this.ui.topmost(event.x(), event.y());
         if (control != null
            && !"recipe_book".equals(control.itemCollection())
            && slotsOf(this.layout).javaSlot(control.itemCollection(), control.itemIndex()) < 0
            && ContainerData.ownButton(control)) {
            this.uiPressed = true;
            this.ui.mouseClicked(event);
            return true;
         }
      }

      if (this.ui.input().focused() != null) {
         this.ui.input().focus(null);
      }

      return super.mouseClicked(event, doubleClick);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.uiPressed && event.button() == 1) {
         this.uiPressed = false;
         this.ui.mouseReleased(event);
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.uiPressed ? this.ui.mouseDragged(event) : super.mouseDragged(event, dx, dy);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.ui.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      this.skipChar = false;
      if (this.ui.input().focused() != null) {
         if (this.ui.keyPressed(event)) {
            return true;
         }

         if (!event.isEscape()) {
            return true;
         }
      } else if (this.layout != CreativeData.Layout.SURVIVAL && this.data.tab() != CreativeData.Tab.SEARCH && this.minecraft.options.keyChat.matches(event)) {
         this.tab(CreativeData.Tab.SEARCH);
         this.skipChar = true;
         return true;
      }

      return super.keyPressed(event);
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      if (this.skipChar) {
         this.skipChar = false;
         return true;
      }

      if (this.ui.input().focused() == null && this.layout != CreativeData.Layout.SURVIVAL && this.data.tab() == CreativeData.Tab.SEARCH) {
         UiControl box = this.searchBox();
         if (box != null) {
            this.ui.input().focus(box);
         }
      }

      return this.ui.input().focused() != null && this.ui.charTyped(event);
   }

   @Override
   public boolean preeditUpdated(@Nullable PreeditEvent event) {
      return this.ui.input().focused() != null && this.ui.preeditUpdated(event);
   }

   @Override
   public boolean isInputCaptured() {
      return this.ui.input().focused() != null || super.isInputCaptured();
   }
}
