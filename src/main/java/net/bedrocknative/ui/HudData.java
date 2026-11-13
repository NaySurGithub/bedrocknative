package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class HudData extends UiDataMap {
   public static final int SIDEBAR_LINES = 15;
   public static final int MAX_EVENTS = 32;
   public static final int FADE_IN = 10;
   public static final int STAY = 70;
   public static final int FADE_OUT = 20;
   public static final String TITLE_FACTORY = "hud_title_text_factory";
   public static final String ACTIONBAR_FACTORY = "hud_actionbar_text_factory";
   public static final String TIP_FACTORY = "hud_tip_text_factory";
   public static final String SUBTITLE_EVENT = "anim_subtitle_text_alpha_in_play_event";
   public static final Set<String> JAVA_OWNED = Set.of(
      "layout_customization_*",
      "left_helpers",
      "right_helpers",
      "emote_expediate_helpers",
      "game_tip",
      "progress_rend",
      "vignette_rend",
      "save_icon",
      "subtitle_container_host",
      "gamertag_label_for_splitscreen"
   );
   public static final String HOTBAR_ITEMS = "hotbar_items";
   public static final String CHAT_LINES = "chat_text_grid";
   public static final String CHAT_FACTORY = "chat_item_factory";
   public static final String ITEM_TEXT_FACTORY = "item_text_factory";
   public static final int CHAT_HISTORY = 100;
   public static final double CHAT_LIFETIME = 10.0;
   private static final UiValue ITEM_NAME_OFFSET = UiValue.array(0.0, -11.0);
   private static final double[][] BOSS_COLORS = new double[][]{
      {0.925, 0.188, 0.792},
      {0.078, 0.643, 0.925},
      {0.918, 0.137, 0.137},
      {0.11, 0.851, 0.161},
      {0.925, 0.882, 0.0},
      {0.459, 0.0, 0.875},
      {0.4, 0.2, 0.6},
      {0.92, 0.92, 0.92}
   };
   private final List<HudData.Event> events = new ArrayList<>();
   private int fadeIn = 10;
   private int stay = 70;
   private int fadeOut = 20;
   private boolean titleShowing;
   private boolean subtitlePending;
   private HudValues values = HudValues.EMPTY;
   private final String[] chat = new String[100];
   private int chatCount;

   public HudData() {
      this.reset();
   }

   public void reset() {
      this.set("#hud_visible", true);
      this.set("#hud_alpha", 1.0);
      this.set("#hud_propagate_alpha", true);
      this.set("#on_new_death_screen", false);
      this.set("#scoreboard_sidebar_visible", false);
      this.set("#objective_background_opacity", 0.3);
      this.set("#scoreboard_objective_background_opacity", 0.4);
      this.set("#boss_grid_dimension", UiValue.array(1.0, 0.0));
      this.set("#hud_visible_centered", true);
      this.set("#hud_visible_centered_gui_elements", true);
      this.set("#hud_visible_not_centered", false);
      this.set("#hud_visible_centered_touch", false);
      this.set("#hotbar_visible_not_centered", false);
      this.set("#hotbar_visible_not_centered_resizable", false);
      this.set("#paper_doll_visible", false);
      this.set("#hotbar_elipses_left_visible", false);
      this.set("#hotbar_elipses_right_visible", false);
      this.set("#inventory_touch_button", false);
      this.set("#interact_visible", false);
      this.set("#emote_tips_visible", false);
      this.set("#boss_hud_padding", false);
      this.set("#boss_hud_touch_padding", false);
      this.set("#hotbar_grid_dimensions", UiValue.array(9.0, 1.0));
      this.set("#item_name_text_offset", ITEM_NAME_OFFSET);
      this.set("#player_position_visible", false);
      this.set("#number_of_days_played_visible", false);
      this.set("#hud_text_background_alpha", 0.7);
      Arrays.fill(this.chat, null);
      this.chatCount = 0;
      this.values = HudValues.EMPTY;
      this.touch();
      this.player(HudValues.EMPTY);
      this.events.clear();
      this.fadeIn = 10;
      this.stay = 70;
      this.fadeOut = 20;
      this.titleShowing = false;
      this.subtitlePending = false;
      this.remove("#hud_title_text_string");
      this.remove("#hud_subtitle_text_string");
      this.remove("#tip_text");
      this.remove("#item_text");
      this.remove("#jukebox_text");
      this.clearCollection("scoreboard_players");
      this.clearCollection("scoreboard_scores");
      this.clearCollection("boss_bars");
   }

   private void queue(HudData.Event event) {
      if (this.events.size() >= 32) {
         this.events.removeFirst();
      }

      this.events.add(event);
   }

   public List<HudData.Event> drainEvents() {
      List<HudData.Event> drained = List.copyOf(this.events);
      this.events.clear();
      return drained;
   }

   public void deliver(UiScreenInstance instance) {
      for (HudData.Event event : this.drainEvents()) {
         switch (event.kind()) {
            case FACTORY:
               instance.factoryEvent(event.name(), event.controlId(), event.variables(), event.replace(), event.collection(), event.index());
               break;
            case CLEAR:
               instance.clearFactory(event.name());
               break;
            case UI_EVENT:
               instance.fireEvent(event.name());
         }
      }
   }

   public void afterUpdate(UiScreenInstance instance) {
      if (this.titleShowing && instance.factoryEmpty("hud_title_text_factory")) {
         this.titleShowing = false;
      }
   }

   public void title(String text) {
      this.set("#hud_title_text_string", text);
      Map<String, UiValue> variables = new HashMap<>();
      variables.put("$title_fade_in_time", UiValue.of(this.fadeIn / 20.0));
      variables.put("$title_stay_time", UiValue.of(this.stay / 20.0));
      variables.put("$title_fade_out_time", UiValue.of(this.fadeOut / 20.0));
      variables.put("$title_shadow", UiValue.TRUE);
      variables.put("$title_alpha", UiValue.ZERO);
      variables.put("$subtitle_initially_visible", UiValue.of(this.subtitlePending));
      this.queue(new HudData.Event(HudData.Event.Kind.FACTORY, "hud_title_text_factory", "hud_title_text", Map.copyOf(variables), true));
      this.titleShowing = true;
      this.subtitlePending = false;
   }

   public void subtitle(String text) {
      this.set("#hud_subtitle_text_string", text);
      if (this.titleShowing) {
         this.queue(new HudData.Event(HudData.Event.Kind.UI_EVENT, "anim_subtitle_text_alpha_in_play_event", null, Map.of(), false));
      } else {
         this.subtitlePending = true;
      }
   }

   public void times(int fadeInTicks, int stayTicks, int fadeOutTicks) {
      this.fadeIn = Math.max(0, fadeInTicks);
      this.stay = Math.max(0, stayTicks);
      this.fadeOut = Math.max(0, fadeOutTicks);
   }

   public void clearTitles() {
      this.queue(new HudData.Event(HudData.Event.Kind.CLEAR, "hud_title_text_factory", null, Map.of(), false));
      this.titleShowing = false;
      this.subtitlePending = false;
      this.remove("#hud_title_text_string");
      this.remove("#hud_subtitle_text_string");
   }

   public void resetTitles() {
      this.clearTitles();
      this.times(10, 70, 20);
   }

   public void actionbar(String text) {
      this.queue(
         new HudData.Event(
            HudData.Event.Kind.FACTORY,
            "hud_actionbar_text_factory",
            "hud_actionbar_text",
            Map.of("$actionbar_text", UiValue.of(text), "$actionbar_text_background_alpha", UiValue.of(0.5)),
            true
         )
      );
   }

   public void tip(String text) {
      this.set("#tip_text", text);
      this.queue(new HudData.Event(HudData.Event.Kind.FACTORY, "hud_tip_text_factory", "hud_tip_text", Map.of(), true));
   }

   public void player(HudValues next) {
      HudValues previous = this.values;
      this.values = next;
      if (!previous.hotbar().equals(next.hotbar()) || previous.selected() != next.selected()) {
         this.touch();
      }

      HudValues.Mode mode = next.mode();
      boolean survival = mode.survivalUi();
      boolean hotbar = mode != HudValues.Mode.SPECTATOR && !next.hides(HudValues.Part.HOTBAR);
      boolean progress = !next.hides(HudValues.Part.PROGRESS_BAR);
      boolean locator = next.bar() == HudValues.Bar.LOCATOR;
      boolean experienceBar = !locator && survival && progress;
      boolean riding = next.mountHearts() > 0;
      this.set("#show_survival_ui", survival);
      this.set("#is_armor_visible", survival && next.armor() > 0 && !next.hides(HudValues.Part.ARMOR));
      this.set("#hotbar_visible", hotbar);
      this.set("#is_spectator_mode", !hotbar);
      this.set("#hotbar_with_xp_bar", experienceBar);
      this.set("#hotbar_no_xp_bar", !locator && !experienceBar);
      this.set("#hotbar_with_locator_bar", locator);
      this.set("#exp_progress", 1.0F - Math.clamp(next.experience(), 0.0F, 1.0F));
      this.set("#level_number", next.level() > 0 ? Integer.toString(next.level()) : "");
      this.set("#level_number_visible", survival && progress && next.level() > 0);
      this.set("#is_riding_bubbles", riding);
      this.set("#is_not_riding_bubbles", !riding);
      this.set("#creative_horse_hearts", riding && !survival);
      this.set("#survival_horse_hearts", riding && survival);
      this.set("#status_effects_visible", !next.hides(HudValues.Part.EFFECTS));
   }

   public HudValues values() {
      return this.values;
   }

   public void hudTexts(@Nullable String position, @Nullable String daysPlayed) {
      this.set("#player_position_visible", position != null);
      this.set("#player_position_text", position == null ? "" : position);
      this.set("#number_of_days_played_visible", daysPlayed != null);
      this.set("#number_of_days_played_text", daysPlayed == null ? "" : daysPlayed);
   }

   public void chat(String text) {
      int index = this.chatCount++;
      this.chat[index % 100] = text;
      this.touch();
      this.queue(new HudData.Event(HudData.Event.Kind.FACTORY, "chat_item_factory", "chat_item", Map.of(), false, "chat_text_grid", index));
   }

   public void clearChat() {
      this.queue(new HudData.Event(HudData.Event.Kind.CLEAR, "chat_item_factory", null, Map.of(), false));
   }

   public int chatCount() {
      return this.chatCount;
   }

   public @Nullable String lastChat() {
      return this.chatCount == 0 ? null : this.chat[(this.chatCount - 1) % 100];
   }

   public void itemText(String text) {
      if (!this.values.hides(HudValues.Part.ITEM_TEXT)) {
         this.set("#item_text", text);
         this.queue(new HudData.Event(HudData.Event.Kind.FACTORY, "item_text_factory", "item_text", Map.of(), true));
      }
   }

   public void clearItemText() {
      this.remove("#item_text");
   }

   public void jukebox(String text) {
      this.set("#jukebox_text", text);
      this.queue(new HudData.Event(HudData.Event.Kind.FACTORY, "item_text_factory", "jukebox_text", Map.of(), true));
   }

   @Override
   public int length(String collection) {
      return switch (collection) {
         case "hotbar_items" -> 9;
         case "chat_text_grid" -> this.chatCount;
         default -> super.length(collection);
      };
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      return switch (collection) {
         case "hotbar_items" -> index < 0 || index >= 9
            ? UiValue.NULL
            : ("#slot_selected".equals(name) ? UiValue.of(this.values.selected() == index) : ContainerData.slotValue(this.values.slot(index), name));
         case "chat_text_grid" -> "#chat_text".equals(name) && index >= 0 && index < this.chatCount && index >= this.chatCount - 100
            ? UiValue.of(this.chat[index % 100])
            : UiValue.NULL;
         default -> super.item(collection, index, name);
      };
   }

   public static Map<String, UiValue> engineVariables(double backgroundOpacity, double fontScale, double lineSpacing) {
      return UiPlatform.desktop(
         Map.of(
            "$chat_background_opacity",
            UiValue.of(backgroundOpacity),
            "$chat_font_scale_factor",
            UiValue.of(fontScale),
            "$chat_line_spacing",
            UiValue.of(lineSpacing),
            "$chat_item_lifetime",
            UiValue.of(10.0),
            "$chat_font_type",
            UiValue.of("default")
         )
      );
   }

   public void sidebar(@Nullable String title, boolean ascending, List<HudData.SidebarEntry> entries) {
      if (title == null) {
         this.set("#scoreboard_sidebar_visible", false);
         this.set("#scoreboard_sidebar_size", 0.0);
         this.clearCollection("scoreboard_players");
         this.clearCollection("scoreboard_scores");
      } else {
         List<HudData.SidebarEntry> sorted = sidebarOrder(entries, ascending);
         List<UiDataMap.Item> players = new ArrayList<>(sorted.size());
         List<UiDataMap.Item> scores = new ArrayList<>(sorted.size());

         for (HudData.SidebarEntry entry : sorted) {
            players.add(new UiDataMap.Item(null, Map.of("#player_name_sidebar", UiValue.of(entry.name()))));
            scores.add(new UiDataMap.Item(null, Map.of("#player_score_sidebar", UiValue.of(Integer.toString(entry.score())))));
         }

         this.set("#objective_sidebar_name", title);
         this.set("#scoreboard_sidebar_size", sorted.size());
         this.collection("scoreboard_players", players);
         this.collection("scoreboard_scores", scores);
         this.set("#scoreboard_sidebar_visible", true);
      }
   }

   public static List<HudData.SidebarEntry> sidebarOrder(List<HudData.SidebarEntry> entries, boolean ascending) {
      Comparator<HudData.SidebarEntry> byScore = Comparator.comparingInt(HudData.SidebarEntry::score);
      List<HudData.SidebarEntry> sorted = new ArrayList<>(entries);
      sorted.sort(ascending ? byScore : byScore.reversed());
      return sorted.size() > 15 ? List.copyOf(sorted.subList(0, 15)) : sorted;
   }

   public void bossBars(List<HudData.BossBar> bars) {
      List<UiDataMap.Item> items = new ArrayList<>(bars.size());

      for (HudData.BossBar bar : bars) {
         double[] rgb = BOSS_COLORS[bar.color() >= 0 && bar.color() < BOSS_COLORS.length ? bar.color() : 5];
         Map<String, UiValue> values = new HashMap<>();
         values.put("#bossName", UiValue.of(bar.name()));
         values.put("#bar_visible", UiValue.of(bar.visible()));
         values.put("#progress_percentage", UiValue.of(1.0F - Math.max(0.0F, Math.min(1.0F, bar.health()))));
         values.put("#bar_color", UiValue.array(rgb));
         items.add(new UiDataMap.Item(null, values));
      }

      this.collection("boss_bars", items);
      JsonArray dimension = new JsonArray();
      dimension.add(1);
      dimension.add(bars.size());
      this.set("#boss_grid_dimension", UiValue.of(dimension));
   }

   public record BossBar(long id, String name, float health, int color, boolean visible) {
   }

   public record Event(
      HudData.Event.Kind kind, String name, @Nullable String controlId, Map<String, UiValue> variables, boolean replace, @Nullable String collection, int index
   ) {
      public Event(HudData.Event.Kind kind, String name, @Nullable String controlId, Map<String, UiValue> variables, boolean replace) {
         this(kind, name, controlId, variables, replace, null, -1);
      }

      public enum Kind {
         FACTORY,
         CLEAR,
         UI_EVENT;
      }
   }

   public record SidebarEntry(long id, String name, int score) {
   }
}
