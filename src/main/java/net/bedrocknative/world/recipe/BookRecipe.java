package net.bedrocknative.world.recipe;

import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement.UnlockingContext;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.InvalidDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;

public record BookRecipe(
   int netId,
   String id,
   boolean shaped,
   String tag,
   int width,
   int height,
   List<ItemDescriptorWithCount> ingredients,
   List<ItemData> results,
   int priority,
   UnlockingContext unlocking
) {
   public static final String CRAFTING_TABLE = "crafting_table";

   public BookRecipe(
      int netId,
      String id,
      boolean shaped,
      String tag,
      int width,
      int height,
      List<ItemDescriptorWithCount> ingredients,
      List<ItemData> results,
      int priority,
      UnlockingContext unlocking
   ) {
      id = id == null ? "" : id;
      tag = tag == null ? "" : tag;
      ingredients = List.copyOf(ingredients);
      results = List.copyOf(results);
      unlocking = unlocking == null ? UnlockingContext.NONE : unlocking;
      this.netId = netId;
      this.id = id;
      this.shaped = shaped;
      this.tag = tag;
      this.width = width;
      this.height = height;
      this.ingredients = ingredients;
      this.results = results;
      this.priority = priority;
      this.unlocking = unlocking;
   }

   public static BookRecipe of(ShapedRecipeData recipe) {
      return new BookRecipe(
         recipe.getNetId(),
         recipe.getId(),
         true,
         recipe.getTag(),
         recipe.getWidth(),
         recipe.getHeight(),
         recipe.getIngredients(),
         recipe.getResults(),
         recipe.getPriority(),
         context(recipe.getRequirement())
      );
   }

   public static BookRecipe of(ShapelessRecipeData recipe) {
      return new BookRecipe(
         recipe.getNetId(),
         recipe.getId(),
         false,
         recipe.getTag(),
         0,
         0,
         recipe.getIngredients(),
         recipe.getResults(),
         recipe.getPriority(),
         context(recipe.getRequirement())
      );
   }

   private static UnlockingContext context(RecipeUnlockingRequirement requirement) {
      return requirement != null && requirement.getContext() != null ? requirement.getContext() : UnlockingContext.NONE;
   }

   public ItemData result() {
      return this.results.isEmpty() ? ItemData.AIR : this.results.getFirst();
   }

   public boolean hasResult() {
      return !this.results.isEmpty() && !this.results.getFirst().isNull();
   }

   public boolean crafting() {
      return "crafting_table".equals(this.tag);
   }

   public boolean alwaysUnlocked() {
      return this.unlocking == UnlockingContext.ALWAYS_UNLOCKED;
   }

   public List<BookRecipe.Unit> units() {
      List<BookRecipe.Unit> units = new ArrayList<>(this.ingredients.size());

      for (int i = 0; i < this.ingredients.size(); i++) {
         ItemDescriptorWithCount ingredient = this.ingredients.get(i);
         if (!empty(ingredient)) {
            units.add(new BookRecipe.Unit(i, ingredient, Math.max(1, ingredient.getCount())));
         }
      }

      return units;
   }

   public boolean fits(int gridSize) {
      if (this.shaped) {
         return this.width <= gridSize && this.height <= gridSize;
      } else {
         int cells = 0;

         for (BookRecipe.Unit unit : this.units()) {
            cells += unit.perCraft();
         }

         return cells <= gridSize * gridSize;
      }
   }

   public static boolean empty(ItemDescriptorWithCount ingredient) {
      return ingredient == null || ingredient.getDescriptor() == null || ingredient.getDescriptor() instanceof InvalidDescriptor || ingredient.getCount() <= 0;
   }

   public record Unit(int index, ItemDescriptorWithCount ingredient, int perCraft) {
   }
}
