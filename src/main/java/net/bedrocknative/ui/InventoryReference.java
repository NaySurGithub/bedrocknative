package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class InventoryReference {
   public static final int SCALE = 2;
   public static final int WIDTH = 1920;
   public static final int HEIGHT = 1080;
   public static final int UNMEASURED = Integer.MIN_VALUE;

   private InventoryReference() {
   }

   public static List<InventoryReference.Rect> of(InventoryReference.Layout layout) {
      return switch (layout) {
         case ITEMS -> items();
         case BOTH -> both();
         case INVENTORY -> inventory();
      };
   }

   public static List<InventoryReference.Rect> items() {
      List<InventoryReference.Rect> rects = new ArrayList<>();
      rects.add(rect("item list panel", named("recipe_book"), 634, 374, 1285, 705));
      rects.add(rect("construction tab", named("construction_tab_panel"), 634, 328, 683, Integer.MIN_VALUE));
      rects.add(rect("search tab", named("search_tab_holder"), 876, 328, 925, Integer.MIN_VALUE));
      toolbar(rects, 1285);
      rects.add(rect("title text 'Tous les objets'", text("creative_label"), 1108, 392, 1267, Integer.MIN_VALUE));
      rects.add(rect("search box", named("text_edit_control"), 646, 412, 1273, 443));
      rects.add(rect("item cell 0", cell("recipe_book", 0), 648, 448, 683, 483));
      rects.add(rect("item cell 16 (17th column)", cell("recipe_book", 16), 1224, 448, 1259, 483));
      rects.add(rect("item cell 17 (second row)", cell("recipe_book", 17), 648, 484, 683, 519));
      rects.add(rect("scroll track", typed(UiType.SCROLL_TRACK), 1266, 448, 1273, 693));
      rects.add(rect("scroll box (smallest)", typed(UiType.SCROLLBAR_BOX), 1266, 448, 1273, 473));
      rects.add(rect("creative hotbar background (sides and bottom)", named("creative_hotbar_background"), 786, Integer.MIN_VALUE, 1133, 751));
      rects.add(rect("creative hotbar slot 0", cell("hotbar_items", 0), 798, 704, 833, 739));
      rects.add(rect("creative hotbar slot 8", cell("hotbar_items", 8), 1086, 704, 1121, 739));
      return rects;
   }

   public static List<InventoryReference.Rect> both() {
      List<InventoryReference.Rect> rects = new ArrayList<>();
      rects.add(rect("item list panel", named("recipe_book"), 634, 374, 925, 705));
      rects.add(rect("centre fold", named("center_fold"), 926, 374, 933, 705));
      rects.add(rect("inventory panel", named("player_inventory"), 934, 374, 1285, 705));
      rects.add(rect("search tab", named("search_tab_holder"), 876, 328, 925, Integer.MIN_VALUE));
      toolbar(rects, 1285);
      rects.add(rect("title text 'Tous les objets'", text("creative_label"), 748, 392, 907, Integer.MIN_VALUE));
      rects.add(rect("search box", named("text_edit_control"), 646, 412, 913, 443));
      rects.add(rect("item cell 0", cell("recipe_book", 0), 648, 448, 683, 483));
      rects.add(rect("item cell 6 (7th column)", cell("recipe_book", 6), 864, 448, 899, 483));
      rects.add(rect("scroll box (smallest)", typed(UiType.SCROLLBAR_BOX), 906, 448, 913, 473));
      inventoryPanel(rects, 150);
      return rects;
   }

   public static List<InventoryReference.Rect> inventory() {
      List<InventoryReference.Rect> rects = new ArrayList<>();
      rects.add(rect("inventory panel", named("player_inventory"), 784, 374, 1135, 705));
      toolbar(rects, 1135);
      inventoryPanel(rects, 0);
      return rects;
   }

   public static List<InventoryReference.Rect> survivalInventory(boolean beside) {
      int dx = beside ? 150 : 0;
      List<InventoryReference.Rect> rects = new ArrayList<>();
      rects.add(rect("inventory panel", named("player_inventory"), 784 + dx, 374, 1135 + dx, 705));
      inventoryPanel(rects, dx);
      return rects;
   }

   private static void toolbar(List<InventoryReference.Rect> rects, int right) {
      rects.add(rect("toolbar", named("toolbar_background"), right - 251, 328, right, Integer.MIN_VALUE));
      rects.add(rect("wide list toggle", toggle("creative_layout_toggle_panel"), right - 235, 336, right - 186, 371));
      rects.add(rect("middle toggle", toggle("recipe_book_layout_toggle_panel"), right - 181, 336, right - 132, 371));
      rects.add(rect("inventory toggle", toggle("survival_layout_toggle_panel"), right - 129, 336, right - 80, 371));
   }

   private static void inventoryPanel(List<InventoryReference.Rect> rects, int dx) {
      rects.add(rect("helmet slot", cell("armor_items", 0), 798 + dx, 388, 833 + dx, 423));
      rects.add(rect("boots slot", cell("armor_items", 3), 798 + dx, 496, 833 + dx, 531));
      rects.add(rect("paper doll box", named("player_bg"), 836 + dx, 390, 939 + dx, 529));
      rects.add(rect("heading text 'Fabrication'", text("crafting_label"), 956 + dx, 390, 1065 + dx, Integer.MIN_VALUE));
      rects.add(rect("crafting cell 0", cell("crafting_input_items", 0), 976 + dx, 410, 1011 + dx, 445));
      rects.add(rect("crafting cell 3", cell("crafting_input_items", 3), 1012 + dx, 446, 1047 + dx, 481));
      rects.add(rect("crafting output", cell("crafting_output_items", 0), 1088 + dx, 426, 1123 + dx, 461));
      rects.add(rect("offhand slot", cell("offhand_items", 0), 942 + dx, 496, 977 + dx, 531));
      rects.add(rect("inventory slot 0", cell("inventory_items", 0), 798 + dx, 546, 833 + dx, 581));
      rects.add(rect("inventory slot 26", cell("inventory_items", 26), 1086 + dx, 618, 1121 + dx, 653));
      rects.add(rect("hotbar slot 0", cell("hotbar_items", 0), 798 + dx, 660, 833 + dx, 695));
   }

   public static List<InventoryReference.Result> compare(InventoryReference.Found found, List<InventoryReference.Rect> rects, float scale) {
      List<InventoryReference.Result> results = new ArrayList<>();

      for (InventoryReference.Rect rect : rects) {
         float[] ui = rect.locator().apply(found);
         if (ui == null) {
            results.add(new InventoryReference.Result(rect, null, Float.POSITIVE_INFINITY, false));
         } else {
            float[] ours = new float[]{ui[0] * scale, ui[1] * scale, ui[2] * scale, ui[3] * scale};
            int[] expected = new int[]{
               rect.x0(),
               rect.y0(),
               rect.x1() == Integer.MIN_VALUE ? Integer.MIN_VALUE : rect.x1() + 1,
               rect.y1() == Integer.MIN_VALUE ? Integer.MIN_VALUE : rect.y1() + 1
            };
            float off = 0.0F;

            for (int i = 0; i < 4; i++) {
               if (expected[i] != Integer.MIN_VALUE) {
                  off = Math.max(off, Math.abs(ours[i] - expected[i]));
               }
            }

            results.add(new InventoryReference.Result(rect, ours, off, off <= rect.slack()));
         }
      }

      return results;
   }

   private static InventoryReference.Rect rect(String what, InventoryReference.Locator locator, int x0, int y0, int x1, int y1) {
      return new InventoryReference.Rect(what, locator, x0, y0, x1, y1, 1, "");
   }

   public static InventoryReference.Mask mask(InventoryReference.Layout layout, UiScreenInstance instance) {
      InventoryReference.Found found = new InventoryReference.Found(instance, UiTextMeasurer.FIXED);
      List<float[]> include = new ArrayList<>();
      List<float[]> exclude = new ArrayList<>();

      for (String panel : List.of("recipe_book", "center_fold", "player_inventory")) {
         float[] r = named(panel).apply(found);
         if (r != null) {
            include.add(r);
            exclude.add(new float[]{r[0], r[1], r[0] + 2.0F, r[1] + 2.0F});
            exclude.add(new float[]{r[2] - 2.0F, r[1], r[2], r[1] + 2.0F});
            exclude.add(new float[]{r[0], r[3] - 2.0F, r[0] + 2.0F, r[3]});
            exclude.add(new float[]{r[2] - 2.0F, r[3] - 2.0F, r[2], r[3]});
         }
      }

      if (layout != InventoryReference.Layout.INVENTORY) {
         for (String tab : layout == InventoryReference.Layout.BOTH
            ? List.of("construction_tab_panel", "equipment_tab_panel", "items_tab_panel", "nature_tab_panel", "search_tab_holder")
            : List.of("construction_tab_panel", "equipment_tab_panel", "items_tab_panel", "nature_tab_panel")) {
            float[] r = named(tab).apply(found);
            if (r != null) {
               include.add(new float[]{r[0] + 2.0F, r[1] + 2.0F, r[2] - 2.0F, r[3]});
            }
         }
      }

      float[] toolbar = named("toolbar_background").apply(found);
      if (toolbar != null) {
         include.add(new float[]{toolbar[0] + 2.0F, toolbar[1] + 2.0F, toolbar[2] - 2.0F, toolbar[3]});
      }

      UiControl root = instance.root();
      if (root != null) {
         root.walk(
            control -> {
               if (control.visible()) {
                  if ("recipe_book".equals(control.itemCollection())
                     && control.collectionIndex() >= 0
                     && control.parent() != null
                     && control.parent().type() == UiType.GRID) {
                     exclude.add(
                        new float[]{control.x() + 1.0F, control.y() + 1.0F, control.x() + control.width() - 1.0F, control.y() + control.height() - 1.0F}
                     );
                  } else if (control.name().equals("bar_and_track")) {
                     exclude.add(new float[]{control.x(), control.y(), control.x() + control.width(), control.y() + control.height()});
                  }
               }
            }
         );
      }

      float[] doll = named("player_bg").apply(found);
      if (doll != null) {
         exclude.add(new float[]{doll[0] + 1.0F, doll[1] + 1.0F, doll[2] - 1.0F, doll[3] - 1.0F});
      }

      if (layout == InventoryReference.Layout.ITEMS) {
         float[] search = named("search_tab_holder").apply(found);
         if (search != null) {
            exclude.add(search);
         }
      }

      return new InventoryReference.Mask(include, exclude);
   }

   @Nullable
   private static float[] rect(@Nullable UiControl control) {
      return control == null ? null : new float[]{control.x(), control.y(), control.x() + control.width(), control.y() + control.height()};
   }

   public static InventoryReference.Locator named(String name) {
      return found -> {
         UiControl[] hit = new UiControl[]{null};
         UiControl root = found.instance().root();
         if (root != null) {
            root.walk(control -> {
               if (hit[0] == null && control.visible() && control.name().equals(name)) {
                  hit[0] = control;
               }
            });
         }

         return rect(hit[0]);
      };
   }

   public static InventoryReference.Locator typed(UiType type) {
      return found -> {
         UiControl[] hit = new UiControl[]{null};
         UiControl root = found.instance().root();
         if (root != null) {
            root.walk(control -> {
               if (hit[0] == null && control.visible() && control.type() == type) {
                  hit[0] = control;
               }
            });
         }

         return rect(hit[0]);
      };
   }

   public static InventoryReference.Locator toggle(String panel) {
      return found -> {
         UiControl holder = found.instance().find(panel);
         UiControl[] hit = new UiControl[]{null};
         if (holder != null) {
            holder.walk(control -> {
               if (hit[0] == null && control.visible() && control.type() == UiType.TOGGLE) {
                  hit[0] = control;
               }
            });
         }

         return rect(hit[0]);
      };
   }

   public static InventoryReference.Locator cell(String collection, int index) {
      return found -> {
         UiControl[] hit = new UiControl[]{null};
         UiControl root = found.instance().root();
         if (root != null) {
            root.walk(
               control -> {
                  if (hit[0] == null
                     && control.visible()
                     && control.collectionIndex() == index
                     && collection.equals(control.itemCollection())
                     && (control.type() == UiType.INPUT_PANEL || control.parent() != null && control.parent().type() == UiType.GRID)) {
                     hit[0] = control;
                  }
               }
            );
         }

         return rect(hit[0]);
      };
   }

   public static InventoryReference.Locator text(String labelName) {
      return found -> {
         UiControl label = found.instance().find(labelName);
         if (label != null && label.visible() && !label.lines().isEmpty()) {
            for (UiDrawCommand command : found.instance().drawList().commands()) {
               if (command instanceof UiDrawCommand.Text text && text.control() == label) {
                  String line = text.lines().getFirst();
                  float width = found.measurer().width(line, text.font());

                  float left = switch (text.alignment()) {
                     case LEFT -> text.x();
                     case CENTER -> text.x() + (text.width() - width) / 2.0F;
                     case RIGHT -> text.x() + text.width() - width;
                  };
                  float top = text.y() + (found.measurer() instanceof UiTextMetrics metrics ? metrics.bitmapCellTop(text.font()) : 0.0F);
                  float spacing = text.font().scale();
                  return new float[]{left, top, left + width - spacing, top + 7.0F * text.font().scale()};
               }
            }

            return null;
         } else {
            return null;
         }
      };
   }

   public record Found(UiScreenInstance instance, UiTextMeasurer measurer) {
   }

   public static enum Layout {
      ITEMS("bedrock-real-creative-items.png", 450, 177),
      BOTH("bedrock-real-creative-both.png", 439, 217),
      INVENTORY("bedrock-real-creative-inventory.png", 663, 265);

      public final String picture;
      public final int frameX;
      public final int frameY;

      private Layout(String picture, int frameX, int frameY) {
         this.picture = picture;
         this.frameX = frameX;
         this.frameY = frameY;
      }
   }

   public interface Locator extends Function<InventoryReference.Found, float[]> {
   }

   public record Mask(List<float[]> include, List<float[]> exclude) {
      public boolean compared(int x, int y) {
         float ux = (x + 0.5F) / 2.0F;
         float uy = (y + 0.5F) / 2.0F;
         return inside(this.include, ux, uy) && !inside(this.exclude, ux, uy);
      }

      private static boolean inside(List<float[]> rects, float x, float y) {
         for (float[] r : rects) {
            if (x >= r[0] && y >= r[1] && x < r[2] && y < r[3]) {
               return true;
            }
         }

         return false;
      }
   }

   public record Rect(String what, InventoryReference.Locator locator, int x0, int y0, int x1, int y1, int slack, String note) {
      public boolean known() {
         return !this.note.isEmpty();
      }
   }

   public record Result(InventoryReference.Rect rect, @Nullable float[] ours, float off, boolean ok) {
      @Override
      public String toString() {
         return this.ours == null
            ? this.rect.what() + ": not found"
            : String.format(
               Locale.ROOT,
               "%s: ours x %.1f..%.1f y %.1f..%.1f, reference x %s..%s y %s..%s, %.1f px off%s%s",
               this.rect.what(),
               this.ours[0],
               this.ours[2] - 1.0F,
               this.ours[1],
               this.ours[3] - 1.0F,
               edge(this.rect.x0()),
               edge(this.rect.x1()),
               edge(this.rect.y0()),
               edge(this.rect.y1()),
               this.off,
               this.rect.known() ? " (known: " + this.rect.note() + ")" : "",
               this.ok ? "" : " OFF"
            );
      }

      private static String edge(int value) {
         return value == Integer.MIN_VALUE ? "?" : Integer.toString(value);
      }
   }
}
