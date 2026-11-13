package net.bedrocknative.world.recipe;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.jspecify.annotations.Nullable;

public final class CraftPlanner {
   private CraftPlanner() {
   }

   public static CraftPlanner.Snapshot snapshot(List<ItemData> sources, ItemMatcher matcher) {
      return new CraftPlanner.Snapshot(sources, matcher);
   }

   public static int @Nullable [][] cells(BookRecipe recipe, int gridSize) {
      if (!recipe.fits(gridSize)) {
         return null;
      }

      List<BookRecipe.Unit> units = recipe.units();
      int[][] cells = new int[units.size()][];
      int next = 0;

      for (int u = 0; u < units.size(); u++) {
         BookRecipe.Unit unit = units.get(u);
         if (recipe.shaped()) {
            int width = Math.max(1, recipe.width());
            cells[u] = new int[]{unit.index() / width * gridSize + unit.index() % width};
         } else {
            cells[u] = new int[unit.perCraft()];

            for (int i = 0; i < unit.perCraft(); i++) {
               cells[u][i] = next++;
            }
         }
      }

      return cells;
   }

   public record Move(int source, int cell, int count) {
   }

   public static final class Snapshot {
      private final List<ItemData> sources;
      private final ItemMatcher matcher;
      private final Map<ItemDescriptor, BitSet> matches = new HashMap<>();

      private Snapshot(List<ItemData> sources, ItemMatcher matcher) {
         this.sources = List.copyOf(sources);
         this.matcher = matcher;
      }

      public List<ItemData> sources() {
         return this.sources;
      }

      private BitSet matching(ItemDescriptor descriptor) {
         BitSet found = this.matches.get(descriptor);
         if (found == null) {
            found = new BitSet(this.sources.size());

            for (int i = 0; i < this.sources.size(); i++) {
               ItemData item = this.sources.get(i);
               if (item != null && !item.isNull() && item.getCount() > 0 && this.matcher.matches(descriptor, item)) {
                  found.set(i);
               }
            }

            this.matches.put(descriptor, found);
         }

         return found;
      }

      public boolean hasAny(BookRecipe recipe) {
         for (BookRecipe.Unit unit : recipe.units()) {
            if (!this.matching(unit.ingredient().getDescriptor()).isEmpty()) {
               return true;
            }
         }

         return false;
      }

      public int maxTimes(BookRecipe recipe, int cap) {
         List<BookRecipe.Unit> units = recipe.units();
         if (!units.isEmpty() && cap > 0) {
            int bound = cap;

            for (BookRecipe.Unit unit : units) {
               long total = 0L;
               BitSet found = this.matching(unit.ingredient().getDescriptor());

               for (int i = found.nextSetBit(0); i >= 0; i = found.nextSetBit(i + 1)) {
                  total += this.sources.get(i).getCount();
               }

               bound = (int)Math.min(bound, total / unit.perCraft());
               if (bound == 0) {
                  return 0;
               }
            }

            int low = 0;
            int high = bound;

            while (low < high) {
               int mid = low + high + 1 >>> 1;
               if (this.allocate(units, mid) != null) {
                  low = mid;
               } else {
                  high = mid - 1;
               }
            }

            return low;
         } else {
            return 0;
         }
      }

      public @Nullable List<List<CraftPlanner.Take>> consume(BookRecipe recipe, int times) {
         return times <= 0 ? null : this.allocate(recipe.units(), times);
      }

      public @Nullable List<CraftPlanner.Move> fill(BookRecipe recipe, int times, int gridSize) {
         int[][] cells = CraftPlanner.cells(recipe, gridSize);
         List<List<CraftPlanner.Take>> takes = this.consume(recipe, times);
         if (cells != null && takes != null) {
            List<BookRecipe.Unit> units = recipe.units();
            List<CraftPlanner.Move> moves = new ArrayList<>();

            for (int u = 0; u < units.size(); u++) {
               int perCell = recipe.shaped() ? units.get(u).perCraft() * times : times;
               List<CraftPlanner.Take> left = new ArrayList<>(takes.get(u));

               for (int cell : cells[u]) {
                  int needed = perCell;

                  while (needed > 0 && !left.isEmpty()) {
                     CraftPlanner.Take take = left.getFirst();
                     int count = Math.min(needed, take.count());
                     moves.add(new CraftPlanner.Move(take.source(), cell, count));
                     needed -= count;
                     if (count == take.count()) {
                        left.removeFirst();
                     } else {
                        left.set(0, new CraftPlanner.Take(take.source(), take.count() - count));
                     }
                  }
               }
            }

            return moves;
         } else {
            return null;
         }
      }

      private @Nullable List<List<CraftPlanner.Take>> allocate(List<BookRecipe.Unit> units, int times) {
         int[] left = new int[this.sources.size()];

         for (int i = 0; i < left.length; i++) {
            ItemData item = this.sources.get(i);
            left[i] = item != null && !item.isNull() ? item.getCount() : 0;
         }

         List<Integer> order = new ArrayList<>(units.size());

         for (int u = 0; u < units.size(); u++) {
            order.add(u);
         }

         order.sort(Comparator.comparingInt(ux -> this.matching(units.get(ux).ingredient().getDescriptor()).cardinality()));
         List<List<CraftPlanner.Take>> takes = new ArrayList<>(units.size());

         for (int u = 0; u < units.size(); u++) {
            takes.add(null);
         }

         for (int u : order) {
            BookRecipe.Unit unit = units.get(u);
            long needed = (long)unit.perCraft() * times;
            List<CraftPlanner.Take> taken = new ArrayList<>(1);
            BitSet found = this.matching(unit.ingredient().getDescriptor());

            for (int i = found.nextSetBit(0); i >= 0 && needed > 0L; i = found.nextSetBit(i + 1)) {
               int count = (int)Math.min(needed, left[i]);
               if (count > 0) {
                  taken.add(new CraftPlanner.Take(i, count));
                  left[i] -= count;
                  needed -= count;
               }
            }

            if (needed > 0L) {
               return null;
            }

            takes.set(u, taken);
         }

         return takes;
      }
   }

   public record Take(int source, int count) {
   }
}
