package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public record UiFont(String type, float scale) {
   public static final UiFont DEFAULT = new UiFont("default", 1.0F);

   public static float sizeFactor(@Nullable String fontSize) {
      if (fontSize == null) {
         return 1.0F;
      }

      return switch (fontSize) {
         case "small" -> 0.8F;
         case "normal", "medium" -> 1.0F;
         case "large" -> 1.5F;
         case "extra_large" -> 2.0F;
         default -> 1.0F;
      };
   }
}
