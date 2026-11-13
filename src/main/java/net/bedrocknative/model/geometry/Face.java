package net.bedrocknative.model.geometry;

import org.jspecify.annotations.Nullable;

public enum Face {
   UP("up", 0.0F, 1.0F, 0.0F, new int[][]{{1, 1, 1}, {0, 1, 1}, {0, 1, 0}, {1, 1, 0}}),
   DOWN("down", 0.0F, -1.0F, 0.0F, new int[][]{{1, 0, 1}, {0, 0, 1}, {0, 0, 0}, {1, 0, 0}}),
   NORTH("north", 0.0F, 0.0F, -1.0F, new int[][]{{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}}),
   SOUTH("south", 0.0F, 0.0F, 1.0F, new int[][]{{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}}),
   EAST("east", -1.0F, 0.0F, 0.0F, new int[][]{{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}}),
   WEST("west", 1.0F, 0.0F, 0.0F, new int[][]{{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}});

   public static final int TOP_LEFT = 0;
   public static final int TOP_RIGHT = 1;
   public static final int BOTTOM_RIGHT = 2;
   public static final int BOTTOM_LEFT = 3;
   private static final int[] WINDING = new int[]{1, 0, 3, 2};
   private static final int[] WINDING_DOWN = new int[]{2, 3, 0, 1};
   private final String key;
   private final float[] normal;
   private final int[][] corners;

   Face(String key, float nx, float ny, float nz, int[][] corners) {
      this.key = key;
      this.normal = new float[]{nx, ny, nz};
      this.corners = corners;
   }

   public String key() {
      return this.key;
   }

   public float normal(int axis) {
      return this.normal[axis];
   }

   public int[] winding() {
      return this == DOWN ? WINDING_DOWN : WINDING;
   }

   public int corner(int corner, int axis) {
      return this.corners[corner][axis];
   }

   public static @Nullable Face byKey(String key) {
      for (Face face : values()) {
         if (face.key.equalsIgnoreCase(key)) {
            return face;
         }
      }

      return null;
   }
}
