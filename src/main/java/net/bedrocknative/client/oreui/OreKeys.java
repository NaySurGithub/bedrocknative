package net.bedrocknative.client.oreui;

import net.bedrocknative.oreui.OreKey;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

public final class OreKeys {
   private OreKeys() {
   }

   public static @Nullable OreKey of(KeyEvent event) {
      if (event.isEscape()) {
         return OreKey.ESCAPE;
      } else if (event.isSelectAll()) {
         return OreKey.SELECT_ALL;
      } else if (event.isCopy()) {
         return OreKey.COPY;
      } else if (event.isPaste()) {
         return OreKey.PASTE;
      } else if (event.isCut()) {
         return OreKey.CUT;
      } else if (event.isCycleFocus()) {
         return OreKey.TAB;
      } else if (event.isLeft()) {
         return OreKey.LEFT;
      } else if (event.isRight()) {
         return OreKey.RIGHT;
      } else if (event.isUp()) {
         return OreKey.UP;
      } else if (event.isDown()) {
         return OreKey.DOWN;
      } else {
         int key = event.key();
         if (key == 40 || key == 88) {
            return OreKey.ENTER;
         } else if (key == 44) {
            return OreKey.SPACE;
         } else if (key == 42) {
            return OreKey.BACKSPACE;
         } else if (key == 76) {
            return OreKey.DELETE;
         } else if (key == 74) {
            return OreKey.HOME;
         } else if (key == 77) {
            return OreKey.END;
         } else if (key == 75) {
            return OreKey.PAGE_UP;
         } else if (key == 78) {
            return OreKey.PAGE_DOWN;
         } else if (key == 47) {
            return OreKey.PREVIOUS_TAB;
         } else {
            return key == 48 ? OreKey.NEXT_TAB : null;
         }
      }
   }
}
