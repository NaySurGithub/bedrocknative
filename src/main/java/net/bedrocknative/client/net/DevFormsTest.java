package net.bedrocknative.client.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.form.BedrockForms;
import net.bedrocknative.client.form.CustomFormScreen;
import net.bedrocknative.client.form.FormScreen;
import net.bedrocknative.client.form.FormSlider;
import net.bedrocknative.client.form.JsonUiFormScreen;
import net.bedrocknative.client.form.ModalFormScreen;
import net.bedrocknative.client.form.SimpleFormScreen;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.ui.FormData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiType;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerSettingsResponsePacket;
import org.jspecify.annotations.Nullable;

final class DevFormsTest {
   private static final long STEP = 700L;
   private static final KeyEvent ESCAPE = new KeyEvent(41, 0, 0);
   private static final KeyEvent ENTER = new KeyEvent(40, 0, 0);
   private static final KeyEvent BACKSPACE = new KeyEvent(42, 8, 0);
   private static final String DIAMOND_URL = "https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/1.20.2/items/diamond.png";
   private static final String NAME = "Alex \"é\" \\o/";
   private final Minecraft minecraft;
   private final BedrockForms forms;

   DevFormsTest(Minecraft minecraft, BedrockForms forms) {
      this.minecraft = minecraft;
      this.forms = forms;
   }

   void run() {
      this.run(() -> this.minecraft.options.pauseOnLostFocus = false);
      log("JSON UI mode {}", JsonUi.mode());
      this.simple();
      this.modal();
      this.custom();
      this.cancel();
      this.busy();
      this.stack();
      this.replaced();
      this.closedByServer();
      this.marker();
      this.urlImage();
      this.badSlider();
      this.serverSettings();
      log("done, {} answers sent", this.onClient(this.forms::answeredCount));
   }

   private void simple() {
      FormScreen form = this.open("simple");
      sleep(2000L);
      this.screenshot("simple");
      log("simple form ({}): {} buttons, pressing button 1 (the URL image one)", kind(form), this.buttons(form));
      this.pressButton(form, 1);
      sleep(700L);
   }

   private void modal() {
      FormScreen form = this.open("modal");
      sleep(700L);
      if (form instanceof JsonUiFormScreen json) {
         this.screenshot("modal-jsonui");
         this.checkModal(json);
         log("modal form (JSON UI): pressing button2");
         this.click(json, this.onClient(() -> json.ui().input().firing("popup_dialog.rightcancel_button", null, -1)));
      } else {
         this.screenshot("modal");
         if (JsonUi.drawn("modal_form")) {
            log("JSON UI: modal form shown by Java's {} while JSON UI draws modal forms: FAIL", form.getClass().getSimpleName());
         }

         log("modal form ({}): pressing button2", kind(form));
         this.press(((ModalFormScreen)form).buttons().get(1));
      }

      sleep(700L);
   }

   private void checkModal(JsonUiFormScreen json) {
      String line = this.onClient(
         () -> {
            if (!(json.form() instanceof BedrockForm.Modal)) {
               return "not a modal form: FAIL";
            } else {
               UiControl dialog = json.ui().instance().find("modal_bg_buttons");
               UiControl first = json.ui().input().firing("popup_dialog.left_button", null, -1);
               UiControl second = json.ui().input().firing("popup_dialog.rightcancel_button", null, -1);
               List<String> labels = new ArrayList<>();
               if (dialog != null) {
                  dialog.walk(control -> {
                     if (control.type() == UiType.LABEL && control.visible() && !control.text().isEmpty()) {
                        labels.add(control.text());
                     }
                  });
               }

               FormData data = json.data();
               List<String> wanted = List.of(
                  data.global("#modal_title_text").asString(),
                  data.global("#modal_label_text").asString(),
                  data.global("#modal_left_button_text").asString(),
                  data.global("#modal_rightcancel_button_text").asString()
               );
               boolean pass = dialog != null
                  && dialog.visible()
                  && first != null
                  && second != null
                  && first.y() + first.height() <= second.y() + 0.01F
                  && labels.equals(wanted);
               return String.format(
                  "popup %s (%.0fx%.0f), labels %s, button1 above button2 %s, problems %s: %s",
                  "modal_dialog_popup",
                  dialog == null ? 0.0F : dialog.width(),
                  dialog == null ? 0.0F : dialog.height(),
                  labels,
                  first != null && second != null && first.y() < second.y(),
                  json.ui().instance().problems(),
                  pass ? "PASS" : "FAIL"
               );
            }
         }
      );
      log("JSON UI: modal form {}", line);
   }

   private void custom() {
      FormScreen form = this.open("custom");
      sleep(700L);
      this.screenshot("custom");
      if (form instanceof JsonUiFormScreen json) {
         this.customJson(json);
      } else {
         CustomFormScreen java = (CustomFormScreen)form;
         this.onClient(() -> {
            List<AbstractWidget> controls = java.controls();
            ((EditBox)controls.get(2)).setValue("Alex \"é\" \\o/");
            cycle(controls.get(3), Boolean.FALSE);
            ((FormSlider)controls.get(4)).setStep(15);
            cycle(controls.get(6), 2);
            ((FormSlider)controls.get(7)).setStep(32);
            return null;
         });
         sleep(700L);
         this.screenshot("custom-filled");
         log("custom form (Java): submitting {}", this.onClient(() -> java.controls().stream().map(c -> c == null ? "-" : c.getMessage().getString()).toList()));
         this.press((AbstractButton)java.submitButton());
         sleep(700L);
      }
   }

   private void customJson(JsonUiFormScreen form) {
      UiControl edit = this.element(form, 2, UiType.EDIT_BOX);
      this.click(form, edit);
      int length = this.onClient(() -> form.ui().input().text(edit).length());

      for (int i = 0; i < length; i++) {
         this.run(() -> form.keyPressed(BACKSPACE));
      }

      "Alex \"é\" \\o/".codePoints().forEach(codePoint -> this.run(() -> form.charTyped(new CharacterEvent(codePoint))));
      this.run(() -> form.keyPressed(ENTER));
      this.click(form, this.element(form, 3, UiType.TOGGLE));
      this.slide(form, this.element(form, 4, UiType.SLIDER), 1.0F);
      this.click(form, this.element(form, 6, UiType.DROPDOWN));
      sleep(700L);
      this.screenshot("custom-dropdown");
      List<UiControl> options = this.onClient(() -> visible(this.generated(form, 6), UiType.TOGGLE));
      log("custom form (JSON UI): dropdown shows {} options", options.size());
      if (options.size() > 2) {
         this.click(form, options.get(2));
      }

      this.slide(form, this.element(form, 7, UiType.SLIDER), 0.50793654F);
      sleep(700L);
      this.screenshot("custom-filled");
      log("custom form (JSON UI): submitting {}", this.onClient(() -> form.data().response().strip()));
      this.click(form, this.onClient(() -> form.ui().input().firing("button.submit_custom_form", null, -1)));
      sleep(700L);
   }

   private void cancel() {
      FormScreen form = this.open("simple");
      sleep(700L);
      log("closing a form with Esc ({})", kind(form));
      this.run(() -> form.keyPressed(ESCAPE));
      sleep(700L);
   }

   private void busy() {
      this.command("scriptevent bedrocknative:form busy");
      sleep(300L);
      this.onClient(() -> {
         this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
         return null;
      });
      int before = this.onClient(this.forms::answeredCount);
      long started = System.currentTimeMillis();

      while (this.onClient(this.forms::answeredCount) == before && System.currentTimeMillis() - started < 6000L) {
         sleep(100L);
      }

      log("busy: form answered while the inventory was open: {}, screen {}", this.onClient(this.forms::answeredCount) > before, this.screenName());
      this.onClient(() -> {
         this.minecraft.gui.setScreen(null);
         return null;
      });
      sleep(700L);
   }

   private void stack() {
      FormScreen first = this.open("stack");
      FormScreen second = this.awaitModal(first, 5000L);
      sleep(700L);
      this.screenshot("stacked");
      log("stack: modal over form {} (below {}, {})", second.formId(), this.onClient(() -> second.below() == first), kind(second));
      if (JsonUi.drawn("modal_form")) {
         log(
            "JSON UI: stacked modal form shown by {} over the {} form: {}",
            second.getClass().getSimpleName(),
            kind(first),
            second instanceof JsonUiFormScreen && this.onClient(() -> second.below() == first) ? "PASS" : "FAIL"
         );
      }

      if (second instanceof JsonUiFormScreen json) {
         this.click(json, this.onClient(() -> json.ui().input().firing("popup_dialog.left_button", null, -1)));
      } else {
         this.press(((ModalFormScreen)second).buttons().getFirst());
      }

      long started = System.currentTimeMillis();

      while (this.onClient(() -> this.minecraft.gui.screen() != first) && System.currentTimeMillis() - started < 2000L) {
         sleep(100L);
      }

      log("stack: back to the first form: {}", this.onClient(() -> this.minecraft.gui.screen() == first));
      sleep(700L);
      this.screenshot("stack-back");
      this.pressButton(first, 0);
      sleep(700L);
   }

   private void replaced() {
      this.open("simple");
      sleep(700L);
      int before = this.onClient(this.forms::answeredCount);
      this.onClient(() -> {
         this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
         return null;
      });
      log("replaced: form answered when the inventory replaced it: {}", this.onClient(this.forms::answeredCount) > before);
      this.onClient(() -> {
         this.minecraft.gui.setScreen(null);
         return null;
      });
      sleep(700L);
   }

   private void closedByServer() {
      this.open("close");
      long started = System.currentTimeMillis();

      while (this.onClient(() -> this.minecraft.gui.screen() instanceof FormScreen) && System.currentTimeMillis() - started < 6000L) {
         sleep(100L);
      }

      log("close: form closed by the server: {}", this.onClient(() -> this.minecraft.gui.screen() == null));
      sleep(1500L);
   }

   private void marker() {
      FormScreen form;
      try {
         form = this.open("marker");
      } catch (IllegalStateException var3) {
         log("marker: no form ({}); run testing/bds/install.sh and restart the BDS", var3.getMessage());
         return;
      }

      sleep(700L);
      this.screenshot("marker");
      if (form instanceof JsonUiFormScreen json) {
         log(
            "marker: JSON UI, marker layout visible {}, vanilla layout visible {}, title '{}'",
            this.onClient(() -> visible(json, "bn_marker_layout")),
            this.onClient(() -> visible(json, "vanilla_layout")),
            this.onClient(() -> {
               UiControl layout = json.ui().instance().find("bn_marker_layout");
               UiControl title = layout == null ? null : layout.find("common_dialogs_1");
               return title == null ? null : title.text();
            })
         );
      } else {
         log("marker: Java screen (the marker layout needs JSON UI, on by default; -PjsonUi=off was given)");
      }

      this.pressButton(form, 1);
      sleep(700L);
   }

   private void urlImage() {
      this.run(
         () -> {
            ModalFormRequestPacket request = new ModalFormRequestPacket();
            request.setFormId(9002);
            request.setFormData(
               "{\"type\":\"form\",\"title\":\"Images\",\"content\":\"url and path images\",\"buttons\":[{\"text\":\"Diamond (url)\",\"image\":{\"type\":\"url\",\"data\":\"https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/1.20.2/items/diamond.png\"}},{\"text\":\"Missing (url)\",\"image\":{\"type\":\"url\",\"data\":\"https://example.invalid/missing.png\"}},{\"text\":\"Apple (path)\",\"image\":{\"type\":\"path\",\"data\":\"textures/items/apple\"}},{\"text\":\"Leather helmet (tga)\",\"image\":{\"type\":\"path\",\"data\":\"textures/items/leather_helmet\"}},{\"text\":\"Netherite sword (vanilla_1.16)\",\"image\":{\"type\":\"path\",\"data\":\"textures/items/netherite_sword\"}},{\"text\":\"Bad path\",\"image\":{\"type\":\"path\",\"data\":\"//cdn.example.com/a|b.png?size=64\"}},{\"text\":\"No host\",\"image\":{\"type\":\"url\",\"data\":\"http://my_host/x.png\"}}]}"
            );
            this.forms.apply(request);
         }
      );
      FormScreen form = this.await(FormScreen.class, 2000L);
      long started = System.currentTimeMillis();

      while (
         this.onClient(
               () -> BedrockTextures.url("https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/1.20.2/items/diamond.png").loading()
            )
            && System.currentTimeMillis() - started < 25000L
      ) {
         sleep(200L);
      }

      sleep(700L);
      this.screenshot("url-images");
      log(
         "url image form ({}) shown with {} buttons, url image loaded: {} after {} ms",
         kind(form),
         this.buttons(form),
         this.onClient(
            () -> BedrockTextures.url("https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/1.20.2/items/diamond.png")
               .texture()
               .isPresent()
         ),
         System.currentTimeMillis() - started
      );
      this.run(() -> form.keyPressed(ESCAPE));
      sleep(700L);
   }

   private void badSlider() {
      this.run(() -> {
         ModalFormRequestPacket request = new ModalFormRequestPacket();
         request.setFormId(9003);
         request.setFormData("{\"type\":\"custom_form\",\"title\":\"Bad slider\",\"content\":[{\"type\":\"slider\",\"text\":\"x\",\"min\":-1e39,\"max\":1}]}");
         this.forms.apply(request);
      });
      FormScreen form = this.await(FormScreen.class, 2000L);
      sleep(700L);
      log("bad slider: error form shown ({}): {}", kind(form), this.<Component>onClient(form::getTitle).getString());
      this.run(() -> form.keyPressed(ESCAPE));
      sleep(700L);
   }

   private void serverSettings() {
      this.onClient(
         () -> {
            this.minecraft.gui.setScreen(new OptionsScreen(null, this.minecraft.options));
            ServerSettingsResponsePacket settings = new ServerSettingsResponsePacket();
            settings.setFormId(9001);
            settings.setFormData(
               "{\"type\":\"custom_form\",\"title\":\"§6My server\",\"icon\":{\"type\":\"path\",\"data\":\"textures/ui/WarningGlyph\"},\"content\":[{\"type\":\"label\",\"text\":\"Lobby options\"},{\"type\":\"toggle\",\"text\":\"Show particles\",\"default\":true},{\"type\":\"step_slider\",\"text\":\"Difficulty\",\"steps\":[\"Easy\",\"Normal\",\"Hard\"],\"default\":1}]}"
            );
            this.forms.apply(settings);
            return null;
         }
      );
      sleep(700L);
      this.screenshot("options");
      AbstractButton button = this.onClient(
         () -> Screens.getWidgets(this.minecraft.gui.screen())
            .stream()
            .filter(BedrockForms.SettingsButton.class::isInstance)
            .map(AbstractButton.class::cast)
            .findFirst()
            .orElse(null)
      );
      log("server settings button on the options screen: {}", button != null ? button.getMessage().getString() : "missing");
      if (button != null) {
         this.press(button);
         FormScreen form = this.await(FormScreen.class, 2000L);
         if (form instanceof JsonUiFormScreen json) {
            sleep(700L);
            this.click(json, this.element(json, 1, UiType.TOGGLE));
            this.slide(json, this.element(json, 2, UiType.SLIDER), 1.0F);
            log("server settings (JSON UI): values {}", this.onClient(() -> json.data().response().strip()));
         } else {
            CustomFormScreen java = (CustomFormScreen)form;
            this.onClient(() -> {
               cycle(java.controls().get(1), Boolean.FALSE);
               ((FormSlider)java.controls().get(2)).setStep(2);
               return null;
            });
         }

         sleep(700L);
         this.screenshot("server-settings");
         this.run(() -> form.keyPressed(ESCAPE));
         sleep(700L);
         log(
            "server settings closed, back on {}, stale settings button gone: {}",
            this.screenName(),
            this.onClient(() -> Screens.getWidgets(this.minecraft.gui.screen()).stream().noneMatch(BedrockForms.SettingsButton.class::isInstance))
         );
         this.onClient(() -> {
            this.minecraft.gui.setScreen(null);
            return null;
         });
      }
   }

   private static String kind(Screen form) {
      return form instanceof JsonUiFormScreen ? "JSON UI" : "Java";
   }

   private int buttons(FormScreen form) {
      return this.onClient(() -> {
         if (form instanceof SimpleFormScreen simple) {
            return simple.buttons().size();
         } else {
            return form instanceof JsonUiFormScreen json && json.form() instanceof BedrockForm.Simple simple ? simple.buttons().size() : 0;
         }
      });
   }

   private void pressButton(FormScreen form, int ordinal) {
      if (form instanceof JsonUiFormScreen json) {
         UiControl control = this.onClient(() -> json.ui().input().firing("button.form_button_click", "form_buttons", json.data().buttonIndex(ordinal)));
         if (control == null) {
            log("no JSON UI control answers button {}", ordinal);
            return;
         }

         this.click(json, control);
      } else if (form instanceof SimpleFormScreen simple) {
         this.press(simple.buttons().get(ordinal));
      }
   }

   @Nullable
   private UiControl generated(JsonUiFormScreen form, int index) {
      UiControl root = form.ui().instance().root();
      if (root == null) {
         return null;
      } else {
         Deque<UiControl> queue = new ArrayDeque<>(List.of(root));

         while (!queue.isEmpty()) {
            UiControl control = queue.poll();
            if (control.isFactory() && "custom_form".equals(control.collectionName()) && control.generated().size() > index) {
               return control.generated().get(index);
            }

            queue.addAll(control.children());
         }

         return null;
      }
   }

   private UiControl element(JsonUiFormScreen form, int index, UiType type) {
      UiControl control = this.onClient(() -> {
         List<UiControl> found = visible(this.generated(form, index), type);
         return found.isEmpty() ? null : found.getFirst();
      });
      if (control == null) {
         throw new IllegalStateException("No " + type + " in custom form element " + index);
      } else {
         return control;
      }
   }

   private static List<UiControl> visible(@Nullable UiControl from, UiType type) {
      List<UiControl> found = new ArrayList<>();
      if (from != null) {
         from.walk(control -> {
            if (control.type() == type && control.visible()) {
               found.add(control);
            }
         });
      }

      return found;
   }

   private static boolean visible(JsonUiFormScreen form, String name) {
      UiControl control = form.ui().instance().find(name);
      return control != null && control.visible();
   }

   private void click(JsonUiFormScreen form, @Nullable UiControl control) {
      if (control == null) {
         log("no control to click");
      } else {
         for (int i = 0; i < 6 && this.onClient(() -> form.ui().input().reveal(control)); i++) {
            sleep(150L);
         }

         float[] at = this.onClient(() -> JsonUiScreen.toGui(control.x() + control.width() / 2.0F, control.y() + control.height() / 2.0F));
         this.clickAt(form, at[0], at[1]);
      }
   }

   private void slide(JsonUiFormScreen form, UiControl slider, float ratio) {
      for (int i = 0; i < 6 && this.onClient(() -> form.ui().input().reveal(slider)); i++) {
         sleep(150L);
      }

      float[] at = this.onClient(
         () -> JsonUiScreen.toGui(Math.min(slider.x() + slider.width() * ratio, slider.x() + slider.width() - 0.5F), slider.y() + slider.height() / 2.0F)
      );
      this.clickAt(form, at[0], at[1]);
   }

   private void clickAt(JsonUiFormScreen form, double x, double y) {
      MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
      this.run(() -> {
         form.mouseMoved(x, y);
         form.mouseClicked(event, false);
      });
      sleep(100L);
      this.run(() -> form.mouseReleased(event));
      sleep(250L);
   }

   private static <T> void cycle(AbstractWidget widget, T value) {
      ((CycleButton)widget).setValue(value);
   }

   private FormScreen open(String kind) {
      return this.open(kind, FormScreen.class);
   }

   private <T extends Screen> T open(String kind, Class<T> type) {
      this.command("scriptevent bedrocknative:form " + kind);
      return this.await(type, 5000L);
   }

   private FormScreen awaitModal(FormScreen first, long timeout) {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < timeout) {
         Screen screen = this.onClient(this.minecraft.gui::screen);
         if (screen != first && (screen instanceof ModalFormScreen || screen instanceof JsonUiFormScreen json && json.form() instanceof BedrockForm.Modal)) {
            return (FormScreen)screen;
         }

         sleep(100L);
      }

      throw new IllegalStateException("No modal form after " + timeout + " ms, screen is " + this.screenName());
   }

   private <T extends Screen> T await(Class<T> type, long timeout) {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < timeout) {
         Screen screen = this.onClient(this.minecraft.gui::screen);
         if (type.isInstance(screen)) {
            return type.cast(screen);
         }

         sleep(100L);
      }

      throw new IllegalStateException("No " + type.getSimpleName() + " after " + timeout + " ms, screen is " + this.screenName());
   }

   private void press(AbstractButton button) {
      this.run(() -> button.keyPressed(ENTER));
   }

   private String screenName() {
      return this.onClient(() -> this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName());
   }

   private void command(String command) {
      this.onClient(() -> {
         this.minecraft.getConnection().sendCommand(command);
         return null;
      });
   }

   private void screenshot(String name) {
      this.onClient(() -> {
         Screenshot.grab(this.minecraft.gameDirectory, "forms-" + name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
         return null;
      });
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev forms test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
