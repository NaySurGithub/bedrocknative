package net.bedrocknative.client.inventory;

import java.lang.runtime.SwitchBootstraps;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.recipe.BookRecipe;
import net.bedrocknative.world.recipe.RecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay.Composite;
import net.minecraft.world.item.crafting.display.SlotDisplay.Empty;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay.TagSlotDisplay;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DeferredDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.jspecify.annotations.Nullable;

final class JavaRecipeBookFeed {
   private static final int ANY_AUX = 32767;
   private static final int RESULT_GROUPS = 1048576;
   private final Minecraft minecraft;
   private final BedrockRecipeBook session;
   private int fed = -1;
   private int fedRecipes;

   JavaRecipeBookFeed(Minecraft minecraft, BedrockRecipeBook session) {
      this.minecraft = minecraft;
      this.session = session;
   }

   void reset() {
      this.fed = -1;
      this.fedRecipes = 0;
   }

   int fedRecipes() {
      return this.fedRecipes;
   }

   void tick() {
      if (this.minecraft.gui.screen() instanceof AbstractRecipeBookScreen) {
         RecipeBook book = this.session.book();
         int version = book.entriesVersion();
         if (version != this.fed) {
            this.fed = version;
            this.feed(book);
         }
      }
   }

   private void feed(RecipeBook book) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && this.minecraft.player != null) {
         List<Entry> entries = new ArrayList<>();
         int index = 0;
         int skipped = 0;

         for (RecipeBook.Entry entry : book.entries()) {
            OptionalInt group = OptionalInt.of(entry.place().named() ? entry.place().group() : 1048576 + index);
            RecipeBookCategory category = category(entry.place().category());

            for (BookRecipe recipe : entry.recipes()) {
               RecipeDisplayEntry display = entry(recipe, group, category);
               if (display != null) {
                  entries.add(new Entry(display, false, false));
               } else {
                  skipped++;
               }
            }

            index++;
         }

         this.fedRecipes = entries.size();
         connection.handleRecipeBookAdd(new ClientboundRecipeBookAddPacket(entries, true));
         BedrockNative.LOGGER.info("Recipe book: Java's book fed with {} recipes ({} it can't show)", entries.size(), skipped);
      }
   }

   private static RecipeBookCategory category(RecipeBook.Tab tab) {
      return switch (tab) {
         case CONSTRUCTION -> RecipeBookCategories.CRAFTING_BUILDING_BLOCKS;
         case EQUIPMENT -> RecipeBookCategories.CRAFTING_EQUIPMENT;
         default -> RecipeBookCategories.CRAFTING_MISC;
      };
   }

   @Nullable
   private static RecipeDisplayEntry entry(BookRecipe recipe, OptionalInt group, RecipeBookCategory category) {
      RecipeDisplay display = display(recipe);
      if (display == null) {
         return null;
      } else {
         List<Ingredient> requirements = new ArrayList<>();

         for (BookRecipe.Unit unit : recipe.units()) {
            Ingredient ingredient = ingredient(unit.ingredient());
            if (ingredient == null) {
               return null;
            }

            for (int i = 0; i < unit.perCraft(); i++) {
               requirements.add(ingredient);
            }
         }

         return new RecipeDisplayEntry(new RecipeDisplayId(recipe.netId()), display, group, category, Optional.of(requirements));
      }
   }

   @Nullable
   static RecipeDisplay display(BookRecipe recipe) {
      ItemStack result = ItemMappings.toJava(recipe.result());
      if (result.isEmpty()) {
         return null;
      } else {
         SlotDisplay shown = new ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(result));
         SlotDisplay station = new ItemSlotDisplay(Items.CRAFTING_TABLE.builtInRegistryHolder());
         List<SlotDisplay> cells = new ArrayList<>();
         if (recipe.shaped()) {
            if (recipe.ingredients().size() != recipe.width() * recipe.height()) {
               return null;
            } else {
               for (ItemDescriptorWithCount ingredient : recipe.ingredients()) {
                  SlotDisplay cell = (SlotDisplay)(BookRecipe.empty(ingredient) ? Empty.INSTANCE : slot(ingredient.getDescriptor()));
                  if (cell == null) {
                     return null;
                  }

                  cells.add(cell);
               }

               return new ShapedCraftingRecipeDisplay(recipe.width(), recipe.height(), cells, shown, station);
            }
         } else {
            for (BookRecipe.Unit unit : recipe.units()) {
               SlotDisplay cell = slot(unit.ingredient().getDescriptor());
               if (cell == null) {
                  return null;
               }

               for (int i = 0; i < unit.perCraft(); i++) {
                  cells.add(cell);
               }
            }

            return new ShapelessCraftingRecipeDisplay(cells, shown, station);
         }
      }
   }

   @Nullable
   private static SlotDisplay slot(ItemDescriptor descriptor) {
      if (!(descriptor instanceof ItemTagDescriptor tag)) {
         ItemStack stack = stack(descriptor);
         return stack.isEmpty() ? null : new ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
      } else {
         HolderSet<Item> javaTag = javaTag(tag.getItemTag());
         if (javaTag != null) {
            return new TagSlotDisplay(javaTag);
         } else {
            List<SlotDisplay> items = new ArrayList<>();

            for (ItemStack stack : tagged(tag.getItemTag())) {
               items.add(new ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack)));
            }

            return (SlotDisplay)(items.isEmpty() ? null : (items.size() == 1 ? items.getFirst() : new Composite(items)));
         }
      }
   }

   @Nullable
   private static Ingredient ingredient(ItemDescriptorWithCount ingredient) {
      ItemDescriptor descriptor = ingredient.getDescriptor();
      if (descriptor instanceof ItemTagDescriptor tag) {
         HolderSet<Item> javaTag = javaTag(tag.getItemTag());
         if (javaTag != null) {
            return Ingredient.of(javaTag);
         } else {
            List<ItemStack> stacks = tagged(tag.getItemTag());
            return stacks.isEmpty() ? null : Ingredient.of(stacks.stream().map(ItemStack::getItem).distinct());
         }
      } else {
         ItemStack stack = stack(descriptor);
         return stack.isEmpty() ? null : Ingredient.of(stack.getItem());
      }
   }

   private static ItemStack stack(ItemDescriptor descriptor) {
      Objects.requireNonNull(descriptor);
      ItemDescriptor var3 = descriptor;
      byte var4 = 0;

      while (true) {
         String identifier;
         int aux;
         switch (SwitchBootstraps.typeSwitch<"typeSwitch",DefaultDescriptor,DeferredDescriptor>(var3, var4)) {
            case 0:
               DefaultDescriptor exact = (DefaultDescriptor)var3;
               if (exact.getItemId() == null) {
                  var4 = 1;
                  continue;
               }

               identifier = exact.getItemId().getIdentifier();
               aux = exact.getAuxValue();
               break;
            case 1:
               DeferredDescriptor named = (DeferredDescriptor)var3;
               identifier = named.getFullName();
               aux = named.getAuxValue();
               break;
            default:
               return ItemStack.EMPTY;
         }

         if (identifier != null && !identifier.isEmpty() && !identifier.equals("minecraft:air")) {
            return ItemMappings.toJava(
               ItemData.builder()
                  .definition(new SimpleItemDefinition(identifier, 0, ItemMappings.custom(identifier) != null))
                  .damage(aux != 32767 && aux >= 0 ? aux : 0)
                  .count(1)
                  .build()
            );
         }

         return ItemStack.EMPTY;
      }
   }

   @Nullable
   private static HolderSet<Item> javaTag(String tag) {
      String name = Crafting.javaTagName(tag);
      Identifier id = name == null ? null : Identifier.tryParse(name);
      if (id == null) {
         return null;
      } else {
         Optional<Named<Item>> set = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id));
         return set.isPresent() && set.get().size() > 0 ? (HolderSet)set.get() : null;
      }
   }

   private static List<ItemStack> tagged(String tag) {
      List<ItemStack> stacks = new ArrayList<>();

      for (CustomItem custom : ItemMappings.customItems()) {
         if (custom.tags().contains(tag)) {
            ItemStack stack = ItemMappings.toJava(ItemData.builder().definition(new SimpleItemDefinition(custom.identifier(), 0, true)).count(1).build());
            if (!stack.isEmpty()) {
               stacks.add(stack);
            }
         }
      }

      for (String identifier : Crafting.namedTagItems(tag)) {
         ItemStack stack = ItemMappings.toJava(ItemData.builder().definition(new SimpleItemDefinition(identifier, 0, false)).count(1).build());
         if (!stack.isEmpty()) {
            stacks.add(stack);
         }
      }

      return stacks;
   }

   boolean place(ServerboundPlaceRecipePacket packet) {
      LocalPlayer player = this.minecraft.player;
      AbstractContainerMenu menu = player == null ? null : player.containerMenu;
      BookRecipe recipe = this.session.requests().crafting().index().byNetId(packet.recipe().index());
      if (menu != null && recipe != null && menu.containerId == packet.containerId()) {
         int gridSize = StackRequests.gridSize(menu);
         StackRequests requests = this.session.requests();
         int placed = gridSize == 0
            ? 0
            : RecipeBookCrafts.place(requests, recipe, gridSize, requests.crafting()::matches, BedrockRecipeBook::maxStack, packet.useMaxItems() ? 64 : 1);
         BedrockNative.LOGGER.info("Recipe book: Java's book placed {} (net id {}) x{}", new Object[]{recipe.id(), recipe.netId(), placed});
         if (placed > 0) {
            requests.inventory().showMenu(menu);
            return true;
         } else {
            RecipeDisplay display = display(recipe);
            ClientPacketListener connection = this.minecraft.getConnection();
            if (display != null && connection != null) {
               connection.handlePlaceRecipe(new ClientboundPlaceGhostRecipePacket(packet.containerId(), display));
            }

            return true;
         }
      } else {
         return recipe != null;
      }
   }
}
