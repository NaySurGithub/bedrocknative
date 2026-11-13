package net.bedrocknative.form;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface BedrockForm permits BedrockForm.Simple, BedrockForm.Modal, BedrockForm.Custom {
   String title();

   public record Custom(String title, List<FormElement> elements, @Nullable String submit, @Nullable FormImage icon) implements BedrockForm {
   }

   public record Modal(String title, String content, String button1, String button2) implements BedrockForm {
   }

   public record Simple(String title, String content, List<FormElement> elements) implements BedrockForm {
      public List<FormElement.Button> buttons() {
         return this.elements.stream().filter(FormElement.Button.class::isInstance).map(FormElement.Button.class::cast).toList();
      }
   }
}
