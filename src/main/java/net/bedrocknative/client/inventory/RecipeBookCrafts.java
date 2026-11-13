package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.recipe.BookRecipe;
import net.bedrocknative.world.recipe.CraftPlanner;
import net.bedrocknative.world.recipe.ItemMatcher;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

final class RecipeBookCrafts {
   static final int MAX_CRAFTS = 64;
   private static final List<BedrockSlot> PLAYER_SLOTS = buildPlayerSlots();

   private RecipeBookCrafts() {
   }

   static List<BedrockSlot> playerSlots() {
      return PLAYER_SLOTS;
   }

   private static List<BedrockSlot> buildPlayerSlots() {
      List<BedrockSlot> slots = new ArrayList<>(36);

      for (int slot = 0; slot < 36; slot++) {
         slots.add(BedrockSlot.player(slot));
      }

      return List.copyOf(slots);
   }

   static boolean place(StackRequests requests, BookRecipe recipe, int gridSize, ItemMatcher matcher, ToIntFunction<ItemData> maxStack) {
      return place(requests, recipe, gridSize, matcher, maxStack, 1) > 0;
   }

   static int place(StackRequests requests, BookRecipe recipe, int gridSize, ItemMatcher matcher, ToIntFunction<ItemData> maxStack, int wanted) {
      int[][] cells = CraftPlanner.cells(recipe, gridSize);
      if (cells != null && wanted > 0) {
         List<BedrockSlot> grid = StackRequests.gridSlots(gridSize);
         StackRequests.Request request = requests.new Request();
         request.autoPlace = true;
         int placed = placedSets(request, recipe, cells, grid, matcher);
         if (placed == 0 && !returnGrid(request, grid, maxStack)) {
            BedrockNative.LOGGER.info("Recipe book: no room in the inventory for the grid's items; recipe {} not placed", recipe.id());
            return 0;
         } else {
            List<ItemData> inGrid = grid.stream().map(request::get).filter(item -> !item.isNull()).toList();
            ItemMatcher fits = placed == 0
               ? matcher
               : (descriptor, item) -> matcher.matches(descriptor, item) && inGrid.stream().anyMatch(there -> StackRequests.sameKind(there, item));
            List<BedrockSlot> slots = playerSlots();
            List<ItemData> items = slots.stream().map(request::get).toList();
            CraftPlanner.Snapshot snapshot = CraftPlanner.snapshot(items, fits);

            for (int sets = snapshot.maxTimes(recipe, Math.min(wanted, 64)); sets > 0; sets--) {
               List<CraftPlanner.Move> moves = snapshot.fill(recipe, sets, gridSize);
               if (moves != null && fitsCells(request, moves, grid, items, maxStack)) {
                  for (CraftPlanner.Move move : moves) {
                     request.move(move.count(), slots.get(move.source()), grid.get(move.cell()));
                  }

                  request.submit();
                  return sets;
               }
            }

            return 0;
         }
      } else {
         return 0;
      }
   }

   private static boolean fitsCells(
      StackRequests.Request request, List<CraftPlanner.Move> moves, List<BedrockSlot> grid, List<ItemData> items, ToIntFunction<ItemData> maxStack
   ) {
      int[] added = new int[grid.size()];
      ItemData[] kind = new ItemData[grid.size()];

      for (CraftPlanner.Move move : moves) {
         ItemData there = request.get(grid.get(move.cell()));
         ItemData coming = items.get(move.source());
         ItemData held = kind[move.cell()] != null ? kind[move.cell()] : (there.isNull() ? null : there);
         if (held != null && !StackRequests.sameKind(held, coming)) {
            return false;
         }

         kind[move.cell()] = held != null ? held : coming;
         added[move.cell()] += move.count();
         if ((there.isNull() ? 0 : there.getCount()) + added[move.cell()] > Math.max(1, maxStack.applyAsInt(coming))) {
            return false;
         }
      }

      return true;
   }

   static int autoCraft(StackRequests requests, BookRecipe recipe, int gridSize, int wanted, ItemMatcher matcher, ToIntFunction<ItemData> maxStack) {
      if (recipe.fits(gridSize) && recipe.hasResult()) {
         List<BedrockSlot> grid = StackRequests.gridSlots(gridSize);
         if (grid.stream().anyMatch(slot -> !requests.inventory().get(slot).isNull())) {
            StackRequests.Request empty = requests.new Request();
            empty.autoPlace = true;
            if (!returnGrid(empty, grid, maxStack)) {
               BedrockNative.LOGGER.info("Recipe book: no room in the inventory for the grid's items; recipe {} not crafted", recipe.id());
               return 0;
            }

            empty.submit();
         }

         List<BedrockSlot> slots = playerSlots();
         List<ItemData> items = slots.stream().map(requests.inventory()::get).toList();
         CraftPlanner.Snapshot snapshot = CraftPlanner.snapshot(items, matcher);
         ItemData result = recipe.result();
         int perCraft = Math.max(1, result.getCount());
         int stack = Math.max(1, maxStack.applyAsInt(result) / perCraft);

         for (int times = snapshot.maxTimes(recipe, Math.min(Math.min(wanted, 64), stack)); times > 0; times--) {
            List<List<CraftPlanner.Take>> consumes = snapshot.consume(recipe, times);
            List<List<AutoCraftRequest.Output>> outputs = consumes == null ? null : outputs(items, consumes, AutoCraftRequest.created(recipe), times, maxStack);
            if (outputs != null) {
               StackRequests.Request request = requests.new Request();
               AutoCraftRequest.append(request, recipe, times, consumes, slots, outputs);
               request.submit();
               return times;
            }
         }

         return 0;
      } else {
         return 0;
      }
   }

   static List<List<AutoCraftRequest.Output>> outputs(
      List<ItemData> items, List<List<CraftPlanner.Take>> consumes, List<ItemData> results, int times, ToIntFunction<ItemData> maxStack
   ) {
      int[] counts = new int[items.size()];
      ItemData[] kinds = new ItemData[items.size()];

      for (int i = 0; i < counts.length; i++) {
         counts[i] = items.get(i).isNull() ? 0 : items.get(i).getCount();
         kinds[i] = items.get(i);
      }

      for (List<CraftPlanner.Take> unit : consumes) {
         for (CraftPlanner.Take take : unit) {
            counts[take.source()] -= take.count();
         }
      }

      List<List<AutoCraftRequest.Output>> all = new ArrayList<>();

      for (ItemData result : results) {
         int max = Math.max(1, maxStack.applyAsInt(result));
         List<AutoCraftRequest.Output> outputs = new ArrayList<>();
         int left = Math.max(1, result.getCount()) * times;

         for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = 0; i < counts.length && left > 0; i++) {
               boolean holds = counts[i] > 0 && StackRequests.sameKind(kinds[i], result);
               int room = pass == 0 ? (holds ? max - counts[i] : 0) : (counts[i] <= 0 ? max : 0);
               if (room > 0) {
                  int count = Math.min(room, left);
                  outputs.add(new AutoCraftRequest.Output(BedrockSlot.player(i), count));
                  if (counts[i] <= 0) {
                     kinds[i] = result;
                  }

                  counts[i] += count;
                  left -= count;
               }
            }
         }

         if (left > 0) {
            return null;
         }

         all.add(outputs);
      }

      return all;
   }

   private static int placedSets(StackRequests.Request request, BookRecipe recipe, int[][] cells, List<BedrockSlot> grid, ItemMatcher matcher) {
      List<BookRecipe.Unit> units = recipe.units();
      boolean[] used = new boolean[grid.size()];
      int sets = -1;

      for (int u = 0; u < units.size(); u++) {
         BookRecipe.Unit unit = units.get(u);
         int perCraft = recipe.shaped() ? unit.perCraft() : 1;

         for (int cell : cells[u]) {
            ItemData item = request.get(grid.get(cell));
            if (item.isNull() || !matcher.matches(unit.ingredient().getDescriptor(), item) || item.getCount() % perCraft != 0) {
               return 0;
            }

            int crafts = item.getCount() / perCraft;
            if (sets >= 0 && crafts != sets) {
               return 0;
            }

            sets = crafts;
            used[cell] = true;
         }
      }

      for (int cell = 0; cell < grid.size(); cell++) {
         if (!used[cell] && !request.get(grid.get(cell)).isNull()) {
            return 0;
         }
      }

      return Math.max(0, sets);
   }

   private static boolean returnGrid(StackRequests.Request request, List<BedrockSlot> grid, ToIntFunction<ItemData> maxStack) {
      for (BedrockSlot cell : grid) {
         ItemData item = request.get(cell);
         int left = item.isNull() ? 0 : item.getCount();
         int max = left == 0 ? 0 : Math.max(1, maxStack.applyAsInt(item));

         for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot = 0; slot < 36 && left > 0; slot++) {
               BedrockSlot target = BedrockSlot.player(slot);
               ItemData there = request.get(target);
               int room = pass == 0 ? (!there.isNull() && StackRequests.sameKind(there, item) ? max - there.getCount() : 0) : (there.isNull() ? max : 0);
               if (room > 0) {
                  int count = Math.min(room, left);
                  request.move(count, cell, target);
                  left -= count;
               }
            }
         }

         if (left > 0) {
            return false;
         }
      }

      return true;
   }
}
