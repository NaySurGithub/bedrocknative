package net.bedrocknative.client.form;

import java.util.List;
import net.bedrocknative.form.FormImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

final class FormButton extends AbstractButton {
   static final int HEIGHT = 32;
   private static final int IMAGE_COLUMN = 34;
   private final Font font;
   private final @Nullable FormImage image;
   private final Runnable action;

   FormButton(Font font, int width, int height, Component text, @Nullable FormImage image, Runnable action) {
      super(0, 0, width, height, text);
      this.font = font;
      this.image = image;
      this.action = action;
   }

   @Override
   public void onPress(InputWithModifiers input) {
      this.action.run();
   }

   @Override
   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      int left = this.getX();
      if (this.image != null) {
         BedrockUi.formImage(graphics, this.image, this.getX(), this.getY() + (this.getHeight() - 32) / 2);
         left += 34;
      }

      int width = this.getRight() - left;
      boolean lit = this.isHoveredOrFocused() && this.active;
      String texture = !this.active
         ? "textures/ui/disabledButtonNoBorder"
         : (lit ? "textures/ui/button_borderless_lighthover" : "textures/ui/button_borderless_light");
      BedrockUi.nineSlice(graphics, texture, left, this.getY(), width, this.getHeight(), -1, lit ? -7566196 : -3750202);
      List<FormattedCharSequence> lines = this.font.split(this.getMessage(), width - 8);
      if (lines.size() > 2) {
         lines = lines.subList(0, 2);
      }

      int y = this.getY() + (this.getHeight() - lines.size() * 9) / 2 + 1;

      for (FormattedCharSequence line : lines) {
         graphics.text(this.font, line, left + (width - this.font.width(line)) / 2, y, lit ? -1 : -11776948, false);
         y += 9;
      }
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }
}
