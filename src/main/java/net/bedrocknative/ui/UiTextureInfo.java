package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

public record UiTextureInfo(String path, int width, int height, float baseWidth, float baseHeight, float @Nullable [] nineslice) {
   public static UiTextureInfo of(String path, int width, int height, @Nullable JsonObject sidecar) {
      float baseWidth = width;
      float baseHeight = height;
      float[] nineslice = null;
      if (sidecar != null) {
         if (sidecar.get("base_size") instanceof JsonArray base && base.size() >= 2 && number(base.get(0)) > 0.0 && number(base.get(1)) > 0.0) {
            baseWidth = (float)number(base.get(0));
            baseHeight = (float)number(base.get(1));
         }

         nineslice = slices(sidecar.get("nineslice_size"));
      }

      return new UiTextureInfo(path, width, height, baseWidth, baseHeight, nineslice);
   }

   public static float @Nullable [] slices(@Nullable JsonElement element) {
      if (element == null) {
         return null;
      }

      if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
         float all = element.getAsFloat();
         return new float[]{all, all, all, all};
      }

      if (element instanceof JsonArray array) {
         if (array.size() == 4) {
            return new float[]{(float)number(array.get(0)), (float)number(array.get(1)), (float)number(array.get(2)), (float)number(array.get(3))};
         }

         if (array.size() == 2) {
            float x = (float)number(array.get(0));
            float y = (float)number(array.get(1));
            return new float[]{x, y, x, y};
         }
      }

      return null;
   }

   private static double number(JsonElement element) {
      return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsDouble() : 0.0;
   }

   public float texelsPerUnitX() {
      return this.width / this.baseWidth;
   }

   public float texelsPerUnitY() {
      return this.height / this.baseHeight;
   }
}
