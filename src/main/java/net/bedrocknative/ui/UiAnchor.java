package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public enum UiAnchor {
   TOP_LEFT(0.0F, 0.0F),
   TOP_MIDDLE(0.5F, 0.0F),
   TOP_RIGHT(1.0F, 0.0F),
   LEFT_MIDDLE(0.0F, 0.5F),
   CENTER(0.5F, 0.5F),
   RIGHT_MIDDLE(1.0F, 0.5F),
   BOTTOM_LEFT(0.0F, 1.0F),
   BOTTOM_MIDDLE(0.5F, 1.0F),
   BOTTOM_RIGHT(1.0F, 1.0F);

   public final float x;
   public final float y;

   UiAnchor(float x, float y) {
      this.x = x;
      this.y = y;
   }

   public static UiAnchor of(@Nullable String name) {
      if (name == null) {
         return CENTER;
      }

      return switch (name.trim()) {
         case "top_left" -> TOP_LEFT;
         case "top_middle" -> TOP_MIDDLE;
         case "top_right" -> TOP_RIGHT;
         case "left_middle" -> LEFT_MIDDLE;
         case "right_middle" -> RIGHT_MIDDLE;
         case "bottom_left" -> BOTTOM_LEFT;
         case "bottom_middle" -> BOTTOM_MIDDLE;
         case "bottom_right" -> BOTTOM_RIGHT;
         default -> CENTER;
      };
   }
}
