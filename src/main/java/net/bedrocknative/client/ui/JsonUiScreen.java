package net.bedrocknative.client.ui;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDataSource;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiInput;
import net.bedrocknative.ui.UiPlatform;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class JsonUiScreen {
   private static final double MAX_FRAME_SECONDS = 0.25;
   private static final long CARET_BLINK_MILLIS = 300L;
   private final UiScreenInstance instance;
   private final UiInput input;
   private final Minecraft minecraft = Minecraft.getInstance();
   private final String name;
   private Runnable fallback = () -> {};
   @Nullable
   private ItemSource items;
   @Nullable
   private EditBox editor;
   @Nullable
   private EditBox external;
   @Nullable
   private UiControl editing;
   private long editingSince;
   private double clock;
   private long lastFrame;
   private boolean failed;
   private JsonUiDiagnostics diagnostics;

   private JsonUiScreen(UiScreenInstance instance, String name) {
      this.instance = instance;
      this.name = name;
      this.diagnostics = new JsonUiDiagnostics(name, name, 0L);
      this.input = UiInput.attach(instance);
      this.input.editor(new UiInput.TextEditor() {
         {
            Objects.requireNonNull(JsonUiScreen.this);
         }

         @Override
         public void begin(UiControl editBox, String text, int maxLength) {
            EditBox own = JsonUiScreen.this.external;
            if (own != null) {
               JsonUiScreen.this.editor = own;
               JsonUiScreen.this.editing = editBox;
               JsonUiScreen.this.editingSince = Util.getMillis();
            } else {
               EditBox box = new EditBox(JsonUiScreen.this.minecraft.font, 0, 0, 4000, 20, Component.empty());
               box.setBordered(false);
               box.setMaxLength(maxLength);
               box.setValue(text);
               box.setFocused(true);
               box.setResponder(value -> JsonUiScreen.this.input.setText(editBox, value));
               JsonUiScreen.this.editor = box;
               JsonUiScreen.this.editing = editBox;
               JsonUiScreen.this.editingSince = Util.getMillis();
            }
         }

         @Override
         public void end(UiControl editBox) {
            if (JsonUiScreen.this.editing == editBox) {
               JsonUiScreen.this.editor = null;
               JsonUiScreen.this.editing = null;
            }
         }
      });
      this.input.sounds((sound, volume, pitch) -> {
         if ("random.click".equals(sound)) {
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
         } else if ("ui.loom.select_pattern".equals(sound)) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
         }
      });
   }

   @Nullable
   public static JsonUiScreen create(UiDefinitionSet definitions, String screen, Map<String, UiValue> variables, UiDataSource data) {
      UiScreenInstance instance = UiScreenInstance.create(
         definitions, screen, UiPlatform.desktop(variables), data, JsonUi.environment(definitions), Set.of(), JsonUi.uiWidth(), JsonUi.uiHeight()
      );
      if (instance.root() == null) {
         return null;
      } else {
         instance.update(0.0);
         return new JsonUiScreen(instance, screen);
      }
   }

   public UiScreenInstance instance() {
      return this.instance;
   }

   public boolean closesOnPlayerHurt() {
      UiControl root = this.instance.root();
      UiValue value = root == null ? null : root.prop("close_on_player_hurt");
      return value != null && value.truthy();
   }

   public UiInput input() {
      return this.input;
   }

   public void items(@Nullable ItemSource source) {
      this.items = source;
   }

   public void diagnostics(String what, String kind) {
      this.diagnostics = new JsonUiDiagnostics(what, kind, 0L);
   }

   public void fallback(Runnable action) {
      this.fallback = action;
   }

   public boolean failed() {
      return this.failed;
   }

   public void event(String event) {
      this.instance.fireEvent(event);
   }

   public void resize(int width, int height) {
      this.instance.resize(JsonUi.uiWidth(), JsonUi.uiHeight());
   }

   public static float[] toGui(float x, float y) {
      return new float[]{JsonUi.toGui(x), JsonUi.toGui(y)};
   }

   private static float ui(double gui) {
      return JsonUi.toUi(gui);
   }

   public void render(GuiGraphicsExtractor graphics) {
      if (!this.failed) {
         long perf = PerfProbe.start();

         try {
            long now = System.nanoTime();
            if (this.lastFrame != 0L) {
               this.clock = this.clock + Math.min(0.25, (now - this.lastFrame) / 1.0E9);
            }

            this.lastFrame = now;
            this.instance.resize(JsonUi.uiWidth(), JsonUi.uiHeight());
            this.instance.update(this.clock);
            this.syncEditor();
            BedrockFont font = JsonUi.font();
            if (font == null) {
               JsonUi.environment(this.instance.definitions());
               font = JsonUi.font();
            }

            if (font != null) {
               JsonUiCanvas.draw(graphics, this.instance.drawList(), font, this.items, this.instance.gaps()::undrawn);
            }

            this.caret(graphics);
            this.diagnostics.frame(this.instance, () -> JsonUi.environment(this.instance.definitions()).textures());
         } catch (RuntimeException var10) {
            this.fail(var10);
         } finally {
            PerfProbe.end(PerfProbe.Part.SCREENS, perf);
         }
      }
   }

   private void syncEditor() {
      EditBox box = this.editor;
      UiControl focused = this.editing;
      if (box != null && focused != null) {
         String shown = this.input.text(focused);
         if (!shown.equals(box.getValue())) {
            if (box == this.external) {
               this.input.setText(focused, box.getValue());
            } else {
               box.setValue(shown);
            }
         }
      }
   }

   public void externalEditor(@Nullable EditBox box) {
      this.external = box;
   }

   private void caret(GuiGraphicsExtractor graphics) {
      EditBox box = this.editor;
      UiControl focused = this.input.focused();
      if (box != null && focused != null && (Util.getMillis() - this.editingSince) / 300L % 2L == 0L) {
         float[] caret = this.input.caret(box.getCursorPosition());
         UiControl label = this.input.textControl(focused);
         if (caret != null) {
            JsonUiCanvas.fill(graphics, caret[0], caret[1], caret[2], caret[3], -1, label == null ? focused.clip() : label.clip());
         }
      }
   }

   public void mouseMoved(double x, double y) {
      this.run(() -> this.input.mouseMoved(ui(x), ui(y)));
   }

   @Nullable
   public UiControl topmost(double x, double y) {
      if (this.failed) {
         return null;
      } else {
         try {
            return this.input.topmost(ui(x), ui(y));
         } catch (RuntimeException var6) {
            this.fail(var6);
            return null;
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent event) {
      return this.call(() -> this.input.mouseDown(ui(event.x()), ui(event.y()), button(event)));
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return this.call(() -> this.input.mouseUp(ui(event.x()), ui(event.y()), button(event)));
   }

   public boolean mouseDragged(MouseButtonEvent event) {
      return this.call(() -> this.input.mouseDragged(ui(event.x()), ui(event.y()), button(event)));
   }

   private static int button(MouseButtonEvent event) {
      return switch (event.button()) {
         case 1 -> 0;
         case 3 -> 1;
         default -> -1;
      };
   }

   public boolean mouseScrolled(double x, double y, double scrollY) {
      return this.call(() -> this.input.scroll(ui(x), ui(y), (float)scrollY));
   }

   public boolean keyPressed(KeyEvent event) {
      return this.call(() -> {
         EditBox box = this.editor;
         if (box != null && box.keyPressed(event)) {
            this.editingSince = Util.getMillis();
            return true;
         } else if (event.isEscape()) {
            return this.input.button("button.menu_cancel");
         } else {
            return event.isConfirmation() ? this.input.button("button.menu_ok") : false;
         }
      });
   }

   public boolean charTyped(CharacterEvent event) {
      return this.call(() -> {
         EditBox box = this.editor;
         if (box != null && box.charTyped(event)) {
            this.editingSince = Util.getMillis();
            return true;
         } else {
            return false;
         }
      });
   }

   public boolean preeditUpdated(@Nullable PreeditEvent event) {
      EditBox box = this.editor;
      return box != null && box.preeditUpdated(event);
   }

   private void run(Runnable action) {
      if (!this.failed) {
         try {
            action.run();
         } catch (RuntimeException var3) {
            this.fail(var3);
         }
      }
   }

   private boolean call(BooleanSupplier action) {
      if (this.failed) {
         return false;
      } else {
         try {
            return action.getAsBoolean();
         } catch (RuntimeException var3) {
            this.fail(var3);
            return false;
         }
      }
   }

   private void fail(RuntimeException e) {
      if (!this.failed) {
         this.failed = true;
         BedrockNative.LOGGER.warn("JSON UI screen {} failed; its Java screen takes its place", this.name, e);
         this.minecraft.execute(this.fallback);
      }
   }
}
