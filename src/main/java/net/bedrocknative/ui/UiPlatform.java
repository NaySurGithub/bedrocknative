package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import java.util.HashMap;
import java.util.Map;

public final class UiPlatform {
   private UiPlatform() {
   }

   public static Map<String, UiValue> desktop() {
      return Map.ofEntries(
         Map.entry("$desktop_screen", UiValue.TRUE),
         Map.entry("$win10_edition", UiValue.TRUE),
         Map.entry("$pocket_screen", UiValue.FALSE),
         Map.entry("$pocket_edition", UiValue.FALSE),
         Map.entry("$is_ps4", UiValue.FALSE),
         Map.entry("$is_xbox", UiValue.FALSE),
         Map.entry("$is_nx", UiValue.FALSE),
         Map.entry("$education_edition", UiValue.FALSE),
         Map.entry("$is_holographic", UiValue.FALSE),
         Map.entry("$compress_hud_width", UiValue.FALSE),
         Map.entry("$is_editor_mode_enabled", UiValue.FALSE),
         Map.entry("$touch", UiValue.FALSE),
         Map.entry("$horizontal_safezone_size", UiValue.ZERO),
         Map.entry("$vertical_safezone_size", UiValue.ZERO),
         Map.entry("$left_horizontal_safezone_size", size(0, "100%")),
         Map.entry("$right_horizontal_safezone_size", size(0, "100%")),
         Map.entry("$top_vertical_safezone_size", size("100%", 0)),
         Map.entry("$bottom_vertical_safezone_size", size("100%", 0))
      );
   }

   private static UiValue size(Object x, Object y) {
      JsonArray array = new JsonArray();

      for (Object part : new Object[]{x, y}) {
         if (part instanceof Number number) {
            array.add(number);
         } else {
            array.add(part.toString());
         }
      }

      return UiValue.of(array);
   }

   public static Map<String, UiValue> desktop(Map<String, UiValue> screen) {
      HashMap<String, UiValue> all = new HashMap<>(desktop());
      all.putAll(screen);
      return all;
   }
}
