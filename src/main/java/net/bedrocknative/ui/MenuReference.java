package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class MenuReference {
   public static final int SCALE = 2;
   public static final int WIDTH = 1920;
   public static final int HEIGHT = 1080;

   private MenuReference() {
   }

   public static List<MenuReference.Rect> start() {
      List<MenuReference.Rect> rects = new ArrayList<>();
      rects.add(rect("logo (drawn)", drawn(named("title_panel_win10", "title_image")), 433, 158, 1487, 339));
      rects.add(rect("Jouer", button("button.menu_play"), 812, 568, 1107, 627));
      rects.add(rect("Paramètres", button("button.menu_settings"), 812, 632, 1107, 691));
      rects.add(rect("Realms", button("button.menu_realms"), 812, 696, 1107, 755));
      rects.add(rect("Magasin", button("button.menu_store"), 812, 760, 1107, 819));
      rects.add(rect("Social", button("button.friends_drawer"), 1750, 10, 1909, 57));
      rects.add(rect("inbox", button("button.menu_inbox"), 10, 916, 57, 963));
      rects.add(rect("Profil", button("button.menu_profile"), 72, 916, 187, 963));
      rects.add(rect("Vestiaire (its border)", border(button("button.to_profile_screen")), 1459, 914, 1566, 963));
      rects.add(rect("paper doll renderer", named("paper_doll"), 1110, 682, 1919, 861));
      rects.add(rect("name tag (top middle)", topMiddle(named("paper_doll_name_tag")), 1515, 700, 1515, 700));
      rects.add(rect("©Mojang AB (left edge)", leftEdge(label("©Mojang AB")), 2, 1058, 2, 1077));
      rects.add(rect("version", label(null), 1854, 1058, 1917, 1077));
      rects.add(rect("splash control (middle)", middle(named("splash_text")), 1472, 269, 1472, 269));
      return rects;
   }

   public static List<MenuReference.Rect> pause() {
      List<MenuReference.Rect> rects = new ArrayList<>();
      rects.add(rect("left panel (right edge)", rightEdge(named("menu")), 1068, 0, 1068, 0));
      rects.add(rect("logo (drawn)", drawn(named("title_image", "image")), 216, 322, 911, 441));
      rects.add(rect("Reprendre le jeu", button("button.menu_continue"), 216, 459, 911, 514));
      rects.add(rect("Paramètres", button("button.menu_settings"), 216, 521, 911, 576));
      rects.add(rect("Parcourez les extensions !", button("button.menu_store"), 216, 583, 911, 638));
      rects.add(rect("Sauv. et quitter", button("button.menu_quit"), 216, 645, 911, 700));
      rects.add(rect("achievements", button("button.menu_achievements"), 469, 709, 516, 756));
      rects.add(rect("how to play", button("button.menu_how_to_play"), 538, 709, 585, 756));
      rects.add(rect("screenshot", button("button.screenshot"), 608, 709, 655, 756));
      rects.add(
         new MenuReference.Rect(
            "Jeu en pause",
            named("pause_text"),
            1552,
            10,
            1719,
            57,
            10,
            "Bedrock makes the Social panel 87 px wide (the 80 px button and its 7 px badge at offset [2,-3]), our %c union 82"
         )
      );
      rects.add(rect("Social", button("button.friends_drawer"), 1750, 10, 1909, 57));
      rects.add(rect("paper doll renderer", named("paper_doll"), 1110, 329, 1919, 648));
      rects.add(rect("name tag (top middle)", topMiddle(named("paper_doll_name_tag")), 1515, 387, 1515, 387));
      rects.add(rect("Vestiaire", button("button.to_profile_or_skins_screen"), 1461, 681, 1568, 728));
      return rects;
   }

   public static List<MenuReference.Result> compare(MenuReference.Found found, List<MenuReference.Rect> rects, float scale) {
      List<MenuReference.Result> results = new ArrayList<>();

      for (MenuReference.Rect rect : rects) {
         float[] ui = rect.locator().apply(found);
         if (ui == null) {
            results.add(new MenuReference.Result(rect, null, Float.POSITIVE_INFINITY, false));
         } else {
            float[] ours = new float[]{ui[0] * scale, ui[1] * scale, ui[2] * scale, ui[3] * scale};
            float[] expected = new float[]{rect.x0(), rect.y0(), rect.x1() + 1, rect.y1() + 1};
            float off = 0.0F;

            for (int i = 0; i < 4; i++) {
               off = Math.max(off, Math.abs(ours[i] - expected[i]));
            }

            results.add(new MenuReference.Result(rect, ours, off, off <= rect.slack()));
         }
      }

      return results;
   }

   private static MenuReference.Rect rect(String what, MenuReference.Locator locator, int x0, int y0, int x1, int y1) {
      return new MenuReference.Rect(what, locator, x0, y0, x1, y1, 1, "");
   }

   @Nullable
   private static float[] rect(@Nullable UiControl control) {
      return control == null ? null : new float[]{control.x(), control.y(), control.x() + control.width(), control.y() + control.height()};
   }

   public static MenuReference.Locator button(String id) {
      return found -> rect(found.input().firing(id, null, -1));
   }

   public static MenuReference.Locator named(String... path) {
      return found -> rect(find(found, path));
   }

   @Nullable
   private static UiControl find(MenuReference.Found found, String... path) {
      UiControl control = found.instance().root();

      for (String name : path) {
         if (control == null) {
            return null;
         }

         control = control.find(name);
      }

      return control;
   }

   public static MenuReference.Locator border(MenuReference.Locator button) {
      return found -> {
         float[] outer = button.apply(found);
         if (outer == null) {
            return null;
         } else {
            UiControl[] border = new UiControl[]{null};
            found.instance()
               .root()
               .walk(
                  control -> {
                     if (border[0] == null
                        && control.visible()
                        && control.name().equals("border")
                        && control.x() >= outer[0] - 0.01F
                        && control.y() >= outer[1] - 0.01F
                        && control.x() + control.width() <= outer[2] + 0.01F
                        && control.y() + control.height() <= outer[3] + 0.01F) {
                        border[0] = control;
                     }
                  }
               );
            return rect(border[0]);
         }
      };
   }

   public static MenuReference.Locator drawn(MenuReference.Locator image) {
      return found -> {
         float[] control = image.apply(found);
         if (control == null) {
            return null;
         } else {
            float[] box = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

            for (UiDrawCommand command : found.instance().drawList().commands()) {
               if (command instanceof UiDrawCommand.Image drawn
                  && drawn.control().x() == control[0]
                  && drawn.control().y() == control[1]
                  && drawn.control().x() + drawn.control().width() == control[2]
                  && drawn.control().y() + drawn.control().height() == control[3]) {
                  for (UiDrawCommand.Quad quad : drawn.quads()) {
                     box[0] = Math.min(box[0], quad.x());
                     box[1] = Math.min(box[1], quad.y());
                     box[2] = Math.max(box[2], quad.x() + quad.w());
                     box[3] = Math.max(box[3], quad.y() + quad.h());
                  }
               }
            }

            return box[0] == Float.MAX_VALUE ? null : box;
         }
      };
   }

   public static MenuReference.Locator label(@Nullable String text) {
      return found -> {
         UiControl[] label = new UiControl[]{null};
         found.instance()
            .root()
            .walk(
               control -> {
                  if (label[0] == null
                     && control.type() == UiType.LABEL
                     && control.visible()
                     && !control.text().isEmpty()
                     && (
                        text != null
                           ? control.text().equals(text)
                           : control.text().startsWith("v") && control.parent() != null && control.parent().name().equals("version")
                     )) {
                     label[0] = control;
                  }
               }
            );
         return rect(label[0]);
      };
   }

   public static MenuReference.Locator topMiddle(MenuReference.Locator locator) {
      return point(locator, true, false);
   }

   public static MenuReference.Locator middle(MenuReference.Locator locator) {
      return point(locator, true, true);
   }

   private static MenuReference.Locator point(MenuReference.Locator locator, boolean middleX, boolean middleY) {
      return found -> {
         float[] r = locator.apply(found);
         if (r == null) {
            return null;
         } else {
            float x = middleX ? (r[0] + r[2]) / 2.0F : r[0];
            float y = middleY ? (r[1] + r[3]) / 2.0F : r[1];
            return new float[]{x, y, x + 0.5F, y + 0.5F};
         }
      };
   }

   public static MenuReference.Locator leftEdge(MenuReference.Locator locator) {
      return found -> {
         float[] r = locator.apply(found);
         return r == null ? null : new float[]{r[0], r[1], r[0] + 0.5F, r[3]};
      };
   }

   public static MenuReference.Locator rightEdge(MenuReference.Locator locator) {
      return found -> {
         float[] r = locator.apply(found);
         return r == null ? null : new float[]{r[2] - 0.5F, 0.0F, r[2], 0.5F};
      };
   }

   public record Found(UiScreenInstance instance, UiInput input) {
   }

   public interface Locator extends Function<MenuReference.Found, float[]> {
   }

   public record Rect(String what, MenuReference.Locator locator, int x0, int y0, int x1, int y1, int slack, String note) {
      boolean known() {
         return !this.note.isEmpty();
      }
   }

   public record Result(MenuReference.Rect rect, @Nullable float[] ours, float off, boolean ok) {
      @Override
      public String toString() {
         return this.ours == null
            ? this.rect.what() + ": not found"
            : String.format(
               Locale.ROOT,
               "%s: ours x %.1f..%.1f y %.1f..%.1f, reference x %d..%d y %d..%d, %.1f px off%s%s",
               this.rect.what(),
               this.ours[0],
               this.ours[2] - 1.0F,
               this.ours[1],
               this.ours[3] - 1.0F,
               this.rect.x0(),
               this.rect.x1(),
               this.rect.y0(),
               this.rect.y1(),
               this.off,
               this.rect.known() ? " (known: " + this.rect.note() + ")" : "",
               this.ok ? "" : " OFF"
            );
      }
   }
}
