package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.bedrocknative.world.recipe.BookRecipe;
import net.bedrocknative.world.recipe.CraftPlanner;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ComplexAliasDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DeferredDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.MolangDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.AutoCraftRecipeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftResultsDeprecatedAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CreateAction;
import org.jspecify.annotations.Nullable;

final class AutoCraftRequest {
   static final ContainerSlotType PLAYER_SLOTS = ContainerSlotType.HOTBAR_AND_INVENTORY;
   private static final int ANY_AUX = 32767;

   private AutoCraftRequest() {
   }

   static List<ItemData> created(BookRecipe recipe) {
      return recipe.results().size() < 2 ? List.of(recipe.result()) : recipe.results().stream().filter(item -> !item.isNull()).toList();
   }

   static void append(
      StackRequests.Request request,
      BookRecipe recipe,
      int times,
      List<List<CraftPlanner.Take>> consumes,
      List<BedrockSlot> sources,
      List<List<AutoCraftRequest.Output>> outputs
   ) {
      request.actions.add(new AutoCraftRecipeAction(recipe.netId(), times, ingredients(request, recipe, consumes, sources), times));
      request.actions.add(new CraftResultsDeprecatedAction(recipe.results().toArray(new ItemData[0]), times));

      for (List<CraftPlanner.Take> unit : consumes) {
         for (CraftPlanner.Take take : unit) {
            request.consume(take.count(), sources.get(take.source()).named(PLAYER_SLOTS));
         }
      }

      List<ItemData> created = created(recipe);
      boolean autoPlace = request.autoPlace;

      for (int k = 0; k < created.size(); k++) {
         ItemData result = created.get(k);
         if (recipe.results().size() > 1) {
            request.actions.add(new CreateAction(k));
         }

         request.autoPlace = false;
         request.put(BedrockSlot.CREATED_OUTPUT, result.toBuilder().count(result.getCount() * times).usingNetId(true).netId(request.id).build());
         request.autoPlace = true;

         for (AutoCraftRequest.Output output : outputs.get(k)) {
            request.move(output.count(), BedrockSlot.CREATED_OUTPUT, output.slot());
         }
      }

      request.autoPlace = autoPlace;
   }

   static List<ItemDescriptorWithCount> ingredients(
      StackRequests.Request request, BookRecipe recipe, List<List<CraftPlanner.Take>> consumes, List<BedrockSlot> sources
   ) {
      List<ItemDescriptorWithCount> list = new ArrayList<>();
      List<BookRecipe.Unit> units = recipe.units();

      for (int i = 0; i < units.size(); i++) {
         BookRecipe.Unit unit = units.get(i);
         ItemDescriptor descriptor = wire(unit.ingredient().getDescriptor());
         if (descriptor != null) {
            if (i < consumes.size() && !consumes.get(i).isEmpty()) {
               ItemData item = request.get(sources.get(consumes.get(i).getFirst().source()));
               if (!item.isNull() && item.getDefinition() != null) {
                  descriptor = new DefaultDescriptor(item.getDefinition(), item.getDamage() >= 0 && item.getDamage() <= 32767 ? item.getDamage() : 0);
               }
            }

            list.add(new ItemDescriptorWithCount(descriptor, unit.perCraft()));
         }
      }

      return list;
   }

   @Nullable
   private static ItemDescriptor wire(ItemDescriptor descriptor) {
      Objects.requireNonNull(descriptor);

      return (ItemDescriptor)(switch (descriptor) {
         case DefaultDescriptor exact when exact.getItemId() == null || exact.getItemId().getIdentifier() == null -> null;
         case DefaultDescriptor exactx -> exactx.getAuxValue() >= 0 && exactx.getAuxValue() <= 32767
            ? exactx
            : new DefaultDescriptor(exactx.getItemId(), 32767);
         case DeferredDescriptor named -> new DefaultDescriptor(
            new SimpleItemDefinition(named.getFullName(), 0, false), named.getAuxValue() >= 0 && named.getAuxValue() <= 32767 ? named.getAuxValue() : 32767
         );
         case ComplexAliasDescriptor alias -> new DefaultDescriptor(new SimpleItemDefinition(alias.getName(), 0, false), 0);
         case ItemTagDescriptor tag -> tag;
         case MolangDescriptor molang -> molang;
         default -> null;
      });
   }

   record Output(BedrockSlot slot, int count) {
   }
}
