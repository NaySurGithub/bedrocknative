package net.bedrocknative.client.lobby;

import java.util.List;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.oreui.OreKeys;
import net.bedrocknative.client.oreui.OreSession;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.oreui.OreDisconnect;
import net.bedrocknative.oreui.OreDisconnectUi;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OreKey;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class OreDisconnectScreen extends Screen implements LobbyScreen, OreDisconnectUi.Actions {
   static final List<String> REQUIRED_SPRITES = List.of("connection_error", "ClientMismatchImage", "arrowBack");
   private final BedrockDisconnects.Ended end;
   private final OreSession session;
   private final OreDisconnectUi ui;

   private OreDisconnectScreen(BedrockDisconnects.Ended end, OreSession session, OreDisconnect data) {
      super(Component.literal(data.title()));
      this.end = end;
      this.session = session;
      this.ui = new OreDisconnectUi(session.text(), session.lang(), session.theme(), data, this);
   }

   static @Nullable OreDisconnectScreen create(BedrockDisconnects.Ended end) {
      OreSession session = OreUi.session();
      if (session == null) {
         return null;
      } else {
         OreInstall install = session.install();
         if (!REQUIRED_SPRITES.stream().allMatch(install::has)) {
            session.release();
            return null;
         } else {
            OreDisconnect data = OreDisconnect.of(end.end(), end.server().name, new BedrockAddress(end.host(), end.port()).display(), session.lang());
            return new OreDisconnectScreen(end, session, data);
         }
      }
   }

   public OreDisconnectUi ui() {
      return this.ui;
   }

   public Screen parent() {
      return this.end.parent();
   }

   @Override
   protected void init() {
      this.ui.size(OreSession.windowWidth(), OreSession.windowHeight());
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      this.session.release();
      super.removed();
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

      try (OreSession.Painter painter = this.session.paint(graphics)) {
         this.ui.render(painter.painter(), System.currentTimeMillis());
      } catch (RuntimeException e) {
         OreUi.fail("disconnect screen", e);
         OreDisconnect data = this.ui.data();
         String words = data.variant() == OreDisconnect.Variant.SERVER ? data.serverMessage() : data.message();
         this.minecraft.execute(() -> {
            this.session.release();
            this.minecraft.gui.setScreen(new DisconnectedScreen(this.end.parent(), Component.literal(data.title()), Component.literal(words)));
         });
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

      this.ui.mousePressed(window(event.x()), window(event.y()));
      return true;
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (event.button() != 1) {
         return false;
      }

      this.ui.mouseReleased(window(event.x()), window(event.y()));
      return true;
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.ui.scrolled(window(x), window(y), scrollY);
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      OreKey key = OreKeys.of(event);
      return key != null && this.ui.key(key, event.hasShiftDown());
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      return false;
   }

   @Override
   public void onClose() {
      this.back();
   }

   @Override
   public void back() {
      this.session.release();
      this.minecraft.gui.setScreen(this.end.parent());
   }

   @Override
   public void reconnect() {
      ServerData server = this.end.server();
      BedrockAddress address = new BedrockAddress(this.end.host(), this.end.port());
      if (!BedrockAddress.parse(server.ip).sameServer(address)) {
         ServerData copy = new ServerData(server.name, address.stored(), server.type());
         copy.copyNameIconFrom(server);
         copy.setResourcePackStatus(server.getResourcePackStatus());
         server = copy;
      }

      BedrockTextures.releaseFiles();
      this.session.release();
      ConnectScreen.startConnecting(this.end.parent(), this.minecraft, ServerAddress.parseString(server.ip), server, false, null);
   }

   @Override
   public void click() {
      AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
   }
}
