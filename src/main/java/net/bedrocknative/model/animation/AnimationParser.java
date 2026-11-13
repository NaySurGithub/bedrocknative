package net.bedrocknative.model.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class AnimationParser {
   private final MolangCache molang;
   private final MolangVersion version;

   public AnimationParser(MolangCache molang, MolangVersion version) {
      this.molang = molang;
      this.version = version;
   }

   public Map<String, AnimationDef> parse(JsonObject file) {
      Map<String, AnimationDef> animations = new LinkedHashMap<>();
      JsonObject root = ModelJson.object(file, "animations");
      if (root == null) {
         return animations;
      } else {
         for (Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getValue() instanceof JsonObject json) {
               animations.put(entry.getKey(), this.animation(entry.getKey(), json));
            }
         }

         return animations;
      }
   }

   public AnimationDef animation(String name, JsonObject json) {
      List<BoneTimeline> bones = new ArrayList<>();
      float lastKey = 0.0F;
      JsonObject bonesJson = ModelJson.object(json, "bones");
      if (bonesJson != null) {
         for (Entry<String, JsonElement> entry : bonesJson.entrySet()) {
            if (entry.getValue() instanceof JsonObject bone) {
               Keyframe[] rotation = this.channel(bone.get("rotation"), 0.0F, true);
               Keyframe[] position = this.channel(bone.get("position"), 0.0F, false);
               Keyframe[] scale = this.channel(bone.get("scale"), 1.0F, false);

               for (Keyframe[] keys : new Keyframe[][]{rotation, position, scale}) {
                  if (keys != null && keys.length > 0) {
                     lastKey = Math.max(lastKey, keys[keys.length - 1].time());
                  }
               }

               JsonObject relative = ModelJson.object(bone, "relative_to");
               boolean entity = "entity".equalsIgnoreCase(ModelJson.string(relative, "rotation"));
               bones.add(new BoneTimeline(entry.getKey(), entry.getKey().toLowerCase(Locale.ROOT), rotation, position, scale, entity));
            }
         }
      }

      List<AnimationDef.TimedScript> timeline = new ArrayList<>();
      JsonObject timelineJson = ModelJson.object(json, "timeline");
      if (timelineJson != null) {
         for (Entry<String, JsonElement> entryx : timelineJson.entrySet()) {
            float time = time(entryx.getKey());
            if (!Float.isNaN(time)) {
               List<MolangProgram> programs = new ArrayList<>();

               for (String source : strings(entryx.getValue())) {
                  programs.add(this.compile(source));
               }

               timeline.add(new AnimationDef.TimedScript(time, List.copyOf(programs)));
               lastKey = Math.max(lastKey, time);
            }
         }

         timeline.sort(Comparator.comparingDouble(AnimationDef.TimedScript::time));
      }

      List<AnimationDef.Effect> sounds = this.effects(json.get("sound_effects"));
      List<AnimationDef.Effect> particles = this.effects(json.get("particle_effects"));

      for (AnimationDef.Effect effect : sounds) {
         lastKey = Math.max(lastKey, effect.time());
      }

      for (AnimationDef.Effect effect : particles) {
         lastKey = Math.max(lastKey, effect.time());
      }

      float length = ModelJson.number(json, "animation_length", Float.NaN);
      JsonElement update = json.get("anim_time_update");
      return new AnimationDef(
         name,
         loop(json.get("loop")),
         Float.isNaN(length) ? lastKey : length,
         update != null && !update.isJsonNull() ? this.program(update, "0") : null,
         this.program(json.get("blend_weight"), "1"),
         this.program(json.get("start_delay"), "0"),
         this.program(json.get("loop_delay"), "0"),
         ModelJson.bool(json, "override_previous_animation", false),
         List.copyOf(bones),
         List.copyOf(timeline),
         sounds,
         particles
      );
   }

   private static LoopMode loop(@Nullable JsonElement loop) {
      if (loop instanceof JsonPrimitive primitive) {
         String text = primitive.getAsString().toLowerCase(Locale.ROOT);
         if (text.equals("hold_on_last_frame")) {
            return LoopMode.HOLD_ON_LAST_FRAME;
         }

         if (text.equals("true") || primitive.isNumber() && primitive.getAsDouble() != 0.0) {
            return LoopMode.LOOP;
         }
      }

      return LoopMode.ONCE;
   }

   @Nullable
   Keyframe[] channel(@Nullable JsonElement json, float fill) {
      return this.channel(json, fill, false);
   }

   @Nullable
   Keyframe[] channel(@Nullable JsonElement json, float fill, boolean rotation) {
      if (json != null && !json.isJsonNull()) {
         List<Keyframe> keys = new ArrayList<>();
         if (json instanceof JsonObject object && isKeyframeMap(object)) {
            for (Entry<String, JsonElement> entry : object.entrySet()) {
               float time = time(entry.getKey());
               if (!Float.isNaN(time)) {
                  keys.add(this.keyframe(time, entry.getValue(), fill, rotation));
               }
            }

            keys.sort(Comparator.comparingDouble(Keyframe::time));
         } else {
            keys.add(this.keyframe(0.0F, json, fill, rotation));
         }

         return keys.toArray(new Keyframe[0]);
      } else {
         return null;
      }
   }

   private static boolean isKeyframeMap(JsonObject object) {
      for (String key : object.keySet()) {
         if (!Float.isNaN(time(key))) {
            return true;
         }
      }

      return false;
   }

   private Keyframe keyframe(float time, JsonElement json, float fill, boolean rotation) {
      if (json instanceof JsonObject object && (object.has("pre") || object.has("post"))) {
         Vec3Value pre = object.has("pre") ? this.vector(object.get("pre"), fill, rotation) : null;
         Vec3Value post = object.has("post") ? this.vector(object.get("post"), fill, rotation) : null;
         boolean catmullRom = "catmullrom".equalsIgnoreCase(ModelJson.string(object, "lerp_mode"));
         return new Keyframe(time, pre != null ? pre : post, post != null ? post : pre, catmullRom, pre != null && post != null);
      } else if (json instanceof JsonObject object && object.has("vector")) {
         Vec3Value value = this.vector(object.get("vector"), fill, rotation);
         return new Keyframe(time, value, value, "catmullrom".equalsIgnoreCase(ModelJson.string(object, "lerp_mode")), false);
      } else {
         Vec3Value value = this.vector(json, fill, rotation);
         return new Keyframe(time, value, value, false, false);
      }
   }

   private Vec3Value vector(JsonElement json, float fill, boolean rotation) {
      if (rotation && json instanceof JsonArray array && isAxisList(array)) {
         float[] constants = new float[]{fill, fill, fill};
         MolangProgram[] programs = new MolangProgram[3];
         boolean molang = false;

         for (JsonElement element : array) {
            Entry<String, JsonElement> entry = (Entry<String, JsonElement>)element.getAsJsonObject().entrySet().iterator().next();
            int axis = "xyz".indexOf(entry.getKey().toLowerCase(Locale.ROOT).charAt(0));
            programs[axis] = null;
            molang |= this.component(entry.getValue(), axis, constants, programs);
         }

         return new Vec3Value(constants, molang ? programs : null);
      } else {
         return this.vector(json, fill);
      }
   }

   private static boolean isAxisList(JsonArray array) {
      if (array.isEmpty()) {
         return false;
      } else {
         for (JsonElement element : array) {
            if (!(element instanceof JsonObject object) || object.size() != 1) {
               return false;
            }

            String key = ((String)object.keySet().iterator().next()).toLowerCase(Locale.ROOT);
            if (!key.equals("x") && !key.equals("y") && !key.equals("z")) {
               return false;
            }
         }

         return true;
      }
   }

   Vec3Value vector(JsonElement json, float fill) {
      float[] constants = new float[]{fill, fill, fill};
      MolangProgram[] programs = new MolangProgram[3];
      boolean molang = false;
      if (json instanceof JsonArray array) {
         if (array.size() == 1) {
            return this.vector(array.get(0), fill);
         }

         for (int axis = 0; axis < Math.min(3, array.size()); axis++) {
            molang |= this.component(array.get(axis), axis, constants, programs);
         }
      } else if (json instanceof JsonPrimitive) {
         molang = this.component(json, 0, constants, programs);
         constants[1] = constants[2] = constants[0];
         programs[1] = programs[2] = programs[0];
      }

      return new Vec3Value(constants, molang ? programs : null);
   }

   private boolean component(JsonElement json, int axis, float[] constants, MolangProgram[] programs) {
      if (json instanceof JsonPrimitive primitive && primitive.isNumber()) {
         constants[axis] = primitive.getAsFloat();
         return false;
      } else if (json instanceof JsonPrimitive primitive && primitive.isString()) {
         MolangProgram program = this.compile(primitive.getAsString());
         if (program.isConstant()) {
            constants[axis] = program.constantValue();
            return false;
         } else {
            programs[axis] = program;
            return true;
         }
      } else {
         return false;
      }
   }

   private MolangProgram program(@Nullable JsonElement json, String fallback) {
      return json instanceof JsonPrimitive primitive
         ? this.compile(primitive.isNumber() ? Float.toString(primitive.getAsFloat()) : primitive.getAsString())
         : this.compile(fallback);
   }

   private MolangProgram compile(String source) {
      return this.molang.compile(source, this.version);
   }

   private List<AnimationDef.Effect> effects(@Nullable JsonElement json) {
      List<AnimationDef.Effect> effects = new ArrayList<>();
      if (!(json instanceof JsonObject object)) {
         return effects;
      } else {
         for (Entry<String, JsonElement> entry : object.entrySet()) {
            float time = time(entry.getKey());
            if (!Float.isNaN(time)) {
               List<JsonElement> list = new ArrayList<>();
               if (entry.getValue() instanceof JsonArray array) {
                  array.forEach(list::add);
               } else {
                  list.add(entry.getValue());
               }

               for (JsonElement element : list) {
                  if (element instanceof JsonObject effect && ModelJson.string(effect, "effect") != null) {
                     String script = ModelJson.string(effect, "pre_effect_script");
                     effects.add(
                        new AnimationDef.Effect(
                           time,
                           ModelJson.string(effect, "effect"),
                           ModelJson.string(effect, "locator"),
                           script != null ? this.compile(script) : null,
                           ModelJson.bool(effect, "bind_to_actor", true)
                        )
                     );
                  }
               }
            }
         }

         effects.sort(Comparator.comparingDouble(AnimationDef.Effect::time));
         return List.copyOf(effects);
      }
   }

   static List<String> strings(@Nullable JsonElement json) {
      List<String> strings = new ArrayList<>();
      if (json instanceof JsonPrimitive primitive) {
         strings.add(primitive.getAsString());
      } else if (json instanceof JsonArray) {
         for (JsonElement element : (JsonArray)json) {
            if (element instanceof JsonPrimitive primitive) {
               strings.add(primitive.getAsString());
            }
         }
      }

      return strings;
   }

   static float time(String key) {
      try {
         return Float.parseFloat(key.trim());
      } catch (NumberFormatException var2) {
         return Float.NaN;
      }
   }
}
