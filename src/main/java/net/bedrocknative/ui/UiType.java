package net.bedrocknative.ui;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum UiType {
   PANEL,
   STACK_PANEL,
   COLLECTION_PANEL,
   GRID,
   IMAGE,
   LABEL,
   FACTORY,
   SCREEN,
   INPUT_PANEL,
   BUTTON,
   TOGGLE,
   SLIDER,
   SLIDER_BOX,
   SCROLLBAR_BOX,
   SCROLLBAR_TRACK,
   SCROLL_TRACK,
   DROPDOWN,
   EDIT_BOX,
   SCROLL_VIEW,
   CUSTOM,
   SELECTION_WHEEL,
   IMAGE_CYCLER,
   LABEL_CYCLER,
   GRID_PAGE_INDICATOR,
   TAB,
   CAROUSEL_LABEL,
   GRID_ITEM,
   SCROLLBAR;

   public static @Nullable UiType of(@Nullable String name) {
      if (name == null) {
         return null;
      }

      try {
         return valueOf(name.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
