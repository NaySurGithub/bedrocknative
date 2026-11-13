package net.bedrocknative.client.lobby;

import net.bedrocknative.client.ui.JsonUiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

final class LobbyHost {
   private JsonUiScreen ui;
   private boolean opened;
   private boolean pressed;

   LobbyHost(JsonUiScreen ui) {
      this.ui = ui;
   }

   JsonUiScreen ui() {
      return this.ui;
   }

   void replace(JsonUiScreen fresh, int width, int height) {
      this.ui = fresh;
      this.ui.resize(width, height);
   }

   void init(int width, int height) {
      this.pressed = false;
      this.ui.resize(width, height);
      if (!this.opened) {
         this.opened = true;
         this.ui.event("screen.entrance_push");
      }
   }

   void shownAgain() {
      this.ui.event("screen.entrance_pop");
   }

   void render(GuiGraphicsExtractor graphics) {
      if (!this.ui.failed()) {
         this.ui.render(graphics);
      }
   }

   void mouseMoved(double x, double y) {
      this.ui.mouseMoved(x, y);
   }

   boolean mouseClicked(MouseButtonEvent event) {
      if (event.button() != 1) {
         return false;
      }

      this.pressed = true;
      return this.ui.mouseClicked(event);
   }

   boolean mouseReleased(MouseButtonEvent event) {
      if (this.pressed && event.button() == 1) {
         this.pressed = false;
         return this.ui.mouseReleased(event);
      } else {
         return false;
      }
   }

   boolean mouseDragged(MouseButtonEvent event) {
      return this.pressed && this.ui.mouseDragged(event);
   }

   boolean mouseScrolled(double x, double y, double scrollY) {
      return this.ui.mouseScrolled(x, y, scrollY);
   }

   boolean keyPressed(KeyEvent event) {
      return this.ui.keyPressed(event);
   }

   boolean charTyped(CharacterEvent event) {
      return this.ui.charTyped(event);
   }

   boolean preeditUpdated(@Nullable PreeditEvent event) {
      return this.ui.preeditUpdated(event);
   }
}
