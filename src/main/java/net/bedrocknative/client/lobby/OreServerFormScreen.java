package net.bedrocknative.client.lobby;

import java.util.List;
import net.bedrocknative.client.oreui.OreKeys;
import net.bedrocknative.client.oreui.OreSession;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.oreui.OreBox;
import net.bedrocknative.oreui.OreKey;
import net.bedrocknative.oreui.OreServerFormUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class OreServerFormScreen extends Screen implements LobbyScreen, OreServerFormUi.Actions {
   private final OrePlayScreen parent;
   private final OreSession session;
   private final OreServerFormUi ui;
   private final int index;
   private boolean textInput;

   private OreServerFormScreen(OrePlayScreen parent, OreSession session, int index, @Nullable String name, @Nullable BedrockAddress address) {
      super(Component.translatable(index < 0 ? "addExternalServerScreen.addTitle" : "addExternalServerScreen.editTitle"));
      this.parent = parent;
      this.session = session;
      this.index = index;
      this.ui = new OreServerFormUi(session.text(), session.lang(), session.theme(), session.vars(), name, address, this);
   }

   static OreServerFormScreen add(OrePlayScreen parent, OreSession session) {
      return new OreServerFormScreen(parent, session, -1, null, null);
   }

   static OreServerFormScreen edit(OrePlayScreen parent, OreSession session, int index, String name, BedrockAddress address) {
      return new OreServerFormScreen(parent, session, index, name, address);
   }

   public OreServerFormUi ui() {
      return this.ui;
   }

   @Override
   protected void init() {
      this.ui.size(OreSession.windowWidth(), OreSession.windowHeight());
      this.syncTextInput();
   }

   private void syncTextInput() {
      boolean wanted = this.ui.focusedField() >= 0 && !this.ui.modalOpen();
      if (wanted != this.textInput) {
         this.textInput = wanted;
         this.minecraft.onTextInputFocusChange(this, wanted);
      }

      if (wanted) {
         OreServerFormUi.Layout layout = this.ui.layout();
         if (layout != null && this.ui.focusedField() < layout.fields().size()) {
            OreBox box = layout.fields().get(this.ui.focusedField());
            double scale = Math.max(1.0, OreSession.guiScale());
            this.minecraft
               .textInputManager()
               .setTextInputArea(
                  (int)Math.round(box.x() / scale), (int)Math.round(box.y() / scale), (int)Math.round(box.w() / scale), (int)Math.round(box.h() / scale)
               );
         }
      }
   }

   @Override
   public void removed() {
      if (this.textInput) {
         this.textInput = false;
         this.minecraft.onTextInputFocusChange(this, false);
      }

      super.removed();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      BedrockPanorama.prepare(this.minecraft);
      this.extractPanorama(graphics, a);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.ui.text(this.session.text());
      this.ui.size(OreSession.windowWidth(), OreSession.windowHeight());
      this.syncTextInput();

      try (OreSession.Painter painter = this.session.paint(graphics)) {
         this.ui.render(painter.painter(), System.currentTimeMillis());
      } catch (RuntimeException e) {
         this.parent.fallBack("server form", e);
      }
   }

   private static double window(double gui) {
      return gui * OreSession.guiScale();
   }

   @Override
   public void mouseMoved(double x, double y) {
      this.ui.mouseMoved(window(x), window(y));
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() != 1) {
         return false;
      }

      this.ui.mousePressed(window(event.x()), window(event.y()), event.hasShiftDown());
      this.syncTextInput();
      return true;
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (event.button() != 1) {
         return false;
      }

      this.ui.mouseReleased(window(event.x()), window(event.y()));
      this.syncTextInput();
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      OreKey key = OreKeys.of(event);
      boolean used = key != null
         && this.ui
            .key(
               key,
               event.hasShiftDown(),
               event.hasControlDownWithQuirk(),
               () -> this.minecraft.keyboardHandler.getClipboard(),
               text -> this.minecraft.keyboardHandler.setClipboard(text)
            );
      this.syncTextInput();
      return used;
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      return event.isAllowedChatCharacter() && this.ui.typed(event.codepointAsString());
   }

   @Override
   public void onClose() {
      this.ui.back();
   }

   @Override
   public void add(String name, BedrockAddress address, boolean play) {
      this.parent.serverAdded(name, address);
      if (play) {
         this.parent.join(new ServerData(name, address.stored(), Type.OTHER));
      } else {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   @Override
   public void save(String name, BedrockAddress address) {
      if (this.index >= 0) {
         this.parent.serverEdited(this.index, name, address);
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void delete() {
      if (this.index >= 0) {
         this.parent.serverRemoved(this.index);
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void back() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public List<BedrockAddress> others() {
      return this.parent.addresses(this.index);
   }

   @Override
   public void click() {
      AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
   }
}
