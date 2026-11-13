package net.bedrocknative.client.inventory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.recipe.BookRecipe;
import net.bedrocknative.world.recipe.RecipeBook;
import net.bedrocknative.world.recipe.RecipeIndex;
import net.bedrocknative.world.state.InventoryOptions;
import net.bedrocknative.world.state.RecipeUnlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.item.ItemStack;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public final class BedrockRecipeBook {
   private static final boolean ENABLED = !switchOff(System.getProperty("bedrocknative.bedrockRecipeBook"));
   @Nullable
   private static volatile BedrockRecipeBook active;
   private final Minecraft minecraft;
   private final StackRequests requests;
   private final RecipeUnlocks unlocks;
   private final Supplier<InventoryOptions> options;
   private final Consumer<InventoryOptions> changeOptions;
   private final BedrockRecipeBook.Catalog catalog;
   private final RecipeBook book;
   private final JavaRecipeBookFeed javaBook;
   private final Map<String, ItemStack> stacks = new HashMap<>();
   private int toasts;

   public BedrockRecipeBook(
      Minecraft minecraft, StackRequests requests, RecipeUnlocks unlocks, Supplier<InventoryOptions> options, Consumer<InventoryOptions> changeOptions
   ) {
      this.minecraft = minecraft;
      this.requests = requests;
      this.unlocks = unlocks;
      this.options = options;
      this.changeOptions = changeOptions;
      this.catalog = new BedrockRecipeBook.Catalog(requests.creative());
      this.book = new RecipeBook(requests.crafting().index(), unlocks, this.catalog, requests.crafting()::matches);
      this.javaBook = new JavaRecipeBookFeed(minecraft, this);
   }

   public static boolean enabled() {
      return ENABLED;
   }

   @Nullable
   public static BedrockRecipeBook active() {
      return ENABLED ? active : null;
   }

   static boolean switchOff(@Nullable String value) {
      if (value != null) {
         String var1 = value.trim().toLowerCase(Locale.ROOT);
         switch (var1) {
            case "off":
            case "false":
            case "0":
            case "no":
               return true;
         }
      }

      return false;
   }

   public void start() {
      active = this;
      this.stacks.clear();
      this.toasts = 0;
      this.javaBook.reset();
   }

   public void tick() {
      if (ENABLED && active == this) {
         this.javaBook.tick();
      }
   }

   public boolean placeFromJava(ServerboundPlaceRecipePacket packet) {
      return this.javaBook.place(packet);
   }

   public int javaRecipes() {
      return this.javaBook.fedRecipes();
   }

   public void close() {
      if (active == this) {
         active = null;
      }
   }

   public RecipeBook book() {
      return this.book;
   }

   public StackRequests requests() {
      return this.requests;
   }

   public RecipeUnlocks unlocks() {
      return this.unlocks;
   }

   public InventoryOptions options() {
      return this.options.get();
   }

   void options(InventoryOptions changed) {
      this.changeOptions.accept(changed);
   }

   public int toasts() {
      return this.toasts;
   }

   ItemStack stack(ItemData item) {
      return item != null && !item.isNull()
         ? this.stacks.computeIfAbsent(RecipeBook.key(item), key -> ItemMappings.toJava(item).copyWithCount(1))
         : ItemStack.EMPTY;
   }

   static int maxStack(ItemData item) {
      ItemStack stack = ItemMappings.toJava(item);
      return stack.isEmpty() ? 64 : stack.getMaxStackSize();
   }

   public void unlocked(List<String> ids) {
      Map<String, BookRecipe> results = new LinkedHashMap<>();
      RecipeIndex index = this.requests.crafting().index();

      for (String id : ids) {
         for (BookRecipe recipe : index.byId(id)) {
            if (recipe.hasResult() && (recipe.crafting() || RecipeUnlockedToast.description(recipe.tag()) != null)) {
               results.putIfAbsent(RecipeBook.key(recipe.result()), recipe);
            }
         }
      }

      if (!results.isEmpty()) {
         BookRecipe first = results.values().iterator().next();
         this.toasts++;
         BedrockNative.LOGGER
            .info(
               "Recipe book: {} new recipes unlocked ({} results), toast for {}",
               new Object[]{ids.size(), results.size(), first.result().getDefinition().getIdentifier()}
            );
         RecipeUnlockedToast.show(this.minecraft, this.stack(first.result()), first.tag(), !first.fits(2), results.size() - 1);
      }
   }

   static final class Catalog implements RecipeBook.Catalog {
      private final CreativeItems creative;
      @Nullable
      private List<CreativeItemData> builtFor;
      private final Map<String, RecipeBook.Place> places = new HashMap<>();
      private final Map<String, String> names = new HashMap<>();
      private int version;

      Catalog(CreativeItems creative) {
         this.creative = creative;
      }

      private void refresh() {
         List<CreativeItemData> contents = this.creative.contents();
         if (contents != this.builtFor) {
            this.builtFor = contents;
            this.places.clear();
            this.names.clear();
            this.version++;
            List<CreativeItemGroup> groups = this.creative.groups();

            for (int i = 0; i < contents.size(); i++) {
               CreativeItemData entry = contents.get(i);
               ItemData item = entry.getItem();
               if (item != null && !item.isNull()) {
                  int group = entry.getGroupId() >= 0 && entry.getGroupId() < groups.size() ? entry.getGroupId() : -1;
                  RecipeBook.Tab category = group < 0 ? RecipeBook.Tab.ITEMS : tab(groups.get(group).getCategory());
                  if (category != null) {
                     boolean named = group >= 0 && !groups.get(group).getName().isEmpty();
                     RecipeBook.Place place = new RecipeBook.Place(category, group, named, i);
                     this.places.putIfAbsent(item.getDefinition().getIdentifier() + ":" + item.getDamage(), place);
                     this.places.putIfAbsent(RecipeBook.key(item), place);
                  }
               }
            }
         }
      }

      @Nullable
      private static RecipeBook.Tab tab(CreativeItemCategory category) {
         return switch (category) {
            case CONSTRUCTION -> RecipeBook.Tab.CONSTRUCTION;
            case EQUIPMENT -> RecipeBook.Tab.EQUIPMENT;
            case ITEMS -> RecipeBook.Tab.ITEMS;
            case NATURE -> RecipeBook.Tab.NATURE;
            default -> null;
         };
      }

      @Nullable
      @Override
      public RecipeBook.Place place(ItemData result) {
         this.refresh();
         RecipeBook.Place place = this.places.get(RecipeBook.key(result));
         return place != null ? place : this.places.get(result.getDefinition().getIdentifier() + ":" + result.getDamage());
      }

      @Override
      public String groupName(int group) {
         List<CreativeItemGroup> groups = this.creative.groups();
         return group >= 0 && group < groups.size() ? BedrockText.translate(groups.get(group).getName(), List.of()) : "";
      }

      @Override
      public ItemData groupIcon(int group) {
         List<CreativeItemGroup> groups = this.creative.groups();
         ItemData icon = group >= 0 && group < groups.size() ? groups.get(group).getIcon() : null;
         return icon == null ? ItemData.AIR : icon;
      }

      @Override
      public String name(ItemData item) {
         return this.names.computeIfAbsent(RecipeBook.key(item), key -> ItemMappings.toJava(item).getHoverName().getString());
      }

      @Override
      public int version() {
         this.refresh();
         return this.version;
      }
   }
}
