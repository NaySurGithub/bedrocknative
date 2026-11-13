package net.bedrocknative.form;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface FormElement
   permits FormElement.Button,
   FormElement.Header,
   FormElement.Label,
   FormElement.Divider,
   FormElement.Input,
   FormElement.Toggle,
   FormElement.Slider,
   FormElement.StepSlider,
   FormElement.Dropdown {
   default boolean hasValue() {
      return false;
   }

   record Button(String text, @Nullable FormImage image) implements FormElement {
   }

   record Divider() implements FormElement {
   }

   record Dropdown(String text, List<String> options, int defaultIndex, String tooltip) implements FormElement {
      @Override
      public boolean hasValue() {
         return true;
      }
   }

   record Header(String text) implements FormElement {
   }

   record Input(String text, String placeholder, String defaultText, String tooltip) implements FormElement {
      @Override
      public boolean hasValue() {
         return true;
      }
   }

   record Label(String text) implements FormElement {
   }

   record Slider(String text, float min, float max, float step, float defaultValue, String tooltip) implements FormElement {
      @Override
      public boolean hasValue() {
         return true;
      }

      public int stepCount() {
         return this.step > 0.0F && this.max > this.min ? (int)Math.floor((double)(this.max - this.min) / this.step + 1.0E-6) : 0;
      }

      public float valueAt(int stepIndex) {
         return (float)(this.min + (double)Math.clamp(stepIndex, 0, this.stepCount()) * this.step);
      }

      public int nearestStep(double value) {
         return this.step > 0.0F ? Math.clamp(Math.round((value - this.min) / this.step), 0, this.stepCount()) : 0;
      }
   }

   record StepSlider(String text, List<String> steps, int defaultIndex, String tooltip) implements FormElement {
      @Override
      public boolean hasValue() {
         return true;
      }
   }

   record Toggle(String text, boolean defaultValue, String tooltip) implements FormElement {
      @Override
      public boolean hasValue() {
         return true;
      }
   }
}
