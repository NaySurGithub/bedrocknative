package net.bedrocknative.model.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class RenderControllerParser {
   private final MolangCache molang;
   private final MolangVersion version;

   public RenderControllerParser(MolangCache molang, MolangVersion version) {
      this.molang = molang;
      this.version = version;
   }

   public Map<String, RenderControllerDef> parse(JsonObject file) {
      Map<String, RenderControllerDef> controllers = new LinkedHashMap<>();
      JsonObject root = ModelJson.object(file, "render_controllers");
      if (root == null) {
         return controllers;
      } else {
         for (Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getValue() instanceof JsonObject json) {
               controllers.put(entry.getKey(), this.controller(entry.getKey(), json));
            }
         }

         return controllers;
      }
   }

   public RenderControllerDef controller(String name, JsonObject json) {
      return new RenderControllerDef(
         name,
         this.arrays(ModelJson.object(json, "arrays")),
         this.program(json.get("geometry"), "Geometry.default"),
         this.patterns(json.get("materials")),
         this.textures(json.get("textures")),
         this.patterns(json.get("part_visibility")),
         this.color(ModelJson.object(json, "color"), "1"),
         this.color(ModelJson.object(json, "overlay_color"), "0"),
         this.color(ModelJson.object(json, "is_hurt_color"), "0"),
         this.color(ModelJson.object(json, "on_fire_color"), "0"),
         this.uvAnimation(ModelJson.object(json, "uv_anim")),
         this.optional(json.get("ignore_lighting")),
         this.optional(json.get("light_color_multiplier")),
         ModelJson.bool(json, "filter_lighting", false),
         ModelJson.bool(json, "rebuild_animation_matrices", false)
      );
   }

   private Map<String, Object[]> arrays(@Nullable JsonObject json) {
      Map<String, List<String>> raw = new HashMap<>();
      if (json != null) {
         for (JsonElement kind : json.asMap().values()) {
            if (kind instanceof JsonObject arrays) {
               for (Entry<String, JsonElement> entry : arrays.entrySet()) {
                  List<String> elements = new ArrayList<>();
                  if (entry.getValue() instanceof JsonArray array) {
                     array.forEach(element -> {
                        if (element instanceof JsonPrimitive primitive) {
                           elements.add(primitive.getAsString());
                        }
                     });
                  }

                  raw.put(arrayName(entry.getKey()), elements);
               }
            }
         }
      }

      Map<String, Object[]> arrays = new HashMap<>();
      MolangEnv env = new MolangEnv();

      for (String name : raw.keySet()) {
         List<Object> values = new ArrayList<>();
         this.flatten(name, raw, values, new HashSet<>(), env);
         arrays.put(name, values.toArray());
      }

      return arrays;
   }

   private void flatten(String name, Map<String, List<String>> raw, List<Object> values, Set<String> visiting, MolangEnv env) {
      if (visiting.add(name)) {
         for (String element : raw.getOrDefault(name, List.of())) {
            String lower = element.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("array.") && raw.containsKey(arrayName(lower))) {
               this.flatten(arrayName(lower), raw, values, visiting, env);
            } else {
               Object value = this.molang.compile(element, this.version).evaluateObject(env);
               if (value != null) {
                  values.add(value);
               }
            }
         }

         visiting.remove(name);
      }
   }

   private static String arrayName(String name) {
      String lower = name.trim().toLowerCase(Locale.ROOT);
      return lower.startsWith("array.") ? lower.substring("array.".length()) : lower;
   }

   private List<RenderControllerDef.PatternValue> patterns(@Nullable JsonElement json) {
      List<RenderControllerDef.PatternValue> patterns = new ArrayList<>();
      if (json instanceof JsonArray array) {
         for (JsonElement element : array) {
            if (element instanceof JsonObject object) {
               for (Entry<String, JsonElement> entry : object.entrySet()) {
                  patterns.add(new RenderControllerDef.PatternValue(entry.getKey(), this.program(entry.getValue(), "0")));
               }
            }
         }
      }

      return List.copyOf(patterns);
   }

   private List<MolangProgram> textures(@Nullable JsonElement json) {
      List<MolangProgram> textures = new ArrayList<>();
      if (json instanceof JsonArray array) {
         array.forEach(element -> textures.add(this.program(element, "0")));
      } else if (json != null) {
         textures.add(this.program(json, "0"));
      }

      return List.copyOf(textures);
   }

   @Nullable
   private RenderControllerDef.Color color(@Nullable JsonObject json, String fill) {
      return json == null
         ? null
         : new RenderControllerDef.Color(
            this.program(json.get("r"), fill), this.program(json.get("g"), fill), this.program(json.get("b"), fill), this.program(json.get("a"), fill)
         );
   }

   @Nullable
   private RenderControllerDef.UvAnimation uvAnimation(@Nullable JsonObject json) {
      if (json == null) {
         return null;
      } else {
         JsonArray offset = ModelJson.array(json, "offset");
         JsonArray scale = ModelJson.array(json, "scale");
         return new RenderControllerDef.UvAnimation(
            this.element(offset, 0, "0"), this.element(offset, 1, "0"), this.element(scale, 0, "1"), this.element(scale, 1, "1")
         );
      }
   }

   private MolangProgram element(@Nullable JsonArray array, int index, String fallback) {
      return this.program(array != null && array.size() > index ? array.get(index) : null, fallback);
   }

   @Nullable
   private MolangProgram optional(@Nullable JsonElement json) {
      return json instanceof JsonPrimitive ? this.program(json, "0") : null;
   }

   private MolangProgram program(@Nullable JsonElement json, String fallback) {
      if (json instanceof JsonPrimitive primitive) {
         return primitive.isBoolean()
            ? this.molang.compile(primitive.getAsBoolean() ? "1" : "0", this.version)
            : this.molang.compile(primitive.isNumber() ? Float.toString(primitive.getAsFloat()) : primitive.getAsString(), this.version);
      } else {
         return this.molang.compile(fallback, this.version);
      }
   }
}
