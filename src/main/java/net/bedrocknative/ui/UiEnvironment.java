package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public record UiEnvironment(UiTextMeasurer measurer, UiTextures textures, UiEnvironment.Localizer localizer) {
   public static final UiEnvironment TEST = new UiEnvironment(UiTextMeasurer.FIXED, UiTextures.NONE, key -> null);

   public String localize(String text) {
      String shown = text;
      if (!text.isEmpty() && text.length() <= 256 && text.indexOf(32) < 0) {
         String translated = this.localizer.lookup(text);
         if (translated != null) {
            shown = translated;
         }
      }

      return shown.contains("%%") ? shown.replace("%%", "%") : shown;
   }

   public interface Localizer {
      @Nullable
      String lookup(String var1);
   }
}
