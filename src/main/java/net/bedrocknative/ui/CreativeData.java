package net.bedrocknative.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CreativeData implements UiDataSource {
   public static final String RECIPE_BOOK = "recipe_book";
   public static final String SEARCH_TEXT = "#text_box_item_name";
   private static final String SEARCH_CLEAR = "button.search_bar_clear";
   static final String PLAIN = "textures/ui/recipe_book_item_bg";
   static final String HEAD_COLLAPSED = "textures/ui/recipe_book_group_collapsed";
   static final String HEAD_EXPANDED = "textures/ui/recipe_book_group_expanded";
   static final String GROUP_ENTRY = "textures/ui/recipe_book_group_bg";
   public static final String SEARCH_LABEL = "craftingScreen.tab.allItems";
   private static final Map<String, CreativeData.Tab> TAB_BINDINGS = Map.of(
      "#is_left_tab_construct",
      CreativeData.Tab.CONSTRUCTION,
      "#is_left_tab_equipment",
      CreativeData.Tab.EQUIPMENT,
      "#is_left_tab_items",
      CreativeData.Tab.ITEMS,
      "#is_left_tab_nature",
      CreativeData.Tab.NATURE,
      "#is_left_tab_search",
      CreativeData.Tab.SEARCH
   );
   private final ContainerData slots;
   @Nullable
   private CreativeData.Actions actions;
   private List<CreativeData.Cell> cells = List.of();
   private CreativeData.Tab tab = CreativeData.Tab.CONSTRUCTION;
   private CreativeData.Layout layout = CreativeData.Layout.CREATIVE;
   private String tabLabel = "";
   private String search = "";
   private long version = 1L;

   public CreativeData(ContainerData slots) {
      this.slots = slots;
   }

   public ContainerData slots() {
      return this.slots;
   }

   public void actions(@Nullable CreativeData.Actions target) {
      this.actions = target;
   }

   public void cells(List<CreativeData.Cell> shown) {
      if (!shown.equals(this.cells)) {
         this.cells = List.copyOf(shown);
         this.version++;
      }
   }

   public List<CreativeData.Cell> cells() {
      return this.cells;
   }

   public void tab(CreativeData.Tab selected, String label) {
      if (selected != this.tab || !Objects.equals(label, this.tabLabel)) {
         this.tab = selected;
         this.tabLabel = label;
         this.version++;
      }
   }

   public CreativeData.Tab tab() {
      return this.tab;
   }

   public void layout(CreativeData.Layout shown) {
      if (shown != this.layout) {
         this.layout = shown;
         this.version++;
      }
   }

   public CreativeData.Layout layout() {
      return this.layout;
   }

   public void search(String text) {
      if (!text.equals(this.search)) {
         this.search = text;
         this.version++;
      }
   }

   public String search() {
      return this.search;
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#is_creative_mode", "#is_creative_layout_button_visible", "#construction_tab_visible", "#equipment_tab_visible", "#items_tab_visible", "#nature_tab_visible", "#text_box_enabled" -> UiValue.TRUE;
         case "#is_creative_layout", "#is_creative_and_creative_layout" -> UiValue.of(this.layout == CreativeData.Layout.CREATIVE);
         case "#is_recipe_book_layout", "#is_creative_and_recipe_book_layout" -> UiValue.of(this.layout == CreativeData.Layout.BOTH);
         case "#is_survival_layout" -> UiValue.of(this.layout == CreativeData.Layout.SURVIVAL);
         case "#is_left_tab_inventory", "#filtering_enabled" -> UiValue.FALSE;
         case "#tab_label_text" -> UiValue.of(this.tabLabel);
         case "#text_box_item_name" -> UiValue.of(this.search);
         default -> {
            CreativeData.Tab of = TAB_BINDINGS.get(name);
            yield of != null ? UiValue.of(of == this.tab) : this.slots.global(name);
         }
      };
   }

   @Override
   public int length(String collection) {
      return collection.equals("recipe_book") ? this.cells.size() : this.slots.length(collection);
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      if (!collection.equals("recipe_book")) {
         return this.slots.item(collection, index, name);
      } else if (index < 0) {
         return name.equals("#recipe_book_total_items") ? UiValue.of(this.cells.size()) : UiValue.NULL;
      } else if (index >= this.cells.size()) {
         return UiValue.NULL;
      } else {
         CreativeData.Cell cell = this.cells.get(index);

         return switch (name) {
            case "#recipe_hover_text", "#hover_text" -> UiValue.of(cell.hoverText());
            case "#recipe_craftable_count", "#inventory_stack_count" -> UiValue.of("");
            case "#stack_count_visible", "#is_creative_selected_slot", "#item_durability_visible", "#item_storage_visible", "#inactive_item", "#item_lock" -> UiValue.FALSE;
            case "#container_item_background_texture" -> {
               yield UiValue.of(switch (cell.kind()) {
                  case ENTRY -> "textures/ui/recipe_book_item_bg";
                  case HEAD_CLOSED -> "textures/ui/recipe_book_group_collapsed";
                  case HEAD_OPEN -> "textures/ui/recipe_book_group_expanded";
                  case GROUP_ENTRY -> "textures/ui/recipe_book_group_bg";
               });
            }
            case "#container_item_modifier" -> UiValue.of(
               cell.kind() == CreativeData.Kind.HEAD_CLOSED ? 2.0 : (cell.kind() == CreativeData.Kind.HEAD_OPEN ? 1.0 : 0.0)
            );
            case "#container_item_background" -> {
               yield UiValue.of(switch (cell.kind()) {
                  case ENTRY -> 0.0;
                  case HEAD_CLOSED -> 1.0;
                  case HEAD_OPEN -> 2.0;
                  case GROUP_ENTRY -> 3.0;
               });
            }
            default -> UiValue.NULL;
         };
      }
   }

   @Nullable
   @Override
   public String kind(String collection, int index) {
      return null;
   }

   @Override
   public long version() {
      return this.version + this.slots.version();
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      if (this.actions != null) {
         if ("button.menu_exit".equals(buttonId)) {
            this.actions.exit();
         } else if ("button.help".equals(buttonId)) {
            this.actions.help();
         } else if ("button.search_bar_clear".equals(buttonId) && !this.search.isEmpty()) {
            this.search = "";
            this.version++;
            this.actions.search("");
         }
      }
   }

   @Override
   public void write(@Nullable String collection, int index, String name, UiValue value, int outer) {
      this.version++;
      if (collection == null && this.actions != null) {
         CreativeData.Tab chosen = TAB_BINDINGS.get(name);
         if (chosen != null && value.truthy()) {
            this.actions.tab(chosen);
         } else if (name.equals("#is_creative_layout") && value.truthy()) {
            this.actions.layout(CreativeData.Layout.CREATIVE);
         } else if (name.equals("#is_recipe_book_layout") && value.truthy()) {
            this.actions.layout(CreativeData.Layout.BOTH);
         } else if (name.equals("#is_survival_layout") && value.truthy()) {
            this.actions.layout(CreativeData.Layout.SURVIVAL);
         } else if (name.equals("#text_box_item_name")) {
            String text = value.isNull() ? "" : value.asString();
            this.search = text;
            this.actions.search(text);
         }
      }
   }

   public static Map<String, UiValue> variables(String title) {
      return ContainerData.variables(title, true);
   }

   public interface Actions {
      void tab(CreativeData.Tab var1);

      void layout(CreativeData.Layout var1);

      void search(String var1);

      void exit();

      default void help() {
      }
   }

   public record Cell(String hoverText, CreativeData.Kind kind) {
   }

   public static enum Kind {
      ENTRY,
      HEAD_CLOSED,
      HEAD_OPEN,
      GROUP_ENTRY;
   }

   public static enum Layout {
      CREATIVE,
      BOTH,
      SURVIVAL;
   }

   public static enum Tab {
      CONSTRUCTION,
      EQUIPMENT,
      ITEMS,
      NATURE,
      SEARCH;
   }
}
