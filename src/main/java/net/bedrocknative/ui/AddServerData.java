package net.bedrocknative.ui;

import java.util.Map;
import net.bedrocknative.net.BedrockAddress;
import org.jspecify.annotations.Nullable;

public final class AddServerData implements UiDataSource {
   public static final String PLAY = "button.play_button";
   public static final String SAVE = "button.save_button";
   public static final String REMOVE = "button.remove_button";
   public static final String EXIT = "button.menu_exit";
   public static final String POPUP_CONFIRM = "popup_dialog.left_button";
   public static final String POPUP_CANCEL = "popup_dialog.rightcancel_button";
   public static final String POPUP_ESCAPE = "popup_dialog.escape";
   public static final String POPUP_FACTORY = "popup_dialog_factory";
   public static final String POPUP = "modal_dialog_popup";
   public static final String NAME = "#name_text_box_content";
   public static final String ADDRESS = "#ip_text_box_content";
   public static final String PORT = "#port_text_box_content";
   static final int NAME_LENGTH = 16;
   static final int ADDRESS_LENGTH = 256;
   static final int PORT_LENGTH = 6;
   private final boolean editMode;
   private AddServerData.@Nullable Actions actions;
   private String name;
   private String host;
   private String port;
   private boolean confirming;
   private long version = 1L;

   private AddServerData(boolean editMode, String name, String host, String port) {
      this.editMode = editMode;
      this.name = name;
      this.host = host;
      this.port = port;
   }

   public static AddServerData add() {
      return new AddServerData(false, "", "", String.valueOf(19132));
   }

   public static AddServerData edit(String name, String storedAddress) {
      BedrockAddress address = BedrockAddress.parse(storedAddress);
      return new AddServerData(true, cut(name, 16), cut(address.hostBox(), 256), String.valueOf(address.port()));
   }

   private static String cut(String text, int length) {
      return text.length() <= length ? text : text.substring(0, length);
   }

   public void actions(AddServerData.@Nullable Actions target) {
      this.actions = target;
   }

   public boolean editMode() {
      return this.editMode;
   }

   public String name() {
      return this.name;
   }

   public String host() {
      return this.host;
   }

   public String port() {
      return this.port;
   }

   public boolean confirming() {
      return this.confirming;
   }

   public @Nullable BedrockAddress address() {
      String trimmed = BedrockAddress.withoutPrefix(this.host);
      if (trimmed.length() > 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
         trimmed = trimmed.substring(1, trimmed.length() - 1);
      }

      int number = BedrockAddress.typedPort(this.port);
      boolean hostAndPort = trimmed.indexOf(58) >= 0 && trimmed.indexOf(58) == trimmed.lastIndexOf(58);
      boolean brackets = trimmed.indexOf(91) >= 0 || trimmed.indexOf(93) >= 0;
      boolean invalid = trimmed.isEmpty() || number < 0 || trimmed.indexOf(32) >= 0 || hostAndPort || brackets;
      return invalid ? null : BedrockAddress.fromForm(this.host, number);
   }

   public boolean canPlay() {
      return this.address() != null;
   }

   public boolean canSave() {
      return this.canPlay() && !this.name.isBlank();
   }

   @Override
   public UiValue global(String binding) {
      return switch (binding) {
         case "#title_text" -> UiValue.of(this.editMode ? "addExternalServerScreen.editTitle" : "addExternalServerScreen.addTitle");
         case "#name_text_box_content" -> UiValue.of(this.name);
         case "#ip_text_box_content" -> UiValue.of(this.host);
         case "#port_text_box_content" -> UiValue.of(this.port);
         case "#text_box_enabled", "#close_button_visible" -> UiValue.TRUE;
         case "#play_button_enabled" -> UiValue.of(this.canPlay());
         case "#play_button_disabled" -> UiValue.of(!this.canPlay());
         case "#save_button_enabled" -> UiValue.of(this.canSave());
         case "#save_button_disabled" -> UiValue.of(!this.canSave());
         case "#modal_title_text" -> UiValue.of("addExternalServerScreen.removeButtonLabel");
         case "#modal_label_text" -> UiValue.of("addExternalServerScreen.removeConfirmation");
         case "#modal_left_button_text" -> UiValue.of("addExternalServerScreen.removeButtonLabel");
         case "#modal_rightcancel_button_text" -> UiValue.of("gui.cancel");
         default -> UiValue.NULL;
      };
   }

   @Override
   public void write(@Nullable String collection, int index, String binding, UiValue value, int outer) {
      if (collection == null) {
         String text = value.isNull() ? "" : value.asString();
         switch (binding) {
            case "#name_text_box_content":
               this.name = cut(text, 16);
               break;
            case "#ip_text_box_content":
               this.host = cut(text, 256);
               break;
            case "#port_text_box_content":
               this.port = cut(UiInput.filter(text, "NumberChars"), 6);
               break;
            default:
               return;
         }

         this.version++;
      }
   }

   @Override
   public int length(String collection) {
      return 0;
   }

   @Override
   public UiValue item(String collection, int index, String binding) {
      return UiValue.NULL;
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
      AddServerData.Actions target = this.actions;
      if (target != null) {
         if (this.confirming) {
            switch (buttonId) {
               case "popup_dialog.left_button":
                  this.confirming = false;
                  target.closePopup();
                  target.remove();
                  break;
               case "popup_dialog.rightcancel_button":
               case "popup_dialog.escape":
               case "button.menu_exit":
                  this.confirming = false;
                  target.closePopup();
            }
         } else {
            BedrockAddress address = this.address();
            switch (buttonId) {
               case "button.play_button":
                  if (!this.editMode && address != null) {
                     target.play(this.name.trim(), address);
                  }
                  break;
               case "button.save_button":
                  if (this.canSave() && address != null) {
                     target.save(this.name.trim(), address);
                  }
                  break;
               case "button.remove_button":
                  if (this.editMode) {
                     this.confirming = true;
                     target.confirmRemove();
                  }
                  break;
               case "button.menu_exit":
                  target.cancel();
            }
         }
      }
   }

   public Map<String, UiValue> variables() {
      return UiPlatform.desktop(Map.of("$edit_mode", UiValue.of(this.editMode)));
   }

   public static Map<String, UiValue> popupVariables() {
      return Map.of("$two_buttons_visible", UiValue.TRUE);
   }

   public interface Actions {
      void play(String var1, BedrockAddress var2);

      void save(String var1, BedrockAddress var2);

      void remove();

      void cancel();

      void confirmRemove();

      void closePopup();
   }
}
