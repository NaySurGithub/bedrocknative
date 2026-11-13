package net.bedrocknative.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.random.RandomGenerator;
import net.bedrocknative.model.ModelJson;
import org.jspecify.annotations.Nullable;

public final class SoundDefinitions {
   public static final String FILE = "sounds/sound_definitions.json";
   private final Map<String, SoundDefinitions.Definition> byName;

   private SoundDefinitions(Map<String, SoundDefinitions.Definition> byName) {
      this.byName = byName;
   }

   public static SoundDefinitions empty() {
      return new SoundDefinitions(Map.of());
   }

   public static SoundDefinitions of(List<SoundDefinitions.PackJson> bottomFirst) {
      Map<String, SoundDefinitions.Definition> merged = new HashMap<>();

      for (SoundDefinitions.PackJson pack : bottomFirst) {
         merge(merged, pack);
      }

      return new SoundDefinitions(Map.copyOf(merged));
   }

   static void merge(Map<String, SoundDefinitions.Definition> into, SoundDefinitions.PackJson pack) {
      JsonObject root = pack.json();
      JsonObject definitions = root.get("sound_definitions") instanceof JsonObject wrapped ? wrapped : root;

      for (Entry<String, JsonElement> entry : definitions.entrySet()) {
         if (entry.getValue() instanceof JsonObject json) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            into.put(name, parse(name, json, pack.pack(), pack.serverPack()));
         }
      }
   }

   static SoundDefinitions.Definition parse(String name, JsonObject json, int pack, boolean serverPack) {
      List<SoundDefinitions.Variant> variants = new ArrayList<>();
      JsonElement variant = json.get("sounds");
      if (variant instanceof JsonArray) {
         for (JsonElement sound : (JsonArray)variant) {
            SoundDefinitions.Variant variantx = variant(sound);
            if (variantx != null) {
               variants.add(variantx);
            }
         }
      } else if (json.get("sounds") instanceof JsonPrimitive single) {
         variants.add(new SoundDefinitions.Variant(single.getAsString(), 1.0F, 1.0F, 1, false, true, true, false));
      } else if (json.get("sounds") instanceof JsonObject single) {
         SoundDefinitions.Variant variantx = variant(single);
         if (variantx != null) {
            variants.add(variantx);
         }
      }

      String category = ModelJson.string(json, "category");
      JsonElement legacy = json.get("__use_legacy_max_distance");
      return new SoundDefinitions.Definition(
         name,
         category == null ? "" : category.toLowerCase(Locale.ROOT),
         distance(json.get("min_distance")),
         distance(json.get("max_distance")),
         legacy != null && ModelJson.bool(json, "__use_legacy_max_distance", false),
         ModelJson.number(json, "volume", 1.0F),
         ModelJson.number(json, "pitch", 1.0F),
         List.copyOf(variants),
         ModelJson.string(json, "subtitle"),
         pack,
         serverPack
      );
   }

   @Nullable
   private static SoundDefinitions.Variant variant(JsonElement element) {
      if (element instanceof JsonPrimitive primitive && primitive.isString()) {
         return primitive.getAsString().isBlank() ? null : new SoundDefinitions.Variant(primitive.getAsString(), 1.0F, 1.0F, 1, false, true, true, false);
      } else if (element instanceof JsonObject object) {
         String path = ModelJson.string(object, "name");
         return path != null && !path.isBlank()
            ? new SoundDefinitions.Variant(
               path,
               ModelJson.number(object, "volume", 1.0F),
               ModelJson.number(object, "pitch", 1.0F),
               Math.max(0, Math.round(ModelJson.number(object, "weight", 1.0F))),
               ModelJson.bool(object, "stream", false),
               ModelJson.bool(object, "is3D", true),
               ModelJson.bool(object, "interruptible", true),
               "event".equalsIgnoreCase(ModelJson.string(object, "type"))
            )
            : null;
      } else {
         return null;
      }
   }

   private static float distance(@Nullable JsonElement element) {
      return element != null && !element.isJsonNull() ? ModelJson.number(element, Float.NaN) : Float.NaN;
   }

   @Nullable
   public SoundDefinitions.Definition get(String name) {
      SoundDefinitions.Definition definition = this.byName.get(name);
      return definition != null ? definition : this.byName.get(name.toLowerCase(Locale.ROOT));
   }

   public int size() {
      return this.byName.size();
   }

   public Map<String, SoundDefinitions.Definition> all() {
      return this.byName;
   }

   public List<String> silentServerDefinitions() {
      return this.byName
         .values()
         .stream()
         .filter(definition -> definition.serverPack() && definition.variants().isEmpty())
         .map(SoundDefinitions.Definition::name)
         .sorted()
         .toList();
   }

   public record Definition(
      String name,
      String category,
      float minDistance,
      float maxDistance,
      boolean legacyMaxDistance,
      float volume,
      float pitch,
      List<SoundDefinitions.Variant> variants,
      @Nullable String subtitle,
      int pack,
      boolean serverPack
   ) {
      @Nullable
      public SoundDefinitions.Variant pick(RandomGenerator random) {
         if (this.variants.isEmpty()) {
            return null;
         } else {
            int total = 0;

            for (SoundDefinitions.Variant variant : this.variants) {
               total += variant.weight();
            }

            if (total <= 0) {
               return this.variants.get(random.nextInt(this.variants.size()));
            } else {
               int roll = random.nextInt(total);

               for (SoundDefinitions.Variant variant : this.variants) {
                  roll -= variant.weight();
                  if (roll < 0) {
                     return variant;
                  }
               }

               return this.variants.getLast();
            }
         }
      }
   }

   public record PackJson(JsonObject json, int pack, boolean serverPack) {
   }

   public record Variant(String path, float volume, float pitch, int weight, boolean stream, boolean is3d, boolean interruptible, boolean event) {
   }
}
