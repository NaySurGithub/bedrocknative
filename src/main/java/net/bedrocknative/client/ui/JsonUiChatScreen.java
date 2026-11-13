package net.bedrocknative.client.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.command.BedrockCommands;
import net.bedrocknative.client.command.SuggestionsView;
import net.bedrocknative.client.mixin.command.ChatScreenAccessor;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.ui.ChatData;
import net.bedrocknative.ui.SuggestionWindow;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiType;
import net.bedrocknative.ui.UiValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

public final class JsonUiChatScreen extends ChatScreen implements JsonUiHost, ChatData.Actions {
   public static final String CHAT = "chat";
   private static final int HISTORY = 100;
   private static final Deque<String> RECENT = new ArrayDeque<>();
   private static final float PANEL_MARGIN = 50.0F;
   private static final float SCROLL_BAR_WIDTH = 2.0F;
   private static final float SCROLL_THUMB_MIN = 4.0F;
   private static final int SCROLL_RAIL = 1711276032;
   private static final int SCROLL_THUMB = -6250336;
   private static boolean failed;
   private final JsonUiScreen ui;
   private final ChatData data;
   private boolean opened;
   private boolean uiPressed;
   @Nullable
   private UiControl highlighted;

   private JsonUiChatScreen(String initial, boolean isDraft, boolean closeOnSubmit, JsonUiScreen ui, ChatData data) {
      super(initial, isDraft, closeOnSubmit);
      this.ui = ui;
      this.data = data;
      data.actions(this);
   }

   static Screen swap(ChatScreen java) {
      Minecraft minecraft = Minecraft.getInstance();
      if (!failed && JsonUi.offered("chat") && BedrockMovement.active() && minecraft.player != null) {
         UiDefinitionSet definitions = JsonUi.definitions();
         if (definitions == null) {
            return java;
         } else {
            try {
               ChatData data = new ChatData();
               Options options = minecraft.options;
               JsonUiScreen ui = JsonUiScreen.create(
                  definitions, "chat.chat_screen", ChatData.variables((Double)options.chatScale().get(), (Double)options.chatLineSpacing().get() * 10.0), data
               );
               if (ui != null && JsonUi.use("chat", ui.instance())) {
                  ui.instance().problems().log("chat screen");
                  ChatScreenAccessor accessor = (ChatScreenAccessor)java;
                  JsonUiChatScreen screen = new JsonUiChatScreen(
                     accessor.bedrocknative$initial(), accessor.bedrocknative$isDraft(), accessor.bedrocknative$closeOnSubmit(), ui, data
                  );
                  ui.fallback(() -> {
                     failed = true;
                     if (minecraft.gui.screen() == screen) {
                        minecraft.gui.setScreen(new ChatScreen(screen.input.getValue(), false));
                     }
                  });
                  return screen;
               } else {
                  return java;
               }
            } catch (RuntimeException var8) {
               BedrockNative.LOGGER.warn("JSON UI couldn't build the chat screen; the Java screen shows it", var8);
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

   static void received(String line) {
      RECENT.addLast(line);

      while (RECENT.size() > 100) {
         RECENT.removeFirst();
      }

      if (Minecraft.getInstance().gui.screen() instanceof JsonUiChatScreen open) {
         open.message(line);
      }
   }

   static void cleared() {
      RECENT.clear();
      if (Minecraft.getInstance().gui.screen() instanceof JsonUiChatScreen open) {
         open.ui.instance().clearFactory("messages_factory");
      }
   }

   @Override
   public JsonUiScreen ui() {
      return this.ui;
   }

   public ChatData data() {
      return this.data;
   }

   public String typed() {
      return this.input.getValue();
   }

   public void setTyped(String text) {
      this.input.setValue(text);
   }

   private void message(String line) {
      this.ui.instance().factoryEvent("messages_factory", "chat_screen_messages", Map.of(), Map.of("#text", UiValue.of(line)));
   }

   protected void init() {
      super.init();
      this.ui.resize(this.width, this.height);
      this.ui.externalEditor(this.input);
      if (!this.opened) {
         this.opened = true;
         this.ui.event("screen.entrance_push");
         RECENT.forEach(this::message);
      }
   }

   @Nullable
   private UiControl box() {
      UiControl[] found = new UiControl[]{null};
      UiControl root = this.ui.instance().root();
      if (root != null) {
         root.walk(control -> {
            if (found[0] == null && control.type() == UiType.EDIT_BOX && control.visible()) {
               found[0] = control;
            }
         });
      }

      return found[0];
   }

   private CommandSuggestions suggestions() {
      return ((ChatScreenAccessor)this).bedrocknative$commandSuggestions();
   }

   private void refresh() {
      this.data.text(this.input.getValue());
      SuggestionsView view = (SuggestionsView)this.suggestions();
      int rows = ChatData.autocompleteRows(this.ui.instance());
      if (rows <= 0) {
         rows = SuggestionWindow.rows(JsonUi.uiHeight() - 50.0F, 10.0);
      }

      view.bedrocknative$rows(rows);
      List<ChatData.Line> lines = new ArrayList<>();
      int chosen = -1;
      SuggestionWindow window = view.bedrocknative$window();
      if (window != null) {
         view.bedrocknative$shown().forEach(text -> lines.add(new ChatData.Line(withDescription(text), true)));
         chosen = window.selected() - window.offset();
      } else {
         List<String> usage = view.bedrocknative$usage();
         usage.subList(0, Math.min(rows, usage.size())).forEach(text -> lines.add(new ChatData.Line(text, false)));
      }

      this.data.lines(lines);
      this.highlight(chosen);
      UiControl box = this.box();
      if (box != null && this.ui.input().focused() != box) {
         this.ui.input().focus(box);
      }
   }

   private void highlight(int index) {
      UiControl button = index >= 0 ? ChatData.rowButton(this.ui.instance(), index) : null;
      if (this.highlighted != null && this.highlighted != button) {
         this.ui.input().hover(this.highlighted, false);
      }

      if (button != null) {
         this.ui.input().hover(button, true);
      }

      this.highlighted = button;
   }

   private static String withDescription(String suggestion) {
      BedrockCommands commands = BedrockCommands.active();
      String description = commands == null ? null : commands.description(suggestion);
      return description == null ? suggestion : suggestion + " – " + description;
   }

   private void scrollBar(GuiGraphicsExtractor graphics) {
      SuggestionWindow window = ((SuggestionsView)this.suggestions()).bedrocknative$window();
      float[] area = window != null && window.scrolls() ? ChatData.autocompleteArea(this.ui.instance()) : null;
      if (area != null) {
         float x = area[0] + area[2] - 2.0F - 1.0F;
         float[] thumb = window.thumb(area[3], 4.0F);
         JsonUiCanvas.fill(graphics, x, area[1], 2.0F, area[3], 1711276032, null);
         JsonUiCanvas.fill(graphics, x, area[1] + thumb[0], 2.0F, thumb[1], -6250336, null);
      }
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      if (!this.ui.failed()) {
         this.refresh();
         this.ui.render(graphics);
         if (!this.ui.failed()) {
            this.scrollBar(graphics);
         }
      }
   }

   public void mouseMoved(double x, double y) {
      this.ui.mouseMoved(x, y);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 1 && this.ui.topmost(event.x(), event.y()) != null) {
         this.uiPressed = true;
         this.ui.mouseClicked(event);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.uiPressed && event.button() == 1) {
         this.uiPressed = false;
         this.ui.mouseReleased(event);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.uiPressed && this.ui.mouseDragged(event);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      SuggestionsView view = (SuggestionsView)this.suggestions();
      if (view.bedrocknative$listShown()) {
         float[] area = ChatData.autocompleteArea(this.ui.instance());
         if (area != null && JsonUi.toUi(y) <= area[1] + area[3]) {
            view.bedrocknative$scroll(scrollY);
            return true;
         }
      }

      return this.ui.mouseScrolled(x, y, scrollY);
   }

   @Override
   public void send() {
      this.keyPressed(new KeyEvent(40, 13, 0));
   }

   @Override
   public void close() {
      if (this.minecraft.gui.screen() == this) {
         this.onClose();
      }
   }

   @Override
   public void suggestion(int index) {
      ((SuggestionsView)this.suggestions()).bedrocknative$use(index);
   }

   @Override
   public void settings() {
      this.minecraft.gui.setScreen(new ChatOptionsScreen(this, this.minecraft.options));
   }
}
