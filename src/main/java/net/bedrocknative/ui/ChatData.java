package net.bedrocknative.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ChatData implements UiDataSource {
   public static final String MESSAGES_FACTORY = "messages_factory";
   public static final String MESSAGE = "chat_screen_messages";
   public static final String AUTO_COMPLETE = "auto_complete";
   public static final String TEXT = "#message_text_box_content";
   public static final String MESSAGE_TEXT = "#text";
   private static final String SEND = "button.send";
   private static final String CANCEL = "button.chat_menu_cancel";
   private static final String SUGGESTION = "button.click_autocomplete";
   private static final String SETTINGS = "button.open_chat_settings";
   public static final int MAX_LENGTH = 256;
   public static final float ROW_HEIGHT = 10.0F;
   private static final String PANEL = "auto_complete_panel";
   private static final String GRID = "auto_complete_grid";
   private static final String ROW_BUTTON = "autocomplete_button";
   private ChatData.@Nullable Actions actions;
   private String text = "";
   private List<ChatData.Line> lines = List.of();
   private long version = 1L;

   public void actions(ChatData.@Nullable Actions target) {
      this.actions = target;
   }

   public void text(String typed) {
      if (!typed.equals(this.text)) {
         this.text = typed;
         this.version++;
      }
   }

   public String text() {
      return this.text;
   }

   public void lines(List<ChatData.Line> shown) {
      if (!shown.equals(this.lines)) {
         this.lines = List.copyOf(shown);
         this.version++;
      }
   }

   public List<ChatData.Line> lines() {
      return this.lines;
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#chat_visible" -> UiValue.of(this.lines.isEmpty());
         case "#send_button_visible", "#text_box_enabled", "#scroll_chat_to_bottom" -> UiValue.TRUE;
         case "#chat_title_visible", "#chat_coordinate_dropdown_visible", "#copy_button_enabled", "#keyboard_button_visible", "#keyboard_being_used", "#has_new_messages", "#is_using_gamepad", "#gamepad_helper_visible", "#cheats_on" -> UiValue.FALSE;
         case "#chat_title_text", "#coordinates_text" -> UiValue.of("");
         case "#back_button_text" -> UiValue.of("gui.back");
         case "#message_text_box_content" -> UiValue.of(this.text);
         default -> UiValue.NULL;
      };
   }

   @Override
   public int length(String collection) {
      return collection.equals("auto_complete") ? this.lines.size() : 0;
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      if (!collection.equals("auto_complete")) {
         return UiValue.NULL;
      }

      if (index < 0) {
         return name.equals("#get_grid_size") ? UiValue.array(1.0, this.lines.size()) : UiValue.NULL;
      }

      if (index >= this.lines.size()) {
         return UiValue.NULL;
      }

      ChatData.Line line = this.lines.get(index);

      return switch (name) {
         case "#auto_complete_text" -> UiValue.of(line.text());
         case "#is_autocomplete_suggestion" -> UiValue.of(line.suggestion());
         default -> UiValue.NULL;
      };
   }

   @Override
   public @Nullable String kind(String collection, int index) {
      return null;
   }

   @Override
   public long version() {
      return this.version;
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      if (this.actions != null) {
         switch (buttonId) {
            case "button.send":
               this.actions.send();
               break;
            case "button.chat_menu_cancel":
               this.actions.close();
               break;
            case "button.open_chat_settings":
               this.actions.settings();
               break;
            case "button.click_autocomplete":
               if ("auto_complete".equals(collection) && index >= 0 && index < this.lines.size() && this.lines.get(index).suggestion()) {
                  this.actions.suggestion(index);
               }
         }
      }
   }

   public static int autocompleteRows(UiScreenInstance instance) {
      UiControl panel = instance.find("auto_complete_panel");
      if (panel != null && panel.height() > 0.0F) {
         UiControl grid = instance.find("auto_complete_grid");
         float row = grid != null && grid.gridCellH > 0.0F ? grid.gridCellH : 10.0F;
         return SuggestionWindow.rows(panel.height(), row);
      } else {
         return 0;
      }
   }

   public static float @Nullable [] autocompleteArea(UiScreenInstance instance) {
      UiControl grid = instance.find("auto_complete_grid");
      return grid != null && grid.visible() && grid.height() > 0.0F ? new float[]{grid.x(), grid.y(), grid.width(), grid.height()} : null;
   }

   public static @Nullable UiControl rowButton(UiScreenInstance instance, int index) {
      UiControl grid = instance.find("auto_complete_grid");
      if (grid != null && index >= 0) {
         for (UiControl item : grid.children()) {
            if (item.collectionIndex() == index) {
               return item.find("autocomplete_button");
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static Map<String, UiValue> variables(double fontScale, double lineSpacing) {
      Map<String, UiValue> variables = new HashMap<>(HudData.engineVariables(0.5, fontScale, lineSpacing));
      variables.put("$max_text_edit_length", UiValue.of(256.0));
      variables.put("$is_pregame", UiValue.FALSE);
      return Map.copyOf(variables);
   }

   public interface Actions {
      void send();

      void close();

      void suggestion(int var1);

      void settings();
   }

   public record Line(String text, boolean suggestion) {
   }
}
