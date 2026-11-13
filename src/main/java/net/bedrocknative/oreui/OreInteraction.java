package net.bedrocknative.oreui;

import org.jspecify.annotations.Nullable;

public final class OreInteraction {
   private @Nullable String hovered;
   private @Nullable String pressed;
   private @Nullable String focused;
   private boolean focusVisible;
   private double mouseX = -1.0;
   private double mouseY = -1.0;

   public OreComponents.State state(String id) {
      boolean over = id.equals(this.hovered);
      return new OreComponents.State(
         over && this.pressed == null || id.equals(this.pressed) && over, id.equals(this.pressed) && over, this.focusVisible && id.equals(this.focused), true
      );
   }

   public @Nullable String hovered() {
      return this.hovered;
   }

   public @Nullable String focused() {
      return this.focused;
   }

   public boolean focusVisible() {
      return this.focusVisible;
   }

   public void focus(@Nullable String id, boolean visible) {
      this.focused = id;
      this.focusVisible = visible && id != null;
   }

   public void moved(OreWidgets widgets, double x, double y) {
      this.mouseX = x;
      this.mouseY = y;
      OreWidget at = widgets.at(x, y);
      this.hovered = at == null ? null : at.id();
   }

   public void refresh(OreWidgets widgets) {
      if (this.mouseX >= 0.0) {
         OreWidget at = widgets.at(this.mouseX, this.mouseY);
         this.hovered = at == null ? null : at.id();
      }

      if (this.focused != null && widgets.get(this.focused) == null) {
         this.focused = null;
         this.focusVisible = false;
      }
   }

   public @Nullable String press(OreWidgets widgets, double x, double y) {
      this.moved(widgets, x, y);
      this.pressed = this.hovered;
      if (this.pressed != null) {
         this.focused = this.pressed;
      }

      this.focusVisible = false;
      return this.pressed;
   }

   public @Nullable String release(OreWidgets widgets, double x, double y) {
      this.moved(widgets, x, y);
      String activated = this.pressed != null && this.pressed.equals(this.hovered) ? this.pressed : null;
      this.pressed = null;
      return activated;
   }

   public void cancelPress() {
      this.pressed = null;
   }

   public @Nullable OreWidget navigate(OreWidgets widgets, OreKey key, boolean shift) {
      OreWidget current = widgets.get(this.focused);
      OreWidget next;
      if (key == OreKey.TAB) {
         next = widgets.next(this.focused, shift);
      } else if (current == null) {
         next = widgets.next(null, false);
      } else {
         next = switch (key) {
            case LEFT -> widgets.toward(current, -1, 0);
            case RIGHT -> widgets.toward(current, 1, 0);
            case UP -> widgets.toward(current, 0, -1);
            case DOWN -> widgets.toward(current, 0, 1);
            default -> null;
         };
      }

      if (next == null) {
         this.focusVisible = current != null;
         return null;
      } else {
         this.focused = next.id();
         this.focusVisible = true;
         return next;
      }
   }
}
