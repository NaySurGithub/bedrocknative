package net.bedrocknative.client.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.FormData;
import net.bedrocknative.ui.UiDefinitionSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

public final class JsonUiFormScreen extends FormScreen implements FormData.Answers {
   static final FormData.Texts TEXTS = new FormData.Texts() {
      @Override
      public String raw(String text) {
         if (text.startsWith("{")) {
            try {
               JsonElement json = JsonParser.parseString(text);
               if (json.isJsonObject() && (json.getAsJsonObject().has("rawtext") || json.getAsJsonObject().has("translate"))) {
                  return BedrockText.rawTextString(text);
               }
            } catch (RuntimeException var3) {
            }
         }

         return text;
      }

      @Override
      public String localized(String text) {
         String raw = this.raw(text);
         String key = raw.startsWith("%") ? raw.substring(1) : raw;
         String found = !key.isEmpty() && key.length() <= 256 && key.indexOf(32) < 0 ? BedrockText.lookupKey(key) : null;
         return found != null ? found : raw;
      }
   };
   private final FormData data;
   private final JsonUiScreen ui;
   @Nullable
   private final Screen settingsParent;
   private boolean opened;
   private boolean hidden;

   private JsonUiFormScreen(FormScreen.Host host, int formId, FormData data, JsonUiScreen ui, @Nullable FormScreen below, @Nullable Screen settingsParent) {
      super(host, formId, data.form().title(), below);
      this.data = data;
      this.ui = ui;
      this.settingsParent = settingsParent;
      data.answers(this);
   }

   @Nullable
   static JsonUiFormScreen create(FormScreen.Host host, int formId, BedrockForm form, @Nullable FormScreen below, @Nullable Screen settingsParent) {
      if (!FormData.supports(form)) {
         return null;
      } else {
         try {
            UiDefinitionSet definitions = JsonUi.definitions();
            if (definitions == null) {
               return null;
            } else {
               FormData data = new FormData(form, TEXTS, settingsParent != null);
               JsonUiScreen ui = JsonUiScreen.create(definitions, "server_form.third_party_server_screen", Map.of(), data);
               if (ui != null
                  && ui.instance().factoryEvent(data.factory(), data.controlId(), data.variables(), true) == 1
                  && JsonUi.use(data.key(), ui.instance())) {
                  ui.instance().problems().log("server form " + formId);
                  int unresolved = ui.instance().problems().count("unresolved-base");
                  if (unresolved > 0) {
                     BedrockNative.LOGGER.info("JSON UI server form {}: {} unresolved base elements, the Java form shows it", formId, unresolved);
                     return null;
                  } else {
                     String title = TEXTS.raw(form.title());
                     ui.diagnostics("server form " + formId, "form " + data.controlId() + " " + (title.length() > 64 ? title.substring(0, 64) : title));
                     return new JsonUiFormScreen(host, formId, data, ui, below, settingsParent);
                  }
               } else {
                  return null;
               }
            }
         } catch (RuntimeException var10) {
            BedrockNative.LOGGER.warn("JSON UI couldn't build server form {}; the Java screen shows it", formId, var10);
            return null;
         }
      }
   }

   public FormData data() {
      return this.data;
   }

   public JsonUiScreen ui() {
      return this.ui;
   }

   public BedrockForm form() {
      return this.data.form();
   }

   @Nullable
   @Override
   public Screen settingsParent() {
      return this.settingsParent;
   }

   @Override
   public boolean isServerSettings() {
      return this.settingsParent != null;
   }

   void fallback(Runnable action) {
      this.ui.fallback(action);
   }

   @Override
   protected void init() {
      this.ui.resize(this.width, this.height);
      if (!this.opened) {
         this.ui.event("screen.entrance_push");
      } else if (this.hidden) {
         this.ui.event("screen.entrance_pop");
      }

      this.opened = true;
      this.hidden = false;
   }

   @Override
   protected void buildBody(LinearLayout body, int width) {
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.minecraft.gui.hud.extractDeferredSubtitles();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      for (String url : this.data.pendingImages()) {
         if (!BedrockTextures.url(url).loading()) {
            this.data.imageLoaded(url);
         }
      }

      this.ui.render(graphics);
   }

   public void mouseMoved(double x, double y) {
      this.ui.mouseMoved(x, y);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return this.ui.mouseClicked(event);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return this.ui.mouseReleased(event);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.ui.mouseDragged(event);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.ui.mouseScrolled(x, y, scrollY);
   }

   public boolean keyPressed(KeyEvent event) {
      return this.ui.keyPressed(event);
   }

   public boolean charTyped(CharacterEvent event) {
      return this.ui.charTyped(event);
   }

   public boolean preeditUpdated(@Nullable PreeditEvent event) {
      return this.ui.preeditUpdated(event);
   }

   @Override
   public void onClose() {
      if (this.isServerSettings()) {
         this.host.submit(this, this.data.response());
      } else {
         this.host.cancel(this);
      }
   }

   @Override
   public void removed() {
      this.hidden = true;
      super.removed();
   }

   @Override
   public void submit(String response) {
      this.host.submit(this, response);
   }

   @Override
   public void close() {
      this.host.cancel(this);
   }
}
