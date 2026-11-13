package net.bedrocknative.oreui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class OreText {
   public static final String SEVEN = "Minecraft Seven v2";
   public static final String TEN = "Minecraft Ten v2";
   public static final String FIVE = "Minecraft Five v2";
   public static final String NOTO = "Noto Sans";
   public static final float LETTER_SPACING = 0.04F;
   private final OreText.Fonts fonts;
   private final OreScale scale;
   private final Map<String, Float> widths = new HashMap<>();

   public OreText(OreText.Fonts fonts, OreScale scale) {
      this.fonts = fonts;
      this.scale = scale;
   }

   public OreScale scale() {
      return this.scale;
   }

   public OreText.Fonts fonts() {
      return this.fonts;
   }

   public float sizePx(OreText.Type type) {
      return this.scale.px(type.sizeRem());
   }

   public float linePx(OreText.Type type) {
      return this.scale.px(type.lineRem());
   }

   public float letterSpacingPx(OreText.Type type) {
      return this.scale.px(type.spacingRem());
   }

   public @Nullable OreFont fontFor(OreText.Type type, int codePoint) {
      OreFont font = this.fonts.font(type.family(), type.weight());
      if (font != null && font.has(codePoint)) {
         return font;
      } else {
         OreFont fallback = this.fonts.fallback();
         if (fallback != null && fallback.has(codePoint)) {
            return fallback;
         } else {
            return font != null ? font : fallback;
         }
      }
   }

   public float width(OreText.Type type, String text) {
      String key = type.ordinal() + "\u0000" + text;
      Float cached = this.widths.get(key);
      if (cached != null) {
         return cached;
      }

      float size = this.sizePx(type);
      float spacing = this.letterSpacingPx(type);
      float width = 0.0F;
      int i = 0;

      while (i < text.length()) {
         int codePoint = text.codePointAt(i);
         i += Character.charCount(codePoint);
         OreFont font = this.fontFor(type, codePoint);
         width += (font == null ? 0.5F : font.advance(codePoint)) * size + spacing;
      }

      if (!text.isEmpty()) {
         width -= spacing;
      }

      if (this.widths.size() < 4096) {
         this.widths.put(key, width);
      }

      return width;
   }

   public List<OreText.Placed> place(OreText.Type type, String text) {
      List<OreText.Placed> placed = new ArrayList<>();
      float size = this.sizePx(type);
      float spacing = this.letterSpacingPx(type);
      float x = 0.0F;
      int i = 0;

      while (i < text.length()) {
         int codePoint = text.codePointAt(i);
         i += Character.charCount(codePoint);
         OreFont font = this.fontFor(type, codePoint);
         if (font != null) {
            placed.add(new OreText.Placed(codePoint, x, font));
            x += font.advance(codePoint) * size + spacing;
         }
      }

      return placed;
   }

   public float baseline(OreText.Type type) {
      OreFont font = this.fonts.font(type.family(), type.weight());
      float size = this.sizePx(type);
      float ascent = font == null ? 0.8F : font.ascent();
      float descent = font == null ? 0.2F : font.descent();
      float halfLeading = (float)Math.floor((this.linePx(type) - (ascent + descent) * size) / 2.0F);
      return halfLeading + ascent * size;
   }

   public String ellipsize(OreText.Type type, String text, float maxWidth) {
      if (this.width(type, text) <= maxWidth + 0.01F) {
         return text;
      }

      OreFont font = this.fonts.font(type.family(), type.weight());
      String dots = font != null && font.has(8230) ? "…" : "...";
      float dotsWidth = this.width(type, dots);
      int end = text.length();

      while (end > 0) {
         end = text.offsetByCodePoints(end, -1);
         String head = text.substring(0, end).stripTrailing();
         if (this.width(type, head) + dotsWidth <= maxWidth + 0.01F) {
            return head + dots;
         }
      }

      return dotsWidth <= maxWidth ? dots : "";
   }

   public List<String> wrap(OreText.Type type, String text, float maxWidth, int maxLines) {
      List<String> lines = new ArrayList<>();

      for (String paragraph : text.split("\n", -1)) {
         StringBuilder line = new StringBuilder();

         for (String word : paragraph.split(" ", -1)) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!(this.width(type, candidate) <= maxWidth) && (!line.isEmpty() || !(this.width(type, word) <= maxWidth))) {
               if (!line.isEmpty()) {
                  lines.add(line.toString());
                  line.setLength(0);
               }

               String rest = word;

               while (this.width(type, rest) > maxWidth && rest.length() > 1) {
                  int cut = rest.length();

                  while (cut > 1 && this.width(type, rest.substring(0, cut)) > maxWidth) {
                     cut = rest.offsetByCodePoints(cut, -1);
                  }

                  lines.add(rest.substring(0, cut));
                  rest = rest.substring(cut);
               }

               line.append(rest);
            } else {
               line.setLength(0);
               line.append(candidate);
            }
         }

         lines.add(line.toString());
      }

      if (lines.size() > maxLines) {
         List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
         cut.set(maxLines - 1, this.ellipsize(type, cut.get(maxLines - 1) + " " + lines.get(maxLines), maxWidth));
         return cut;
      } else {
         return lines;
      }
   }

   public interface Fonts {
      @Nullable OreFont font(String var1, int var2);

      @Nullable OreFont fallback();
   }

   public record Placed(int codePoint, float x, OreFont font) {
   }

   public enum Type {
      HEADER5A("Minecraft Ten v2", 400, 2.0F, 2.4F, 0.04F),
      HEADER4A("Minecraft Ten v2", 400, 2.4F, 2.8F, 0.04F),
      SECTION_HEADER("Minecraft Ten v2", 400, 1.6F, 2.0F, 0.04F),
      BODY("Minecraft Seven v2", 400, 1.6F, 2.0F, 0.04F),
      CAPTION_SHORT("Minecraft Seven v2", 400, 1.4F, 2.0F, 0.04F),
      CAPTION_TINY("Minecraft Seven v2", 400, 1.0F, 1.2F, 0.04F),
      CAPTION_LONG("Noto Sans", 400, 1.4F, 2.0F, 0.04F),
      PARAGRAPHS("Noto Sans", 400, 1.6F, 2.0F, 0.08F),
      PRIMARY_BUTTON("Minecraft Ten v2", 400, 2.0F, 2.4F, 0.04F),
      SECONDARY_BUTTON("Minecraft Seven v2", 400, 1.6F, 2.0F, 0.04F),
      TEXT_FIELD("Minecraft Seven v2", 400, 1.6F, 2.8F, 0.04F);

      private final String family;
      private final int weight;
      private final float size;
      private final float line;
      private final float spacing;

      Type(String family, int weight, float size, float line, float spacing) {
         this.family = family;
         this.weight = weight;
         this.size = size;
         this.line = line;
         this.spacing = spacing;
      }

      public float spacingRem() {
         return this.spacing;
      }

      public String family() {
         return this.family;
      }

      public int weight() {
         return this.weight;
      }

      public float sizeRem() {
         return this.size;
      }

      public float lineRem() {
         return this.line;
      }
   }
}
