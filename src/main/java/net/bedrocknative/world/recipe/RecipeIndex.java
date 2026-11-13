package net.bedrocknative.world.recipe;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.jspecify.annotations.Nullable;

public final class RecipeIndex {
   public static final int MAX_RECIPES = 50000;
   private final Int2ObjectMap<BookRecipe> byNetId = new Int2ObjectLinkedOpenHashMap();
   private final Map<String, List<BookRecipe>> byId = new HashMap<>();
   private int version;

   public void apply(CraftingDataPacket packet) {
      if (packet.isCleanRecipes()) {
         this.byNetId.clear();
         this.byId.clear();
      }

      for (ShapedRecipeData recipe : packet.getShapedData()) {
         this.add(BookRecipe.of(recipe));
      }

      for (ShapelessRecipeData recipe : packet.getShapelessData()) {
         this.add(BookRecipe.of(recipe));
      }

      this.version++;
   }

   public void add(BookRecipe recipe) {
      if (recipe.netId() > 0 && (this.byNetId.size() < 50000 || this.byNetId.containsKey(recipe.netId()))) {
         BookRecipe old = (BookRecipe)this.byNetId.put(recipe.netId(), recipe);
         if (old != null) {
            List<BookRecipe> same = this.byId.get(old.id());
            if (same != null) {
               same.remove(old);
            }
         }

         this.byId.computeIfAbsent(recipe.id(), id -> new ArrayList<>(1)).add(recipe);
         this.version++;
      }
   }

   public void clear() {
      this.byNetId.clear();
      this.byId.clear();
      this.version++;
   }

   public @Nullable BookRecipe byNetId(int netId) {
      return (BookRecipe)this.byNetId.get(netId);
   }

   public List<BookRecipe> byId(String id) {
      List<BookRecipe> recipes = this.byId.get(id);
      return recipes == null ? List.of() : Collections.unmodifiableList(recipes);
   }

   public Set<Integer> netIds(Collection<String> ids) {
      Set<Integer> netIds = new LinkedHashSet<>();

      for (String id : ids) {
         for (BookRecipe recipe : this.byId(id)) {
            netIds.add(recipe.netId());
         }
      }

      return netIds;
   }

   public Collection<BookRecipe> all() {
      return Collections.unmodifiableCollection(this.byNetId.values());
   }

   public List<BookRecipe> withTag(String tag) {
      List<BookRecipe> tagged = new ArrayList<>();
      ObjectIterator var3 = this.byNetId.values().iterator();

      while (var3.hasNext()) {
         BookRecipe recipe = (BookRecipe)var3.next();
         if (recipe.tag().equals(tag)) {
            tagged.add(recipe);
         }
      }

      return tagged;
   }

   public int size() {
      return this.byNetId.size();
   }

   public int version() {
      return this.version;
   }
}
