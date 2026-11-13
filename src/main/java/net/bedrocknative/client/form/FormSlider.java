package net.bedrocknative.client.form;

import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public final class FormSlider extends AbstractSliderButton {
   private final int steps;
   private final IntFunction<Component> label;
   private int step;

   FormSlider(int width, int steps, int initial, IntFunction<Component> label) {
      super(0, 0, width, 20, Component.empty(), steps == 0 ? 0.0 : (double)Math.clamp(initial, 0, steps) / steps);
      this.steps = steps;
      this.label = label;
      this.step = Math.clamp(initial, 0, steps);
      this.active = steps > 0;
      this.updateMessage();
   }

   public int step() {
      return this.step;
   }

   public void setStep(int step) {
      this.setValue(this.steps == 0 ? 0.0 : (double)Math.clamp(step, 0, this.steps) / this.steps);
   }

   @Override
   protected void updateMessage() {
      this.setMessage(this.label.apply(this.step));
   }

   @Override
   protected void applyValue() {
      this.step = this.steps == 0 ? 0 : (int)Math.round(this.value * this.steps);
      this.value = this.steps == 0 ? 0.0 : (double)this.step / this.steps;
   }
}
