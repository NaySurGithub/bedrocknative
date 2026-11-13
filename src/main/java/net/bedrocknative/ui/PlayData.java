package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class PlayData implements UiDataSource {
   public static final int TAB_WORLDS = 0;
   public static final int TAB_FRIENDS = 1;
   public static final int TAB_SERVERS = 2;
   public static final String TABS = "navigation_tab";
   public static final String SERVER_LIST = "server_navigation_toggle";
   public static final int ADD_SERVER = 100;
   public static final int FIRST_SERVER = 1000;
   public static final int FIRST_FEATURED = 100000;
   public static final String WORLDS = "local_worlds";
   public static final String LAN = "lan_network_worlds";
   public static final String SERVERS = "servers_network_worlds";
   public static final String FEATURED = "third_party_server_network_worlds";
   static final String START_WORLD = "button.menu_start_local_world";
   static final String EDIT_WORLD = "button.menu_local_world_item_edit";
   static final String CREATE_WORLD = "button.menu_local_world_create";
   static final String JOIN_NETWORK_WORLD = "button.menu_network_world_item";
   static final String JOIN_SERVER = "button.menu_network_server_item";
   static final String EDIT_SERVER = "button.menu_network_server_world_edit";
   static final String EXIT = "button.menu_exit";
   static final String TAB_LEFT = "button.menu_tab_left";
   static final String TAB_RIGHT = "button.menu_tab_right";
   @Nullable
   private PlayData.Actions actions;
   private List<PlayData.World> worlds = List.of();
   private List<PlayData.Lan> lan = List.of();
   private List<PlayData.Server> servers = List.of();
   private List<PlayData.Server> featured = List.of();
   private boolean worldsLoaded;
   private int tab;
   private int selected = -1;
   private long version = 1L;

   public PlayData(int tab) {
      this.tab = Math.clamp((long)tab, 0, 2);
   }

   public void actions(@Nullable PlayData.Actions target) {
      this.actions = target;
   }

   public int tab() {
      return this.tab;
   }

   public void tab(int newTab) {
      int clamped = Math.clamp((long)newTab, 0, 2);
      if (clamped != this.tab) {
         this.tab = clamped;
         this.version++;
         PlayData.Actions target = this.actions;
         if (target != null) {
            target.tabChanged(this.tab);
         }
      }
   }

   public int selected() {
      return this.selected;
   }

   public void selectServer(int index) {
      int target = index >= 0 && index < this.servers.size() ? 1000 + index : (this.servers.isEmpty() ? 100 : 1000);
      if (target != this.selected) {
         this.selected = target;
         this.version++;
      }
   }

   public List<PlayData.World> worlds() {
      return this.worlds;
   }

   public void worlds(List<PlayData.World> list, boolean loaded) {
      this.worlds = List.copyOf(list);
      this.worldsLoaded = loaded;
      this.version++;
   }

   public List<PlayData.Lan> lan() {
      return this.lan;
   }

   public void lan(List<PlayData.Lan> list) {
      if (!list.equals(this.lan)) {
         this.lan = List.copyOf(list);
         this.version++;
      }
   }

   public List<PlayData.Server> servers() {
      return this.servers;
   }

   public void servers(List<PlayData.Server> list) {
      if (!list.equals(this.servers)) {
         this.servers = List.copyOf(list);
         if (this.selected >= 1000 && this.selected < 100000 && this.selected - 1000 >= this.servers.size()) {
            this.selected = this.servers.isEmpty() ? 100 : 1000 + this.servers.size() - 1;
         } else if (this.selected < 0) {
            this.selected = this.servers.isEmpty() ? 100 : 1000;
         }

         this.version++;
      }
   }

   public void status(int index, PlayData.Status status) {
      if (index >= 0 && index < this.servers.size() && !this.servers.get(index).status().equals(status)) {
         PlayData.Server server = this.servers.get(index);
         ArrayList<PlayData.Server> copy = new ArrayList<>(this.servers);
         copy.set(index, new PlayData.Server(server.name(), server.address(), status));
         this.servers = List.copyOf(copy);
         this.version++;
      }
   }

   public void featured(List<PlayData.Server> list) {
      if (!list.equals(this.featured)) {
         this.featured = List.copyOf(list);
         this.version++;
      }
   }

   private boolean featuredShown() {
      return !this.featured.isEmpty();
   }

   @Nullable
   private PlayData.Server selectedServer() {
      int index = this.selected - 1000;
      return this.selected >= 1000 && this.selected < 100000 && index < this.servers.size() ? this.servers.get(index) : null;
   }

   @Nullable
   private PlayData.Server selectedFeatured() {
      int index = this.selected - 100000;
      return this.selected >= 100000 && index < this.featured.size() ? this.featured.get(index) : null;
   }

   @Override
   public UiValue global(String name) {
      PlayData.Server server = this.selectedServer();
      PlayData.Server partner = this.selectedFeatured();

      return switch (name) {
         case "#world_item_count" -> UiValue.of(String.valueOf(this.worlds.size()));
         case "#friend_world_item_count" -> UiValue.of(String.valueOf(this.lan.size()));
         case "#server_world_item_count" -> UiValue.of(String.valueOf(this.servers.size() + this.featured.size()));
         case "#friends_tab_show_not_bouncing" -> UiValue.TRUE;
         case "#exit_button_visible" -> UiValue.FALSE;
         case "#local_worlds_visible" -> UiValue.of(!this.worlds.isEmpty());
         case "#local_world_item_grid_dimension" -> UiValue.array(1.0, this.worlds.size());
         case "#no_local_worlds" -> UiValue.of(this.worldsLoaded && this.worlds.isEmpty());
         case "#no_friends_grid_message_visible" -> UiValue.TRUE;
         case "#no_friends_grid_message_text" -> UiValue.of("networkWorld.no_joinable_friends_label");
         case "#lan_grid_visible" -> UiValue.of(!this.lan.isEmpty());
         case "#lan_network_world_item_grid_dimension" -> UiValue.array(1.0, this.lan.size());
         case "#is_network_available", "#is_network_available_and_multiplayer_visible", "#cross_platform_enabled", "#add_server_enabled" -> UiValue.TRUE;
         case "#featured_servers_visible_and_available", "#featured_servers_visible" -> UiValue.of(this.featuredShown());
         case "#third_party_featured_item_grid_dimension" -> UiValue.array(1.0, this.featured.size());
         case "#is_additional_server_label_visible", "#servers_grid_visible" -> UiValue.of(!this.servers.isEmpty());
         case "#servers_network_world_item_grid_dimension" -> UiValue.array(1.0, this.servers.size());
         case "#is_additional_server_selected" -> UiValue.of(server != null);
         case "#is_third_party_server_selected", "#is_server_info_available" -> UiValue.of(partner != null);
         case "#add_server_info_visible" -> UiValue.of(this.selected == 100);
         case "#info_additional_server_name" -> UiValue.of(server == null ? "" : server.name());
         case "#info_third_party_server_name" -> UiValue.of(partner == null ? "" : partner.name());
         case "#info_additional_server_player_count" -> UiValue.of(server == null ? "" : players(server.status()));
         case "#info_third_party_server_player_count" -> UiValue.of(partner == null ? "" : players(partner.status()));
         case "#server_player_count_icon" -> UiValue.of(
            statusIcon(server != null ? server.status() : (partner != null ? partner.status() : PlayData.Status.UNKNOWN))
         );
         case "#info_additional_server_ping" -> UiValue.of(server == null ? "" : ping(server.status()));
         case "#info_server_ping" -> UiValue.of(partner == null ? "" : ping(partner.status()));
         case "#info_additional_server_ping_texture_name", "#info_ping_texture_name" -> UiValue.of(
            pingTexture(server != null ? server.status() : (partner != null ? partner.status() : PlayData.Status.UNKNOWN))
         );
         case "#ping_ready_thirdparty", "#ping_ready_external" -> {
            PlayData.Status status = server != null ? server.status() : (partner != null ? partner.status() : PlayData.Status.UNKNOWN);
            yield UiValue.of(status.state() == PlayData.State.ONLINE || status.state() == PlayData.State.OFFLINE);
         }
         case "#info_third_party_server_logo_texture_path" -> UiValue.EMPTY;
         case "#toggle_state", "#toggle_enabled", "#slider_enabled", "#text_edit_box_visible" -> UiValue.NULL;
         case "#realms_warning_text", "#label_text", "#feature_server_message_text", "#internet_connection_text", "#multiplayer_blocked_text", "#version", "#development_version", "#realms_loading_display_message", "#legacy_worlds_loading_display_message", "#worlds_tab_import_button_focus_down_override", "#sync_legacy_button_text", "#no_local_worlds_help_label", "#general_no_multiplayer_grid_message_text", "#no_cross_platform_friends_grid_message_text", "#privilegesBlockedText" -> UiValue.EMPTY;
         case "#legacy_world_item_grid_dimension", "#beta_retail_local_world_item_grid_dimension", "#beta_retail_legacy_world_item_grid_dimension", "#personal_realms_grid_dimension", "#friend_realms_grid_dimension", "#loading_personal_realms_grid_dimension", "#loading_friends_realms_grid_dimension", "#friends_network_world_item_grid_dimension", "#cross_platform_friends_network_world_item_grid_dimension" -> UiValue.array(
            1.0, 0.0
         );
         default -> !name.endsWith("_visible")
               && !name.endsWith("_enabled")
               && !name.startsWith("#is_")
               && !name.startsWith("#has_")
               && !name.startsWith("#show_")
               && !name.startsWith("#friends_tab_")
               && !name.equals("#device_sunsetting")
               && !name.equals("#inbox_enabled")
               && !name.equals("#feature_server_message_visible")
               && !name.equals("#no_local_worlds_switch_setting_visible")
            ? UiValue.NULL
            : UiValue.FALSE;
      };
   }

   private static String players(PlayData.Status status) {
      if (status.state() != PlayData.State.ONLINE) {
         return "";
      } else {
         return status.countsKnown() ? status.online() + "/" + status.max() : "-/-";
      }
   }

   private static String ping(PlayData.Status status) {
      return switch (status.state()) {
         case ONLINE -> status.latencyMillis() + " ms";
         case OFFLINE -> "-";
         default -> "";
      };
   }

   static String pingTexture(PlayData.Status status) {
      return switch (status.state()) {
         case ONLINE -> status.latencyMillis() < 150
            ? "textures/ui/Ping_Green"
            : (status.latencyMillis() < 300 ? "textures/ui/Ping_Yellow" : "textures/ui/Ping_Red");
         case OFFLINE -> "textures/ui/Ping_Offline_Red";
         default -> "";
      };
   }

   private static String statusIcon(PlayData.Status status) {
      return switch (status.state()) {
         case ONLINE -> "textures/ui/online";
         case OFFLINE -> "textures/ui/offline";
         default -> "";
      };
   }

   @Override
   public int length(String collection) {
      return switch (collection) {
         case "local_worlds" -> this.worlds.size();
         case "lan_network_worlds" -> this.lan.size();
         case "servers_network_worlds" -> this.servers.size();
         case "third_party_server_network_worlds" -> this.featured.size();
         default -> 0;
      };
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      return switch (collection) {
         case "local_worlds" -> index >= 0 && index < this.worlds.size() ? world(this.worlds.get(index), index, name) : UiValue.NULL;
         case "lan_network_worlds" -> index >= 0 && index < this.lan.size() ? lan(this.lan.get(index), name) : UiValue.NULL;
         case "servers_network_worlds" -> index >= 0 && index < this.servers.size() ? server(this.servers.get(index), 1000 + index, name) : UiValue.NULL;
         case "third_party_server_network_worlds" -> index >= 0 && index < this.featured.size()
            ? featuredServer(this.featured.get(index), 100000 + index, name)
            : UiValue.NULL;
         default -> UiValue.NULL;
      };
   }

   private static UiValue world(PlayData.World world, int index, String name) {
      return switch (name) {
         case "#local_world_name" -> UiValue.of(world.name());
         case "#local_world_date" -> UiValue.of(world.date());
         case "#local_world_game_mode" -> UiValue.of(world.mode());
         case "#local_worldfile_size" -> UiValue.of(world.size());
         case "#local_world_image" -> UiValue.of(world.image());
         case "#local_world_texture_source" -> UiValue.of(world.imageSource());
         case "#world_lock_visible" -> UiValue.of(world.locked());
         case "#world_button_focus_identifier" -> UiValue.of("local_world_" + index);
         default -> !name.endsWith("_visible") && !name.startsWith("#is_") ? UiValue.NULL : UiValue.FALSE;
      };
   }

   private static UiValue lan(PlayData.Lan game, String name) {
      return switch (name) {
         case "#network_world_header" -> UiValue.of(game.name());
         case "#network_world_details" -> UiValue.of(game.world());
         case "#network_world_player_count" -> UiValue.of(game.online() + "/" + game.max());
         case "#game_online", "#network_world_button_enabled" -> UiValue.TRUE;
         case "#game_offline", "#game_unavailable", "#network_world_is_hardcore" -> UiValue.FALSE;
         case "#network_world_header_icon_texture_path", "#network_world_header_icon_texture_location" -> UiValue.EMPTY;
         default -> UiValue.NULL;
      };
   }

   private static UiValue server(PlayData.Server server, int toggleIndex, String name) {
      PlayData.Status status = server.status();

      return switch (name) {
         case "#network_world_header" -> UiValue.of(server.name());
         case "#network_world_details" -> UiValue.of(status.state() == PlayData.State.ONLINE && !status.motd().isBlank() ? status.motd() : server.address());
         case "#network_world_player_count", "#server_player_count" -> UiValue.of(players(status));
         case "#additional_server_toggle_index" -> UiValue.of(toggleIndex);
         case "#is_server_info_available_collection", "#network_world_button_enabled", "#is_network_available" -> UiValue.TRUE;
         case "#game_online" -> UiValue.of(status.state() == PlayData.State.ONLINE);
         case "#game_offline" -> UiValue.of(status.state() == PlayData.State.OFFLINE);
         case "#game_unavailable", "#network_world_is_hardcore" -> UiValue.FALSE;
         case "#texture_name" -> UiValue.of(pingTexture(status));
         case "#network_world_header_icon_texture_path", "#network_world_header_icon_texture_location" -> UiValue.EMPTY;
         default -> UiValue.NULL;
      };
   }

   private static UiValue featuredServer(PlayData.Server server, int toggleIndex, String name) {
      PlayData.Status status = server.status();

      return switch (name) {
         case "#third_party_server_name" -> UiValue.of(server.name());
         case "#third_party_server_message" -> UiValue.of(status.motd());
         case "#third_party_toggle_index" -> UiValue.of(toggleIndex);
         case "#is_server_info_available_collection", "#is_network_available", "#is_network_available_and_ping_not_loading" -> UiValue.TRUE;
         case "#server_player_count" -> UiValue.of(players(status));
         case "#texture_name" -> UiValue.of(pingTexture(status));
         case "#third_party_screenshot_visible" -> UiValue.FALSE;
         default -> UiValue.NULL;
      };
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
   public UiValue radioSelection(String toggleName) {
      return switch (toggleName) {
         case "navigation_tab" -> UiValue.of(this.tab);
         case "server_navigation_toggle" -> UiValue.of(this.selected);
         default -> UiValue.NULL;
      };
   }

   @Override
   public void radioSelected(String toggleName, int index) {
      if ("navigation_tab".equals(toggleName)) {
         this.tab(index);
      } else if ("server_navigation_toggle".equals(toggleName)) {
         if (index != this.selected) {
            this.selected = index;
            this.version++;
         }

         PlayData.Actions target = this.actions;
         if (index == 100 && target != null) {
            target.addServer();
         }
      }
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      PlayData.Actions target = this.actions;
      if (target != null) {
         switch (buttonId) {
            case "button.menu_start_local_world":
               if ("local_worlds".equals(collection) && index >= 0 && index < this.worlds.size()) {
                  target.openWorld(index);
               }
               break;
            case "button.menu_local_world_item_edit":
               if ("local_worlds".equals(collection) && index >= 0 && index < this.worlds.size()) {
                  target.editWorld(index);
               }
               break;
            case "button.menu_local_world_create":
               target.createWorld();
               break;
            case "button.menu_network_world_item":
               if ("lan_network_worlds".equals(collection) && index >= 0 && index < this.lan.size()) {
                  target.joinLan(index);
               }
               break;
            case "button.menu_network_server_item":
               if (this.selectedServer() != null) {
                  target.joinServer(this.selected - 1000);
               } else if (this.selectedFeatured() != null) {
                  target.joinFeatured(this.selected - 100000);
               }
               break;
            case "button.menu_network_server_world_edit":
               if ("servers_network_worlds".equals(collection) && index >= 0 && index < this.servers.size()) {
                  target.editServer(index);
               }
               break;
            case "button.menu_exit":
               target.close();
               break;
            case "button.menu_tab_left":
               this.tab(this.tab - 1);
               break;
            case "button.menu_tab_right":
               this.tab(this.tab + 1);
         }
      }
   }

   public static Map<String, UiValue> variables(int tab) {
      Map<String, UiValue> engine = new HashMap<>();
      engine.put("$default_tab", UiValue.of(tab));

      for (String flag : List.of(
         "$world_archive_support",
         "$is_realms_enabled",
         "$local_world_upload",
         "$supports_legacy_worlds",
         "$legacy_worlds_share_storage",
         "$legacy_worlds_supports_migration",
         "$ignore_add_servers",
         "$ignore_3rd_party_servers",
         "$is_console",
         "$supports_add_friend",
         "$pre_release",
         "$beta_build",
         "$is_editor_playtest_roundtrip",
         "$nx_os",
         "$world_picker_screen"
      )) {
         engine.put(flag, UiValue.FALSE);
      }

      engine.put("$auto_fetch_legacy_worlds", UiValue.TRUE);
      engine.put("$game_tip_text", UiValue.EMPTY);
      engine.put("$cross_platform_information_text", UiValue.EMPTY);
      return UiPlatform.desktop(engine);
   }

   public interface Actions {
      void openWorld(int var1);

      void editWorld(int var1);

      void createWorld();

      void joinServer(int var1);

      void editServer(int var1);

      void addServer();

      void joinLan(int var1);

      void joinFeatured(int var1);

      void close();

      default void tabChanged(int tab) {
      }
   }

   public record Lan(String name, String world, String address, int online, int max) {
   }

   public record Server(String name, String address, PlayData.Status status) {
   }

   public static enum State {
      UNKNOWN,
      PINGING,
      ONLINE,
      OFFLINE;
   }

   public record Status(PlayData.State state, String motd, int online, int max, int latencyMillis) {
      public static final PlayData.Status UNKNOWN = new PlayData.Status(PlayData.State.UNKNOWN, "", 0, 0, 0);
      public static final PlayData.Status PINGING = new PlayData.Status(PlayData.State.PINGING, "", 0, 0, 0);
      public static final PlayData.Status OFFLINE = new PlayData.Status(PlayData.State.OFFLINE, "", 0, 0, 0);

      public static PlayData.Status online(String motd, int online, int max, long latencyMillis) {
         return new PlayData.Status(PlayData.State.ONLINE, motd, online, max, (int)Math.min(latencyMillis, 99999L));
      }

      public boolean countsKnown() {
         return this.online >= 0 && this.max >= 0;
      }
   }

   public record World(String id, String name, String date, String mode, String size, String image, String imageSource, boolean locked) {
   }
}
