package net.bedrocknative.world.block.custom;

import java.util.Arrays;
import java.util.List;
import net.bedrocknative.content.BedrockTextureSet;

public final class SlotPlan {
   public static final int[] SIZES = new int[]{16, 32, 64, 128, 256, 512, 1024};
   public static final int[] BASE = new int[]{768, 192, 48, 12, 4, 0, 0};

   private SlotPlan() {
   }

   public static int sizeClass(int width, int height) {
      int needed = Math.max(width, height);

      for (int i = 0; i < SIZES.length; i++) {
         if (SIZES[i] >= needed) {
            return i;
         }
      }

      return SIZES.length - 1;
   }

   public static long cost(int[] counts, int padding, int granularity) {
      long cost = 0L;

      for (int i = 0; i < SIZES.length; i++) {
         cost += counts[i] * BedrockTextureSet.cost(SIZES[i], SIZES[i], padding, granularity);
      }

      return cost;
   }

   public static long budget(int maxTextureSide) {
      return (long)maxTextureSide * maxTextureSide / 4L;
   }

   public static SlotPlan.Plan plan(List<int[]> sizes, long budget, int padding, int granularity) {
      long limit = Math.max(budget, cost(BASE, padding, granularity));
      int[] classes = new int[sizes.size()];
      int[] needed = new int[SIZES.length];

      for (int i = 0; i < classes.length; i++) {
         classes[i] = sizeClass(sizes.get(i)[0], sizes.get(i)[1]);
         needed[classes[i]]++;
      }

      while (cost(reserved(needed), padding, granularity) > limit) {
         int from = -1;

         for (int i = SIZES.length - 1; i > 0; i--) {
            if (needed[i] > BASE[i]) {
               from = i;
               break;
            }
         }

         if (from < 0) {
            break;
         }

         int ix = classes.length - 1;

         while (true) {
            if (ix >= 0) {
               if (classes[ix] != from) {
                  ix--;
                  continue;
               }

               classes[ix]--;
            }

            needed[from]--;
            needed[from - 1]++;
            break;
         }
      }

      int[] counts = reserved(needed);
      long over = cost(counts, padding, granularity) - limit;
      int unplaced = 0;
      if (over > 0L) {
         long perSlot = BedrockTextureSet.cost(SIZES[0], SIZES[0], padding, granularity);
         unplaced = (int)Math.min((long)(counts[0] - BASE[0]), (over + perSlot - 1L) / perSlot);
         counts[0] -= unplaced;
         int left = unplaced;

         for (int ix = classes.length - 1; ix >= 0 && left > 0; ix--) {
            if (classes[ix] == 0) {
               classes[ix] = -1;
               left--;
            }
         }
      }

      return new SlotPlan.Plan(counts, classes, unplaced);
   }

   private static int[] reserved(int[] needed) {
      int[] counts = new int[SIZES.length];

      for (int i = 0; i < SIZES.length; i++) {
         counts[i] = Math.max(BASE[i], needed[i]);
      }

      return counts;
   }

   public static boolean exceeds(int[] wanted, int[] have) {
      for (int i = 0; i < SIZES.length; i++) {
         if (wanted[i] > (i < have.length ? have[i] : 0)) {
            return true;
         }
      }

      return false;
   }

   public static int[] max(int[] a, int[] b) {
      int[] out = Arrays.copyOf(a, SIZES.length);

      for (int i = 0; i < SIZES.length && i < b.length; i++) {
         out[i] = Math.max(out[i], b[i]);
      }

      return out;
   }

   public record Plan(int[] counts, int[] classes, int unplaced) {
      public Plan(int[] counts, int[] classes, int unplaced) {
         counts = (int[])counts.clone();
         classes = (int[])classes.clone();
         this.counts = counts;
         this.classes = classes;
         this.unplaced = unplaced;
      }

      public int count(int sizeClass) {
         return this.counts[sizeClass];
      }

      public int[] counts() {
         return (int[])this.counts.clone();
      }

      @Override
      public String toString() {
         StringBuilder out = new StringBuilder("{");

         for (int i = 0; i < SlotPlan.SIZES.length; i++) {
            if (this.counts[i] > 0) {
               out.append(out.length() > 1 ? ", " : "").append(SlotPlan.SIZES[i]).append(": ").append(this.counts[i]);
            }
         }

         return out.append('}').toString();
      }
   }
}
