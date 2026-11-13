package net.bedrocknative.client.form;

import java.util.List;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.form.FormResponse;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import org.jspecify.annotations.Nullable;

public final class ModalFormScreen extends FormScreen {
   private static final int BUTTONS_HEIGHT = 64;
   private final BedrockForm.Modal form;
   private List<AbstractButton> buttons = List.of();

   public ModalFormScreen(FormScreen.Host host, int formId, BedrockForm.Modal form, @Nullable FormScreen below) {
      super(host, formId, form.title(), below);
      this.form = form;
   }

   public List<AbstractButton> buttons() {
      return this.buttons;
   }

   @Override
   protected int preferredWidth() {
      return 214;
   }

   @Override
   protected int preferredHeight() {
      return 175;
   }

   @Override
   protected int footerHeight() {
      return 64;
   }

   @Override
   protected boolean leftAlignedTitle() {
      return true;
   }

   @Override
   protected String dialogTexture() {
      return "textures/ui/dialog_background_hollow_1";
   }

   @Override
   protected int fillAlpha() {
      return 255;
   }

   @Override
   protected void buildBody(LinearLayout body, int width) {
      body.addChild(new FormTextWidget(this.font, BedrockText.ui(this.form.content()), width, FormTextWidget.Kind.TEXT));
   }

   @Override
   protected void buildFooter(int x, int y, int width, int height) {
      int buttonHeight = (height - 2) / 2;
      FormButton first = new FormButton(
         this.font, width, buttonHeight, BedrockText.ui(this.form.button1()), null, () -> this.host.submit(this, FormResponse.modal(true))
      );
      FormButton second = new FormButton(
         this.font, width, buttonHeight, BedrockText.ui(this.form.button2()), null, () -> this.host.submit(this, FormResponse.modal(false))
      );
      first.setPosition(x, y);
      second.setPosition(x, y + height - buttonHeight);
      this.buttons = List.of((AbstractButton)this.addRenderableWidget(first), (AbstractButton)this.addRenderableWidget(second));
   }
}
