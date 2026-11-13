package net.bedrocknative.client.form;

import java.util.List;
import java.util.Objects;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.ScrollableLayout.ReserveStrategy;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public abstract class FormScreen extends Screen {
   private static final int TITLE_BAND = 23;
   private static final int CLOSE_SIZE = 15;
   protected final FormScreen.Host host;
   private final int formId;
   @Nullable
   private final FormScreen below;
   private boolean answered;
   protected int panelX;
   protected int panelY;
   protected int panelWidth;
   protected int panelHeight;

   protected FormScreen(FormScreen.Host host, int formId, String title, @Nullable FormScreen below) {
      super(BedrockText.ui(title));
      this.host = host;
      this.formId = formId;
      this.below = below;
   }

   public int formId() {
      return this.formId;
   }

   @Nullable
   public FormScreen below() {
      return this.below;
   }

   public boolean isAnswered() {
      return this.answered;
   }

   public boolean markAnswered() {
      if (this.answered) {
         return false;
      } else {
         this.answered = true;
         return true;
      }
   }

   public boolean isServerSettings() {
      return false;
   }

   @Nullable
   public Screen settingsParent() {
      return null;
   }

   protected int preferredWidth() {
      return 225;
   }

   protected int preferredHeight() {
      return 200;
   }

   protected int footerHeight() {
      return 0;
   }

   protected boolean leftAlignedTitle() {
      return false;
   }

   protected String dialogTexture() {
      return "textures/ui/dialog_background_hollow_3";
   }

   protected int fillAlpha() {
      return 204;
   }

   protected abstract void buildBody(LinearLayout var1, int var2);

   protected void buildFooter(int x, int y, int width, int height) {
   }

   protected void init() {
      this.panelWidth = Math.min(this.preferredWidth(), this.width - 8);
      this.panelHeight = Math.min(this.preferredHeight(), this.height - 8);
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = (this.height - this.panelHeight) / 2;
      int paneWidth = this.panelWidth - 20;
      int footer = this.footerHeight();
      LinearLayout content = LinearLayout.vertical().spacing(2);
      this.buildBody(content, paneWidth - 8);
      ScrollableLayout body = new ScrollableLayout(this.minecraft, content, this.panelHeight - 23 - 10 - (footer > 0 ? footer + 9 : 0), ReserveStrategy.RIGHT);
      body.setScrollbarSpacing(2);
      body.arrangeElements();
      body.setX(this.panelX + 10);
      body.setY(this.panelY + 23 + 2);
      body.visitWidgets(x$0 -> this.addRenderableWidget(x$0));
      if (footer > 0) {
         this.buildFooter(this.panelX + 7, this.panelY + this.panelHeight - 7 - footer, this.panelWidth - 14, footer);
      }

      this.addRenderableWidget(new FormScreen.CloseButton(this.panelX + this.panelWidth - 15 - 5, this.panelY + 4));
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.extractTransparentBackground(graphics);
      String dialog = this.dialogTexture();
      BedrockUi.nineSlice(graphics, dialog, this.panelX, this.panelY, this.panelWidth, this.panelHeight, -1, -3750202);
      int bottomBand = BedrockTextures.nineSlice(dialog)
         .map(BedrockTextures.NineSlice::bottom)
         .orElse(8 + (this.footerHeight() > 0 ? this.footerHeight() + 7 : 0));
      int innerBottom = Math.max(this.panelY + 23, this.panelY + this.panelHeight - bottomBand);
      int alpha = this.fillAlpha() << 24;
      BedrockTextures.pack("textures/ui/control")
         .ifPresentOrElse(
            control -> graphics.blit(
               RenderPipelines.GUI_TEXTURED,
               control.id(),
               this.panelX + 8,
               this.panelY + 23,
               0.0F,
               0.0F,
               this.panelWidth - 16,
               innerBottom - this.panelY - 23,
               control.width(),
               control.height(),
               control.width(),
               control.height(),
               alpha | 16777215
            ),
            () -> graphics.fill(this.panelX + 8, this.panelY + 23, this.panelX + this.panelWidth - 8, innerBottom, alpha | 3223857)
         );
      List<FormattedCharSequence> title = this.font.split(this.getTitle(), this.panelWidth - 46);
      if (!title.isEmpty()) {
         int x = this.leftAlignedTitle() ? this.panelX + 9 : this.panelX + (this.panelWidth - this.font.width(title.getFirst())) / 2;
         graphics.text(this.font, title.getFirst(), x, this.panelY + 9, -11776948, false);
      }

      this.minecraft.gui.hud.extractDeferredSubtitles();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.host.cancel(this);
   }

   public void removed() {
      this.host.removed(this);
   }

   private final class CloseButton extends AbstractButton {
      CloseButton(int x, int y) {
         Objects.requireNonNull(FormScreen.this);
         super(x, y, 15, 15, CommonComponents.GUI_CANCEL);
      }

      public void onPress(InputWithModifiers input) {
         FormScreen.this.onClose();
      }

      protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
         String texture = this.isHoveredOrFocused() ? "textures/ui/close_button_hover" : "textures/ui/close_button_default";
         BedrockTextures.pack(texture)
            .ifPresentOrElse(
               sprite -> BedrockUi.image(graphics, sprite, this.getX(), this.getY(), this.getWidth(), this.getHeight()),
               () -> graphics.centeredText(FormScreen.this.font, Component.literal("x"), this.getX() + this.getWidth() / 2, this.getY() + 4, -11776948)
            );
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
         this.defaultButtonNarrationText(output);
      }
   }

   public interface Host {
      void submit(FormScreen var1, String var2);

      void cancel(FormScreen var1);

      void removed(FormScreen var1);
   }
}
