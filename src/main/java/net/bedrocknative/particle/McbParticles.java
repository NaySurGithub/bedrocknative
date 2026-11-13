package net.bedrocknative.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class McbParticles {
   private static final String[] COMPONENTS = new String[]{
      "emitter_initialization",
      "emitter_local_space",
      "emitter_rate_instant",
      "emitter_rate_steady",
      "emitter_rate_manual",
      "emitter_lifetime_looping",
      "emitter_lifetime_once",
      "emitter_lifetime_expression",
      "emitter_lifetime_events",
      "emitter_shape_point",
      "emitter_shape_sphere",
      "emitter_shape_box",
      "emitter_shape_disc",
      "emitter_shape_custom",
      "emitter_shape_entity_aabb",
      "particle_initial_speed",
      "particle_initial_spin",
      "particle_initialization",
      "particle_lifetime_expression",
      "particle_lifetime_events",
      "particle_expire_if_in_blocks",
      "particle_expire_if_not_in_blocks",
      "particle_kill_plane",
      "particle_motion_dynamic",
      "particle_motion_parametric",
      "particle_motion_collision",
      "particle_appearance_billboard",
      "particle_appearance_tinting",
      "particle_appearance_lighting"
   };
   private static final Map<Integer, String> BY_HASH = new HashMap<>();

   private McbParticles() {
   }

   public static boolean isMcb(byte[] data) {
      return data.length >= 12 && data[0] == 127 && data[1] == 77 && data[2] == 67 && data[3] == 66;
   }

   static int fnv1a(String text) {
      int hash = -2128831035;

      for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
         hash ^= b & 255;
         hash *= 16777619;
      }

      return hash;
   }

   @Nullable
   public static JsonObject toJson(byte[] data) {
      if (!isMcb(data)) {
         return null;
      } else {
         try {
            McbParticles.Reader reader = new McbParticles.Reader(data);
            reader.position = 4;
            int major = reader.u16();
            int minor = reader.u16();
            long patch = reader.u32() & 4294967295L;
            reader.string();
            String identifier = reader.string();
            String material = reader.string();
            String texture = reader.string();
            JsonObject components = new JsonObject();
            long count = reader.u32() & 4294967295L;

            for (long i = 0L; i < count; i++) {
               String name = BY_HASH.get(reader.u32());
               if (name == null) {
                  return null;
               }

               components.add("minecraft:" + name, component(name, reader));
            }

            JsonObject effect = new JsonObject();
            JsonObject description = new JsonObject();
            description.addProperty("identifier", identifier);
            JsonObject render = new JsonObject();
            render.addProperty("material", material);
            render.addProperty("texture", texture);
            description.add("basic_render_parameters", render);
            effect.add("description", description);
            int tail = reader.position;

            try {
               JsonObject curves = curves(reader);
               JsonObject events = events(reader);
               if (curves != null) {
                  effect.add("curves", curves);
               }

               if (events != null) {
                  effect.add("events", events);
               }
            } catch (RuntimeException var18) {
               reader.position = tail;
            }

            effect.add("components", components);
            JsonObject root = new JsonObject();
            root.addProperty("format_version", major + "." + minor + "." + patch);
            root.add("particle_effect", effect);
            return root;
         } catch (RuntimeException var19) {
            return null;
         }
      }
   }

   private static JsonElement component(String name, McbParticles.Reader r) {
      JsonObject out = new JsonObject();
      switch (name) {
         case "emitter_lifetime_expression":
            out.add("activation_expression", r.molang());
            out.add("expiration_expression", r.molang());
            break;
         case "emitter_lifetime_once":
            out.add("active_time", r.molang());
            break;
         case "emitter_lifetime_looping":
            out.add("active_time", r.molang());
            out.add("sleep_time", r.molang());
            break;
         case "emitter_rate_instant":
            out.add("num_particles", r.molang());
            break;
         case "emitter_rate_steady":
            out.add("spawn_rate", r.molang());
            out.add("max_particles", r.molang());
            break;
         case "emitter_rate_manual":
            out.add("max_particles", r.molang());
            break;
         case "emitter_shape_point":
            out.add("offset", r.molang3());
            if (r.present()) {
               out.add("direction", r.molang3());
            }
            break;
         case "emitter_shape_custom":
            out.add("offset", r.molang3());
            out.add("direction", r.molang3());
            break;
         case "emitter_shape_sphere":
            out.add("offset", r.molang3());
            out.add("radius", r.molang());
            out.addProperty("surface_only", r.bool());
            direction(r, out);
            break;
         case "emitter_shape_box":
            out.add("offset", r.molang3());
            out.add("half_dimensions", r.molang3());
            out.addProperty("surface_only", r.bool());
            direction(r, out);
            break;
         case "emitter_shape_disc":
            out.add("offset", r.molang3());
            out.add("radius", r.molang());
            out.addProperty("surface_only", r.bool());
            direction(r, out);
            if (r.present()) {
               out.add("plane_normal", r.molang3());
            }
            break;
         case "emitter_shape_entity_aabb":
            out.addProperty("surface_only", r.bool());
            direction(r, out);
            break;
         case "emitter_local_space":
            out.addProperty("position", r.bool());
            out.addProperty("rotation", r.bool());
            out.addProperty("velocity", r.bool());
            break;
         case "emitter_initialization":
            out.add("creation_expression", r.molang());
            out.add("per_update_expression", r.molang());
            break;
         case "particle_initial_speed":
            int kind = r.u8();
            if (kind == 1) {
               return r.molang();
            }

            if (kind != 0) {
               throw new IllegalStateException("speed kind " + kind);
            }

            return r.molang3();
         case "particle_initial_spin":
            out.add("rotation", r.molang());
            out.add("rotation_rate", r.molang());
            break;
         case "particle_initialization":
            out.add("per_update_expression", r.molang());
            out.add("per_render_expression", r.molang());
            break;
         case "particle_lifetime_expression":
            if (r.present()) {
               out.add("max_lifetime", r.molang());
            }

            out.add("expiration_expression", r.molang());
            break;
         case "particle_expire_if_in_blocks":
         case "particle_expire_if_not_in_blocks":
            return r.strings();
         case "particle_kill_plane":
            JsonArray plane = new JsonArray();

            for (int i = 0; i < 4; i++) {
               plane.add(r.f32());
            }

            return plane;
         case "particle_motion_dynamic":
            out.add("linear_acceleration", r.molang3());
            out.add("linear_drag_coefficient", r.molang());
            out.add("rotation_acceleration", r.molang());
            out.add("rotation_drag_coefficient", r.molang());
            break;
         case "particle_motion_parametric":
            if (r.present()) {
               out.add("relative_position", r.molang3());
            }

            if (r.present()) {
               out.add("direction", r.molang3());
            }

            if (r.present()) {
               out.add("rotation", r.molang());
            }
            break;
         case "particle_motion_collision":
            out.add("enabled", r.molang());
            out.addProperty("collision_drag", r.f32());
            out.addProperty("coefficient_of_restitution", r.f32());
            out.addProperty("collision_radius", r.f32());
            out.addProperty("expire_on_contact", r.bool());
            if (r.present()) {
               JsonArray events = new JsonArray();
               long count = r.varint();

               for (long i = 0L; i < count; i++) {
                  JsonObject event = new JsonObject();
                  event.addProperty("min_speed", r.f32());
                  event.addProperty("event", r.string());
                  events.add(event);
               }

               out.add("events", events);
            }
            break;
         case "particle_appearance_billboard":
            billboard(r, out);
            break;
         case "particle_appearance_tinting":
            int kindx = r.u8();
            if (kindx == 0) {
               out.add("color", r.molang(4));
            } else {
               if (kindx != 1) {
                  throw new IllegalStateException("tint kind " + kindx);
               }

               JsonObject color = new JsonObject();
               JsonObject gradient = new JsonObject();
               if (r.present()) {
                  long count = r.varint();

                  for (long i = 0L; i < count; i++) {
                     String key = r.string();
                     gradient.add(key, r.molang(4));
                  }
               }

               color.add("gradient", gradient);
               color.add("interpolant", r.molang());
               out.add("color", color);
            }
         case "particle_appearance_lighting":
            break;
         case "emitter_lifetime_events":
            out.add("creation_event", r.strings());
            out.add("expiration_event", r.strings());
            out.add("timeline", timeline(r));
            out.add("travel_distance_events", timeline(r));
            JsonArray looping = new JsonArray();
            long count = r.varint();

            for (long i = 0L; i < count; i++) {
               JsonObject event = new JsonObject();
               event.addProperty("distance", r.f32());
               event.add("effects", r.strings());
               looping.add(event);
            }

            out.add("looping_travel_distance_events", looping);
            break;
         case "particle_lifetime_events":
            out.add("creation_event", r.strings());
            out.add("expiration_event", r.strings());
            out.add("timeline", timeline(r));
            break;
         default:
            throw new IllegalStateException("no layout for " + name);
      }

      return out;
   }

   private static JsonObject timeline(McbParticles.Reader r) {
      JsonObject timeline = new JsonObject();
      long count = r.varint();

      for (long i = 0L; i < count; i++) {
         float time = r.f32();
         timeline.add(Float.toString(time), r.strings());
      }

      return timeline;
   }

   private static void direction(McbParticles.Reader r, JsonObject out) {
      if (r.present()) {
         int kind = r.u8();
         if (kind == 1) {
            out.add("direction", r.molang3());
         } else {
            if (kind != 0) {
               throw new IllegalStateException("direction kind " + kind);
            }

            out.addProperty("direction", r.string());
         }
      }
   }

   private static void billboard(McbParticles.Reader r, JsonObject out) {
      out.add("size", r.molang(2));
      if (r.present()) {
         out.addProperty("facing_camera_mode", r.string());
      }

      if (r.present()) {
         JsonObject direction = new JsonObject();
         direction.addProperty("mode", r.string());
         if (r.present()) {
            direction.add("custom_direction", r.molang3());
         }

         direction.addProperty("min_speed_threshold", r.f32());
         out.add("direction", direction);
      }

      if (r.present()) {
         JsonObject uv = new JsonObject();
         uv.addProperty("texture_width", r.u32() & 4294967295L);
         uv.addProperty("texture_height", r.u32() & 4294967295L);
         if (r.present()) {
            uv.add("uv", r.molang(2));
         }

         if (r.present()) {
            uv.add("uv_size", r.molang(2));
         }

         if (r.present()) {
            JsonObject flipbook = new JsonObject();
            flipbook.add("base_UV", r.molang(2));
            flipbook.add("size_UV", floats(r, 2));
            flipbook.add("step_UV", floats(r, 2));
            flipbook.addProperty("frames_per_second", r.f32());
            flipbook.add("max_frame", r.molang());
            flipbook.addProperty("stretch_to_lifetime", r.bool());
            flipbook.addProperty("loop", r.bool());
            uv.add("flipbook", flipbook);
         }

         out.add("uv", uv);
      }
   }

   private static JsonArray floats(McbParticles.Reader r, int count) {
      JsonArray array = new JsonArray();

      for (int i = 0; i < count; i++) {
         array.add(r.f32());
      }

      return array;
   }

   @Nullable
   private static JsonObject curves(McbParticles.Reader r) {
      if (!r.present()) {
         return null;
      } else {
         JsonObject curves = new JsonObject();
         long count = r.varint();

         for (long i = 0L; i < count; i++) {
            String name = r.string();
            if (r.u8() != 0) {
               throw new IllegalStateException("curve node layout");
            }

            JsonObject curve = new JsonObject();
            curve.addProperty("type", r.string());
            JsonArray nodes = new JsonArray();
            long nodeCount = r.varint();

            for (long n = 0L; n < nodeCount; n++) {
               nodes.add(r.molang());
            }

            curve.add("nodes", nodes);
            curve.add("input", r.molang());
            curve.add("horizontal_range", r.molang());
            curves.add(name, curve);
         }

         return curves;
      }
   }

   @Nullable
   private static JsonObject events(McbParticles.Reader r) {
      if (!r.present()) {
         return null;
      } else {
         JsonObject events = new JsonObject();
         long count = r.varint();

         for (long i = 0L; i < count; i++) {
            String name = r.string();
            events.add(name, event(r, 0));
         }

         return events;
      }
   }

   private static JsonObject event(McbParticles.Reader r, int depth) {
      if (depth > 16) {
         throw new IllegalStateException("event nesting");
      } else {
         JsonObject node = new JsonObject();
         if (r.present()) {
            r.u32();
            JsonObject effect = new JsonObject();
            effect.addProperty("effect", r.string());
            effect.addProperty("type", r.string());
            effect.add("pre_effect_expression", r.molang());
            node.add("particle_effect", effect);
         }

         if (r.present()) {
            JsonObject sound = new JsonObject();
            sound.addProperty("event_name", r.string());
            node.add("sound_effect", sound);
         }

         if (r.present()) {
            node.add("expression", r.molang());
         }

         if (r.present()) {
            node.addProperty("log", r.string());
         }

         long sequence = r.varint();
         if (sequence > 0L) {
            JsonArray children = new JsonArray();

            for (long i = 0L; i < sequence; i++) {
               children.add(event(r, depth + 1));
            }

            node.add("sequence", children);
         }

         return node;
      }
   }

   static {
      for (String component : COMPONENTS) {
         BY_HASH.put(fnv1a("minecraft:" + component), component);
      }
   }

   private static final class Reader {
      private final byte[] data;
      private int position;

      Reader(byte[] data) {
         this.data = data;
      }

      int u8() {
         if (this.position >= this.data.length) {
            throw new IllegalStateException("truncated");
         } else {
            return this.data[this.position++] & 0xFF;
         }
      }

      int u16() {
         return this.u8() | this.u8() << 8;
      }

      int u32() {
         return this.u8() | this.u8() << 8 | this.u8() << 16 | this.u8() << 24;
      }

      float f32() {
         return Float.intBitsToFloat(this.u32());
      }

      long varint() {
         long value = 0L;

         for (int shift = 0; shift < 35; shift += 7) {
            int b = this.u8();
            value |= (long)(b & 127) << shift;
            if (b < 128) {
               return value;
            }
         }

         throw new IllegalStateException("varint");
      }

      String string() {
         long length = this.varint();
         if (length >= 0L && this.position + length <= this.data.length) {
            String text = new String(this.data, this.position, (int)length, StandardCharsets.UTF_8);
            this.position += (int)length;
            return text;
         } else {
            throw new IllegalStateException("string length");
         }
      }

      boolean present() {
         int flag = this.u8();
         if (flag > 1) {
            throw new IllegalStateException("presence byte " + flag);
         } else {
            return flag == 1;
         }
      }

      boolean bool() {
         return this.present();
      }

      JsonElement molang() {
         int tag = this.u8();
         if (tag == 1) {
            float value = this.f32();
            return (JsonElement)(Float.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE);
         } else if (tag == 0) {
            return new JsonPrimitive(this.string());
         } else {
            throw new IllegalStateException("value tag " + tag);
         }
      }

      JsonArray molang3() {
         return this.molang(3);
      }

      JsonArray molang(int count) {
         JsonArray array = new JsonArray();

         for (int i = 0; i < count; i++) {
            array.add(this.molang());
         }

         return array;
      }

      JsonArray strings() {
         JsonArray array = new JsonArray();
         long count = this.varint();

         for (long i = 0L; i < count; i++) {
            array.add(this.string());
         }

         return array;
      }
   }
}
