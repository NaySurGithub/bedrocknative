package net.bedrocknative.world.chunk;

import org.jspecify.annotations.Nullable;

public record DimensionRange(int firstSection, int sectionCount) {
   static int[] builtinHeights(int dimension) {
      return switch (dimension) {
         case 1 -> new int[]{0, 128};
         case 2 -> new int[]{0, 256};
         default -> new int[]{-64, 320};
      };
   }

   public static DimensionRange of(int dimension, @Nullable Integer definedMinimum, @Nullable Integer definedMaximum, int javaMinY, int javaHeight) {
      int minimum;
      int maximum;
      if (definedMinimum != null && definedMaximum != null) {
         minimum = definedMinimum;
         maximum = definedMaximum;
      } else {
         int[] builtin = builtinHeights(dimension);
         minimum = builtin[0];
         maximum = builtin[1];
      }

      int javaFirst = Math.floorDiv(javaMinY, 16);
      int javaEnd = javaFirst + Math.ceilDiv(javaHeight, 16);
      int first = Math.max(Math.floorDiv(minimum, 16), javaFirst);
      int end = Math.min(Math.ceilDiv(maximum, 16), javaEnd);
      return end <= first ? new DimensionRange(javaFirst, javaEnd - javaFirst) : new DimensionRange(first, end - first);
   }
}
