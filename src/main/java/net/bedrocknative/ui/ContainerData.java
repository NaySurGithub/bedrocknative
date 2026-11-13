package net.bedrocknative.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ContainerData implements UiDataSource {
   public static final String CONTAINER_ITEMS = "container_items";
   public static final String INVENTORY_ITEMS = "inventory_items";
   public static final String HOTBAR_ITEMS = "hotbar_items";
   public static final int INVENTORY_SIZE = 27;
   public static final int HOTBAR_SIZE = 9;
   public static final String SELECTED_ITEM = "#inventory_selected_item";
   public static final String EXIT = "button.menu_exit";
   public static final Set<String> SLOT_BUTTONS = Set.of(
      "button.container_take_all_place_all",
      "button.container_take_half_place_one",
      "button.container_auto_place",
      "button.container_auto_place_one",
      "button.drop_one",
      "button.drop_all",
      "button.coalesce_stack",
      "button.shape_drawing",
      "button.container_slot_hovered",
      "button.container_reset_held",
      "button.cursor_drop_all",
      "button.cursor_drop_one",
      "button.crafting_output_primary",
      "button.crafting_output_secondary",
      "button.crafting_output_tertiary",
      "button.recipe_select",
      "button.recipe_secondary",
      "button.recipe_tertiary",
      "button.destroy_selection",
      "button.clear_hotbar_or_remove_one",
      "button.container_auto_destroy"
   );
   private static final String CURSOR_DROP = "button.cursor_drop_";
   private static final String SURVIVAL_LAYOUT = "#is_survival_layout";
   private static final Set<String> LAYOUTS = Set.of("#is_survival_layout", "#is_recipe_book_layout", "#is_creative_layout");
   private static final String CRAFTING_LABEL = "container.crafting";
   private static final int[] BUBBLES = new int[]{29, 24, 20, 16, 11, 6, 0};
   private static final float BUBBLES_HEIGHT = 29.0F;
   private static final int BREW_TICKS = 400;
   private static final int BLAZE_FUEL = 20;
   private static final int LOCK_IN_SLOT = 1;
   private static final int LOCK_IN_INVENTORY = 2;
   public static final String HELP = "button.help";
   private final ContainerSlots slots;
   private final Map<String, ContainerData.Slot[]> items = new LinkedHashMap<>();
   private ContainerData.Slot carried = ContainerData.Slot.EMPTY;
   @Nullable
   private ContainerData.Controller controller;
   @Nullable
   private ContainerData.Station station;
   private long version = 1L;
   private float furnaceArrow = 1.0F;
   private float furnaceFlame = 1.0F;
   private float brewingArrow = 1.0F;
   private float brewingBubbles = 1.0F;
   private float brewingFuel = 1.0F;
   private UiValue owner = UiValue.NULL;
   public static final String HOVER_TEXT_RENDERER = "hover_text_renderer";
   public static final String SECONDARY_SELECT = "button.menu_secondary_select";

   public ContainerData(ContainerSlots slots) {
      this.slots = slots;

      for (String collection : slots.collections()) {
         this.items.put(collection, filled(slots.size(collection)));
      }
   }

   public ContainerData(int ownSlots) {
      this(ContainerSlots.chest(ownSlots));
   }

   public void controller(@Nullable ContainerData.Controller target) {
      this.controller = target;
   }

   public void station(@Nullable ContainerData.Station data) {
      this.station = data;
      this.version++;
   }

   @Nullable
   public ContainerData.Station station() {
      return this.station;
   }

   public void owner(@Nullable Long uniqueId) {
      UiValue value = uniqueId == null ? UiValue.NULL : UiActorRenderers.value(uniqueId);
      if (!value.equals(this.owner)) {
         this.owner = value;
         this.version++;
      }
   }

   public ContainerSlots slots() {
      return this.slots;
   }

   public int ownSlots() {
      return this.slots.size("container_items");
   }

   public static int javaSlot(@Nullable String collection, int index, int ownSlots) {
      return ContainerSlots.chest(ownSlots).javaSlot(collection, index);
   }

   public static boolean ownButton(UiControl control) {
      if (control.type() != UiType.TOGGLE
         && control.type() != UiType.DROPDOWN
         && control.type() != UiType.SLIDER
         && control.type() != UiType.SLIDER_BOX
         && control.type() != UiType.EDIT_BOX
         && control.type() != UiType.SCROLLBAR_BOX
         && control.type() != UiType.SCROLL_TRACK
         && control.type() != UiType.SCROLLBAR_TRACK) {
         for (String target : selectTargets(control)) {
            if (!target.isEmpty() && !slotButton(target)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public static boolean slotButton(String id) {
      return SLOT_BUTTONS.contains(id)
         || id.startsWith("button.") && (id.endsWith("_take_all_place_all") || id.endsWith("_take_half_place_one") || id.endsWith("_coalesce_stack"));
   }

   public static boolean outside(@Nullable UiControl hit) {
      if (hit == null) {
         return true;
      } else {
         for (String target : selectTargets(hit)) {
            if (target.startsWith("button.cursor_drop_")) {
               return true;
            }
         }

         return false;
      }
   }

   private static List<String> selectTargets(UiControl control) {
      List<String> targets = new ArrayList<>(1);

      for (JsonObject mapping : control.buttonMappings()) {
         if ("button.menu_select".equals(string(mapping.get("from_button_id"))) && "pressed".equals(string(mapping.get("mapping_type")))) {
            String target = string(mapping.get("to_button_id"));
            if (target != null) {
               targets.add(target);
            }
         }
      }

      return targets;
   }

   @Nullable
   private static String string(@Nullable JsonElement element) {
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   public static boolean showsHoverText(@Nullable UiControl cell) {
      if (cell != null && cell.visible()) {
         boolean[] found = new boolean[]{false};
         cell.walk(control -> {
            if (!found[0] && control.type() == UiType.CUSTOM && "hover_text_renderer".equals(control.renderer()) && control.visible()) {
               found[0] = true;
            }
         });
         return found[0];
      } else {
         return false;
      }
   }

   public void slot(String collection, int index, ContainerData.Slot slot) {
      ContainerData.Slot[] collectionItems = this.items.get(collection);
      if (collectionItems != null && index >= 0 && index < collectionItems.length) {
         ContainerData.Slot value = slot == null ? ContainerData.Slot.EMPTY : slot;
         if (!value.equals(collectionItems[index])) {
            collectionItems[index] = value;
            this.version++;
         }
      }
   }

   public ContainerData.Slot slot(String collection, int index) {
      ContainerData.Slot[] collectionItems = this.items.get(collection);
      return collectionItems != null && index >= 0 && index < collectionItems.length ? collectionItems[index] : ContainerData.Slot.EMPTY;
   }

   public void carried(ContainerData.Slot slot) {
      ContainerData.Slot value = slot == null ? ContainerData.Slot.EMPTY : slot;
      if (!value.equals(this.carried)) {
         this.carried = value;
         this.version++;
      }
   }

   public ContainerData.Slot carried() {
      return this.carried;
   }

   public void furnace(float cooking, float fuelLeft, boolean lit) {
      float arrow = 1.0F - clamp(cooking);
      float flame = lit ? 1.0F - clamp(fuelLeft) : 1.0F;
      if (arrow != this.furnaceArrow || flame != this.furnaceFlame) {
         this.furnaceArrow = arrow;
         this.furnaceFlame = flame;
         this.version++;
      }
   }

   public void brewing(int brewTicks, int totalTicks, int fuel, int totalFuel) {
      int total = totalTicks > 0 ? totalTicks : 400;
      boolean brewing = brewTicks > 0;
      float arrow = brewing ? clamp((float)brewTicks / total) : 1.0F;
      float bubbles = brewing ? 1.0F - BUBBLES[brewTicks / 2 % BUBBLES.length] / 29.0F : 1.0F;
      float fuelBar = 1.0F - clamp((float)fuel / (totalFuel > 0 ? totalFuel : 20));
      if (arrow != this.brewingArrow || bubbles != this.brewingBubbles || fuelBar != this.brewingFuel) {
         this.brewingArrow = arrow;
         this.brewingBubbles = bubbles;
         this.brewingFuel = fuelBar;
         this.version++;
      }
   }

   private static float clamp(float value) {
      return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
   }

   private static ContainerData.Slot[] filled(int size) {
      ContainerData.Slot[] collectionItems = new ContainerData.Slot[size];
      Arrays.fill(collectionItems, ContainerData.Slot.EMPTY);
      return collectionItems;
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#close_button_visible" -> UiValue.TRUE;
         case "#show_persistent_bundle_hover_text" -> UiValue.TRUE;
         case "#using_touch", "#is_using_gamepad", "#gamepad_helper_visible", "#progress_bar_visible", "#touch_progress_bar_visible", "#stack_splitting_bar_visible", "#classic_stack_splitting_overlay_visible", "#pocket_stack_splitting_overlay_visible", "#recipe_details_label", "#is_recipe_book_layout", "#is_creative_layout", "#is_creative_layout_button_visible", "#needs_crafting_table" -> UiValue.FALSE;
         case "#is_survival_layout" -> UiValue.TRUE;
         case "#crafting_label_text" -> UiValue.of("container.crafting");
         case "#furnace_arrow_ratio" -> UiValue.of(this.furnaceArrow);
         case "#furnace_flame_ratio" -> UiValue.of(this.furnaceFlame);
         case "#brewing_arrow_ratio" -> UiValue.of(this.brewingArrow);
         case "#brewing_bubbles_ratio" -> UiValue.of(this.brewingBubbles);
         case "#brewing_fuel_ratio" -> UiValue.of(this.brewingFuel);
         case "#inventory_selected_item_stack_count" -> UiValue.of(this.carried.countText());
         case "#selected_item_durability_visible" -> UiValue.of(this.carried.durability());
         case "#selected_item_durability_total_amount" -> UiValue.of(this.carried.durabilityTotal());
         case "#selected_item_durability_current_amount" -> UiValue.of(this.carried.durabilityCurrent());
         case "#selected_item_storage_visible" -> UiValue.of(this.carried.storage());
         case "#selected_item_storage_total_amount" -> UiValue.of(this.carried.storageTotal());
         case "#selected_item_storage_current_amount" -> UiValue.of(this.carried.storageCurrent());
         case "#inventory_selected_item_lock_in_slot" -> UiValue.of(this.carried.lock() == 1);
         case "#inventory_selected_item_lock_in_inventory" -> UiValue.of(this.carried.lock() == 2);
         case "#entity_id" -> this.owner.isNull() && this.station != null ? this.station.global(name) : this.owner;
         default -> this.station != null ? this.station.global(name) : UiValue.NULL;
      };
   }

   @Override
   public UiValue global(String name, UiItemPath path) {
      UiValue value = this.station == null ? null : this.station.global(name, path);
      return value != null ? value : this.global(name);
   }

   @Override
   public int length(String collection) {
      ContainerData.Slot[] collectionItems = this.items.get(collection);
      if (collectionItems == null && this.station != null) {
         return Math.max(0, this.station.length(collection));
      } else {
         return collectionItems == null ? 0 : collectionItems.length;
      }
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      ContainerData.Slot[] collectionItems = this.items.get(collection);
      if (collectionItems == null) {
         UiValue value = this.station == null ? null : this.station.item(collection, index, name);
         return value == null ? UiValue.NULL : value;
      } else if (index < 0) {
         return name.equals("#collection_total_items") ? UiValue.of(collectionItems.length) : UiValue.NULL;
      } else if (index >= collectionItems.length) {
         return UiValue.NULL;
      } else {
         if (this.station != null) {
            UiValue value = this.station.item(collection, index, name);
            if (value != null) {
               return value;
            }
         }

         return slotValue(collectionItems[index], name);
      }
   }

   public static UiValue slotValue(ContainerData.Slot slot, String name) {
      return switch (name) {
         case "#inventory_stack_count" -> UiValue.of(slot.countText());
         case "#stack_count_visible" -> UiValue.of(slot.count() > 1);
         case "#item_durability_visible" -> UiValue.of(slot.durability());
         case "#item_durability_total_amount" -> UiValue.of(slot.durabilityTotal());
         case "#item_durability_current_amount" -> UiValue.of(slot.durabilityCurrent());
         case "#item_storage_visible" -> UiValue.of(slot.storage());
         case "#item_storage_total_amount" -> UiValue.of(slot.storageTotal());
         case "#item_storage_current_amount" -> UiValue.of(slot.storageCurrent());
         case "#hover_text" -> UiValue.of(slot.empty() ? "" : slot.hoverText());
         case "#item_lock" -> UiValue.of(!slot.empty() && slot.lock() != 0);
         case "#item_lock_in_slot" -> UiValue.of(!slot.empty() && slot.lock() == 1);
         case "#item_lock_in_inventory" -> UiValue.of(!slot.empty() && slot.lock() == 2);
         case "#empty_armor_image_visible", "#empty_offhand_image_visible", "#empty_bottle_image_visible", "#empty_fuel_image_visible", "#empty_image_visible" -> UiValue.of(
            slot.empty()
         );
         case "#container_item_background", "#container_item_modifier" -> UiValue.ZERO;
         case "#is_selected_slot", "#inactive_item", "#bundle_selected_item_visible", "#is_selected_bundle_item_slot", "#bundle_open_back", "#bundle_open_front" -> UiValue.FALSE;
         default -> UiValue.NULL;
      };
   }

   @Nullable
   @Override
   public String kind(String collection, int index) {
      return null;
   }

   @Override
   public long version() {
      return this.station == null ? this.version : this.version + this.station.version();
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      if ("button.menu_exit".equals(buttonId) && this.controller != null) {
         this.controller.exit();
      } else if ("button.help".equals(buttonId) && this.controller != null) {
         this.controller.help();
      } else if (this.station != null) {
         this.station.event(buttonId, collection, index);
      }
   }

   @Override
   public void event(String buttonId, UiItemPath path) {
      if (this.station == null || !this.station.event(buttonId, path)) {
         this.event(buttonId, null, -1);
      }
   }

   @Override
   public void write(@Nullable String collection, int index, String name, UiValue value, int outer) {
      if (collection == null && LAYOUTS.contains(name)) {
         this.version++;
      } else if (this.station != null) {
         this.station.write(collection, index, name, value);
      }
   }

   public static Map<String, UiValue> variables(String title, boolean localize) {
      return Map.ofEntries(
         Map.entry("$container_title", UiValue.of(title)),
         Map.entry("$localize_title", UiValue.of(localize)),
         Map.entry("$search_index", UiValue.of(0.0)),
         Map.entry("$construction_index", UiValue.of(1.0)),
         Map.entry("$equipment_index", UiValue.of(2.0)),
         Map.entry("$items_index", UiValue.of(3.0)),
         Map.entry("$nature_index", UiValue.of(4.0)),
         Map.entry("$food_index", UiValue.of(1.0)),
         Map.entry("$blocks_index", UiValue.of(2.0)),
         Map.entry("$recipe_book_layout_index", UiValue.of(0.0)),
         Map.entry("$survival_layout_index", UiValue.of(1.0)),
         Map.entry("$creative_layout_index", UiValue.of(2.0)),
         Map.entry("$use_smithing_table_2_ui", UiValue.TRUE)
      );
   }

   public interface Controller {
      void exit();

      default void help() {
      }
   }

   public record Slot(int count, int durabilityTotal, int durabilityCurrent, int storageTotal, int storageCurrent, String hoverText, int lock) {
      public static final ContainerData.Slot EMPTY = new ContainerData.Slot(0, 0, 0, 0, 0, "", 0);

      public static ContainerData.Slot of(int count) {
         return count <= 0 ? EMPTY : new ContainerData.Slot(count, 0, 0, 0, 0, "", 0);
      }

      boolean empty() {
         return this.count <= 0;
      }

      String countText() {
         return this.count > 1 ? Integer.toString(this.count) : "";
      }

      boolean durability() {
         return !this.empty() && this.durabilityTotal > 0 && this.durabilityCurrent < this.durabilityTotal;
      }

      boolean storage() {
         return !this.empty() && this.storageTotal > 0 && this.storageCurrent > 0;
      }
   }

   public interface Station {
      UiValue global(String var1);

      int length(String var1);

      @Nullable
      UiValue item(String var1, int var2, String var3);

      boolean event(String var1, @Nullable String var2, int var3);

      boolean write(@Nullable String var1, int var2, String var3, UiValue var4);

      long version();

      @Nullable
      default UiValue global(String name, UiItemPath path) {
         return null;
      }

      default boolean event(String buttonId, UiItemPath path) {
         return false;
      }
   }
}
