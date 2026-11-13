package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.recipe.RecipeIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.CraftingRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DeferredDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.InvalidDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.jspecify.annotations.Nullable;

public final class Crafting {
   static final int INVENTORY_GRID_START = 28;
   static final int TABLE_GRID_START = 32;
   private static final String CRAFTING_TABLE = "crafting_table";
   private static final String STONECUTTER = "stonecutter";
   private static final Map<String, String> TAG_ALIASES = Map.of(
      "minecraft:trimmable_armors",
      ItemTags.TRIMMABLE_ARMOR.location().toString(),
      "minecraft:egg",
      ItemTags.EGGS.location().toString(),
      "minecraft:is_pickaxe",
      ItemTags.PICKAXES.location().toString()
   );
   private static final String TRIM_TEMPLATES = "minecraft:trim_templates";
   private static final String STEW_MUSHROOMS = "minecraft:mushrooms_for_stew";
   private static final Set<String> STEW_MUSHROOM_ITEMS = Set.of("minecraft:red_mushroom", "minecraft:brown_mushroom");
   private static final int ANY_AUX = 32767;
   private final List<ShapedRecipeData> shaped = new ArrayList<>();
   private final List<ShapelessRecipeData> shapeless = new ArrayList<>();
   private final List<ShapelessRecipeData> stonecutter = new ArrayList<>();
   private final List<SmithingTransformRecipeData> smithingTransforms = new ArrayList<>();
   private final List<SmithingTrimRecipeData> smithingTrims = new ArrayList<>();
   private final Set<Integer> brewingInputs = new LinkedHashSet<>();
   private final Set<Integer> brewingReagents = new LinkedHashSet<>();
   private final Map<UUID, Integer> multi = new HashMap<>();
   private final Map<String, TagKey<Item>> tags = new HashMap<>();
   private final RecipeIndex index = new RecipeIndex();
   private int version;

   public void apply(CraftingDataPacket packet) {
      this.index.apply(packet);
      if (packet.isCleanRecipes()) {
         this.shaped.clear();
         this.shapeless.clear();
         this.stonecutter.clear();
         this.smithingTransforms.clear();
         this.smithingTrims.clear();
         this.brewingInputs.clear();
         this.brewingReagents.clear();
         this.multi.clear();
      }

      this.version++;
      packet.getShapedData().stream().filter(recipe -> recipe.getTag().equals("crafting_table")).forEach(this.shaped::add);
      packet.getShapelessData().stream().filter(recipe -> recipe.getTag().equals("crafting_table")).forEach(this.shapeless::add);
      packet.getShapelessData()
         .stream()
         .filter(recipe -> recipe.getTag().equals("stonecutter") && recipe.getIngredients().size() == 1 && !recipe.getResults().isEmpty())
         .forEach(this.stonecutter::add);
      this.smithingTransforms.addAll(packet.getSmithingTransformData());
      this.smithingTrims.addAll(packet.getSmithingTrimData());
      packet.getPotionMixData().forEach(mix -> {
         this.brewingInputs.add(mix.getInputId());
         this.brewingReagents.add(mix.getReagentId());
      });
      packet.getContainerMixData().forEach(mix -> {
         this.brewingInputs.add(mix.getInputId());
         this.brewingReagents.add(mix.getReagentId());
      });
      packet.getMultiData().forEach(recipe -> this.multi.put(recipe.getUuid(), recipe.getNetId()));
      List<CraftingRecipeData> custom = new ArrayList<>();
      packet.getShapedData().stream().filter(Crafting::serverDefined).forEach(custom::add);
      packet.getShapelessData().stream().filter(Crafting::serverDefined).forEach(custom::add);
      BedrockNative.LOGGER
         .info(
            "Bedrock crafting recipes: {} shaped ({} not mirrored), {} shapeless; {} of {} shaped and shapeless recipes name a server-defined item, {} of them on the crafting table, making {}",
            new Object[]{
               this.shaped.size(),
               this.shaped.stream().filter(recipe -> !recipe.isAssumeSymetry()).count(),
               this.shapeless.size(),
               custom.size(),
               packet.getShapedData().size() + packet.getShapelessData().size(),
               custom.stream().filter(recipe -> recipe.getTag().equals("crafting_table")).count(),
               custom.stream()
                  .limit(8L)
                  .map(recipe -> recipe.getResults().isEmpty() ? "nothing" : recipe.getResults().getFirst().getDefinition().getIdentifier())
                  .toList()
            }
         );
   }

   public static boolean serverDefined(CraftingRecipeData recipe) {
      return recipe.getIngredients().stream().anyMatch(ingredient -> serverDefined(ingredient.getDescriptor()))
         || recipe.getResults().stream().anyMatch(result -> !result.isNull() && serverDefined(result.getDefinition().getIdentifier()));
   }

   private static boolean serverDefined(ItemDescriptor descriptor) {
      return switch (descriptor) {
         case DefaultDescriptor exact -> exact.getItemId() != null && serverDefined(exact.getItemId().getIdentifier());
         case DeferredDescriptor named -> serverDefined(named.getFullName());
         case ItemTagDescriptor tag -> serverDefined(tag.getItemTag());
         default -> false;
      };
   }

   private static boolean serverDefined(String identifier) {
      return identifier != null && !identifier.isEmpty() && !identifier.startsWith("minecraft:");
   }

   public List<ShapedRecipeData> shaped() {
      return Collections.unmodifiableList(this.shaped);
   }

   public List<ShapelessRecipeData> shapeless() {
      return Collections.unmodifiableList(this.shapeless);
   }

   @Nullable
   static String javaTagName(String tag) {
      return !tag.equals("minecraft:trim_templates") && !tag.equals("minecraft:mushrooms_for_stew") ? TAG_ALIASES.getOrDefault(tag, tag) : null;
   }

   static List<String> namedTagItems(String tag) {
      if (tag.equals("minecraft:mushrooms_for_stew")) {
         return List.copyOf(STEW_MUSHROOM_ITEMS);
      } else {
         return tag.equals("minecraft:trim_templates")
            ? BuiltInRegistries.ITEM
               .keySet()
               .stream()
               .<String>map(Identifier::toString)
               .filter(id -> id.endsWith("_armor_trim_smithing_template"))
               .sorted()
               .toList()
            : List.of();
      }
   }

   public RecipeIndex index() {
      return this.index;
   }

   public int version() {
      return this.version;
   }

   public List<ShapelessRecipeData> stonecutter() {
      return Collections.unmodifiableList(this.stonecutter);
   }

   public Set<Integer> brewingInputs() {
      return Collections.unmodifiableSet(this.brewingInputs);
   }

   public Set<Integer> brewingReagents() {
      return Collections.unmodifiableSet(this.brewingReagents);
   }

   public List<SmithingTransformRecipeData> smithingTransforms() {
      return Collections.unmodifiableList(this.smithingTransforms);
   }

   public List<SmithingTrimRecipeData> smithingTrims() {
      return Collections.unmodifiableList(this.smithingTrims);
   }

   public int multiRecipe(UUID uuid) {
      return this.multi.getOrDefault(uuid, 0);
   }

   void putMultiRecipe(UUID uuid, int netId) {
      this.multi.put(uuid, netId);
   }

   public Crafting.Match match(List<ItemData> grid, int size) {
      int top = size;
      int left = size;
      int bottom = -1;
      int right = -1;
      List<ItemData> items = new ArrayList<>();

      for (int i = 0; i < grid.size(); i++) {
         if (!grid.get(i).isNull()) {
            items.add(grid.get(i));
            top = Math.min(top, i / size);
            bottom = Math.max(bottom, i / size);
            left = Math.min(left, i % size);
            right = Math.max(right, i % size);
         }
      }

      if (items.isEmpty()) {
         return null;
      } else {
         CraftingRecipeData best = null;

         for (ShapedRecipeData recipe : this.shaped) {
            if (recipe.getWidth() == right - left + 1
               && recipe.getHeight() == bottom - top + 1
               && better(recipe, best)
               && (this.fits(recipe, grid, size, top, left, false) || recipe.isAssumeSymetry() && this.fits(recipe, grid, size, top, left, true))) {
               best = recipe;
            }
         }

         for (ShapelessRecipeData recipex : this.shapeless) {
            if (better(recipex, best) && this.fitsShapeless(recipex, items)) {
               best = recipex;
            }
         }

         return best == null ? null : new Crafting.Match(best.getNetId(), best.getResults());
      }
   }

   private static boolean better(CraftingRecipeData recipe, CraftingRecipeData best) {
      return !recipe.getResults().isEmpty() && (best == null || recipe.getPriority() < best.getPriority());
   }

   private boolean fits(ShapedRecipeData recipe, List<ItemData> grid, int size, int top, int left, boolean mirrored) {
      int width = recipe.getWidth();

      for (int row = 0; row < recipe.getHeight(); row++) {
         for (int column = 0; column < width; column++) {
            ItemDescriptorWithCount ingredient = recipe.getIngredients().get(row * width + (mirrored ? width - 1 - column : column));
            if (!this.matches(ingredient.getDescriptor(), grid.get((top + row) * size + left + column))) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean fitsShapeless(ShapelessRecipeData recipe, List<ItemData> items) {
      List<ItemDescriptor> needed = new ArrayList<>();

      for (ItemDescriptorWithCount ingredient : recipe.getIngredients()) {
         for (int i = 0; i < Math.max(1, ingredient.getCount()); i++) {
            needed.add(ingredient.getDescriptor());
         }
      }

      return needed.size() == items.size() && this.assign(needed, items, 0, new boolean[items.size()]);
   }

   private boolean assign(List<ItemDescriptor> needed, List<ItemData> items, int next, boolean[] used) {
      if (next == needed.size()) {
         return true;
      } else {
         for (int i = 0; i < items.size(); i++) {
            if (!used[i] && this.matches(needed.get(next), items.get(i))) {
               used[i] = true;
               if (this.assign(needed, items, next + 1, used)) {
                  return true;
               }

               used[i] = false;
            }
         }

         return false;
      }
   }

   public boolean matches(ItemDescriptor descriptor, ItemData item) {
      return switch (descriptor) {
         case InvalidDescriptor ignored -> item.isNull();
         case DefaultDescriptor exact -> exact.getItemId() != null && exact.getItemId().getRuntimeId() != 0
            ? !item.isNull() && exact.getItemId().getRuntimeId() == item.getDefinition().getRuntimeId() && aux(exact.getAuxValue(), item)
            : item.isNull();
         case DeferredDescriptor named -> !item.isNull() && named.getFullName().equals(item.getDefinition().getIdentifier()) && aux(named.getAuxValue(), item);
         case ItemTagDescriptor tag -> !item.isNull() && this.inTag(tag.getItemTag(), item);
         default -> false;
      };
   }

   private static boolean aux(int aux, ItemData item) {
      return aux == 32767 || aux == -1 || aux == item.getDamage();
   }

   private boolean inTag(String tag, ItemData item) {
      CustomItem custom = ItemMappings.custom(item.getDefinition().getIdentifier());
      if (custom != null) {
         return custom.tags().contains(tag);
      } else if (tag.equals("minecraft:trim_templates")) {
         return item.getDefinition().getIdentifier().endsWith("_armor_trim_smithing_template");
      } else if (tag.equals("minecraft:mushrooms_for_stew")) {
         return STEW_MUSHROOM_ITEMS.contains(item.getDefinition().getIdentifier());
      } else {
         TagKey<Item> key = this.tags.computeIfAbsent(TAG_ALIASES.getOrDefault(tag, tag), name -> {
            Identifier id = Identifier.tryParse(name);
            return id == null ? null : TagKey.create(Registries.ITEM, id);
         });
         if (key == null) {
            return false;
         } else {
            ItemStack java = ItemMappings.toJava(item);
            return java.is(key);
         }
      }
   }

   public record Match(int netId, List<ItemData> results) {
      ItemData result() {
         return this.results.getFirst();
      }
   }
}
