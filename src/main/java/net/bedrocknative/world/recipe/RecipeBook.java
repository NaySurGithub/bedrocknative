package net.bedrocknative.world.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.bedrocknative.world.state.RecipeUnlocks;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public final class RecipeBook {
   public static final int MAX_TIMES = 999;
   private final RecipeIndex index;
   private final @Nullable RecipeUnlocks unlocks;
   private final RecipeBook.Catalog catalog;
   private final ItemMatcher matcher;
   private String tag = "crafting_table";
   private int gridSize = 3;
   private List<ItemData> sources = List.of();
   private RecipeBook.Tab tab = RecipeBook.Tab.SEARCH;
   private String search = "";
   private boolean filtering;
   private final Set<Integer> open = new HashSet<>();
   private @Nullable String selected;
   private int indexVersion = -1;
   private int unlocksVersion = -1;
   private int catalogVersion = -1;
   private boolean entriesDirty = true;
   private boolean timesDirty = true;
   private boolean cellsDirty = true;
   private List<RecipeBook.Entry> entries = List.of();
   private List<RecipeBook.Cell> cells = List.of();
   private int version;
   private int entriesVersion;

   public RecipeBook(RecipeIndex index, @Nullable RecipeUnlocks unlocks, RecipeBook.Catalog catalog, ItemMatcher matcher) {
      this.index = index;
      this.unlocks = unlocks;
      this.catalog = catalog;
      this.matcher = matcher;
   }

   public void station(String recipeTag, int size) {
      if (!recipeTag.equals(this.tag)) {
         this.tag = recipeTag;
         this.entriesDirty = true;
      }

      if (size != this.gridSize) {
         this.gridSize = size;
         this.timesDirty = true;
      }
   }

   public int gridSize() {
      return this.gridSize;
   }

   public void sources(List<ItemData> items) {
      if (!items.equals(this.sources)) {
         this.sources = List.copyOf(items);
         this.timesDirty = true;
      }
   }

   public List<ItemData> sources() {
      return this.sources;
   }

   public void tab(RecipeBook.Tab shown) {
      if (shown != this.tab) {
         this.tab = shown;
         this.cellsDirty = true;
      }
   }

   public RecipeBook.Tab tab() {
      return this.tab;
   }

   public void search(String text) {
      String query = text == null ? "" : text;
      if (!query.equals(this.search)) {
         this.search = query;
         this.cellsDirty = true;
      }
   }

   public String search() {
      return this.search;
   }

   public void filtering(boolean on) {
      if (on != this.filtering) {
         this.filtering = on;
         this.cellsDirty = true;
      }
   }

   public boolean filtering() {
      return this.filtering;
   }

   public void toggle(int group) {
      if (!this.open.remove(group)) {
         this.open.add(group);
      }

      this.cellsDirty = true;
   }

   public boolean isOpen(int group) {
      return this.open.contains(group);
   }

   public void select(RecipeBook.@Nullable Entry entry) {
      String key = entry == null ? null : entry.key();
      if (!Objects.equals(key, this.selected)) {
         this.selected = key;
         this.cellsDirty = true;
      }
   }

   public RecipeBook.@Nullable Entry selected() {
      if (this.selected == null) {
         return null;
      }

      for (RecipeBook.Entry entry : this.entries()) {
         if (entry.key().equals(this.selected)) {
            return entry;
         }
      }

      return null;
   }

   public boolean needsCraftingTable() {
      RecipeBook.Entry chosen = this.selected();
      return chosen != null && !chosen.fits();
   }

   public List<RecipeBook.Entry> entries() {
      this.refresh();
      return this.entries;
   }

   public List<RecipeBook.Cell> cells() {
      this.refresh();
      return this.cells;
   }

   public int version() {
      this.refresh();
      return this.version;
   }

   public int entriesVersion() {
      this.refresh();
      return this.entriesVersion;
   }

   public RecipeBook.@Nullable Entry entry(ItemData result) {
      String key = key(result);

      for (RecipeBook.Entry entry : this.entries()) {
         if (entry.key().equals(key)) {
            return entry;
         }
      }

      return null;
   }

   public boolean visible(BookRecipe recipe) {
      return recipe.alwaysUnlocked() || this.unlocks == null || !this.unlocks.known() || this.unlocks.isUnlocked(recipe.id());
   }

   private void refresh() {
      if (this.index.version() != this.indexVersion
         || this.unlocks != null && this.unlocks.version() != this.unlocksVersion
         || this.catalog.version() != this.catalogVersion) {
         this.indexVersion = this.index.version();
         this.unlocksVersion = this.unlocks == null ? -1 : this.unlocks.version();
         this.catalogVersion = this.catalog.version();
         this.entriesDirty = true;
      }

      if (this.entriesDirty) {
         this.entries = this.build();
         this.entriesDirty = false;
         this.timesDirty = true;
         this.entriesVersion++;
      }

      if (this.timesDirty) {
         this.count();
         this.timesDirty = false;
         this.cellsDirty = true;
      }

      if (this.cellsDirty) {
         this.cells = this.layout();
         this.cellsDirty = false;
         this.version++;
      }
   }

   private List<RecipeBook.Entry> build() {
      Map<String, List<BookRecipe>> byResult = new LinkedHashMap<>();

      for (BookRecipe recipe : this.index.withTag(this.tag)) {
         if (recipe.hasResult() && this.visible(recipe)) {
            byResult.computeIfAbsent(key(recipe.result()), key -> new ArrayList<>(1)).add(recipe);
         }
      }

      List<RecipeBook.Entry> built = new ArrayList<>(byResult.size());

      for (Map.Entry<String, List<BookRecipe>> result : byResult.entrySet()) {
         List<BookRecipe> recipes = new ArrayList<>(result.getValue());
         recipes.sort(Comparator.comparingInt(BookRecipe::priority));
         ItemData item = recipes.getFirst().result();
         RecipeBook.Place place = this.catalog.place(item);
         built.add(new RecipeBook.Entry(result.getKey(), item, recipes, place == null ? RecipeBook.Place.NONE : place, this.catalog.name(item)));
      }

      built.sort(
         Comparator.<RecipeBook.Entry>comparingInt(entry -> entry.place().category().ordinal())
            .thenComparingInt(entry -> entry.place().group() < 0 ? Integer.MAX_VALUE : entry.place().group())
            .thenComparingInt(entry -> entry.place().order())
            .thenComparingInt(entry -> entry.recipes().getFirst().netId())
      );
      return List.copyOf(built);
   }

   private void count() {
      CraftPlanner.Snapshot snapshot = CraftPlanner.snapshot(this.sources, this.matcher);

      for (RecipeBook.Entry entry : this.entries) {
         entry.times = 0;
         entry.fits = false;
         entry.partial = false;
         entry.usable = null;
         BookRecipe fitting = null;

         for (BookRecipe recipe : entry.recipes()) {
            if (recipe.fits(this.gridSize)) {
               entry.fits = true;
               if (fitting == null) {
                  fitting = recipe;
               }

               if (!entry.partial && snapshot.hasAny(recipe)) {
                  entry.partial = true;
               }

               int times = snapshot.maxTimes(recipe, 999);
               if (times > entry.times) {
                  entry.times = times;
                  entry.usable = recipe;
               }
            }
         }

         if (entry.usable == null) {
            entry.usable = fitting;
         }
      }
   }

   private List<RecipeBook.Cell> layout() {
      List<RecipeBook.Cell> laid = new ArrayList<>();
      if (this.tab == RecipeBook.Tab.SEARCH) {
         String query = this.search.trim().toLowerCase(Locale.ROOT);

         for (RecipeBook.Entry entry : this.entries) {
            if (this.passesFilter(entry) && (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query))) {
               laid.add(this.cell(RecipeBook.Kind.ENTRY, entry));
            }
         }

         return List.copyOf(laid);
      } else {
         List<RecipeBook.Entry> shown = new ArrayList<>();

         for (RecipeBook.Entry entry : this.entries) {
            if (entry.place().category() == this.tab && this.passesFilter(entry)) {
               shown.add(entry);
            }
         }

         int i = 0;

         while (i < shown.size()) {
            RecipeBook.Entry first = shown.get(i);
            int group = first.place().group();
            int end = i + 1;
            if (first.place().named()) {
               while (end < shown.size() && shown.get(end).place().group() == group) {
                  end++;
               }
            }

            if (end - i < 2) {
               laid.add(this.cell(RecipeBook.Kind.ENTRY, first));
               i = end;
            } else {
               List<RecipeBook.Entry> members = shown.subList(i, end);
               boolean isOpen = this.open.contains(group);
               ItemData icon = this.catalog.groupIcon(group);
               laid.add(
                  new RecipeBook.Cell(
                     isOpen ? RecipeBook.Kind.HEAD_OPEN : RecipeBook.Kind.HEAD_CLOSED,
                     null,
                     group,
                     icon != null && !icon.isNull() ? icon : first.result(),
                     this.catalog.groupName(group),
                     members.stream().anyMatch(RecipeBook.Entry::craftable),
                     false
                  )
               );
               if (isOpen) {
                  for (RecipeBook.Entry member : members) {
                     laid.add(this.cell(RecipeBook.Kind.GROUP_ENTRY, member));
                  }
               }

               i = end;
            }
         }

         return List.copyOf(laid);
      }
   }

   private boolean passesFilter(RecipeBook.Entry entry) {
      return !this.filtering || entry.craftable() || entry.partial();
   }

   private RecipeBook.Cell cell(RecipeBook.Kind kind, RecipeBook.Entry entry) {
      return new RecipeBook.Cell(kind, entry, entry.place().group(), entry.result(), entry.name(), entry.craftable(), entry.key().equals(this.selected));
   }

   public static String key(ItemData item) {
      if (item != null && !item.isNull()) {
         NbtMap tag = item.getTag();
         return item.getDefinition().getIdentifier() + ":" + item.getDamage() + (tag != null && !tag.isEmpty() ? "#" + tag.hashCode() : "");
      } else {
         return "";
      }
   }

   public interface Catalog {
      RecipeBook.@Nullable Place place(ItemData var1);

      String groupName(int var1);

      ItemData groupIcon(int var1);

      String name(ItemData var1);

      default int version() {
         return 0;
      }
   }

   public record Cell(RecipeBook.Kind kind, RecipeBook.@Nullable Entry entry, int group, ItemData icon, String name, boolean craftable, boolean selected) {
   }

   public static final class Entry {
      private final String key;
      private final ItemData result;
      private final List<BookRecipe> recipes;
      private final RecipeBook.Place place;
      private final String name;
      private int times;
      private boolean fits;
      private boolean partial;
      private @Nullable BookRecipe usable;

      Entry(String key, ItemData result, List<BookRecipe> recipes, RecipeBook.Place place, String name) {
         this.key = key;
         this.result = result;
         this.recipes = List.copyOf(recipes);
         this.place = place;
         this.name = name;
      }

      public String key() {
         return this.key;
      }

      public ItemData result() {
         return this.result;
      }

      public List<BookRecipe> recipes() {
         return this.recipes;
      }

      public RecipeBook.Place place() {
         return this.place;
      }

      public String name() {
         return this.name;
      }

      public int times() {
         return this.times;
      }

      public boolean craftable() {
         return this.times > 0;
      }

      public boolean partial() {
         return this.partial;
      }

      public boolean fits() {
         return this.fits;
      }

      public BookRecipe recipe() {
         return this.usable != null ? this.usable : this.recipes.getFirst();
      }

      public int craftableItems() {
         return this.times * Math.max(1, this.recipe().result().getCount());
      }
   }

   public enum Kind {
      ENTRY,
      HEAD_CLOSED,
      HEAD_OPEN,
      GROUP_ENTRY;
   }

   public record Place(RecipeBook.Tab category, int group, boolean named, int order) {
      public static final RecipeBook.Place NONE = new RecipeBook.Place(RecipeBook.Tab.ITEMS, -1, false, Integer.MAX_VALUE);
   }

   public enum Tab {
      CONSTRUCTION,
      EQUIPMENT,
      ITEMS,
      NATURE,
      SEARCH;
   }
}
