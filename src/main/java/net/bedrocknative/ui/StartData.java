package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StartData implements UiDataSource {
   public static final String PLAY = "button.menu_play";
   public static final String SETTINGS = "button.menu_settings";
   public static final String PROFILE = "button.menu_profile";
   public static final String SIGN_IN = "button.signin";
   public static final String REALMS = "button.menu_realms";
   public static final String STORE = "button.menu_store";
   public static final String INBOX = "button.menu_inbox";
   public static final String DRESSING_ROOM = "button.to_profile_screen";
   public static final String SOCIAL = "button.friends_drawer";
   public static final String SOCIAL_ICON = "textures/ui/socialbuttonicon/social-default-icon";
   public static final String SOCIAL_ICON_HOVERED = "textures/ui/socialbuttonicon/social-hover-icon";
   private static final Map<String, String> SERVICES = Map.of(
      "button.menu_realms",
      "menu.realms",
      "button.menu_store",
      "menu.store",
      "button.menu_inbox",
      "menu.inbox",
      "button.to_profile_screen",
      "profileScreen.header",
      "button.friends_drawer",
      "menu.socialfriends"
   );
   private StartData.@Nullable Actions actions;
   private String gamertag = "";
   private boolean face;
   private String versionText = "";
   private String socialText = "Social (0)";
   private long version = 1L;

   public void actions(StartData.@Nullable Actions target) {
      this.actions = target;
   }

   public void account(@Nullable String activeGamertag) {
      String name = activeGamertag == null ? "" : activeGamertag;
      if (!name.equals(this.gamertag)) {
         this.gamertag = name;
         this.version++;
      }
   }

   public String gamertag() {
      return this.gamertag;
   }

   public void face(boolean shown) {
      if (shown != this.face) {
         this.face = shown;
         this.version++;
      }
   }

   public void version(String text) {
      if (!text.equals(this.versionText)) {
         this.versionText = text;
         this.version++;
      }
   }

   public void social(String text) {
      if (!text.equals(this.socialText)) {
         this.socialText = text;
         this.version++;
      }
   }

   @Override
   public UiValue global(String name) {
      boolean signedIn = !this.gamertag.isEmpty();

      return switch (name) {
         case "#not_realms_promo_visible_and_supports_launching_legacy_version", "#realms_promo_visible" -> UiValue.TRUE;
         case "#marketplace_disabled", "#store_button_art_visible", "#store_button_banner_visible", "#play_button_art_visible", "#play_button_banner_visible", "#update_icon_visible", "#new_offers" -> UiValue.FALSE;
         case "#online_stack_visible", "#inbox_enabled", "#profile_button_a_visible" -> UiValue.TRUE;
         case "#upper_online_buttons_visible", "#sign_in_visible" -> UiValue.of(!signedIn);
         case "#inbox_icon_state" -> UiValue.of(2.0);
         case "#unread_notification_icon_visibility" -> UiValue.FALSE;
         case "#show_gamerpic" -> UiValue.of(this.face);
         case "#persona_and_skins_enabled", "#show_paper_doll", "#is_paper_doll_visible", "#dressing_room_button_visible" -> UiValue.TRUE;
         case "#profile_button_b_visible" -> UiValue.FALSE;
         case "#playername", "#gamertag_label" -> UiValue.of(this.gamertag);
         case "#friends_drawer_button_enabled" -> UiValue.TRUE;
         case "#social_icon_content" -> socialIcons();
         case "#social_icon" -> UiValue.of("textures/ui/socialbuttonicon/social-default-icon");
         case "#social_icon_hovered" -> UiValue.of("textures/ui/socialbuttonicon/social-hover-icon");
         case "#social_button_text" -> UiValue.of(this.socialText);
         case "#social_notification_icon_visibility" -> UiValue.FALSE;
         case "#version" -> UiValue.of(this.versionText);
         case "#development_version", "#signingin_text", "#platform_signin_text", "#gathering_button_text", "#unlock_full_game_button_text" -> UiValue.EMPTY;
         case "#toggle_enabled", "#slider_enabled", "#text_edit_box_visible" -> UiValue.NULL;
         default -> !name.endsWith("_visible")
               && !name.endsWith("_enabled")
               && !name.endsWith("_visibility")
               && !name.startsWith("#is_")
               && !name.startsWith("#show")
               && !name.startsWith("#edu_")
               && !name.equals("#screen_needs_rebuild")
               && !name.equals("#editions_visible")
            ? UiValue.NULL
            : UiValue.FALSE;
      };
   }

   private static UiValue socialIcons() {
      JsonArray kinds = new JsonArray();
      kinds.add("social_button_control");
      return UiValue.of(kinds);
   }

   @Override
   public int length(String collection) {
      return 0;
   }

   @Override
   public UiValue item(String collection, int index, String name) {
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
      StartData.Actions target = this.actions;
      if (target != null) {
         switch (buttonId) {
            case "button.menu_play":
               target.play();
               break;
            case "button.menu_settings":
               target.settings();
               break;
            case "button.menu_profile":
            case "button.signin":
               target.account();
               break;
            default:
               String service = SERVICES.get(buttonId);
               if (service != null) {
                  target.unavailable(service);
               }
         }
      }
   }

   public static List<String> services() {
      return List.of("button.menu_realms", "button.menu_store", "button.menu_inbox", "button.to_profile_screen", "button.friends_drawer");
   }

   public static Map<String, UiValue> variables() {
      Map<String, UiValue> engine = new HashMap<>();
      engine.put("$play_button_visible", UiValue.TRUE);
      engine.put("$play_button_target", UiValue.of("button.menu_play"));

      for (String ignored : List.of("$featured_world_ignored", "$unlock_full_game_button_ignored", "$is_publish", "$edu_feedback_ignored")) {
         engine.put(ignored, UiValue.TRUE);
      }

      for (String flag : List.of(
         "$store_ignored",
         "$store_disabled",
         "$trial",
         "$use_single_column_for_buttons",
         "$supports_launching_legacy_version",
         "$is_preview_app",
         "$can_quit",
         "$nx_os",
         "$is_ps4",
         "$supports_xbl_achievements",
         "$pre_release",
         "$showing_new_player_flow_buttons",
         "$is_ios",
         "$osx_edition",
         "$console_edition"
      )) {
         engine.put(flag, UiValue.FALSE);
      }

      return UiPlatform.desktop(engine);
   }

   public interface Actions {
      void play();

      void settings();

      void account();

      void unavailable(String var1);
   }
}
