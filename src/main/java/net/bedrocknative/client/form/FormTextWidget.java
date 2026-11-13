package net.bedrocknative.client.form;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class FormTextWidget extends AbstractWidget {
   private final Font font;
   private final FormTextWidget.Kind kind;
   private final List<FormattedCharSequence> lines;

   FormTextWidget(Font font, Component text, int width, FormTextWidget.Kind kind) {
      super(0, 0, width, 0, text);
      this.font = font;
      this.kind = kind;
      Component shown = (Component)(kind == FormTextWidget.Kind.HEADER ? text.copy().withStyle(ChatFormatting.BOLD) : text);
      this.lines = kind == FormTextWidget.Kind.DIVIDER ? List.of() : font.split(shown, width - 4);
      this.setHeight(kind == FormTextWidget.Kind.DIVIDER ? 7 : Math.max(1, this.lines.size()) * 9 + (kind == FormTextWidget.Kind.HEADER ? 3 : 1));
      this.active = false;
   }

   static FormTextWidget divider(Font font, int width) {
      return new FormTextWidget(font, Component.empty(), width, FormTextWidget.Kind.DIVIDER);
   }

   protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      if (this.kind == FormTextWidget.Kind.DIVIDER) {
         int y = this.getY() + this.getHeight() / 2;
         graphics.fill(this.getX() + 2, y, this.getRight() - 2, y + 1, -2130706433);
      } else {
         int y = this.getY() + (this.kind == FormTextWidget.Kind.HEADER ? 2 : 1);

         for (FormattedCharSequence line : this.lines) {
            graphics.text(this.font, line, this.getX() + 2, y, -1, false);
            y += 9;
         }
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, this.getMessage());
   }

   static enum Kind {
      TEXT,
      HEADER,
      DIVIDER;
   }
}
