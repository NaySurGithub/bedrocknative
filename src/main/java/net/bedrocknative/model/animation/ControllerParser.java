package net.bedrocknative.model.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class ControllerParser {
   private static final int LOWER_CASE_BEFORE = MolangVersion.version(1, 17, 30);
   private final MolangCache molang;
   private final MolangVersion version;

   public ControllerParser(MolangCache molang, MolangVersion version) {
      this.molang = molang;
      this.version = version;
   }

   public Map<String, ControllerDef> parse(JsonObject file) {
      Map<String, ControllerDef> controllers = new LinkedHashMap<>();
      int format = MolangVersion.parse(ModelJson.string(file, "format_version"));
      JsonObject root = ModelJson.object(file, "animation_controllers");
      if (root == null) {
         return controllers;
      }

      for (Entry<String, JsonElement> entry : root.entrySet()) {
         if (entry.getValue() instanceof JsonObject json) {
            controllers.put(entry.getKey(), this.controller(entry.getKey(), json, format));
         }
      }

      return controllers;
   }

   public ControllerDef controller(String name, JsonObject json, int format) {
      boolean lowerCase = format < LOWER_CASE_BEFORE;
      Map<String, ControllerDef.State> states = new LinkedHashMap<>();
      JsonObject statesJson = ModelJson.object(json, "states");
      if (statesJson != null) {
         for (Entry<String, JsonElement> entry : statesJson.entrySet()) {
            if (entry.getValue() instanceof JsonObject state) {
               states.put(entry.getKey(), this.state(entry.getKey(), state, lowerCase));
            }
         }
      }

      String initial = ModelJson.string(json, "initial_state");
      if (initial == null || !states.containsKey(initial)) {
         initial = !states.containsKey("default") && !states.isEmpty() ? states.keySet().iterator().next() : "default";
      }

      return new ControllerDef(name, initial, states, format);
   }

   private ControllerDef.State state(String name, JsonObject json, boolean lowerCase) {
      List<ControllerDef.StateAnimation> animations = new ArrayList<>();
      JsonArray animationArray = ModelJson.array(json, "animations");
      if (animationArray != null) {
         for (JsonElement element : animationArray) {
            if (element instanceof JsonPrimitive primitive) {
               animations.add(new ControllerDef.StateAnimation(primitive.getAsString(), null));
            } else if (element instanceof JsonObject object) {
               for (Entry<String, JsonElement> entry : object.entrySet()) {
                  animations.add(new ControllerDef.StateAnimation(entry.getKey(), this.program(entry.getValue(), false)));
               }
            }
         }
      }

      List<ControllerDef.Transition> transitions = new ArrayList<>();
      JsonArray transitionArray = ModelJson.array(json, "transitions");
      if (transitionArray != null) {
         for (JsonElement element : transitionArray) {
            if (element instanceof JsonObject object) {
               for (Entry<String, JsonElement> entry : object.entrySet()) {
                  transitions.add(new ControllerDef.Transition(entry.getKey(), this.program(entry.getValue(), lowerCase)));
               }
            }
         }
      }

      float blend = 0.0F;
      float[] curve = null;
      JsonElement blendJson = json.get("blend_transition");
      if (blendJson instanceof JsonObject object) {
         curve = curve(object);
         blend = curve.length > 0 ? curve[curve.length - 2] : 0.0F;
      } else if (blendJson != null) {
         blend = Math.max(0.0F, ModelJson.number(blendJson, 0.0F));
      }

      List<StateVariable> variables = new ArrayList<>();
      JsonObject variablesJson = ModelJson.object(json, "variables");
      if (variablesJson != null) {
         for (Entry<String, JsonElement> entry : variablesJson.entrySet()) {
            if (entry.getValue() instanceof JsonObject variable) {
               float[] remap = curve(ModelJson.object(variable, "remap_curve"));
               float[] keys = new float[remap.length / 2];
               float[] values = new float[remap.length / 2];

               for (int i = 0; i < keys.length; i++) {
                  keys[i] = remap[i * 2];
                  values[i] = remap[i * 2 + 1];
               }

               String variableName = entry.getKey().startsWith("variable.")
                  ? entry.getKey().substring("variable.".length())
                  : (entry.getKey().startsWith("v.") ? entry.getKey().substring(2) : entry.getKey());
               variables.add(new StateVariable(variableName, this.program(variable.get("input"), false), keys, values));
            }
         }
      }

      return new ControllerDef.State(
         name,
         List.copyOf(animations),
         List.copyOf(transitions),
         blend,
         curve,
         ModelJson.bool(json, "blend_via_shortest_path", false),
         this.scripts(json.get("on_entry")),
         this.scripts(json.get("on_exit")),
         List.copyOf(variables),
         this.effects(json.get("particle_effects")),
         this.effects(json.get("sound_effects"))
      );
   }

   private static float[] curve(@Nullable JsonObject json) {
      if (json == null) {
         return new float[0];
      }

      List<float[]> points = new ArrayList<>();

      for (Entry<String, JsonElement> entry : json.entrySet()) {
         float key = AnimationParser.time(entry.getKey());
         if (!Float.isNaN(key)) {
            points.add(new float[]{key, ModelJson.number(entry.getValue(), 0.0F)});
         }
      }

      points.sort((a, b) -> Float.compare(a[0], b[0]));
      float[] flat = new float[points.size() * 2];

      for (int i = 0; i < points.size(); i++) {
         flat[i * 2] = points.get(i)[0];
         flat[i * 2 + 1] = points.get(i)[1];
      }

      return flat;
   }

   private List<MolangProgram> scripts(@Nullable JsonElement json) {
      List<MolangProgram> programs = new ArrayList<>();

      for (String source : AnimationParser.strings(json)) {
         String trimmed = source.trim();
         if (!trimmed.isEmpty() && !trimmed.startsWith("/") && !trimmed.startsWith("@")) {
            programs.add(this.molang.compile(source, this.version));
         }
      }

      return List.copyOf(programs);
   }

   private List<AnimationDef.Effect> effects(@Nullable JsonElement json) {
      List<AnimationDef.Effect> effects = new ArrayList<>();
      if (json instanceof JsonArray) {
         for (JsonElement element : (JsonArray)json) {
            if (element instanceof JsonObject effect && ModelJson.string(effect, "effect") != null) {
               String script = ModelJson.string(effect, "pre_effect_script");
               effects.add(
                  new AnimationDef.Effect(
                     0.0F,
                     ModelJson.string(effect, "effect"),
                     ModelJson.string(effect, "locator"),
                     script != null ? this.molang.compile(script, this.version) : null,
                     ModelJson.bool(effect, "bind_to_actor", true)
                  )
               );
            }
         }
      }

      return List.copyOf(effects);
   }

   private MolangProgram program(@Nullable JsonElement json, boolean lowerCase) {
      return json instanceof JsonPrimitive primitive
         ? this.molang.compile(primitive.isNumber() ? Float.toString(primitive.getAsFloat()) : primitive.getAsString(), this.version, lowerCase)
         : this.molang.compile("0", this.version);
   }
}
