package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public record UiGradient(int from, int to, boolean horizontal) {
   public static final String RENDERER = "gradient_renderer";

   @Nullable
   public static UiGradient of(UiDrawCommand.Custom command) {
      if (!"gradient_renderer".equals(command.renderer())) {
         return null;
      } else {
         UiControl control = command.control();
         float alpha = (command.argb() >>> 24) / 255.0F;
         int from = color(control.prop("color1"), alpha);
         int to = color(control.prop("color2"), alpha);
         if (from >>> 24 == 0 && to >>> 24 == 0) {
            return null;
         } else {
            UiValue direction = control.prop("gradient_direction");
            return new UiGradient(from, to, direction != null && "horizontal".equals(direction.stringOrNull()));
         }
      }
   }

   static int color(@Nullable UiValue value, float alpha) {
      float[] parts = value == null ? null : value.floats();
      return parts != null && parts.length >= 3 && parts.length <= 4
         ? UiDrawList.argb(new float[]{parts[0], parts[1], parts[2], parts.length == 4 ? parts[3] : 1.0F}, alpha)
         : 0;
   }

   public int at(float t) {
      float k = Math.max(0.0F, Math.min(1.0F, t));
      int color = 0;

      for (int shift = 0; shift <= 24; shift += 8) {
         int a = this.from >>> shift & 0xFF;
         int b = this.to >>> shift & 0xFF;
         color |= Math.round(a + (b - a) * k) << shift;
      }

      return color;
   }
}
