package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class TradeData implements ContainerData.Station {
   public static final String TIERS = "trade_tiers";
   public static final String TRADES = "trades";
   public static final String FIRST = "trade_item_1";
   public static final String SECOND = "trade_item_2";
   public static final String SELL = "sell_item";
   public static final String SELECT = "button.trade_select";
   public static final String SECONDARY_SELECT = "button.trade_secondary_select";
   public static final String TRADE = "button.trade";
   private static final String STATE = "#trade_toggle_state";
   private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([sd])");
   private static final int VILLAGER_LEVELS = 5;
   private final Function<String, @Nullable String> lang;
   private TradeData.@Nullable Actions actions;
   private long version = 1L;
   private String traderName = "";
   private boolean levels;
   private List<Integer> tierExperience = List.of();
   private int unlockedTier;
   private int experience;
   private List<TradeData.Offer> offers = List.of();
   private int[] tiers = new int[0];
   private int[][] tierOffers = new int[0][];
   private boolean[] affordable = new boolean[0];
   private int selected = -1;
   private boolean firstValid = true;
   private boolean secondValid = true;
   private boolean resultReady;

   public TradeData(Function<String, @Nullable String> lang) {
      this.lang = lang;
   }

   public void actions(TradeData.@Nullable Actions target) {
      this.actions = target;
   }

   @Override
   public long version() {
      return this.version;
   }

   private void changed() {
      this.version++;
   }

   public void trader(String name, boolean hasLevels, List<Integer> tierExp, int tier, List<TradeData.Offer> list) {
      if (!name.equals(this.traderName)
         || hasLevels != this.levels
         || !tierExp.equals(this.tierExperience)
         || tier != this.unlockedTier
         || !list.equals(this.offers)) {
         this.traderName = name;
         this.levels = hasLevels;
         this.tierExperience = List.copyOf(tierExp);
         this.unlockedTier = tier;
         this.offers = List.copyOf(list);
         TreeMap<Integer, List<Integer>> byTier = new TreeMap<>();

         for (int i = 0; i < this.offers.size(); i++) {
            byTier.computeIfAbsent(this.offers.get(i).tier(), key -> new ArrayList<>()).add(i);
         }

         this.tiers = byTier.keySet().stream().mapToInt(Integer::intValue).toArray();
         this.tierOffers = byTier.values().stream().map(indices -> indices.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
         if (this.affordable.length != this.offers.size()) {
            this.affordable = new boolean[this.offers.size()];
         }

         if (this.selected >= this.offers.size()) {
            this.selected = -1;
         }

         this.changed();
      }
   }

   public void experience(int xp) {
      if (xp != this.experience) {
         this.experience = xp;
         this.changed();
      }
   }

   public void affordable(boolean[] flags) {
      if (!Arrays.equals(flags, this.affordable)) {
         this.affordable = (boolean[])flags.clone();
         this.changed();
      }
   }

   public void slots(boolean firstOk, boolean secondOk, boolean result) {
      if (firstOk != this.firstValid || secondOk != this.secondValid || result != this.resultReady) {
         this.firstValid = firstOk;
         this.secondValid = secondOk;
         this.resultReady = result;
         this.changed();
      }
   }

   public List<TradeData.Offer> offers() {
      return this.offers;
   }

   public int selected() {
      return this.selected;
   }

   public boolean unlocked(int offer) {
      return offer >= 0 && offer < this.offers.size() && this.offers.get(offer).tier() <= this.unlockedTier;
   }

   public int javaOffer(int offer) {
      if (!this.unlocked(offer)) {
         return -1;
      }

      int index = 0;

      for (int i = 0; i < offer; i++) {
         if (this.unlocked(i)) {
            index++;
         }
      }

      return index;
   }

   public int offerAt(UiItemPath path) {
      int tier = path.index("trade_tiers");
      int trade = path.index("trades");
      return tier >= 0 && tier < this.tierOffers.length && trade >= 0 && trade < this.tierOffers[tier].length ? this.tierOffers[tier][trade] : -1;
   }

   public static @Nullable String cellAt(UiItemPath path) {
      for (int depth = 0; depth < path.size(); depth++) {
         String collection = path.collection(depth);
         if ("trade_item_1".equals(collection) || "trade_item_2".equals(collection) || "sell_item".equals(collection)) {
            return collection;
         }

         if ("trades".equals(collection)) {
            return null;
         }
      }

      return null;
   }

   private String text(String key, Object... args) {
      String pattern = this.lang.apply(key);
      if (pattern == null) {
         return args.length == 0 ? key : key + " " + Arrays.toString(args);
      }

      Matcher matcher = ARGUMENT.matcher(pattern);
      StringBuilder out = new StringBuilder();
      int next = 0;

      while (matcher.find()) {
         int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
         matcher.appendReplacement(out, Matcher.quoteReplacement(index >= 0 && index < args.length ? String.valueOf(args[index]) : ""));
      }

      matcher.appendTail(out);
      return out.toString();
   }

   private String levelName(int tier) {
      String key = "trade.level." + (tier + 1);
      return this.lang.apply(key) != null ? this.text(key) : this.text("trade.levelPrefix", tier + 1);
   }

   private int lastTier() {
      return this.tierExperience.isEmpty() ? 4 : this.tierExperience.size() - 1;
   }

   private boolean maxTier(int tier) {
      return this.levels && tier >= this.lastTier();
   }

   public String nameLabel() {
      return this.levels ? this.text("trade.nameAndLevel", this.traderName, this.levelName(this.unlockedTier)) : this.traderName;
   }

   public String tierName(int tier) {
      return this.maxTier(tier)
         ? this.text("trade.levelPrefixAndName.max", this.levelName(tier))
         : this.text("trade.levelPrefixAndName", tier + 1, this.levelName(tier));
   }

   public float progress(int extra) {
      if (this.levels && !this.tierExperience.isEmpty()) {
         int tier = Math.max(0, Math.min(this.unlockedTier, this.tierExperience.size() - 1));
         if (tier >= this.tierExperience.size() - 1) {
            return 1.0F;
         }

         int from = this.tierExperience.get(tier);
         int to = this.tierExperience.get(tier + 1);
         return to <= from ? 1.0F : Math.max(0.0F, Math.min(1.0F, (float)(this.experience + extra - from) / (to - from)));
      } else {
         return 0.0F;
      }
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#name_label" -> UiValue.of(this.nameLabel());
         case "#show_level", "#exp_bar_visible" -> UiValue.of(this.levels);
         case "#trade_selector_total" -> UiValue.of(this.tiers.length);
         case "#exp_progress" -> UiValue.of(1.0F - this.progress(0));
         case "#exp_possible_progress" -> UiValue.of(1.0F - this.progress(this.selected >= 0 ? this.offers.get(this.selected).traderExp() : 0));
         case "#trade_button_enabled" -> UiValue.of(this.resultReady);
         case "#trade_details_button_1_visible", "#trade_details_button_2_visible", "#enchantment_details_button_visible", "#gamepad_helper_x_visible", "#gamepad_helper_y_visible" -> UiValue.FALSE;
         case "#selected_hover_text" -> UiValue.EMPTY;
         default -> UiValue.NULL;
      };
   }

   @Override
   public int length(String collection) {
      return "trade_tiers".equals(collection) ? this.tiers.length : -1;
   }

   @Override
   public @Nullable UiValue item(String collection, int index, String name) {
      if ("trade_tiers".equals(collection)) {
         if (index >= 0 && index < this.tiers.length) {
            int tier = this.tiers[index];

            return switch (name) {
               case "#tier_name" -> UiValue.of(this.tierName(tier));
               case "#is_tier_unlocked" -> UiValue.of(tier <= this.unlockedTier);
               case "#tier_visible" -> UiValue.TRUE;
               case "#trade_tier_total" -> UiValue.of(this.tierOffers[index].length);
               default -> UiValue.NULL;
            };
         } else {
            return UiValue.NULL;
         }
      } else if (name.equals("#item_valid")) {
         return switch (collection) {
            case "trade2_ingredient1_item" -> UiValue.of(this.firstValid);
            case "trade2_ingredient2_item" -> UiValue.of(this.secondValid);
            case "trade2_result_item" -> UiValue.TRUE;
            default -> null;
         };
      } else {
         return null;
      }
   }

   @Override
   public @Nullable UiValue global(String name, UiItemPath path) {
      int offer = this.offerAt(path);
      if (offer < 0) {
         return null;
      }

      TradeData.Offer shown = this.offers.get(offer);
      switch (name) {
         case "#trade_toggle_state":
            return UiValue.of(offer == this.selected);
         case "#trade_toggle_enabled":
            return UiValue.of(this.unlocked(offer));
         case "#trade_possible":
            return UiValue.of(!shown.outOfStock() && offer < this.affordable.length && this.affordable[offer]);
         case "#trade_cross_out_visible":
            return UiValue.of(shown.outOfStock());
         case "#padding_around_sell_item":
            return UiValue.of(!shown.first().changed() && !shown.second().changed());
         case "#using_touch":
            return UiValue.FALSE;
         default:
            String cell = cellAt(path);
            if (cell == null) {
               return null;
            } else {
               TradeData.Item item = switch (cell) {
                  case "trade_item_1" -> shown.first();
                  case "trade_item_2" -> shown.second();
                  default -> shown.sell();
               };

               return switch (name) {
                  case "#trade_item_count" -> UiValue.of(item.changed() ? Integer.toString(item.baseCount()) : count(item.count()));
                  case "#second_trade_item_count" -> UiValue.of(item.changed() ? Integer.toString(item.count()) : "");
                  case "#trade_price_different" -> UiValue.of(item.changed());
                  case "#single_slash_visible" -> UiValue.of(item.changed() && item.baseCount() < 10);
                  case "#double_slash_visible" -> UiValue.of(item.changed() && item.baseCount() >= 10);
                  case "#hover_text" -> UiValue.of(item.present() ? item.name() : "");
                  default -> null;
               };
            }
      }
   }

   private static String count(int count) {
      return count > 1 ? Integer.toString(count) : "";
   }

   @Override
   public boolean event(String buttonId, UiItemPath path) {
      if (!"button.trade_select".equals(buttonId) && !"button.trade_secondary_select".equals(buttonId)) {
         return false;
      }

      int offer = this.offerAt(path);
      if (offer < 0) {
         return false;
      }

      this.changed();
      if (this.unlocked(offer)) {
         this.selected = offer;
         if (this.actions != null) {
            this.actions.select(this.javaOffer(offer));
         }
      }

      return true;
   }

   @Override
   public boolean event(String buttonId, @Nullable String collection, int index) {
      if ("button.trade".equals(buttonId)) {
         if (this.resultReady && this.actions != null) {
            this.actions.trade();
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean write(@Nullable String collection, int index, String name, UiValue value) {
      if ("#trade_toggle_state".equals(name)) {
         this.changed();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return "TradeData[" + this.traderName + ", tier " + this.unlockedTier + ", " + this.offers.size() + " offers, selected " + this.selected + "]";
   }

   public interface Actions {
      default void select(int javaOffer) {
      }

      default void trade() {
      }
   }

   public record Item(int count, int baseCount, String name) {
      public static final TradeData.Item NONE = new TradeData.Item(0, 0, "");

      public boolean present() {
         return this.count > 0;
      }

      public boolean changed() {
         return this.present() && this.baseCount > 0 && this.baseCount != this.count;
      }
   }

   public record Offer(int tier, TradeData.Item first, TradeData.Item second, TradeData.Item sell, boolean outOfStock, int traderExp) {
   }
}
