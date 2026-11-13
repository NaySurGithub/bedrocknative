package net.bedrocknative.client.ui;

import java.util.List;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class MenuNotice {
   private static final long SHOWN_MS = 3000L;
   private static final long FADE_MS = 400L;
   private @Nullable Component text;
   private long shownAt;

   public void unavailable(String labelKey) {
      String label = BedrockText.translate(labelKey, List.of("0")).replaceAll("\\s*\\(0\\)$", "");
      this.show(Component.translatable("bedrocknative.menu.unavailable", BedrockText.format(label)));
   }

   public void show(Component message) {
      this.text = message;
      this.shownAt = Util.getMillis();
   }

   public @Nullable Component text() {
      return this.text != null && Util.getMillis() - this.shownAt < 3000L ? this.text : null;
   }

   public void render(GuiGraphicsExtractor graphics, int screenWidth) {
      Component shown = this.text();
      if (shown != null) {
         long age = Util.getMillis() - this.shownAt;
         float alpha = age > 2600L ? (float)(3000L - age) / 400.0F : 1.0F;
         Font font = Minecraft.getInstance().font;
         int width = font.width(shown);
         int x = (screenWidth - width) / 2;
         int y = 8;
         graphics.nextStratum();
         graphics.fill(x - 8, y - 6, x + width + 8, y + 9 + 5, (int)(alpha * 204.0F) << 24);
         graphics.text(font, shown, x, y, (int)(alpha * 255.0F) << 24 | 16777215, false);
      }
   }
}
