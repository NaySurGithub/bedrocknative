package net.bedrocknative.form;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class FormParser {
   private FormParser() {
   }

   public static BedrockForm parse(String json) throws FormParser.FormParseException {
      try {
         JsonElement root = JsonParser.parseString(json);
         if (!root.isJsonObject()) {
            throw new FormParser.FormParseException("form JSON is not an object");
         }

         JsonObject form = root.getAsJsonObject();
         String type = string(form, "type").toLowerCase(Locale.ROOT);

         return switch (type) {
            case "form" -> new BedrockForm.Simple(text(form, "title"), text(form, "content"), simpleElements(form));
            case "modal" -> new BedrockForm.Modal(text(form, "title"), text(form, "content"), text(form, "button1"), text(form, "button2"));
            case "custom_form" -> new BedrockForm.Custom(
               text(form, "title"),
               customElements(form),
               form.has("submit") && !form.get("submit").isJsonNull() ? text(form, "submit") : null,
               image(form.get("icon"))
            );
            default -> throw new FormParser.FormParseException("unknown form type '" + type + "'");
         };
      } catch (FormParser.FormParseException e) {
         throw e;
      } catch (RuntimeException e) {
         throw new FormParser.FormParseException(e.getMessage() != null ? e.getMessage() : e.toString());
      }
   }

   private static List<FormElement> simpleElements(JsonObject form) throws FormParser.FormParseException {
      List<FormElement> elements = new ArrayList<>();
      JsonElement list = form.has("elements") ? form.get("elements") : form.get("buttons");
      if (list != null && !list.isJsonNull()) {
         for (JsonElement entry : list.getAsJsonArray()) {
            JsonObject element = entry.getAsJsonObject();
            String type = element.has("type") ? string(element, "type") : "button";

            elements.add(switch (type) {
               case "button" -> new FormElement.Button(text(element, "text"), image(element.get("image")));
               case "header" -> new FormElement.Header(text(element, "text"));
               case "label" -> new FormElement.Label(text(element, "text"));
               case "divider" -> new FormElement.Divider();
               default -> throw new FormParser.FormParseException("unknown simple form element '" + type + "'");
            });
         }

         return elements;
      } else {
         return elements;
      }
   }

   private static List<FormElement> customElements(JsonObject form) throws FormParser.FormParseException {
      List<FormElement> elements = new ArrayList<>();
      JsonElement content = form.get("content");
      if (content != null && !content.isJsonNull()) {
         for (JsonElement entry : content.getAsJsonArray()) {
            JsonObject element = entry.getAsJsonObject();
            String type = string(element, "type");
            String text = text(element, "text");
            String tooltip = text(element, "tooltip");

            elements.add(
               switch (type) {
                  case "label" -> new FormElement.Label(text);
                  case "header" -> new FormElement.Header(text);
                  case "divider" -> new FormElement.Divider();
                  case "input" -> new FormElement.Input(text, text(element, "placeholder"), text(element, "default"), tooltip);
                  case "toggle" -> new FormElement.Toggle(
                     text, element.has("default") && !element.get("default").isJsonNull() && element.get("default").getAsBoolean(), tooltip
                  );
                  case "slider" -> slider(element, text, tooltip);
                  case "step_slider" -> {
                     List<String> steps = strings(element.get("steps"));
                     yield new FormElement.StepSlider(text, steps, index(element, steps.size()), tooltip);
                  }
                  case "dropdown" -> {
                     List<String> options = strings(element.get("options"));
                     yield new FormElement.Dropdown(text, options, index(element, options.size()), tooltip);
                  }
                  default -> throw new FormParser.FormParseException("unknown custom form element '" + type + "'");
               }
            );
         }

         return elements;
      } else {
         return elements;
      }
   }

   private static FormElement.Slider slider(JsonObject element, String text, String tooltip) throws FormParser.FormParseException {
      float min = number(element, "min", 0.0F);
      float max = Math.max(min, number(element, "max", min));
      float step = number(element, "step", 1.0F);
      float value = Math.clamp(number(element, "default", min), min, max);
      return new FormElement.Slider(text, min, max, step > 0.0F ? step : 1.0F, value, tooltip);
   }

   private static int index(JsonObject element, int size) {
      int index = element.has("default") && !element.get("default").isJsonNull() ? element.get("default").getAsInt() : 0;
      return size == 0 ? 0 : Math.clamp(index, 0, size - 1);
   }

   private static float number(JsonObject element, String key, float fallback) throws FormParser.FormParseException {
      JsonElement value = element.get(key);
      float number = value != null && !value.isJsonNull() ? value.getAsFloat() : fallback;
      if (!Float.isFinite(number)) {
         throw new FormParser.FormParseException("slider " + key + " " + value + " is out of range");
      } else {
         return number;
      }
   }

   private static @Nullable FormImage image(@Nullable JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject image = element.getAsJsonObject();
         String data = string(image, "data");
         return data.isBlank() ? null : new FormImage("url".equalsIgnoreCase(string(image, "type")) ? FormImage.Type.URL : FormImage.Type.PATH, data);
      } else {
         return null;
      }
   }

   private static List<String> strings(@Nullable JsonElement element) {
      List<String> values = new ArrayList<>();
      if (element != null && element.isJsonArray()) {
         for (JsonElement value : element.getAsJsonArray()) {
            values.add(textOf(value));
         }
      }

      return values;
   }

   private static String string(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
   }

   private static String text(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value == null ? "" : textOf(value);
   }

   private static String textOf(JsonElement value) {
      if (value.isJsonNull()) {
         return "";
      } else if (value.isJsonPrimitive()) {
         return value.getAsString();
      } else if (value instanceof JsonArray array) {
         JsonObject rawText = new JsonObject();
         rawText.add("rawtext", array);
         return rawText.toString();
      } else {
         return value.toString();
      }
   }

   public static final class FormParseException extends Exception {
      public FormParseException(String message) {
         super(message);
      }
   }
}
