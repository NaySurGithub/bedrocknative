package net.bedrocknative.oreui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class OreWidgets {
   private final List<OreWidget> widgets = new ArrayList<>();

   public void add(OreWidget widget) {
      this.widgets.add(widget);
   }

   public List<OreWidget> all() {
      return this.widgets;
   }

   public @Nullable OreWidget at(double x, double y) {
      for (int i = this.widgets.size() - 1; i >= 0; i--) {
         OreWidget widget = this.widgets.get(i);
         if (widget.enabled() && widget.hit().contains(x, y)) {
            return widget;
         }
      }

      return null;
   }

   public @Nullable OreWidget get(@Nullable String id) {
      if (id == null) {
         return null;
      }

      for (OreWidget widget : this.widgets) {
         if (widget.id().equals(id)) {
            return widget;
         }
      }

      return null;
   }

   private List<OreWidget> focusable() {
      return this.widgets.stream().filter(widget -> widget.focusable() && widget.enabled() && !widget.box().empty()).toList();
   }

   public @Nullable OreWidget next(@Nullable String id, boolean reverse) {
      List<OreWidget> focusable = this.focusable();
      if (focusable.isEmpty()) {
         return null;
      }

      int at = -1;

      for (int i = 0; i < focusable.size(); i++) {
         if (focusable.get(i).id().equals(id)) {
            at = i;
         }
      }

      if (at < 0) {
         return reverse ? focusable.getLast() : focusable.getFirst();
      } else {
         return focusable.get(Math.floorMod(at + (reverse ? -1 : 1), focusable.size()));
      }
   }

   public @Nullable OreWidget toward(OreWidget from, int dx, int dy) {
      OreWidget best = null;
      float bestScore = Float.MAX_VALUE;
      OreBox a = from.box();

      for (OreWidget widget : this.focusable()) {
         if (!widget.id().equals(from.id())) {
            OreBox b = widget.box();
            float along;
            float across;
            if (dx != 0) {
               along = dx > 0 ? b.x() - (a.right() - 1.0F) : a.x() - (b.right() - 1.0F);
               across = gap(a.y(), a.bottom(), b.y(), b.bottom());
            } else {
               along = dy > 0 ? b.y() - (a.bottom() - 1.0F) : a.y() - (b.bottom() - 1.0F);
               across = gap(a.x(), a.right(), b.x(), b.right());
            }

            if (!(along <= 0.0F)) {
               float score = along + 2.0F * across;
               if (score < bestScore) {
                  bestScore = score;
                  best = widget;
               }
            }
         }
      }

      return best;
   }

   private static float gap(float a0, float a1, float b0, float b1) {
      if (b1 <= a0) {
         return a0 - b1;
      } else {
         return b0 >= a1 ? b0 - a1 : 0.0F;
      }
   }
}
