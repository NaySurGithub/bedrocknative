package net.bedrocknative.model.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class DefinitionParser {
   private static final int LOWER_CASE_SCRIPTS_BEFORE = MolangVersion.version(1, 18, 20);
   private final MolangCache molang;
   private final MolangVersion version;

   public DefinitionParser(MolangCache molang, MolangVersion version) {
      this.molang = molang;
      this.version = version;
   }

   @Nullable
   public ClientEntityDef parse(JsonObject file, int pack, boolean serverPack) {
      int format = MolangVersion.parse(ModelJson.string(file, "format_version"));
      boolean attachable = false;
      JsonObject root = ModelJson.object(file, "minecraft:client_entity");
      if (root == null) {
         root = ModelJson.object(file, "minecraft:attachable");
         attachable = root != null;
      }

      JsonObject description = ModelJson.object(root, "description");
      String identifier = ModelJson.string(description, "identifier");
      if (description != null && identifier != null) {
         Map<String, String> animations = strings(ModelJson.object(description, "animations"));
         Map<String, String> legacy = new LinkedHashMap<>();
         JsonArray legacyControllers = ModelJson.array(description, "animation_controllers");
         if (legacyControllers != null) {
            for (JsonElement element : legacyControllers) {
               if (element instanceof JsonObject controller) {
                  legacy.putAll(strings(controller));
               }
            }
         }

         List<ClientEntityDef.RenderControllerRef> renderControllers = new ArrayList<>();
         JsonArray controllers = ModelJson.array(description, "render_controllers");
         if (controllers != null) {
            for (JsonElement elementx : controllers) {
               if (elementx instanceof JsonPrimitive name) {
                  renderControllers.add(new ClientEntityDef.RenderControllerRef(name.getAsString(), null));
               } else if (elementx instanceof JsonObject conditional) {
                  conditional.entrySet()
                     .forEach(
                        entryx -> renderControllers.add(
                           new ClientEntityDef.RenderControllerRef((String)entryx.getKey(), this.program((JsonElement)entryx.getValue(), false))
                        )
                     );
               }
            }
         }

         Map<String, MolangProgram> items = null;
         JsonObject itemJson = ModelJson.object(description, "item");
         if (itemJson != null) {
            items = new LinkedHashMap<>();

            for (Entry<String, JsonElement> entry : itemJson.entrySet()) {
               String condition = entry.getValue() instanceof JsonPrimitive primitive ? primitive.getAsString() : "";
               items.put(entry.getKey(), this.molang.compile(condition.isBlank() ? "1" : condition, this.version));
            }
         }

         return new ClientEntityDef(
            identifier,
            attachable,
            MolangVersion.parse(ModelJson.string(description, "min_engine_version")),
            format,
            strings(ModelJson.object(description, "materials")),
            strings(ModelJson.object(description, "textures")),
            strings(ModelJson.object(description, "geometry")),
            animations,
            legacy,
            this.scripts(ModelJson.object(description, "scripts"), format < LOWER_CASE_SCRIPTS_BEFORE),
            List.copyOf(renderControllers),
            ModelJson.bool(description, "enable_attachables", false),
            ModelJson.bool(description, "hide_armor", false),
            ModelJson.string(description, "queryable_geometry"),
            items,
            pack,
            serverPack,
            strings(ModelJson.object(description, "particle_effects")),
            strings(ModelJson.object(description, "sound_effects"))
         );
      } else {
         return null;
      }
   }

   private ClientEntityDef.Scripts scripts(@Nullable JsonObject json, boolean lowerCase) {
      List<ClientEntityDef.AnimateEntry> animate = new ArrayList<>();
      JsonArray animateArray = member(json, "animate") instanceof JsonArray array ? array : null;
      if (animateArray != null) {
         for (JsonElement element : animateArray) {
            if (element instanceof JsonPrimitive name) {
               animate.add(new ClientEntityDef.AnimateEntry(name.getAsString(), null));
            } else if (element instanceof JsonObject weighted) {
               weighted.entrySet()
                  .forEach(
                     entryx -> animate.add(new ClientEntityDef.AnimateEntry((String)entryx.getKey(), this.program((JsonElement)entryx.getValue(), false)))
                  );
            }
         }
      }

      Set<String> publicVariables = new HashSet<>();
      JsonObject variables = member(json, "variables") instanceof JsonObject object ? object : null;
      if (variables != null) {
         for (Entry<String, JsonElement> entry : variables.entrySet()) {
            if (entry.getValue() instanceof JsonPrimitive access && access.getAsString().equalsIgnoreCase("public")) {
               String name = entry.getKey().toLowerCase(Locale.ROOT);
               publicVariables.add(name.startsWith("variable.") ? name.substring(9) : (name.startsWith("v.") ? name.substring(2) : name));
            }
         }
      }

      return new ClientEntityDef.Scripts(
         this.joined(json, "initialize", lowerCase),
         this.joined(json, "pre_animation", lowerCase),
         List.copyOf(animate),
         this.optional(json, "scale"),
         this.optional(json, "scaleX"),
         this.optional(json, "scaleY"),
         this.optional(json, "scaleZ"),
         Set.copyOf(publicVariables),
         this.joined(json, "parent_setup", false),
         this.optional(json, "should_update_bones_and_effects_offscreen"),
         this.optional(json, "should_update_effects_offscreen"),
         this.optional(json, "hide_held_items")
      );
   }

   @Nullable
   private MolangProgram joined(@Nullable JsonObject json, String key, boolean lowerCase) {
      JsonElement element = member(json, key);
      List<String> lines = new ArrayList<>();
      if (element instanceof JsonPrimitive primitive) {
         lines.add(primitive.getAsString());
      } else if (element instanceof JsonArray array) {
         array.forEach(line -> {
            if (line instanceof JsonPrimitive primitivex) {
               lines.add(primitivex.getAsString());
            }
         });
      }

      return lines.isEmpty() ? null : this.molang.script(lines, this.version, lowerCase);
   }

   @Nullable
   private MolangProgram optional(@Nullable JsonObject json, String key) {
      return member(json, key) instanceof JsonPrimitive primitive ? this.program(primitive, false) : null;
   }

   @Nullable
   static JsonElement member(@Nullable JsonObject json, String key) {
      if (json == null) {
         return null;
      } else {
         JsonElement exact = json.get(key);
         if (exact != null) {
            return exact;
         } else {
            for (Entry<String, JsonElement> entry : json.entrySet()) {
               if (entry.getKey().equalsIgnoreCase(key)) {
                  return entry.getValue();
               }
            }

            return null;
         }
      }
   }

   private MolangProgram program(JsonElement json, boolean lowerCase) {
      if (json instanceof JsonPrimitive primitive) {
         return primitive.isBoolean()
            ? this.molang.compile(primitive.getAsBoolean() ? "1" : "0", this.version)
            : this.molang.compile(primitive.isNumber() ? Float.toString(primitive.getAsFloat()) : primitive.getAsString(), this.version, lowerCase);
      } else {
         return this.molang.compile("1", this.version);
      }
   }

   private static Map<String, String> strings(@Nullable JsonObject json) {
      Map<String, String> map = new LinkedHashMap<>();
      if (json != null) {
         for (Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue() instanceof JsonPrimitive primitive) {
               map.put(entry.getKey(), primitive.getAsString());
            }
         }
      }

      return map;
   }
}
