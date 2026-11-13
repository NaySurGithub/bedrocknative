package net.bedrocknative.client.lobby;

import java.util.List;
import java.util.Random;
import net.bedrocknative.client.account.AccountManager;
import net.bedrocknative.client.account.AccountsScreen;
import net.bedrocknative.client.ui.ItemSource;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.client.ui.MenuNotice;
import net.bedrocknative.client.ui.NameTagRenderer;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.SplashText;
import net.bedrocknative.ui.StartData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiDrawCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class JsonUiStartScreen extends TitleScreen implements LobbyScreen, JsonUiHost, StartData.Actions, ItemSource {
   public static final String SCREEN = "start.start_screen";
   @Nullable
   private static String pinnedSplash;
   private final UiDefinitionSet definitions;
   private final StartData data;
   private final LobbyHost host;
   private final LobbyDoll doll;
   private final MenuNotice notice = new MenuNotice();
   private final String splash;
   @Nullable
   private List<String> splashLines;
   private boolean hidden;
   private boolean turning;
   @Nullable
   private String builtLanguage;
   private final DiscordButton discord = new DiscordButton(false, false);

   private JsonUiStartScreen(UiDefinitionSet definitions, StartData data, JsonUiScreen ui) {
      super(false);
      this.definitions = definitions;
      this.data = data;
      this.host = new LobbyHost(ui);
      this.doll = new LobbyDoll(this.minecraft);
      this.builtLanguage = this.minecraft.options.languageCode;
      String pinned = pinnedSplash;
      this.splash = pinned != null ? pinned : SplashText.pick(SplashText.load(PackStack.active()), new Random());
      data.actions(this);
      ui.items(this);
      ui.fallback(this::fallBack);
   }

   @Nullable
   static JsonUiStartScreen create(UiDefinitionSet definitions) {
      Minecraft minecraft = Minecraft.getInstance();
      BedrockLobby.language(minecraft, true);
      StartData data = new StartData();
      fill(data, null);
      JsonUiScreen ui = JsonUiScreen.create(definitions, "start.start_screen", StartData.variables(), data);
      if (ui == null) {
         return null;
      } else {
         ui.instance().problems().log("lobby start screen");
         return new JsonUiStartScreen(definitions, data, ui);
      }
   }

   public static void pinSplash(@Nullable String text) {
      pinnedSplash = text;
   }

   public static String versionText() {
      String version = "1.26.63";
      return "v" + (version.startsWith("1.") ? version.substring(2) : version);
   }

   private static void fill(StartData data, @Nullable LobbyDoll doll) {
      data.account(AccountManager.store().active().map(entry -> entry.gamertag()).orElse(null));
      data.face(doll != null && !data.gamertag().isEmpty() && doll.hasSkin());
      data.version(versionText());
      data.social(BedrockText.translate("menu.socialfriends", List.of("0")));
   }

   @Override
   public JsonUiScreen ui() {
      return this.host.ui();
   }

   public StartData data() {
      return this.data;
   }

   public MenuNotice notice() {
      return this.notice;
   }

   public String splash() {
      return this.splash;
   }

   @Nullable
   Screen playScreen(int tab) {
      return BedrockLobby.playScreen(this, tab);
   }

   private void fallBack() {
      BedrockLobby.fail("start screen", new IllegalStateException("JSON UI start screen failed"));
      if (this.minecraft.gui.screen() == this) {
         BedrockLobby.showJavaTitle(this.minecraft);
      }
   }

   protected void init() {
      this.splashLines = null;
      if (!this.minecraft.options.languageCode.equals(this.builtLanguage)) {
         this.builtLanguage = this.minecraft.options.languageCode;
         BedrockLobby.language(this.minecraft, false);
         JsonUiScreen fresh = JsonUiScreen.create(this.definitions, "start.start_screen", StartData.variables(), this.data);
         if (fresh != null) {
            fresh.items(this);
            fresh.fallback(this::fallBack);
            this.host.replace(fresh, this.width, this.height);
         }
      }

      fill(this.data, this.doll);
      this.host.init(this.width, this.height);
      if (this.hidden) {
         this.hidden = false;
         this.host.shownAgain();
      }
   }

   public void tick() {
      fill(this.data, this.doll);
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      BedrockPanorama.prepare(this.minecraft);
      this.extractPanorama(graphics, a);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.host.render(graphics);
      this.notice.render(graphics, this.width);
      this.discord.render(graphics, this.width, this.height, mouseX, mouseY);
   }

   public void removed() {
      this.hidden = true;
      this.releaseDoll();
      super.removed();
   }

   public void mouseMoved(double x, double y) {
      this.host.mouseMoved(x, y);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 1 && this.discord.clicked(event.x(), event.y())) {
         return true;
      } else {
         if (event.button() == 1 && this.doll.contains(event.x(), event.y())) {
            this.turning = true;
         }

         return this.host.mouseClicked(event);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.turning = false;
      return this.host.mouseReleased(event);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.turning) {
         this.doll.turn(dx);
         return true;
      } else {
         return this.host.mouseDragged(event);
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.host.mouseScrolled(x, y, scrollY);
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.isEscape()) {
         this.minecraft.gui.setScreen(new StartExitScreen(this));
         return true;
      } else {
         return this.host.keyPressed(event);
      }
   }

   public boolean charTyped(CharacterEvent event) {
      return this.host.charTyped(event);
   }

   public boolean preeditUpdated(@Nullable PreeditEvent event) {
      return this.host.preeditUpdated(event);
   }

   @Override
   public void play() {
      Screen play = this.playScreen(BedrockLobby.lastTab());
      if (play != null) {
         this.minecraft.gui.setScreen(play);
      } else {
         BedrockLobby.showJavaTitle(this.minecraft);
      }
   }

   @Override
   public void settings() {
      this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options));
   }

   @Override
   public void account() {
      this.minecraft.gui.setScreen(new AccountsScreen(this));
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
      String var3 = renderer.renderer();
      switch (var3) {
         case "paper_doll_renderer":
            this.doll.draw(graphics, renderer.x(), renderer.y(), renderer.w(), renderer.h());
            break;
         case "name_tag_renderer":
            NameTagRenderer.draw(graphics, renderer, this.data.gamertag());
            break;
         case "profile_image_renderer":
            this.doll.face(graphics, renderer.x(), renderer.y(), Math.min(renderer.w(), renderer.h()));
            break;
         case "splash_text_renderer":
            this.splash(graphics, renderer);
            break;
         default:
            return false;
      }

      return true;
   }

   private void splash(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      if (!this.splash.isEmpty()) {
         float k = JsonUi.toGui(1.0F);
         List<String> lines = this.splashLines;
         if (lines == null) {
            lines = SplashText.wrap(this.splash, this.font::width);
            this.splashLines = lines;
         }

         float scale = SplashText.scale(Util.getMillis());
         graphics.pose().pushMatrix();

         try {
            graphics.pose().translate(renderer.x() + renderer.w() / 2.0F, renderer.y() + renderer.h() / 2.0F);
            graphics.pose().scale(k, k);
            graphics.pose().translate(0.0F, 2.0F);
            graphics.pose().rotate(SplashText.ROTATION);
            graphics.pose().scale(scale, scale);
            int top = -lines.size() * 10 / 2;

            for (int i = 0; i < lines.size(); i++) {
               String line = lines.get(i);
               graphics.text(this.font, line, -this.font.width(line) / 2, top + i * 10 + 1, -256, true);
            }
         } finally {
            graphics.pose().popMatrix();
         }
      }
   }

   void releaseDoll() {
      this.doll.release();
   }
}
