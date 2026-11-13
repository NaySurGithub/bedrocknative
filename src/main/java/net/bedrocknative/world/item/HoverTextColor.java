package net.bedrocknative.world.item;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.bedrocknative.ui.UiFormatting;
import org.jspecify.annotations.Nullable;

public final class HoverTextColor {
   private static final Map<String, Character> CODES = codes();

   private HoverTextColor() {
   }

   private static Map<String, Character> codes() {
      String[] names = new String[]{
         "black",
         "dark_blue",
         "dark_green",
         "dark_aqua",
         "dark_red",
         "dark_purple",
         "gold",
         "gray",
         "dark_gray",
         "blue",
         "green",
         "aqua",
         "red",
         "light_purple",
         "yellow",
         "white",
         "minecoin_gold",
         "material_quartz",
         "material_iron",
         "material_netherite",
         "material_redstone",
         "material_copper",
         "material_gold",
         "material_emerald",
         "material_diamond",
         "material_lapis",
         "material_amethyst",
         "material_resin"
      };
      String codes = "0123456789abcdefghijmnpqstuv";
      Map<String, Character> map = new HashMap<>();

      for (int i = 0; i < names.length; i++) {
         map.put(normalise(names[i]), codes.charAt(i));
      }

      return Map.copyOf(map);
   }

   private static String normalise(String name) {
      return name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
   }

   public static @Nullable Integer rgb(@Nullable String value) {
      if (value != null && !value.isBlank()) {
         String trimmed = value.trim();
         if (trimmed.length() == 2 && trimmed.charAt(0) == 167) {
            int rgb = UiFormatting.color(trimmed.charAt(1));
            return rgb >= 0 ? rgb : null;
         }

         if (trimmed.length() == 7 && trimmed.charAt(0) == '#') {
            try {
               return Integer.parseInt(trimmed.substring(1), 16);
            } catch (NumberFormatException e) {
               return null;
            }
         } else {
            Character code = CODES.get(normalise(trimmed));
            return code == null ? null : UiFormatting.color(code);
         }
      } else {
         return null;
      }
   }
}
