package net.bedrocknative.client.content;

import java.util.List;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.base.BaseConsent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public final class BaseDownloadScreen extends Screen {
   private static final int BUTTON_WIDTH = 100;
   private static final int GAP = 6;
   private static final int WHITE = -1;
   private static final int DIM = -5197648;
   private final Screen parent;
   private final String size;
   private final Consumer<BaseConsent.Answer> answer;
   private boolean answered;
   private @Nullable Button later;

   BaseDownloadScreen(Screen parent, String size, Consumer<BaseConsent.Answer> answer) {
      super(Component.translatable("bedrocknative.base.title"));
      this.parent = parent;
      this.size = size;
      this.answer = answer;
   }

   @Override
   protected void init() {
      int total = 312;
      int left = this.width / 2 - total / 2;
      int y = Math.min(this.height - 28, this.height / 2 + 60);
      this.addRenderableWidget(
         Button.builder(Component.translatable("bedrocknative.base.yes"), button -> this.choose(BaseConsent.Answer.YES)).bounds(left, y, 100, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("bedrocknative.base.no"), button -> this.choose(BaseConsent.Answer.NO))
            .bounds(left + 100 + 6, y, 100, 20)
            .build()
      );
      this.later = this.addRenderableWidget(
         Button.builder(Component.translatable("bedrocknative.base.later"), button -> this.choose(BaseConsent.Answer.LATER))
            .bounds(left + 212, y, 100, 20)
            .build()
      );
   }

   @Override
   protected void setInitialFocus() {
      if (this.later != null) {
         this.setInitialFocus(this.later);
      } else {
         super.setInitialFocus();
      }
   }

   void choose(BaseConsent.Answer picked) {
      if (!this.answered) {
         this.answered = true;
         BedrockNative.LOGGER.info("Base download question answered: {}", picked);
         this.answer.accept(picked);
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   @Override
   public void onClose() {
      this.choose(BaseConsent.Answer.LATER);
   }

   @Override
   public void removed() {
      super.removed();
      if (!this.answered) {
         this.answered = true;
         this.answer.accept(BaseConsent.Answer.LATER);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      int wrap = Math.max(1, Math.min(this.width - 40, 360));
      int y = Math.max(8, this.height / 2 - 92);
      graphics.centeredText(this.font, this.title, this.width / 2, y, -1);
      y += 16;
      y = this.paragraph(graphics, Component.translatable("bedrocknative.base.question"), wrap, y, -1) + 6;
      y = this.paragraph(graphics, Component.translatable("bedrocknative.base.why"), wrap, y, -5197648) + 4;
      y = this.paragraph(graphics, Component.translatable("bedrocknative.base.what", this.size), wrap, y, -5197648) + 4;
      y = this.paragraph(graphics, Component.translatable("bedrocknative.base.without"), wrap, y, -5197648) + 4;
      this.paragraph(graphics, Component.translatable("bedrocknative.base.eula"), wrap, y, -5197648);
   }

   private int paragraph(GuiGraphicsExtractor graphics, Component text, int wrap, int y, int color) {
      List<FormattedCharSequence> lines = this.font.split(text, wrap);

      for (FormattedCharSequence line : lines.stream().limit(4L).toList()) {
         graphics.centeredText(this.font, line, this.width / 2, y, color);
         y += 10;
      }

      return y;
   }
}
