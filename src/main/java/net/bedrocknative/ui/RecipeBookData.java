package net.bedrocknative.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class RecipeBookData implements UiDataSource {
   public static final String RECIPE_BOOK = "recipe_book";
   public static final String SEARCH_TEXT = "#text_box_item_name";
   public static final String FILTER = "#filtering_enabled";
   public static final String RECIPE_BOOK_LAYOUT = "#is_recipe_book_layout";
   public static final String SURVIVAL_LAYOUT = "#is_survival_layout";
   private static final String SEARCH_CLEAR = "button.search_bar_clear";
   public static final String CRAFTABLE = "textures/ui/recipe_book_item_bg";
   public static final String NOT_CRAFTABLE = "textures/ui/recipe_book_red_button";
   public static final String HEAD_COLLAPSED = "textures/ui/recipe_book_group_collapsed";
   public static final String HEAD_EXPANDED = "textures/ui/recipe_book_group_expanded";
   public static final String GROUP_ENTRY = "textures/ui/recipe_book_group_bg";
   private static final Map<RecipeBookData.Tab, String> TAB_LABELS = Map.of(
      RecipeBookData.Tab.CONSTRUCTION,
      "craftingScreen.tab.construction",
      RecipeBookData.Tab.EQUIPMENT,
      "craftingScreen.tab.equipment",
      RecipeBookData.Tab.ITEMS,
      "craftingScreen.tab.items",
      RecipeBookData.Tab.NATURE,
      "craftingScreen.tab.nature"
   );
   private static final String ALL_RECIPES = "craftingScreen.tab.allRecipes";
   private static final String CRAFTABLE_RECIPES = "craftingScreen.tab.craftableRecipes";
   private static final Map<String, RecipeBookData.Tab> TAB_BINDINGS = Map.of(
      "#is_left_tab_construct",
      RecipeBookData.Tab.CONSTRUCTION,
      "#is_left_tab_equipment",
      RecipeBookData.Tab.EQUIPMENT,
      "#is_left_tab_items",
      RecipeBookData.Tab.ITEMS,
      "#is_left_tab_nature",
      RecipeBookData.Tab.NATURE,
      "#is_left_tab_search",
      RecipeBookData.Tab.SEARCH
   );
   private final ContainerData slots;
   private RecipeBookData.@Nullable Actions actions;
   private List<RecipeBookData.Cell> cells = List.of();
   private RecipeBookData.Tab tab = RecipeBookData.Tab.SEARCH;
   private RecipeBookData.Layout layout = RecipeBookData.Layout.RECIPE_BOOK;
   private boolean filtering;
   private String search = "";
   private boolean needsCraftingTable;
   private long version = 1L;

   public RecipeBookData(ContainerData slots) {
      this.slots = slots;
   }

   public ContainerData slots() {
      return this.slots;
   }

   public void actions(RecipeBookData.@Nullable Actions target) {
      this.actions = target;
   }

   public void cells(List<RecipeBookData.Cell> shown) {
      if (!shown.equals(this.cells)) {
         this.cells = List.copyOf(shown);
         this.version++;
      }
   }

   public List<RecipeBookData.Cell> cells() {
      return this.cells;
   }

   public void tab(RecipeBookData.Tab shown) {
      if (shown != this.tab) {
         this.tab = shown;
         this.version++;
      }
   }

   public RecipeBookData.Tab tab() {
      return this.tab;
   }

   public void layout(RecipeBookData.Layout shown) {
      if (shown != this.layout) {
         this.layout = shown;
         this.version++;
      }
   }

   public RecipeBookData.Layout layout() {
      return this.layout;
   }

   public void filtering(boolean on) {
      if (on != this.filtering) {
         this.filtering = on;
         this.version++;
      }
   }

   public boolean filtering() {
      return this.filtering;
   }

   public void search(String text) {
      String value = text == null ? "" : text;
      if (!value.equals(this.search)) {
         this.search = value;
         this.version++;
      }
   }

   public String search() {
      return this.search;
   }

   public void needsCraftingTable(boolean needs) {
      if (needs != this.needsCraftingTable) {
         this.needsCraftingTable = needs;
         this.version++;
      }
   }

   public String label() {
      return this.tab == RecipeBookData.Tab.SEARCH
         ? (this.filtering ? "craftingScreen.tab.craftableRecipes" : "craftingScreen.tab.allRecipes")
         : TAB_LABELS.get(this.tab);
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#is_recipe_book_layout" -> UiValue.of(this.layout == RecipeBookData.Layout.RECIPE_BOOK);
         case "#is_survival_layout" -> UiValue.of(this.layout == RecipeBookData.Layout.SURVIVAL);
         case "#is_creative_layout", "#is_creative_mode", "#is_creative_layout_button_visible", "#is_creative_and_recipe_book_layout", "#is_creative_and_creative_layout", "#is_left_tab_inventory" -> UiValue.FALSE;
         case "#construction_tab_visible", "#equipment_tab_visible", "#items_tab_visible", "#nature_tab_visible", "#text_box_enabled" -> UiValue.TRUE;
         case "#tab_label_text" -> UiValue.of(this.label());
         case "#filtering_enabled" -> UiValue.of(this.filtering);
         case "#text_box_item_name" -> UiValue.of(this.search);
         case "#needs_crafting_table" -> UiValue.of(this.needsCraftingTable);
         default -> {
            RecipeBookData.Tab of = TAB_BINDINGS.get(name);
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
      }

      if (index >= 0) {
         if (index >= this.cells.size()) {
            return UiValue.NULL;
         }

         RecipeBookData.Cell cell = this.cells.get(index);

         return switch (name) {
            case "#recipe_hover_text", "#hover_text" -> UiValue.of(cell.hoverText());
            case "#recipe_craftable_count", "#inventory_stack_count" -> UiValue.of(cell.countText());
            case "#stack_count_visible" -> UiValue.of(!cell.countText().isEmpty());
            case "#is_creative_selected_slot", "#is_selected_slot" -> UiValue.of(cell.selected());
            case "#item_durability_visible", "#item_storage_visible", "#inactive_item", "#item_lock", "#item_lock_in_slot", "#item_lock_in_inventory" -> UiValue.FALSE;
            case "#container_item_background_texture" -> UiValue.of(texture(cell));
            case "#container_item_modifier" -> UiValue.of(
               cell.kind() == RecipeBookData.Kind.HEAD_CLOSED ? 2.0 : (cell.kind() == RecipeBookData.Kind.HEAD_OPEN ? 1.0 : 0.0)
            );
            case "#container_item_background" -> {
               yield UiValue.of(switch (cell.kind()) {
                  case ENTRY -> cell.craftable() ? 0.0 : 5.0;
                  case HEAD_CLOSED -> 1.0;
                  case HEAD_OPEN -> 2.0;
                  case GROUP_ENTRY -> cell.craftable() ? 3.0 : 5.0;
               });
            }
            default -> UiValue.NULL;
         };
      } else {
         return !name.equals("#recipe_book_total_items") && !name.equals("#collection_total_items") ? UiValue.NULL : UiValue.of(this.cells.size());
      }
   }

   public static String texture(RecipeBookData.Cell cell) {
      return switch (cell.kind()) {
         case ENTRY -> cell.craftable() ? "textures/ui/recipe_book_item_bg" : "textures/ui/recipe_book_red_button";
         case HEAD_CLOSED -> cell.craftable() ? "textures/ui/recipe_book_group_collapsed" : "textures/ui/recipe_book_red_button";
         case HEAD_OPEN -> "textures/ui/recipe_book_group_expanded";
         case GROUP_ENTRY -> cell.craftable() ? "textures/ui/recipe_book_group_bg" : "textures/ui/recipe_book_red_button";
      };
   }

   @Override
   public @Nullable String kind(String collection, int index) {
      return null;
   }

   @Override
   public long version() {
      return this.version + this.slots.version();
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      if ("button.menu_exit".equals(buttonId)) {
         if (this.actions != null) {
            this.actions.exit();
         } else {
            this.slots.event(buttonId, collection, index);
         }
      } else if ("button.search_bar_clear".equals(buttonId)) {
         if (!this.search.isEmpty()) {
            this.search = "";
            this.version++;
            if (this.actions != null) {
               this.actions.search("");
            }
         }
      } else {
         this.slots.event(buttonId, collection, index);
      }
   }

   @Override
   public void write(@Nullable String collection, int index, String name, UiValue value, int outer) {
      if (collection != null) {
         this.slots.write(collection, index, name, value, outer);
      } else {
         this.version++;
         RecipeBookData.Tab chosen = TAB_BINDINGS.get(name);
         if (this.actions != null) {
            if (chosen != null) {
               if (value.truthy()) {
                  this.actions.tab(chosen);
               }
            } else if ("#is_recipe_book_layout".equals(name)) {
               if (value.truthy()) {
                  this.actions.layout(RecipeBookData.Layout.RECIPE_BOOK);
               }
            } else if ("#is_survival_layout".equals(name)) {
               if (value.truthy()) {
                  this.actions.layout(RecipeBookData.Layout.SURVIVAL);
               }
            } else if ("#filtering_enabled".equals(name)) {
               this.actions.filtering(value.truthy());
            } else if ("#text_box_item_name".equals(name)) {
               String text = value.isNull() ? "" : value.asString();
               if (!Objects.equals(text, this.search)) {
                  this.search = text;
                  this.actions.search(text);
               }
            } else {
               this.slots.write(null, index, name, value, outer);
            }
         }
      }
   }

   public static Map<String, UiValue> variables(String title, boolean localize) {
      return ContainerData.variables(title, localize);
   }

   public interface Actions {
      void tab(RecipeBookData.Tab var1);

      void layout(RecipeBookData.Layout var1);

      void filtering(boolean var1);

      void search(String var1);

      void exit();
   }

   public record Cell(String hoverText, RecipeBookData.Kind kind, boolean craftable, String countText, boolean selected) {
   }

   public enum Kind {
      ENTRY,
      HEAD_CLOSED,
      HEAD_OPEN,
      GROUP_ENTRY;
   }

   public enum Layout {
      RECIPE_BOOK,
      SURVIVAL;
   }

   public enum Tab {
      CONSTRUCTION,
      EQUIPMENT,
      ITEMS,
      NATURE,
      SEARCH;
   }
}
