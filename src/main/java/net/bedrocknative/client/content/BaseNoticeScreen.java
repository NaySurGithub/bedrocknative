package net.bedrocknative.client.content;

import java.util.List;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class BaseNoticeScreen extends Screen {
   private static final int WHITE = -1;
   private static final int DIM = -5197648;
   private static final int WARN = -10133;
   private final Screen parent;
   private final Runnable seen;
   private boolean done;

   BaseNoticeScreen(Screen parent, Runnable seen) {
      super(Component.translatable("bedrocknative.base.notice.title"));
      this.parent = parent;
      this.seen = seen;
   }

   protected void init() {
      int y = Math.min(this.height - 28, this.height / 2 + 70);
      this.addRenderableWidget(
         Button.builder(Component.translatable("bedrocknative.base.notice.ok"), button -> this.dismiss()).bounds(this.width / 2 - 75, y, 150, 20).build()
      );
   }

   void dismiss() {
      if (!this.done) {
         this.done = true;
         BedrockNative.LOGGER.info("Base notice read");
         this.seen.run();
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   public void onClose() {
      this.dismiss();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      int wrap = Math.max(1, Math.min(this.width - 40, 380));
      int y = Math.max(8, this.height / 2 - 96);
      graphics.centeredText(this.font, this.title, this.width / 2, y, -1);
      y += 16;
      y = this.paragraph(graphics, Component.translatable("bedrocknative.base.notice.inside"), wrap, y, -1) + 6;
      y = this.paragraph(graphics, Component.translatable("bedrocknative.base.notice.personal"), wrap, y, -10133) + 6;
      this.paragraph(graphics, Component.translatable("bedrocknative.base.notice.install"), wrap, y, -5197648);
   }

   private int paragraph(GuiGraphicsExtractor graphics, Component text, int wrap, int y, int color) {
      List<FormattedCharSequence> lines = this.font.split(text, wrap);

      for (FormattedCharSequence line : lines.stream().limit(5L).toList()) {
         graphics.centeredText(this.font, line, this.width / 2, y, color);
         y += 10;
      }

      return y;
   }
}
