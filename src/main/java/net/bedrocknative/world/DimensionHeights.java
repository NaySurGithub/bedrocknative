package net.bedrocknative.world;

import org.jspecify.annotations.Nullable;

public record DimensionHeights(int minY, int height) {
   public static final int MIN_Y = -2032;
   public static final int MAX_Y = 2031;
   public static final int STEP = 16;

   @Nullable
   public static DimensionHeights of(int minimum, int maximum) {
      if (maximum <= minimum) {
         return null;
      } else {
         long low = Math.floorDiv((long)minimum, 16L) * 16L;
         long top = Math.ceilDiv((long)maximum, 16L) * 16L;
         long limitTop = 2032L;
         if (low <= limitTop - 16L && top >= -2016L) {
            long minY = Math.max(low, -2032L);
            long end = Math.min(top, limitTop);
            return new DimensionHeights((int)minY, (int)(end - minY));
         } else {
            return null;
         }
      }
   }

   public int top() {
      return this.minY + this.height;
   }
}
