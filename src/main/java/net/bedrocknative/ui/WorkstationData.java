package net.bedrocknative.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class WorkstationData implements ContainerData.Station {
   public static final String ENCHANT_BUTTONS = "#enchant_buttons";
   public static final String STONES = "stones";
   public static final String PATTERNS = "patterns";
   public static final List<String> PRIMARY_POWERS = List.of("speed", "haste", "resist", "jump", "strength");
   public static final String REGENERATION = "regen";
   public static final String EXTRA = "extra";
   public static final String CONFIRM = "confirm";
   public static final String CANCEL = "cancel";
   private static final Set<String> BEACON_BUTTONS = Set.of("speed", "haste", "resist", "jump", "strength", "regen", "extra", "confirm", "cancel");
   private static final Map<String, Integer> POWER_LEVELS = Map.of("speed", 1, "haste", 1, "resist", 2, "jump", 2, "strength", 3, "regen", 4, "extra", 4);
   private static final int OPTIONS = 3;
   private static final String OK = "button.menu_ok";
   private static final String STONE_SELECT = "button.stone_select";
   private static final String PATTERN_SELECT = "button.pattern_select";
   private static final String NAME = "#text_box_item_name";
   static final String OPTION_TEXTURE = "textures/ui/recipe_book_item_bg";
   static final String OPTION_SELECTED_TEXTURE = "textures/ui/recipe_book_touch_cell_selected";
   static final String CELL_TEXTURE = "textures/ui/cell_image";
   private final ContainerSlots.Kind kind;
   private WorkstationData.@Nullable Actions actions;
   private long version = 1L;
   private String name = "";
   private boolean nameEditable;
   private String costText = "";
   private boolean affordable;
   private boolean crossOut;
   private final WorkstationData.Option[] options = new WorkstationData.Option[]{
      WorkstationData.Option.NONE, WorkstationData.Option.NONE, WorkstationData.Option.NONE
   };
   private boolean bookOpen;
   private List<WorkstationData.Choice> choices = List.of();
   private int selected = -1;
   private WorkstationData.Template template = WorkstationData.Template.NONE;
   private boolean templateEmpty = true;
   private boolean baseEmpty = true;
   private boolean materialEmpty = true;
   private String noResultTooltip = "";
   private String cartographyMode = "none";
   private String outputDescription = "";
   private int levels;
   private @Nullable String primary;
   private @Nullable String secondary;
   private boolean payment;

   public WorkstationData(ContainerSlots.Kind kind) {
      this.kind = kind;
   }

   public static @Nullable WorkstationData of(ContainerSlots.Kind kind) {
      return switch (kind) {
         case ANVIL, ENCHANTING, GRINDSTONE, STONECUTTER, LOOM, SMITHING, CARTOGRAPHY, BEACON -> new WorkstationData(kind);
         default -> null;
      };
   }

   public ContainerSlots.Kind kind() {
      return this.kind;
   }

   public void actions(WorkstationData.@Nullable Actions target) {
      this.actions = target;
   }

   @Override
   public long version() {
      return this.version;
   }

   private void changed() {
      this.version++;
   }

   private static <T> boolean differs(T a, T b) {
      return !Objects.equals(a, b);
   }

   public void anvil(String text, boolean editable, String cost, boolean canPay, boolean cross) {
      if (differs(text, this.name) || editable != this.nameEditable || differs(cost, this.costText) || canPay != this.affordable || cross != this.crossOut) {
         this.name = text;
         this.nameEditable = editable;
         this.costText = cost;
         this.affordable = canPay;
         this.crossOut = cross;
         this.changed();
      }
   }

   public String name() {
      return this.name;
   }

   public void grindstone(boolean cross) {
      if (cross != this.crossOut) {
         this.crossOut = cross;
         this.changed();
      }
   }

   public void enchanting(List<WorkstationData.Option> shown, boolean open) {
      boolean change = open != this.bookOpen;

      for (int i = 0; i < 3; i++) {
         WorkstationData.Option option = i < shown.size() && shown.get(i) != null ? shown.get(i) : WorkstationData.Option.NONE;
         if (!option.equals(this.options[i])) {
            this.options[i] = option;
            change = true;
         }
      }

      this.bookOpen = open;
      if (change) {
         this.changed();
      }
   }

   public void choices(List<WorkstationData.Choice> shown, int chosen) {
      if (!shown.equals(this.choices) || chosen != this.selected) {
         this.choices = List.copyOf(shown);
         this.selected = chosen;
         this.changed();
      }
   }

   public List<WorkstationData.Choice> choices() {
      return this.choices;
   }

   public int selected() {
      return this.selected;
   }

   public void smithing(WorkstationData.Template kindIn, boolean noTemplate, boolean noBase, boolean noMaterial, boolean cross, String tooltip) {
      if (kindIn != this.template
         || noTemplate != this.templateEmpty
         || noBase != this.baseEmpty
         || noMaterial != this.materialEmpty
         || cross != this.crossOut
         || differs(tooltip, this.noResultTooltip)) {
         this.template = kindIn;
         this.templateEmpty = noTemplate;
         this.baseEmpty = noBase;
         this.materialEmpty = noMaterial;
         this.crossOut = cross;
         this.noResultTooltip = tooltip;
         this.changed();
      }
   }

   public void cartography(String mode, String description) {
      if (differs(mode, this.cartographyMode) || differs(description, this.outputDescription)) {
         this.cartographyMode = mode;
         this.outputDescription = description;
         this.changed();
      }
   }

   public void beacon(int pyramid, @Nullable String primaryPower, @Nullable String secondaryPower, boolean paid) {
      if (pyramid != this.levels || differs(primaryPower, this.primary) || differs(secondaryPower, this.secondary) || paid != this.payment) {
         this.levels = pyramid;
         this.primary = primaryPower;
         this.secondary = secondaryPower;
         this.payment = paid;
         this.changed();
      }
   }

   public @Nullable String primary() {
      return this.primary;
   }

   public @Nullable String secondary() {
      return this.secondary;
   }

   public boolean beaconActive(String button) {
      return switch (button) {
         case "confirm" -> this.payment && this.primary != null;
         case "cancel" -> true;
         case "extra" -> this.beaconVisible(button) && this.levels >= POWER_LEVELS.get(button);
         default -> POWER_LEVELS.containsKey(button) && this.levels >= POWER_LEVELS.get(button);
      };
   }

   public boolean beaconVisible(String button) {
      return !button.equals("extra") || this.primary != null;
   }

   public boolean beaconSelected(String button) {
      if (!button.equals("extra")) {
         return button.equals("regen") ? "regen".equals(this.secondary) : button.equals(this.primary);
      } else {
         return this.secondary != null && this.secondary.equals(this.primary);
      }
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#text_box_item_name" -> this.kind == ContainerSlots.Kind.ANVIL ? UiValue.of(this.name) : UiValue.of("");
         case "#text_box_enabled" -> UiValue.of(this.kind == ContainerSlots.Kind.ANVIL && this.nameEditable);
         case "#text_box_placeholder_name" -> UiValue.of("");
         case "#cost_text" -> UiValue.of(this.costText);
         case "#cost_text_green" -> UiValue.of(!this.costText.isEmpty() && this.affordable);
         case "#cost_text_red" -> UiValue.of(!this.costText.isEmpty() && !this.affordable);
         case "#cross_out_icon" -> UiValue.of(this.crossOut);
         case "#open" -> UiValue.of(this.bookOpen);
         case "#show_input_overlay_templates" -> UiValue.of(this.templateEmpty);
         case "#show_input_overlay_armors_and_tools" -> UiValue.of(this.baseEmpty && this.template != WorkstationData.Template.TRIM);
         case "#show_input_overlay_armors" -> UiValue.of(this.baseEmpty && this.template == WorkstationData.Template.TRIM);
         case "#show_material_overlay_ingot" -> UiValue.of(this.materialEmpty && this.template != WorkstationData.Template.TRIM);
         case "#show_material_overlay_all" -> UiValue.of(this.materialEmpty && this.template == WorkstationData.Template.TRIM);
         case "#no_result_arrow_tooltip" -> UiValue.of(this.noResultTooltip);
         case "#output_description" -> UiValue.of(this.outputDescription);
         case "#supports_netherite" -> UiValue.TRUE;
         default -> name.startsWith("#is_") && name.endsWith("_mode") && this.kind == ContainerSlots.Kind.CARTOGRAPHY
            ? UiValue.of(name.equals("#is_" + this.cartographyMode + "_mode"))
            : UiValue.NULL;
      };
   }

   @Override
   public int length(String collection) {
      return switch (collection) {
         case "#enchant_buttons" -> this.kind == ContainerSlots.Kind.ENCHANTING ? 3 : -1;
         case "stones", "patterns" -> this.owns(collection) ? this.choices.size() : -1;
         default -> -1;
      };
   }

   private boolean owns(String collection) {
      return collection.equals("stones") && this.kind == ContainerSlots.Kind.STONECUTTER
         || collection.equals("patterns") && this.kind == ContainerSlots.Kind.LOOM;
   }

   @Override
   public @Nullable UiValue item(String collection, int index, String name) {
      if (this.kind == ContainerSlots.Kind.ENCHANTING && collection.equals("#enchant_buttons")) {
         if (index >= 0 && index < 3) {
            WorkstationData.Option option = this.options[index];
            boolean shown = option.cost() > 0;

            return switch (name) {
               case "#selectable_button_visibility", "#selectable_dust_is_visible" -> UiValue.of(shown && option.affordable());
               case "#unselectable_button_visibility", "#unselectable_dust_is_visible" -> UiValue.of(shown && !option.affordable());
               case "#hover_text" -> UiValue.of(shown ? option.hoverText() : "");
               case "#runes" -> UiValue.of(shown ? option.runes() : "");
               case "#cost" -> UiValue.of(shown ? Integer.toString(option.cost()) : "");
               default -> UiValue.NULL;
            };
         } else {
            return UiValue.NULL;
         }
      } else if (this.owns(collection)) {
         if (index >= 0) {
            if (index >= this.choices.size()) {
               return UiValue.NULL;
            }

            WorkstationData.Choice choice = this.choices.get(index);

            return switch (name) {
               case "#hover_text" -> UiValue.of(choice.hoverText());
               case "#stone_cell_background_texture", "#pattern_cell_background_texture" -> UiValue.of(
                  index == this.selected ? "textures/ui/recipe_book_touch_cell_selected" : "textures/ui/recipe_book_item_bg"
               );
               case "#item_stack_count" -> UiValue.of(choice.count() > 1 ? Integer.toString(choice.count()) : "");
               default -> UiValue.NULL;
            };
         } else {
            return !name.equals("#stone_selector_total_items") && !name.equals("#pattern_selector_total_items")
               ? UiValue.NULL
               : UiValue.of(this.choices.size());
         }
      } else if (this.kind == ContainerSlots.Kind.BEACON && BEACON_BUTTONS.contains(collection)) {
         return switch (name) {
            case "#button_visible" -> UiValue.of(this.beaconVisible(collection));
            case "#active" -> UiValue.of(this.beaconActive(collection));
            case "#inactive" -> UiValue.of(!this.beaconActive(collection));
            case "#selected" -> UiValue.of(this.beaconSelected(collection));
            case "#button_hover" -> UiValue.of("");
            case "#extra_image_selection" -> UiValue.of(collection.equals(this.primary));
            default -> UiValue.NULL;
         };
      } else {
         return this.kind == ContainerSlots.Kind.LOOM && name.equals("#container_cell_background_texture") ? UiValue.of("textures/ui/cell_image") : null;
      }
   }

   @Override
   public boolean event(String buttonId, @Nullable String collection, int index) {
      WorkstationData.Actions target = this.actions;
      if (target == null || collection == null) {
         return false;
      }

      if (this.kind == ContainerSlots.Kind.ENCHANTING && buttonId.equals("button.menu_ok") && collection.equals("#enchant_buttons") && index >= 0 && index < 3) {
         target.enchant(index);
         return true;
      }

      if ((buttonId.equals("button.stone_select") || buttonId.equals("button.pattern_select"))
         && this.owns(collection)
         && index >= 0
         && index < this.choices.size()) {
         target.select(index);
         return true;
      }

      if (this.kind == ContainerSlots.Kind.BEACON && buttonId.equals("button.menu_ok") && BEACON_BUTTONS.contains(collection)) {
         this.changed();
         if (this.beaconActive(collection)) {
            target.beacon(collection);
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean write(@Nullable String collection, int index, String name, UiValue value) {
      if (this.kind == ContainerSlots.Kind.ANVIL && collection == null && name.equals("#text_box_item_name")) {
         String text = value.isNull() ? "" : value.asString();
         if (!text.equals(this.name)) {
            this.name = text;
            this.changed();
            if (this.actions != null) {
               this.actions.rename(text);
            }
         }

         return true;
      } else if (this.kind == ContainerSlots.Kind.BEACON && collection != null && BEACON_BUTTONS.contains(collection)) {
         this.changed();
         return true;
      } else {
         return false;
      }
   }

   public interface Actions {
      default void rename(String name) {
      }

      default void enchant(int option) {
      }

      default void select(int option) {
      }

      default void beacon(String button) {
      }
   }

   public record Choice(String hoverText, int count) {
   }

   public record Option(int cost, boolean affordable, String hoverText, String runes) {
      public static final WorkstationData.Option NONE = new WorkstationData.Option(0, false, "", "");
   }

   public enum Template {
      NONE,
      UPGRADE,
      TRIM;
   }
}
