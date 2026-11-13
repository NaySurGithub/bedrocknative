package net.bedrocknative.client.form;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.form.FormElement;
import net.bedrocknative.form.FormResponse;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class CustomFormScreen extends FormScreen {
   private static final int MAX_INPUT_LENGTH = 100;
   private final BedrockForm.Custom form;
   @Nullable
   private final Screen settingsParent;
   private final Object[] state;
   private final List<AbstractWidget> controls = new ArrayList<>();
   @Nullable
   private AbstractWidget submit;

   public CustomFormScreen(FormScreen.Host host, int formId, BedrockForm.Custom form, @Nullable FormScreen below, @Nullable Screen settingsParent) {
      super(host, formId, form.title(), below);
      this.form = form;
      this.settingsParent = settingsParent;
      this.state = form.elements().stream().map(CustomFormScreen::initialState).toArray();
   }

   public BedrockForm.Custom form() {
      return this.form;
   }

   @Nullable
   @Override
   public Screen settingsParent() {
      return this.settingsParent;
   }

   @Override
   public boolean isServerSettings() {
      return this.settingsParent != null;
   }

   public List<AbstractWidget> controls() {
      return this.controls;
   }

   public void submit() {
      this.host.submit(this, this.response());
   }

   @Override
   public void onClose() {
      if (this.isServerSettings()) {
         this.submit();
      } else {
         super.onClose();
      }
   }

   @Override
   public void removed() {
      this.syncState();
      super.removed();
   }

   @Override
   protected void buildBody(LinearLayout body, int width) {
      this.controls.clear();
      List<FormElement> elements = this.form.elements();

      for (int i = 0; i < elements.size(); i++) {
         FormElement element = elements.get(i);

         AbstractWidget var10000 = switch (element) {
            case FormElement.Label label -> this.text(body, label.text(), width, FormTextWidget.Kind.TEXT);
            case FormElement.Header header -> this.text(body, header.text(), width, FormTextWidget.Kind.HEADER);
            case FormElement.Divider ignored -> {
               body.addChild(FormTextWidget.divider(this.font, width));
               yield null;
            }
            case FormElement.Input input -> {
               if (!input.text().isEmpty()) {
                  this.text(body, input.text(), width, FormTextWidget.Kind.TEXT);
               }

               EditBox box = new EditBox(this.font, width, 20, BedrockText.ui(input.text()));
               box.setMaxLength(100);
               box.setValue((String)this.state[i]);
               box.setHint(BedrockText.ui(input.placeholder()).withColor(-8355712));
               yield withTooltip(box, input.tooltip());
            }
            case FormElement.Toggle toggle -> withTooltip(
               CycleButton.onOffBuilder((Boolean)this.state[i]).create(0, 0, width, 20, BedrockText.ui(toggle.text())), toggle.tooltip()
            );
            case FormElement.Slider slider -> withTooltip(
               new FormSlider(
                  width, slider.stepCount(), (Integer)this.state[i], step -> labelled(slider.text(), Component.literal(number(slider.valueAt(step))))
               ),
               slider.tooltip()
            );
            case FormElement.StepSlider stepSlider -> withTooltip(
               new FormSlider(
                  width,
                  Math.max(0, stepSlider.steps().size() - 1),
                  (Integer)this.state[i],
                  step -> labelled(stepSlider.text(), stepSlider.steps().isEmpty() ? Component.empty() : BedrockText.ui(stepSlider.steps().get(step)))
               ),
               stepSlider.tooltip()
            );
            case FormElement.Dropdown dropdown -> withTooltip(this.dropdown(dropdown, (Integer)this.state[i], width), dropdown.tooltip());
            default -> null;
         };

         AbstractWidget control = var10000;
         boolean textOnly = !element.hasValue();
         if (control != null && !textOnly) {
            body.addChild(control);
         }

         this.controls.add(textOnly ? null : control);
      }

      Component submitText = (Component)(this.isServerSettings()
         ? CommonComponents.GUI_DONE
         : BedrockText.ui(this.form.submit() != null && !this.form.submit().isEmpty() ? this.form.submit() : "gui.submit"));
      this.submit = (AbstractWidget)body.addChild(new FormButton(this.font, width, 32, submitText, null, this::submit));
   }

   @Nullable
   private AbstractWidget text(LinearLayout body, String text, int width, FormTextWidget.Kind kind) {
      body.addChild(new FormTextWidget(this.font, BedrockText.ui(text), width, kind));
      return null;
   }

   private AbstractWidget dropdown(FormElement.Dropdown dropdown, int selected, int width) {
      Component label = BedrockText.ui(dropdown.text());
      if (dropdown.options().isEmpty()) {
         CycleButton<Integer> empty = CycleButton.builder(index -> Component.empty(), 0).withValues(new Integer[]{0}).create(0, 0, width, 20, label);
         empty.active = false;
         return empty;
      } else {
         List<Integer> indices = IntStream.range(0, dropdown.options().size()).boxed().toList();
         return CycleButton.builder(index -> BedrockText.ui(dropdown.options().get(index)), selected).withValues(indices).create(0, 0, width, 20, label);
      }
   }

   private static AbstractWidget withTooltip(AbstractWidget widget, String tooltip) {
      if (!tooltip.isEmpty()) {
         widget.setTooltip(Tooltip.create(BedrockText.ui(tooltip)));
      }

      return widget;
   }

   private static Component labelled(String text, Component value) {
      return (Component)(text.isEmpty() ? value : Component.empty().append(BedrockText.ui(text)).append(": ").append(value));
   }

   static String number(float value) {
      return Float.isFinite(value) ? new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString() : Float.toString(value);
   }

   private static Object initialState(FormElement element) {
      return switch (element) {
         case FormElement.Input input -> input.defaultText().length() > 100 ? input.defaultText().substring(0, 100) : input.defaultText();
         case FormElement.Toggle toggle -> toggle.defaultValue();
         case FormElement.Slider slider -> slider.nearestStep(slider.defaultValue());
         case FormElement.StepSlider stepSlider -> stepSlider.defaultIndex();
         case FormElement.Dropdown dropdown -> dropdown.defaultIndex();
         default -> Boolean.FALSE;
      };
   }

   private void syncState() {
      for (int i = 0; i < this.controls.size(); i++) {
         switch ((AbstractWidget)this.controls.get(i)) {
            case EditBox box:
               this.state[i] = box.getValue();
               break;
            case FormSlider slider:
               this.state[i] = slider.step();
               break;
            case CycleButton<?> cycle:
               this.state[i] = cycle.getValue();
               break;
            case null:
            default:
         }
      }
   }

   String response() {
      this.syncState();
      List<Object> values = new ArrayList<>(Arrays.asList());
      List<FormElement> elements = this.form.elements();

      for (int i = 0; i < elements.size(); i++) {
         values.set(i, switch ((FormElement)elements.get(i)) {
            case FormElement.Input ignored -> this.state[i];
            case FormElement.Toggle ignoredx -> this.state[i];
            case FormElement.Slider slider -> slider.valueAt((Integer)this.state[i]);
            case FormElement.StepSlider ignoredxx -> this.state[i];
            case FormElement.Dropdown ignoredxxx -> this.state[i];
            default -> null;
         });
      }

      return FormResponse.custom(values);
   }

   @Nullable
   public AbstractWidget submitButton() {
      return this.submit;
   }
}
