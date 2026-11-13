package net.bedrocknative.client.account;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.ui.ItemSource;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.text.BedrockLanguage;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.QrCode;
import net.bedrocknative.ui.SignInData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiDrawCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class JsonUiSignIn implements ItemSource {
   private static final int HINT_COLOUR = -3092272;
   @Nullable
   private static String languageLoaded;
   private final Minecraft minecraft = Minecraft.getInstance();
   private final JsonUiSignIn.Host host;
   @Nullable
   private JsonUiScreen ui;
   @Nullable
   private SignInData data;
   private String builtFor = "";
   private boolean failed;
   private boolean pressed;
   @Nullable
   private Component hint;
   @Nullable
   private String pictureUrl;
   @Nullable
   private BedrockTextures.UrlImage picture;

   JsonUiSignIn(JsonUiSignIn.Host host) {
      this.host = host;
   }

   static boolean wanted(SignInData.View view) {
      return JsonUi.drawn(view.gateKey) && JsonUi.mode() != JsonUi.Mode.OFF && BedrockInstall.vanillaResourcePack().isPresent();
   }

   boolean showing() {
      return this.ui != null && !this.failed;
   }

   boolean failedOnce() {
      return this.failed;
   }

   @Nullable
   SignInData.View view() {
      return this.showing() && this.data != null ? this.data.view() : null;
   }

   @Nullable
   SignInData data() {
      return this.showing() ? this.data : null;
   }

   @Nullable
   JsonUiScreen screen() {
      return this.showing() ? this.ui : null;
   }

   boolean show(SignInData.View view, String key, Consumer<SignInData> fill) {
      if (!this.failed && wanted(view)) {
         String fullKey = view + "\n" + key;
         if (this.ui != null && this.data != null && fullKey.equals(this.builtFor)) {
            fill.accept(this.data);
            return true;
         } else {
            UiDefinitionSet definitions = JsonUi.definitions();
            if (definitions == null) {
               return false;
            } else {
               this.bedrockLanguage();
               SignInData fresh = new SignInData(view);
               fresh.actions(this.host);
               fill.accept(fresh);

               JsonUiScreen screen;
               try {
                  screen = JsonUiScreen.create(definitions, view.screen, SignInData.variables(), fresh);
               } catch (RuntimeException var9) {
                  BedrockNative.LOGGER.warn("Bedrock's sign-in screen {} failed; the account screen's own panel takes its place", view, var9);
                  this.failed = true;
                  return false;
               }

               if (screen == null) {
                  BedrockNative.LOGGER.info("Bedrock's sign-in screen {} does not resolve here; the account screen's own panel takes its place", view);
                  this.failed = true;
                  return false;
               } else {
                  screen.items(this);
                  screen.diagnostics("Xbox sign-in " + view, view.screen);
                  screen.fallback(() -> {
                     this.failed = true;
                     this.ui = null;
                  });
                  screen.instance().problems().log("Xbox sign-in " + view);
                  this.ui = screen;
                  this.data = fresh;
                  this.builtFor = fullKey;
                  this.pressed = false;
                  screen.resize(this.minecraft.getWindow().getGuiScaledWidth(), this.minecraft.getWindow().getGuiScaledHeight());
                  screen.event("screen.entrance_push");
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   void hide() {
      this.ui = null;
      this.data = null;
      this.builtFor = "";
      this.hint = null;
   }

   void hint(@Nullable Component line) {
      this.hint = line;
   }

   void picture(@Nullable String url) {
      if (!Objects.equals(url, this.pictureUrl)) {
         this.pictureUrl = url;
         this.picture = url == null ? null : BedrockTextures.url(url);
      }
   }

   private void bedrockLanguage() {
      String code = this.minecraft.options.languageCode;
      if (this.minecraft.getConnection() == null && !code.equals(languageLoaded)) {
         languageLoaded = code;
         BedrockLanguage.textsFolder().ifPresent(texts -> BedrockText.useBedrockLanguage(BedrockLanguage.loadCached(texts, code)));
      }
   }

   void resize(int width, int height) {
      if (this.ui != null) {
         this.ui.resize(width, height);
      }
   }

   void render(GuiGraphicsExtractor graphics, int width, int height) {
      JsonUiScreen screen = this.ui;
      if (screen != null && !this.failed) {
         screen.render(graphics);
         Component line = this.hint;
         if (line != null && !this.failed) {
            int y = height - 4;
            List<FormattedCharSequence> lines = this.minecraft.font.split(line, Math.max(100, width - 20));
            y -= lines.size() * 10;

            for (FormattedCharSequence part : lines) {
               graphics.centeredText(this.minecraft.font, part, width / 2, y, -3092272);
               y += 10;
            }
         }
      }
   }

   void mouseMoved(double x, double y) {
      if (this.showing()) {
         this.ui.mouseMoved(x, y);
      }
   }

   boolean mouseClicked(MouseButtonEvent event) {
      if (this.showing() && event.button() == 1) {
         if (this.data != null && this.data.view() == SignInData.View.CODE) {
            if (this.hits("banner_1", event.x(), event.y())) {
               this.host.openLink();
               return true;
            }

            if (this.hits("banner_2", event.x(), event.y())) {
               this.host.copyCode();
               return true;
            }
         }

         this.pressed = true;
         return this.ui.mouseClicked(event);
      } else {
         return this.showing();
      }
   }

   boolean mouseReleased(MouseButtonEvent event) {
      if (!this.showing()) {
         return false;
      } else if (this.pressed && event.button() == 1) {
         this.pressed = false;
         return this.ui.mouseReleased(event);
      } else {
         return true;
      }
   }

   boolean mouseDragged(MouseButtonEvent event) {
      return this.showing() && this.pressed && this.ui.mouseDragged(event);
   }

   boolean mouseScrolled(double x, double y, double scrollY) {
      return this.showing() && this.ui.mouseScrolled(x, y, scrollY);
   }

   boolean keyPressed(KeyEvent event) {
      return this.showing() && this.ui.keyPressed(event);
   }

   boolean charTyped(CharacterEvent event) {
      return this.showing() && this.ui.charTyped(event);
   }

   @Nullable
   UiControl control(String name) {
      JsonUiScreen screen = this.ui;
      if (screen != null && screen.instance().root() != null) {
         UiControl[] found = new UiControl[1];
         screen.instance().root().walk(control -> {
            if (found[0] == null && control.visible() && name.equals(control.name())) {
               found[0] = control;
            }
         });
         return found[0];
      } else {
         return null;
      }
   }

   private boolean hits(String name, double x, double y) {
      UiControl control = this.control(name);
      if (control == null) {
         return false;
      } else {
         float ux = JsonUi.toUi(x);
         float uy = JsonUi.toUi(y);
         return ux >= control.x() && uy >= control.y() && ux < control.x() + control.width() && uy < control.y() + control.height();
      }
   }

   @Override
   public ItemStack stack(UiControl renderer) {
      return ItemStack.EMPTY;
   }

   @Override
   public boolean draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      String var3 = renderer.renderer();

      return switch (var3) {
         case "qr_code_renderer" -> {
            SignInData current = this.data;
            QrCode qr = current == null ? null : current.qr();
            if (qr != null) {
               this.qr(graphics, qr, renderer);
            }

            yield true;
         }
         case "profile_image_renderer" -> {
            this.picture(graphics, renderer);
            yield true;
         }
         default -> false;
      };
   }

   private void qr(GuiGraphicsExtractor graphics, QrCode qr, UiDrawCommand.Custom renderer) {
      float guiScale = this.minecraft.getWindow().getGuiScale();
      int left = Math.round(renderer.x() * guiScale);
      int top = Math.round(renderer.y() * guiScale);
      int side = Math.round(Math.min(renderer.w(), renderer.h()) * guiScale);
      int module = SignInData.moduleSize(qr, side);
      int x0 = left + (side - module * qr.size()) / 2;
      int y0 = top + (side - module * qr.size()) / 2;
      graphics.pose().pushMatrix();

      try {
         graphics.pose().scale(1.0F / guiScale, 1.0F / guiScale);
         graphics.fill(left, top, left + side, top + side, -1);

         for (int y = 0; y < qr.size(); y++) {
            int x = 0;

            while (x < qr.size()) {
               if (!qr.dark(x, y)) {
                  x++;
               } else {
                  int run = x;

                  while (run < qr.size() && qr.dark(run, y)) {
                     run++;
                  }

                  graphics.fill(x0 + x * module, y0 + y * module, x0 + run * module, y0 + (y + 1) * module, -16777216);
                  x = run;
               }
            }
         }
      } finally {
         graphics.pose().popMatrix();
      }
   }

   private void picture(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      BedrockTextures.UrlImage image = this.picture;
      if (image != null) {
         image.texture()
            .ifPresent(
               texture -> {
                  int size = Math.round(Math.min(renderer.w(), renderer.h()));
                  graphics.blit(
                     RenderPipelines.GUI_TEXTURED,
                     texture.id(),
                     Math.round(renderer.x()),
                     Math.round(renderer.y()),
                     0.0F,
                     0.0F,
                     size,
                     size,
                     texture.width(),
                     texture.height(),
                     texture.width(),
                     texture.height(),
                     -1
                  );
               }
            );
      }
   }

   interface Host extends SignInData.Actions {
      void openLink();

      void copyCode();
   }
}
