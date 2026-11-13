package net.bedrocknative.client.ui;

import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class NameTagRenderer {
   public static final String RENDERER = "name_tag_renderer";

   private NameTagRenderer() {
   }

   public static void draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer, String name) {
      if (!name.isEmpty()) {
         Minecraft minecraft = Minecraft.getInstance();
         float k = JsonUi.toGui(1.0F);
         int width = minecraft.font.width(name);
         graphics.pose().pushMatrix();

         try {
            graphics.pose().translate(renderer.x() + renderer.w() / 2.0F, renderer.y());
            graphics.pose().scale(k, k);
            int x = -width / 2;
            int y = 0;
            int background = background(renderer.control());
            if (background != 0) {
               graphics.fill(x - 2, y - 1, x + width + 1, y + 9, background);
            }

            graphics.text(minecraft.font, name, x, y, -1, false);
         } finally {
            graphics.pose().popMatrix();
         }
      }
   }

   static int background(UiControl control) {
      UiValue value = control.prop("background_color");
      float[] rgba = value != null ? value.floats() : null;
      if (rgba != null && rgba.length >= 3) {
         float alpha = rgba.length > 3 ? rgba[3] : 1.0F;
         return Math.round(alpha * 255.0F) << 24 | Math.round(rgba[0] * 255.0F) << 16 | Math.round(rgba[1] * 255.0F) << 8 | Math.round(rgba[2] * 255.0F);
      } else {
         return 0;
      }
   }
}
