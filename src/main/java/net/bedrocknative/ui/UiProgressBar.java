package net.bedrocknative.ui;

import java.util.List;

public final class UiProgressBar {
   private UiProgressBar() {
   }

   public static List<UiProgressBar.Fill> fills(UiDrawCommand.Custom command) {
      UiControl c = command.control();
      if (!truthy(c.bag("#progress_bar_visible")) && !truthy(c.bag("#touch_progress_bar_visible"))) {
         return List.of();
      } else {
         double total = number(c.bag("#progress_bar_total_amount"));
         if (total > 0.0 && !(command.w() <= 0.0F) && !(command.h() <= 0.0F)) {
            float fraction = (float)Math.max(0.0, Math.min(1.0, number(c.bag("#progress_bar_current_amount")) / total));
            float width = command.w() * fraction;
            if (truthy(c.bag("round_value"))) {
               width = Math.round(width);
            }

            int alpha = command.argb() >>> 24;
            int color;
            if (truthy(c.bag("is_durability"))) {
               color = hsv(fraction / 3.0F, 1.0F, 1.0F);
            } else if (truthy(c.bag("is_storage_bar"))) {
               color = rgb(fraction >= 1.0F ? c.prop("full_storage_color") : c.prop("primary_color"), command.argb());
            } else {
               color = rgb(c.prop("primary_color"), command.argb());
            }

            UiProgressBar.Fill bar = new UiProgressBar.Fill(command.x(), command.y(), width, command.h(), alpha << 24 | color & 16777215);
            if (!truthy(c.bag("drop_shadow"))) {
               return width > 0.0F ? List.of(bar) : List.of();
            } else {
               UiProgressBar.Fill shadow = new UiProgressBar.Fill(command.x(), command.y(), command.w(), command.h() * 2.0F, alpha << 24);
               return width > 0.0F ? List.of(shadow, bar) : List.of(shadow);
            }
         } else {
            return List.of();
         }
      }
   }

   private static boolean truthy(UiValue value) {
      return value != null && value.truthy();
   }

   private static double number(UiValue value) {
      return value != null && !value.isNull() ? value.asNumber() : 0.0;
   }

   private static int rgb(UiValue value, int fallback) {
      float[] parts = value == null ? null : value.floats();
      return parts != null && parts.length >= 3 ? channel(parts[0]) << 16 | channel(parts[1]) << 8 | channel(parts[2]) : fallback & 16777215;
   }

   private static int channel(float value) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
   }

   static int hsv(float hue, float saturation, float value) {
      int sector = (int)(hue * 6.0F) % 6;
      float f = hue * 6.0F - (int)(hue * 6.0F);
      float p = value * (1.0F - saturation);
      float q = value * (1.0F - f * saturation);
      float t = value * (1.0F - (1.0F - f) * saturation);
      float r;
      float g;
      float b;
      switch (sector) {
         case 0:
            r = value;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = value;
            b = p;
            break;
         case 2:
            r = p;
            g = value;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = value;
            break;
         case 4:
            r = t;
            g = p;
            b = value;
            break;
         default:
            r = value;
            g = p;
            b = q;
      }

      return truncated(r) << 16 | truncated(g) << 8 | truncated(b);
   }

   private static int truncated(float value) {
      return Math.max(0, Math.min(255, (int)(value * 255.0F)));
   }

   public record Fill(float x, float y, float w, float h, int argb) {
   }
}
