package net.bedrocknative.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.form.FormElement;
import net.bedrocknative.form.FormImage;
import net.bedrocknative.form.FormResponse;
import org.jspecify.annotations.Nullable;

public final class FormData extends UiDataMap {
   public static final String SCREEN = "server_form.third_party_server_screen";
   public static final String FACTORY = "server_form_factory";
   public static final String SCREEN_KEY = "server_form";
   public static final String MODAL_KEY = "modal_form";
   public static final String POPUP_FACTORY = "popup_dialog_factory";
   public static final String POPUP = "modal_dialog_popup";
   public static final String MODAL_FIRST = "popup_dialog.left_button";
   public static final String MODAL_SECOND = "popup_dialog.rightcancel_button";
   public static final String MODAL_ESCAPE = "popup_dialog.escape";
   public static final String FORM_BUTTONS = "form_buttons";
   public static final String CUSTOM_FORM = "custom_form";
   public static final String CUSTOM_DROPDOWN = "custom_dropdown";
   public static final String CLICK = "button.form_button_click";
   public static final String SUBMIT = "button.submit_custom_form";
   public static final int MAX_INPUT_LENGTH = 100;
   public static final String LOADING = "loading";
   public static final String IN_USER_PACKAGE = "InUserPackage";
   public static final String INTERNET = "Internet";
   private final BedrockForm form;
   private final FormData.Texts texts;
   private final boolean settings;
   private final int[] ordinals;
   private final Object[] values;
   private final List<Map<String, UiValue>> items = new ArrayList<>();
   private final Map<String, List<Integer>> urls = new LinkedHashMap<>();
   @Nullable
   private FormData.Answers answers;
   private boolean answered;

   public FormData(BedrockForm form, FormData.Texts texts, boolean settings) {
      if (!supports(form)) {
         throw new IllegalArgumentException("not a server_form screen form: " + form.getClass().getSimpleName());
      } else {
         this.form = form;
         this.texts = texts;
         this.settings = settings;
         this.set("#title_text", texts.raw(form.title()));
         this.set("#close_button_visible", true);
         this.set("#tts_dialog_title", texts.raw(form.title()));
         List<FormElement> elements = this.elements();
         this.ordinals = new int[elements.size()];
         this.values = new Object[elements.size()];
         switch (form) {
            case BedrockForm.Simple simple:
               this.simple(simple);
               break;
            case BedrockForm.Custom custom:
               this.custom(custom);
               break;
            case BedrockForm.Modal modal:
               this.modal(modal);
               break;
            default:
               throw new MatchException(null, null);
         }
      }
   }

   public static boolean supports(BedrockForm form) {
      return form instanceof BedrockForm.Simple || form instanceof BedrockForm.Custom || form instanceof BedrockForm.Modal;
   }

   public BedrockForm form() {
      return this.form;
   }

   public String controlId() {
      return switch (this.form) {
         case BedrockForm.Simple ignored -> "long_form";
         case BedrockForm.Custom ignoredx -> "custom_form";
         case BedrockForm.Modal ignoredxx -> "modal_dialog_popup";
         default -> throw new MatchException(null, null);
      };
   }

   public String factory() {
      return this.form instanceof BedrockForm.Modal ? "popup_dialog_factory" : "server_form_factory";
   }

   public Map<String, UiValue> variables() {
      return this.form instanceof BedrockForm.Modal ? Map.of("$two_buttons_visible", UiValue.TRUE) : Map.of();
   }

   public String key() {
      return this.form instanceof BedrockForm.Modal ? "modal_form" : "server_form";
   }

   public boolean settings() {
      return this.settings;
   }

   public void answers(@Nullable FormData.Answers target) {
      this.answers = target;
   }

   public boolean answered() {
      return this.answered;
   }

   private List<FormElement> elements() {
      return switch (this.form) {
         case BedrockForm.Simple simple -> simple.elements();
         case BedrockForm.Custom custom -> custom.elements();
         case BedrockForm.Modal ignored -> List.of();
         default -> throw new MatchException(null, null);
      };
   }

   private void modal(BedrockForm.Modal modal) {
      this.set("#modal_title_text", this.texts.raw(modal.title()));
      this.set("#modal_label_text", this.texts.raw(modal.content()));
      this.set("#modal_left_button_text", this.texts.raw(modal.button1()));
      this.set("#modal_rightcancel_button_text", this.texts.raw(modal.button2()));
   }

   private void simple(BedrockForm.Simple simple) {
      this.set("#form_text", this.texts.raw(simple.content()));
      List<FormElement> elements = simple.elements();
      this.set("#form_button_contents", elements.size());
      this.set("#form_button_length", elements.size());
      List<UiDataMap.Item> collection = new ArrayList<>(elements.size());
      int buttons = 0;

      for (int i = 0; i < elements.size(); i++) {
         FormElement element = elements.get(i);
         Map<String, UiValue> values = new HashMap<>();
         this.ordinals[i] = -1;
         String kind;
         switch (element) {
            case FormElement.Button button:
               kind = "button";
               this.ordinals[i] = buttons++;
               values.put("#form_button_text", UiValue.of(this.texts.raw(button.text())));
               this.image(values, button.image(), i);
               break;
            case FormElement.Header header:
               kind = "header";
               values.put("#form_button_text", UiValue.of(this.texts.raw(header.text())));
               break;
            case FormElement.Label label:
               kind = "label";
               values.put("#form_button_text", UiValue.of(this.texts.raw(label.text())));
               break;
            default:
               kind = "divider";
         }

         if (!values.containsKey("#form_button_texture")) {
            values.put("#form_button_texture", UiValue.EMPTY);
            values.put("#form_button_texture_file_system", UiValue.of("InUserPackage"));
         }

         this.items.add(values);
         collection.add(new UiDataMap.Item(kind, values));
      }

      this.collection("form_buttons", collection);
   }

   private void image(Map<String, UiValue> values, @Nullable FormImage image, int index) {
      if (image != null) {
         if (image.type() == FormImage.Type.URL) {
            values.put("#form_button_texture", UiValue.of("loading"));
            values.put("#form_button_texture_file_system", UiValue.of("Internet"));
            this.urls.computeIfAbsent(image.data(), url -> new ArrayList<>()).add(index);
         } else {
            values.put("#form_button_texture", UiValue.of(image.data()));
            values.put("#form_button_texture_file_system", UiValue.of("InUserPackage"));
         }
      }
   }

   public Set<String> pendingImages() {
      return Set.copyOf(this.urls.keySet());
   }

   public void imageLoaded(String url) {
      List<Integer> indices = this.urls.remove(url);
      if (indices != null) {
         for (int index : indices) {
            this.items.get(index).put("#form_button_texture", UiValue.of(url));
         }

         this.touch();
      }
   }

   private void custom(BedrockForm.Custom custom) {
      List<FormElement> elements = custom.elements();
      this.set("#custom_form_length", elements.size());
      this.set("#submit_text", custom.submit() != null && !custom.submit().isEmpty() ? this.texts.raw(custom.submit()) : "gui.submit");
      this.set("#submit_button_visible", !this.settings);
      List<UiDataMap.Item> collection = new ArrayList<>(elements.size());

      for (int i = 0; i < elements.size(); i++) {
         FormElement element = elements.get(i);
         this.ordinals[i] = -1;
         this.values[i] = initial(element);
         Map<String, UiValue> item = new HashMap<>();

         String kind = switch (element) {
            case FormElement.Label label -> {
               item.put("#custom_text", UiValue.of(this.texts.raw(label.text())));
               yield "label";
            }
            case FormElement.Header header -> {
               item.put("#custom_text", UiValue.of(this.texts.raw(header.text())));
               yield "header";
            }
            case FormElement.Input input -> {
               item.put("#custom_text", UiValue.of(this.texts.raw(input.text())));
               item.put("#custom_placeholder_text", UiValue.of(this.texts.raw(input.placeholder())));
               item.put("#custom_input_enabled", UiValue.TRUE);
               item.put("#custom_tooltip_text", UiValue.of(this.texts.raw(input.tooltip())));
               yield "input";
            }
            case FormElement.Toggle toggle -> {
               item.put("#custom_text", UiValue.of(this.texts.raw(toggle.text())));
               item.put("#custom_toggle_enabled", UiValue.TRUE);
               item.put("#custom_tooltip_text", UiValue.of(this.texts.raw(toggle.tooltip())));
               yield "toggle";
            }
            case FormElement.Slider slider -> {
               item.put("#custom_slider_enabled", UiValue.TRUE);
               item.put("#custom_tooltip_text", UiValue.of(this.texts.raw(slider.tooltip())));
               yield "slider";
            }
            case FormElement.StepSlider stepSlider -> {
               item.put("#custom_slider_enabled", UiValue.TRUE);
               item.put("#custom_slider_steps", UiValue.of(Math.max(1, stepSlider.steps().size())));
               item.put("#custom_tooltip_text", UiValue.of(this.texts.raw(stepSlider.tooltip())));
               yield "step_slider";
            }
            case FormElement.Dropdown dropdown -> {
               item.put("#custom_text", UiValue.of(this.texts.raw(dropdown.text())));
               item.put("#custom_dropdown_length", UiValue.of(dropdown.options().size()));
               item.put("#custom_toggle_enabled", UiValue.TRUE);
               item.put("#custom_tooltip_text", UiValue.of(this.texts.raw(dropdown.tooltip())));
               yield "dropdown";
            }
            default -> "divider";
         };
         this.items.add(item);
         this.refresh(i);
         collection.add(new UiDataMap.Item(kind, item));
      }

      this.collection("custom_form", collection);
   }

   private static Object initial(FormElement element) {
      return switch (element) {
         case FormElement.Input input -> cut(input.defaultText());
         case FormElement.Toggle toggle -> toggle.defaultValue();
         case FormElement.Slider slider -> slider.nearestStep(slider.defaultValue());
         case FormElement.StepSlider stepSlider -> stepSlider.defaultIndex();
         case FormElement.Dropdown dropdown -> dropdown.defaultIndex();
         default -> Boolean.FALSE;
      };
   }

   private void refresh(int index) {
      Map<String, UiValue> item = this.items.get(index);
      switch ((FormElement)this.elements().get(index)) {
         case FormElement.Input ignored:
            item.put("#custom_input_text", UiValue.of((String)this.values[index]));
            break;
         case FormElement.Toggle ignoredx:
            item.put("#custom_toggle_state", UiValue.of((Boolean)this.values[index]));
            break;
         case FormElement.Slider slider: {
            int step = (Integer)this.values[index];
            String value = number(slider.valueAt(step));
            item.put("#custom_slider_value", UiValue.of(slider.stepCount() > 0 ? (double)step / slider.stepCount() : 0.0));
            item.put("#custom_slider_text", UiValue.of(this.labelled(slider.text(), value)));
            item.put("#custom_slider_text_value", UiValue.of(value));
            break;
         }
         case FormElement.StepSlider stepSlider: {
            int step = (Integer)this.values[index];
            int count = stepSlider.steps().size();
            String value = count == 0 ? "" : this.texts.localized(stepSlider.steps().get(Math.min(step, count - 1)));
            item.put("#custom_slider_step_value", UiValue.of(count > 1 ? (double)step / (count - 1) : 0.0));
            item.put("#custom_slider_step_text", UiValue.of(this.labelled(stepSlider.text(), value)));
            item.put("#custom_slider_step_text_value", UiValue.of(value));
            break;
         }
         case FormElement.Dropdown dropdown:
            int selected = (Integer)this.values[index];
            item.put("#dropdown_option_text", UiValue.of(dropdown.options().isEmpty() ? "" : this.texts.raw(dropdown.options().get(selected))));
            break;
         default:
      }
   }

   private String labelled(String text, String value) {
      return text.isEmpty() ? value : this.texts.localized(text) + ": " + value;
   }

   static String number(float value) {
      return Float.isFinite(value) ? new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString() : Float.toString(value);
   }

   private static String cut(String text) {
      return text.length() > 100 ? text.substring(0, Character.isHighSurrogate(text.charAt(99)) ? 99 : 100) : text;
   }

   @Override
   public UiValue item(String collection, int index, String name, int outer) {
      if ("custom_dropdown".equals(collection)) {
         if (this.form instanceof BedrockForm.Custom custom
            && outer >= 0
            && outer < custom.elements().size()
            && custom.elements().get(outer) instanceof FormElement.Dropdown dropdown
            && index >= 0
            && index < dropdown.options().size()) {
            return switch (name) {
               case "#custom_radio_text" -> UiValue.of(this.texts.raw(dropdown.options().get(index)));
               case "#custom_radio_toggled" -> UiValue.of((Integer)this.values[outer] == index);
               default -> UiValue.NULL;
            };
         } else {
            return UiValue.NULL;
         }
      } else {
         return this.item(collection, index, name);
      }
   }

   @Override
   public void write(@Nullable String collection, int index, String name, UiValue value, int outer) {
      List<FormElement> elements = this.elements();
      if ("custom_dropdown".equals(collection)) {
         if (outer >= 0
            && outer < elements.size()
            && elements.get(outer) instanceof FormElement.Dropdown dropdown
            && "#custom_radio_toggled".equals(name)
            && value.truthy()
            && index >= 0
            && index < dropdown.options().size()) {
            this.values[outer] = index;
            this.refresh(outer);
            this.touch();
         }
      } else if (collection == null) {
         this.set(name, value);
      } else {
         boolean custom = "custom_form".equals(collection) && this.form instanceof BedrockForm.Custom;
         if ((custom || "form_buttons".equals(collection) && this.form instanceof BedrockForm.Simple) && index >= 0 && index < this.items.size()) {
            FormElement element = elements.get(index);
            String var9 = custom ? name : "";
            switch (var9) {
               case "#custom_toggle_state":
                  if (element instanceof FormElement.Toggle) {
                     this.values[index] = value.truthy();
                  }
                  break;
               case "#custom_slider_value":
                  if (element instanceof FormElement.Slider slider) {
                     this.values[index] = (int)Math.max(0L, Math.min((long)slider.stepCount(), Math.round(value.asNumber() * slider.stepCount())));
                  }
                  break;
               case "#custom_slider_step_value":
                  if (element instanceof FormElement.StepSlider stepSlider && !stepSlider.steps().isEmpty()) {
                     int last = stepSlider.steps().size() - 1;
                     this.values[index] = (int)Math.max(0L, Math.min((long)last, Math.round(value.asNumber() * last)));
                  }
                  break;
               case "#custom_input_text":
                  if (element instanceof FormElement.Input) {
                     this.values[index] = cut(value.isNull() ? "" : value.asString());
                  }
                  break;
               default:
                  this.items.get(index).put(name, value);
            }

            if (custom) {
               this.refresh(index);
            }

            this.touch();
         }
      }
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      FormData.Answers target = this.answers;
      if (!this.answered && target != null) {
         switch (buttonId) {
            case "button.form_button_click":
               if (this.form instanceof BedrockForm.Simple
                  && "form_buttons".equals(collection)
                  && index >= 0
                  && index < this.ordinals.length
                  && this.ordinals[index] >= 0) {
                  this.answered = true;
                  target.submit(FormResponse.button(this.ordinals[index]));
               }
               break;
            case "button.submit_custom_form":
               if (this.form instanceof BedrockForm.Custom) {
                  this.answered = true;
                  target.submit(this.response());
               }
               break;
            case "popup_dialog.left_button":
            case "popup_dialog.rightcancel_button":
               if (this.form instanceof BedrockForm.Modal) {
                  this.answered = true;
                  target.submit(FormResponse.modal("popup_dialog.left_button".equals(buttonId)));
               }
               break;
            case "popup_dialog.escape":
               if (this.form instanceof BedrockForm.Modal) {
                  this.answered = true;
                  target.close();
               }
               break;
            case "button.menu_exit":
               this.answered = true;
               if (this.settings && this.form instanceof BedrockForm.Custom) {
                  target.submit(this.response());
               } else {
                  target.close();
               }
         }
      }
   }

   public int ordinal(int index) {
      return index >= 0 && index < this.ordinals.length ? this.ordinals[index] : -1;
   }

   public int buttonIndex(int ordinal) {
      for (int i = 0; i < this.ordinals.length; i++) {
         if (this.ordinals[i] == ordinal) {
            return i;
         }
      }

      return -1;
   }

   public String response() {
      List<Object> out = new ArrayList<>(Arrays.asList());
      List<FormElement> elements = this.elements();

      for (int i = 0; i < elements.size(); i++) {
         out.set(i, switch ((FormElement)elements.get(i)) {
            case FormElement.Input ignored -> this.values[i];
            case FormElement.Toggle ignoredx -> this.values[i];
            case FormElement.Slider slider -> slider.valueAt((Integer)this.values[i]);
            case FormElement.StepSlider ignoredxx -> this.values[i];
            case FormElement.Dropdown ignoredxxx -> this.values[i];
            default -> null;
         });
      }

      return FormResponse.custom(out);
   }

   @Nullable
   public Object value(int index) {
      return index >= 0 && index < this.values.length ? this.values[index] : null;
   }

   public interface Answers {
      void submit(String var1);

      void close();
   }

   public interface Texts {
      FormData.Texts PLAIN = new FormData.Texts() {
         @Override
         public String raw(String text) {
            return text;
         }

         @Override
         public String localized(String text) {
            return text;
         }
      };

      String raw(String var1);

      String localized(String var1);
   }
}
