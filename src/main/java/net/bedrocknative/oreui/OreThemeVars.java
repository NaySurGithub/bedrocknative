package net.bedrocknative.oreui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class OreThemeVars {
   private static final Pattern VARIABLE = Pattern.compile("--([A-Za-z0-9_-]+):([^;}]*)");
   private static final Pattern URL = Pattern.compile("url\\(\\s*['\"]?(?:/hbui/)?assets/([^'\")]+)['\"]?\\s*\\)");
   private static final Pattern NUMBER = Pattern.compile("(-?[0-9]*\\.?[0-9]+)(rem|px)?");
   private final Map<String, String> values;
   public static final OreThemeVars EMPTY = new OreThemeVars(Map.of());

   private OreThemeVars(Map<String, String> values) {
      this.values = Map.copyOf(values);
   }

   public static OreThemeVars parse(String css) {
      int open = css.indexOf(123);
      int close = open < 0 ? -1 : css.indexOf(125, open);
      if (open >= 0 && close >= 0) {
         Map<String, String> values = new HashMap<>();
         Matcher matcher = VARIABLE.matcher(css.substring(open + 1, close));

         while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2).trim());
         }

         return new OreThemeVars(values);
      } else {
         return EMPTY;
      }
   }

   public Optional<String> get(String name) {
      return Optional.ofNullable(this.values.get(name));
   }

   public int size() {
      return this.values.size();
   }

   public int color(String name, int fallback) {
      String value = this.values.get(name);
      Integer parsed = value == null ? null : parseColor(value);
      return parsed == null ? fallback : parsed;
   }

   public static @Nullable Integer parseColor(String text) {
      String value = text.trim().toLowerCase(Locale.ROOT);

      try {
         if (value.startsWith("#")) {
            String hex = value.substring(1);

            return switch (hex.length()) {
               case 3 -> 0xFF000000 | expand(hex.charAt(0)) << 16 | expand(hex.charAt(1)) << 8 | expand(hex.charAt(2));
               case 6 -> 0xFF000000 | Integer.parseInt(hex, 16);
               case 8 -> {
                  int rgba = (int)Long.parseLong(hex, 16);
                  yield (rgba & 0xFF) << 24 | rgba >>> 8;
               }
               default -> null;
            };
         } else {
            if (!value.startsWith("rgba(") && !value.startsWith("rgb(")) {
               return null;
            }

            String[] parts = value.substring(value.indexOf(40) + 1, value.lastIndexOf(41)).split(",");
            if (parts.length < 3) {
               return null;
            }

            int r = Math.clamp(Math.round(Float.parseFloat(parts[0].trim())), 0, 255);
            int g = Math.clamp(Math.round(Float.parseFloat(parts[1].trim())), 0, 255);
            int b = Math.clamp(Math.round(Float.parseFloat(parts[2].trim())), 0, 255);
            float a = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1.0F;
            return Math.clamp(Math.round(a * 255.0F), 0, 255) << 24 | r << 16 | g << 8 | b;
         }
      } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
         return null;
      }
   }

   private static int expand(char c) {
      int v = Character.digit(c, 16);
      return v << 4 | v;
   }

   public @Nullable OreBorderImage borderImage(String prefix) {
      String source = this.values.get(prefix + "BorderImageSource");
      String slice = this.values.get(prefix + "BorderImageSlice");
      if (source != null && slice != null) {
         Matcher url = URL.matcher(source);
         if (!url.find()) {
            return null;
         }

         String name = OreInstall.nameOf(url.group(1));
         if (name == null) {
            return null;
         }

         boolean fill = slice.contains("fill");
         float[] slices = edges(slice.replace("fill", ""), false);
         float[] widths = edges(this.values.getOrDefault(prefix + "BorderImageWidth", ""), true);
         float[] outset = edges(this.values.getOrDefault(prefix + "BorderImageOutset", "0"), true);
         float[] borders = new float[]{
            length(this.values.get(prefix + "BorderTopWidth")),
            length(this.values.get(prefix + "BorderRightWidth")),
            length(this.values.get(prefix + "BorderBottomWidth")),
            length(this.values.get(prefix + "BorderLeftWidth"))
         };
         return slices != null && widths != null
            ? new OreBorderImage(
               name,
               Math.round(slices[0]),
               Math.round(slices[1]),
               Math.round(slices[2]),
               Math.round(slices[3]),
               fill,
               widths,
               outset == null ? new float[4] : outset,
               borders
            )
            : null;
      } else {
         return null;
      }
   }

   static float @Nullable [] edges(String text, boolean lengths) {
      String[] parts = text.trim().split("\\s+");
      if (parts.length != 0 && parts.length <= 4 && !parts[0].isEmpty()) {
         float[] read = new float[parts.length];

         for (int i = 0; i < parts.length; i++) {
            Matcher number = NUMBER.matcher(parts[i]);
            if (!number.matches()) {
               return null;
            }

            if (lengths && "px".equals(number.group(2))) {
               return null;
            }

            read[i] = Float.parseFloat(number.group(1));
         }
         return switch (read.length) {
            case 1 -> new float[]{read[0], read[0], read[0], read[0]};
            case 2 -> new float[]{read[0], read[1], read[0], read[1]};
            case 3 -> new float[]{read[0], read[1], read[2], read[1]};
            default -> read;
         };
      } else {
         return null;
      }
   }

   static float length(@Nullable String text) {
      if (text == null) {
         return 0.0F;
      }

      Matcher number = NUMBER.matcher(text.trim());
      return number.matches() ? Float.parseFloat(number.group(1)) : 0.0F;
   }
}
