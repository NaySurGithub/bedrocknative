package net.bedrocknative.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record FogDefinition(String identifier, Map<FogDefinition.Medium, FogDefinition.Distance> distances) {
   private static float lerp(float from, float to, float progress) {
      return from + (to - from) * progress;
   }

   private static int lerpColor(int from, int to, float progress) {
      int rgb = 0;

      for (int shift = 16; shift >= 0; shift -= 8) {
         int a = from >> shift & 0xFF;
         int b = to >> shift & 0xFF;
         rgb |= Math.clamp(Math.round(lerp(a, b, progress)), 0, 255) << shift;
      }

      return rgb;
   }

   public static @Nullable FogDefinition parse(String json) {
      try {
         JsonElement root = JsonParser.parseString(json);
         if (root.isJsonObject() && root.getAsJsonObject().has("minecraft:fog_settings")) {
            JsonObject settings = root.getAsJsonObject().getAsJsonObject("minecraft:fog_settings");
            JsonObject description = settings.getAsJsonObject("description");
            if (description != null && description.has("identifier")) {
               String identifier = description.get("identifier").getAsString().toLowerCase(Locale.ROOT);
               Map<FogDefinition.Medium, FogDefinition.Distance> distances = new EnumMap<>(FogDefinition.Medium.class);
               JsonObject distance = settings.has("distance") && settings.get("distance").isJsonObject()
                  ? settings.getAsJsonObject("distance")
                  : new JsonObject();

               for (FogDefinition.Medium medium : FogDefinition.Medium.values()) {
                  if (distance.has(medium.key()) && distance.get(medium.key()).isJsonObject()) {
                     FogDefinition.Distance parsed = distance(distance.getAsJsonObject(medium.key()));
                     if (parsed != null) {
                        distances.put(medium, parsed);
                     }
                  }
               }

               return new FogDefinition(identifier, distances);
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static FogDefinition.@Nullable Distance distance(JsonObject object) {
      if (object.has("fog_start") && object.has("fog_end") && object.has("fog_color")) {
         try {
            float start = object.get("fog_start").getAsFloat();
            float end = object.get("fog_end").getAsFloat();
            Integer rgb = color(object.get("fog_color"));
            if (rgb != null && Float.isFinite(start) && Float.isFinite(end)) {
               boolean relative = object.has("render_distance_type") && "render".equalsIgnoreCase(object.get("render_distance_type").getAsString());
               return new FogDefinition.Distance(start, end, rgb, relative, transition(object.get("transition_fog")));
            } else {
               return null;
            }
         } catch (RuntimeException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static FogDefinition.@Nullable Transition transition(@Nullable JsonElement element) {
      if (element instanceof JsonObject object && object.get("init_fog") instanceof JsonObject initJson) {
         try {
            FogDefinition.Distance init = distance(initJson);
            if (init == null) {
               return null;
            }

            init = new FogDefinition.Distance(init.start(), init.end(), init.rgb(), init.relativeToRenderDistance());
            float minPercent = number(object, "min_percent", 0.0F);
            float midSeconds = number(object, "mid_seconds", 0.0F);
            float midPercent = number(object, "mid_percent", minPercent);
            float maxSeconds = number(object, "max_seconds", 0.0F);
            return maxSeconds > 0.0F && !(midSeconds < 0.0F) && !(midSeconds > maxSeconds) && Float.isFinite(minPercent) && Float.isFinite(midPercent)
               ? new FogDefinition.Transition(init, Math.clamp(minPercent, 0.0F, 1.0F), midSeconds, Math.clamp(midPercent, 0.0F, 1.0F), maxSeconds)
               : null;
         } catch (RuntimeException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static float number(JsonObject object, String key, float fallback) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : fallback;
   }

   static @Nullable Integer color(JsonElement element) {
      if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         String text = element.getAsString().trim();
         if (text.startsWith("#")) {
            text = text.substring(1);
         }

         if (text.length() == 3) {
            text = "" + text.charAt(0) + text.charAt(0) + text.charAt(1) + text.charAt(1) + text.charAt(2) + text.charAt(2);
         }

         if (text.length() == 8) {
            text = text.substring(2);
         }

         if (text.length() != 6) {
            return null;
         }

         try {
            return Integer.parseInt(text, 16);
         } catch (NumberFormatException e) {
            return null;
         }
      } else if (element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         if (array.size() < 3) {
            return null;
         }

         float[] channels = new float[3];
         boolean bytes = false;

         for (int i = 0; i < 3; i++) {
            channels[i] = array.get(i).getAsFloat();
            bytes |= channels[i] > 1.0F;
         }

         int rgb = 0;

         for (float channel : channels) {
            int value = Math.round(Math.clamp(bytes ? channel : channel * 255.0F, 0.0F, 255.0F));
            rgb = rgb << 8 | value;
         }

         return rgb;
      } else {
         return null;
      }
   }

   public record Distance(float start, float end, int rgb, boolean relativeToRenderDistance, FogDefinition.@Nullable Transition transition) {
      public Distance(float start, float end, int rgb, boolean relativeToRenderDistance) {
         this(start, end, rgb, relativeToRenderDistance, null);
      }

      public float startBlocks(float renderDistanceBlocks) {
         return this.relativeToRenderDistance ? this.start * renderDistanceBlocks : this.start;
      }

      public float endBlocks(float renderDistanceBlocks) {
         return this.relativeToRenderDistance ? this.end * renderDistanceBlocks : this.end;
      }

      public float[] at(float seconds, float renderDistanceBlocks) {
         float start = this.startBlocks(renderDistanceBlocks);
         float end = this.endBlocks(renderDistanceBlocks);
         if (this.transition == null) {
            return new float[]{start, end, this.rgb};
         }

         float progress = this.transition.progress(seconds);
         FogDefinition.Distance init = this.transition.init();
         return new float[]{
            FogDefinition.lerp(init.startBlocks(renderDistanceBlocks), start, progress),
            FogDefinition.lerp(init.endBlocks(renderDistanceBlocks), end, progress),
            FogDefinition.lerpColor(init.rgb(), this.rgb, progress)
         };
      }
   }

   public enum Medium {
      AIR("air"),
      WATER("water"),
      LAVA("lava"),
      LAVA_RESISTANCE("lava_resistance"),
      POWDER_SNOW("powder_snow"),
      WEATHER("weather");

      private final String key;

      Medium(String key) {
         this.key = key;
      }

      public String key() {
         return this.key;
      }
   }

   public record Transition(FogDefinition.Distance init, float minPercent, float midSeconds, float midPercent, float maxSeconds) {
      public float progress(float seconds) {
         if (!(seconds > 0.0F)) {
            return this.minPercent;
         } else if (seconds >= this.maxSeconds) {
            return 1.0F;
         } else if (seconds < this.midSeconds) {
            return FogDefinition.lerp(this.minPercent, this.midPercent, seconds / this.midSeconds);
         } else {
            return this.maxSeconds > this.midSeconds
               ? FogDefinition.lerp(this.midPercent, 1.0F, (seconds - this.midSeconds) / (this.maxSeconds - this.midSeconds))
               : 1.0F;
         }
      }
   }
}
