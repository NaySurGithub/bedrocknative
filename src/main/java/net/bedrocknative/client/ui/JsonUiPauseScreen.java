package net.bedrocknative.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.PaperDoll;
import net.bedrocknative.ui.PauseData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiDrawCommand;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class JsonUiPauseScreen extends PauseScreen implements JsonUiHost, PauseData.Actions, ItemSource {
   public static final String PAUSE = "pause";
   private static final int SCREENSHOT_DELAY_TICKS = 2;
   private static final float PLAYER_HEIGHT = 1.875F;
   private static final float DOLL_MIDDLE = 0.9F;
   private static final float DOLL_Y_OFFSET = 0.0625F;
   private static boolean failed;
   private static int screenshotIn = -1;
   private static boolean tickRegistered;
   private final JsonUiScreen ui;
   private final PauseData data;
   private final MenuNotice notice = new MenuNotice();
   private boolean opened;
   private boolean uiPressed;
   private double mouseX = Double.NaN;
   private double mouseY = Double.NaN;

   private JsonUiPauseScreen(JsonUiScreen ui, PauseData data) {
      super(true);
      this.ui = ui;
      this.data = data;
      data.actions(this);
      ui.items(this);
   }

   static Screen swap(PauseScreen java) {
      Minecraft minecraft = Minecraft.getInstance();
      if (java.showsPauseMenu() && !failed && JsonUi.offered("pause") && BedrockMovement.active() && minecraft.player != null) {
         UiDefinitionSet definitions = JsonUi.definitions();
         if (definitions == null) {
            return java;
         } else {
            try {
               PauseData data = new PauseData();
               data.social(BedrockText.translate("menu.socialfriends", List.of("0")));
               JsonUiScreen ui = JsonUiScreen.create(definitions, "pause.pause_screen", PauseData.variables(), data);
               if (ui != null && JsonUi.use("pause", ui.instance())) {
                  ui.instance().problems().log("pause screen");
                  JsonUiPauseScreen screen = new JsonUiPauseScreen(ui, data);
                  ui.fallback(() -> {
                     failed = true;
                     if (minecraft.gui.screen() == screen) {
                        minecraft.gui.setScreen(new PauseScreen(true));
                     }
                  });
                  return screen;
               } else {
                  return java;
               }
            } catch (RuntimeException var6) {
               BedrockNative.LOGGER.warn("JSON UI couldn't build the pause screen; the Java screen shows it", var6);
               return java;
            }
         }
      } else {
         return java;
      }
   }

   public static void reset() {
      failed = false;
   }

   public static boolean hidesHud() {
      return Minecraft.getInstance().gui.screen() instanceof JsonUiPauseScreen screen && !screen.ui.failed();
   }

   @Override
   public JsonUiScreen ui() {
      return this.ui;
   }

   public PauseData data() {
      return this.data;
   }

   public MenuNotice notice() {
      return this.notice;
   }

   protected void init() {
      this.ui.resize(this.width, this.height);
      if (!this.opened) {
         this.opened = true;
         this.ui.event("screen.entrance_push");
      }
   }

   private void refresh() {
      LocalPlayer player = this.minecraft.player;
      if (player != null) {
         String name = player.getGameProfile().name();
         List<String> names = new ArrayList<>();

         for (PlayerInfo info : player.connection.getListedOnlinePlayers().stream().sorted(Comparator.comparing(infox -> infox.getProfile().name())).toList()) {
            names.add(info.getProfile().name());
         }

         this.data.players(name, names, names.indexOf(name));
      }
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      if (!this.ui.failed()) {
         this.refresh();
         this.ui.render(graphics);
         this.notice.render(graphics, this.width);
      }
   }

   public void mouseMoved(double x, double y) {
      this.mouseX = x;
      this.mouseY = y;
      this.ui.mouseMoved(x, y);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 1) {
         this.uiPressed = true;
         return this.ui.mouseClicked(event);
      } else {
         return false;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.uiPressed && event.button() == 1) {
         this.uiPressed = false;
         return this.ui.mouseReleased(event);
      } else {
         return false;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.uiPressed && this.ui.mouseDragged(event);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.ui.mouseScrolled(x, y, scrollY);
   }

   @Override
   public void resume() {
      if (this.minecraft.gui.screen() == this) {
         this.onClose();
      }
   }

   @Override
   public void settings() {
      this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options));
   }

   @Override
   public void quit() {
      this.minecraft
         .getReportingContext()
         .draftReportHandled(this.minecraft, this, () -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
   }

   @Override
   public void screenshot() {
      if (!tickRegistered) {
         tickRegistered = true;
         ClientTickEvents.END_CLIENT_TICK.register(JsonUiPauseScreen::tickScreenshot);
      }

      screenshotIn = 2;
      this.resume();
   }

   private static void tickScreenshot(Minecraft minecraft) {
      if (screenshotIn >= 0) {
         if (screenshotIn-- == 0) {
            Screenshot.grab(minecraft, false);
         }
      }
   }

   @Override
   public void unavailable(String labelKey) {
      this.notice.unavailable(labelKey);
   }

   @Override
   public ItemStack stack(UiControl renderer) {
      return ItemStack.EMPTY;
   }

   @Override
   public boolean draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      LocalPlayer player = this.minecraft.player;
      String kind = renderer.renderer();
      if (player != null && "name_tag_renderer".equals(kind)) {
         NameTagRenderer.draw(graphics, renderer, player.getGameProfile().name());
         return true;
      } else if (player != null && !(renderer.h() <= 0.0F) && ("paper_doll_renderer".equals(kind) || "live_player_renderer".equals(kind))) {
         PaperDoll.Stand stand = PaperDoll.stand(renderer.x(), renderer.y(), renderer.w(), renderer.h());
         float scale = stand.height() / 1.875F;
         float middle = stand.feetY() - 0.9625F * scale;
         float half = 1.1F * scale;
         int x0 = Math.round(stand.centreX() - half);
         int y0 = Math.round(middle - half);
         int x1 = Math.round(stand.centreX() + half);
         int y1 = Math.round(middle + half);
         float gazeX = Double.isNaN(this.mouseX) ? (x0 + x1) / 2.0F : (float)this.mouseX;
         float gazeY = Double.isNaN(this.mouseY) ? (y0 + y1) / 2.0F : (float)this.mouseY;
         InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, Math.round(scale), 0.0625F, gazeX, gazeY, player);
         return true;
      } else {
         return false;
      }
   }
}
