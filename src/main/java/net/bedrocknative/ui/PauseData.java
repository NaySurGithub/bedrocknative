package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PauseData implements UiDataSource {
   public static final String PLAYERS = "players_collection";
   public static final String RESUME = "button.menu_continue";
   public static final String SETTINGS = "button.menu_settings";
   public static final String QUIT = "button.menu_quit";
   public static final String STORE = "button.menu_store";
   public static final String ACHIEVEMENTS = "button.menu_achievements";
   public static final String HOW_TO_PLAY = "button.menu_how_to_play";
   public static final String SCREENSHOT = "button.screenshot";
   public static final String DRESSING_ROOM = "button.to_profile_or_skins_screen";
   public static final String SOCIAL = "button.friends_drawer";
   private static final Map<String, String> SERVICES = Map.of(
      "button.menu_store",
      "menu.store",
      "button.menu_achievements",
      "menu.achievements",
      "button.menu_how_to_play",
      "menu.howToPlay",
      "button.to_profile_or_skins_screen",
      "profileScreen.header",
      "button.friends_drawer",
      "menu.socialfriends"
   );
   @Nullable
   private PauseData.Actions actions;
   private String playerName = "";
   private List<String> players = List.of();
   private int local = -1;
   private String socialText = "Social (0)";
   private long version = 1L;

   public void actions(@Nullable PauseData.Actions target) {
      this.actions = target;
   }

   public void players(String name, List<String> names, int localIndex) {
      if (!name.equals(this.playerName) || !names.equals(this.players) || localIndex != this.local) {
         this.playerName = name;
         this.players = List.copyOf(names);
         this.local = localIndex;
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
      return switch (name) {
         case "#playername" -> UiValue.of(this.playerName);
         case "#playername_visible" -> UiValue.TRUE;
         case "#pause_annoucement_visible", "#friends_drawer_button_visible" -> UiValue.TRUE;
         case "#social_icon_content" -> socialIcons();
         case "#social_icon" -> UiValue.of("textures/ui/socialbuttonicon/social-default-icon");
         case "#social_icon_hovered" -> UiValue.of("textures/ui/socialbuttonicon/social-hover-icon");
         case "#social_button_text" -> UiValue.of(this.socialText);
         case "#store_button_visible", "#achievements_visible", "#screenshot_button_visible" -> UiValue.TRUE;
         case "#dressing_room_button_visible", "#can_change_skin" -> UiValue.TRUE;
         case "#players_list_title" -> UiValue.of("pauseScreen.currentPlayers");
         case "#players_grid_dimension" -> UiValue.array(1.0, this.players.size());
         case "#ip_address_text" -> UiValue.of("");
         case "#toggle_enabled", "#slider_enabled", "#text_edit_box_visible" -> UiValue.NULL;
         default -> !name.endsWith("_visible")
               && !name.endsWith("_enabled")
               && !name.startsWith("#is_")
               && !name.startsWith("#has_")
               && !name.equals("#list_has_display_objective")
               && !name.startsWith("#disconnected_from")
               && !name.equals("#realms_hub_enabled")
               && !name.endsWith("_visibility")
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
      return collection.equals("players_collection") ? this.players.size() : 0;
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      if (collection.equals("players_collection") && index >= 0 && index < this.players.size()) {
         return switch (name) {
            case "#gamertag" -> UiValue.of(this.players.get(index));
            case "#local_icon_visible" -> UiValue.of(index == this.local);
            case "#button_disabled" -> UiValue.TRUE;
            case "#button_enabled", "#gamerpic_visible", "#is_platform_icon_visible" -> UiValue.FALSE;
            case "#texture", "#texture_source", "#player_permission_level_icon_texture" -> UiValue.of("");
            default -> UiValue.NULL;
         };
      } else {
         return UiValue.NULL;
      }
   }

   @Nullable
   @Override
   public String kind(String collection, int index) {
      return null;
   }

   @Override
   public long version() {
      return this.version;
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      PauseData.Actions target = this.actions;
      if (target != null) {
         switch (buttonId) {
            case "button.menu_continue":
               target.resume();
               break;
            case "button.menu_settings":
               target.settings();
               break;
            case "button.menu_quit":
               target.quit();
               break;
            case "button.screenshot":
               target.screenshot();
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
      return List.of("button.menu_store", "button.menu_achievements", "button.menu_how_to_play", "button.to_profile_or_skins_screen", "button.friends_drawer");
   }

   public static Map<String, UiValue> variables() {
      Map<String, UiValue> engine = new HashMap<>();

      for (String flag : List.of(
         "$pause_menu_icon_settings_layout",
         "$is_big_screen",
         "$education_edition",
         "$trial",
         "$is_secondary_client",
         "$is_editor_mode_enabled",
         "$is_editor_playtest_roundtrip",
         "$is_new_trial_mode_enabled",
         "$platform_icons_visible",
         "$is_holographic",
         "$pocket_edition",
         "$pocket_screen"
      )) {
         engine.put(flag, UiValue.FALSE);
      }

      engine.put("$supports_xbl_achievements", UiValue.TRUE);
      engine.put("$ignore_edu_pause", UiValue.TRUE);
      engine.put("$store_button_text", UiValue.of("menu.store.addons"));
      engine.put("$unlock_full_game_button_text", UiValue.of(""));
      return UiPlatform.desktop(Objects.requireNonNull(engine));
   }

   public interface Actions {
      void resume();

      void settings();

      void quit();

      void screenshot();

      void unavailable(String var1);
   }
}
