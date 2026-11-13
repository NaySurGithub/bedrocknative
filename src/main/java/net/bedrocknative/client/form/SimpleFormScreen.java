package net.bedrocknative.client.form;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.form.FormElement;
import net.bedrocknative.form.FormResponse;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import org.jspecify.annotations.Nullable;

public final class SimpleFormScreen extends FormScreen {
   private final BedrockForm.Simple form;
   private final List<AbstractButton> buttons = new ArrayList<>();

   public SimpleFormScreen(FormScreen.Host host, int formId, BedrockForm.Simple form, @Nullable FormScreen below) {
      super(host, formId, form.title(), below);
      this.form = form;
   }

   public List<AbstractButton> buttons() {
      return this.buttons;
   }

   @Override
   protected void buildBody(LinearLayout body, int width) {
      this.buttons.clear();
      if (!this.form.content().isEmpty()) {
         body.addChild(new FormTextWidget(this.font, BedrockText.ui(this.form.content()), width, FormTextWidget.Kind.TEXT));
      }

      for (FormElement element : this.form.elements()) {
         switch (element) {
            case FormElement.Button button:
               int index = this.buttons.size();
               FormButton widget = new FormButton(
                  this.font, width, 32, BedrockText.ui(button.text()), button.image(), () -> this.host.submit(this, FormResponse.button(index))
               );
               this.buttons.add(widget);
               body.addChild(widget);
               break;
            case FormElement.Header header:
               body.addChild(new FormTextWidget(this.font, BedrockText.ui(header.text()), width, FormTextWidget.Kind.HEADER));
               break;
            case FormElement.Label label:
               body.addChild(new FormTextWidget(this.font, BedrockText.ui(label.text()), width, FormTextWidget.Kind.TEXT));
               break;
            case FormElement.Divider ignored:
               body.addChild(FormTextWidget.divider(this.font, width));
               break;
            default:
         }
      }
   }
}
