package net.bedrocknative.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class ParticleEffectParser {
   private static final Set<String> PARTICLE_BUILTINS = Set.of(
      "particle_age",
      "particleage",
      "particle_lifetime",
      "particlelifetime",
      "particle_random_1",
      "particle_random_2",
      "particle_random_3",
      "particle_random_4",
      "particlerandom1",
      "particlerandom2",
      "particlerandom3",
      "particlerandom4"
   );
   private final MolangCache molang;
   private final MolangVersion version;
   private final List<MolangProgram> emitterPrograms = new ArrayList<>();
   private final List<MolangProgram> particlePrograms = new ArrayList<>();
   private List<MolangProgram> collecting = this.emitterPrograms;

   public ParticleEffectParser(MolangCache molang, MolangVersion version) {
      this.molang = molang;
      this.version = version;
   }

   @Nullable
   public ParticleEffect parse(JsonObject root, int pack, boolean serverPack) {
      JsonObject effect = ModelJson.object(root, "particle_effect");
      JsonObject description = ModelJson.object(effect, "description");
      String identifier = ModelJson.string(description, "identifier");
      if (effect != null && identifier != null && !identifier.isBlank()) {
         this.emitterPrograms.clear();
         this.particlePrograms.clear();
         JsonObject render = ModelJson.object(description, "basic_render_parameters");
         String texture = ModelJson.string(render, "texture");
         ParticleEffect.Material material = ParticleEffect.Material.of(ModelJson.string(render, "material"));
         JsonObject components = ModelJson.object(effect, "components");
         if (components == null) {
            components = new JsonObject();
         }

         this.collecting = this.particlePrograms;
         List<ParticleCurve> curves = this.curves(ModelJson.object(effect, "curves"));
         this.collecting = this.emitterPrograms;
         Map<String, ParticleEvent> events = this.events(ModelJson.object(effect, "events"));
         ParticleEffect.Emitter emitter = this.emitter(components);
         this.collecting = this.particlePrograms;
         ParticleEffect.Particle particle = this.particle(components);
         this.collecting = this.emitterPrograms;
         return new ParticleEffect(
            identifier.toLowerCase(Locale.ROOT),
            texture == null ? "" : texture,
            material,
            pack,
            serverPack,
            List.copyOf(curves),
            Map.copyOf(events),
            emitter,
            particle,
            this.builtins()
         );
      } else {
         return null;
      }
   }

   private ParticleEffect.Builtins builtins() {
      Set<String> reads = new HashSet<>();
      Set<String> particleWrites = new HashSet<>();

      for (MolangProgram program : this.emitterPrograms) {
         lower(program.variableReads(), reads);
      }

      for (MolangProgram program : this.particlePrograms) {
         lower(program.variableReads(), reads);
         lower(program.variableWrites(), particleWrites);
      }

      particleWrites.removeAll(PARTICLE_BUILTINS);
      return new ParticleEffect.Builtins(
         reads.contains("particle_age") || reads.contains("particleage"),
         reads.contains("particle_lifetime") || reads.contains("particlelifetime"),
         reads.stream().anyMatch(name -> name.startsWith("particle_random_") || name.startsWith("particlerandom")),
         reads.contains("emitter_age") || reads.contains("emitterage"),
         reads.contains("emitter_lifetime") || reads.contains("emitterlifetime"),
         reads.stream().anyMatch(name -> name.startsWith("emitter_random_") || name.startsWith("emitterrandom")),
         reads.contains("entity_scale"),
         !particleWrites.isEmpty()
      );
   }

   private static void lower(List<String> names, Set<String> into) {
      for (String name : names) {
         into.add(name.toLowerCase(Locale.ROOT));
      }
   }

   private ParticleEffect.Emitter emitter(JsonObject components) {
      JsonObject initialization = component(components, "emitter_initialization");
      MolangProgram creation = this.optional(initialization, "creation_expression");
      MolangProgram perUpdate = this.optional(initialization, "per_update_expression");
      JsonObject local = component(components, "emitter_local_space");
      boolean localPosition = ModelJson.bool(local, "position", false);
      boolean localRotation = ModelJson.bool(local, "rotation", false) && localPosition;
      boolean localVelocity = ModelJson.bool(local, "velocity", false);
      ParticleEffect.Rate rate = ParticleEffect.Rate.NONE;
      MolangProgram rateAmount = MolangProgram.constant(0.0F);
      MolangProgram maxParticles = MolangProgram.constant(50.0F);
      JsonObject instant = component(components, "emitter_rate_instant");
      JsonObject steady = component(components, "emitter_rate_steady");
      JsonObject manual = component(components, "emitter_rate_manual");
      if (instant != null) {
         rate = ParticleEffect.Rate.INSTANT;
         rateAmount = this.program(instant.get("num_particles"), 10.0F);
      } else if (steady != null) {
         rate = ParticleEffect.Rate.STEADY;
         rateAmount = this.program(steady.get("spawn_rate"), 1.0F);
         maxParticles = this.program(steady.get("max_particles"), 50.0F);
      } else if (manual != null) {
         rate = ParticleEffect.Rate.MANUAL;
         maxParticles = this.program(manual.get("max_particles"), 50.0F);
      }

      ParticleEffect.Lifetime lifetime = ParticleEffect.Lifetime.EXPRESSION;
      MolangProgram activeTime = MolangProgram.constant(10.0F);
      MolangProgram sleepTime = MolangProgram.constant(0.0F);
      MolangProgram activation = MolangProgram.constant(1.0F);
      MolangProgram expiration = MolangProgram.constant(0.0F);
      JsonObject once = component(components, "emitter_lifetime_once");
      JsonObject looping = component(components, "emitter_lifetime_looping");
      JsonObject expression = component(components, "emitter_lifetime_expression");
      if (once != null) {
         lifetime = ParticleEffect.Lifetime.ONCE;
         activeTime = this.program(once.get("active_time"), 10.0F);
      } else if (looping != null) {
         lifetime = ParticleEffect.Lifetime.LOOPING;
         activeTime = this.program(looping.get("active_time"), 10.0F);
         sleepTime = this.program(looping.get("sleep_time"), 0.0F);
      } else if (expression != null) {
         activation = this.program(expression.get("activation_expression"), 1.0F);
         expiration = this.program(expression.get("expiration_expression"), 0.0F);
      }

      JsonObject lifetimeEvents = component(components, "emitter_lifetime_events");
      List<ParticleEffect.DistanceEvent> distances = new ArrayList<>();
      JsonObject travel = ModelJson.object(lifetimeEvents, "travel_distance_events");
      if (travel != null) {
         for (Entry<String, JsonElement> entry : travel.entrySet()) {
            float distance = parseFloat(entry.getKey(), Float.NaN);
            if (!Float.isNaN(distance)) {
               distances.add(new ParticleEffect.DistanceEvent(distance, names(entry.getValue()), false));
            }
         }
      }

      JsonArray loopingTravel = ModelJson.array(lifetimeEvents, "looping_travel_distance_events");
      if (loopingTravel != null) {
         for (JsonElement element : loopingTravel) {
            if (element instanceof JsonObject event) {
               float distance = ModelJson.number(event, "distance", 0.0F);
               if (distance > 0.0F) {
                  distances.add(new ParticleEffect.DistanceEvent(distance, names(event.get("effects")), true));
               }
            }
         }
      }

      ParticleEffect.Shape shape = ParticleEffect.Shape.POINT;
      JsonObject shapeJson = component(components, "emitter_shape_point");
      if (shapeJson == null && (shapeJson = component(components, "emitter_shape_sphere")) != null) {
         shape = ParticleEffect.Shape.SPHERE;
      } else if (shapeJson == null && (shapeJson = component(components, "emitter_shape_box")) != null) {
         shape = ParticleEffect.Shape.BOX;
      } else if (shapeJson == null && (shapeJson = component(components, "emitter_shape_disc")) != null) {
         shape = ParticleEffect.Shape.DISC;
      } else if (shapeJson == null && (shapeJson = component(components, "emitter_shape_custom")) != null) {
         shape = ParticleEffect.Shape.CUSTOM;
      } else if (shapeJson == null && (shapeJson = component(components, "emitter_shape_entity_aabb")) != null) {
         shape = ParticleEffect.Shape.ENTITY_AABB;
      }

      JsonElement directionJson = shapeJson != null ? shapeJson.get("direction") : null;
      ParticleEffect.Direction direction = ParticleEffect.Direction.OUTWARDS;
      MolangProgram[] customDirection = this.programs(null, 3, 0.0F);
      if (directionJson instanceof JsonPrimitive primitive && primitive.isString() && primitive.getAsString().equalsIgnoreCase("inwards")) {
         direction = ParticleEffect.Direction.INWARDS;
      } else if (directionJson instanceof JsonArray) {
         direction = ParticleEffect.Direction.CUSTOM;
         customDirection = this.programs(directionJson, 3, 0.0F);
      } else if (shape == ParticleEffect.Shape.POINT || shape == ParticleEffect.Shape.CUSTOM) {
         direction = directionJson == null ? ParticleEffect.Direction.CUSTOM : direction;
      }

      JsonElement normalJson = shapeJson != null ? shapeJson.get("plane_normal") : null;
      MolangProgram[] planeNormal;
      if (normalJson instanceof JsonPrimitive axis && axis.isString()) {
         String name = axis.getAsString().toLowerCase(Locale.ROOT);
         planeNormal = new MolangProgram[]{
            constant(name.equals("x") ? 1.0F : 0.0F), constant(name.equals("y") ? 1.0F : 0.0F), constant(name.equals("z") ? 1.0F : 0.0F)
         };
      } else {
         planeNormal = normalJson != null ? this.programs(normalJson, 3, 0.0F) : new MolangProgram[]{constant(0.0F), constant(1.0F), constant(0.0F)};
      }

      return new ParticleEffect.Emitter(
         creation,
         perUpdate,
         localPosition,
         localRotation,
         localVelocity,
         rate,
         rateAmount,
         maxParticles,
         lifetime,
         activeTime,
         sleepTime,
         activation,
         expiration,
         names(lifetimeEvents != null ? lifetimeEvents.get("creation_event") : null),
         names(lifetimeEvents != null ? lifetimeEvents.get("expiration_event") : null),
         timeline(ModelJson.object(lifetimeEvents, "timeline")),
         List.copyOf(distances),
         shape,
         this.programs(shapeJson != null ? shapeJson.get("offset") : null, 3, 0.0F),
         this.program(shapeJson != null ? shapeJson.get("radius") : null, 1.0F),
         this.programs(shapeJson != null ? shapeJson.get("half_dimensions") : null, 3, 0.0F),
         ModelJson.bool(shapeJson, "surface_only", false),
         planeNormal,
         direction,
         customDirection
      );
   }

   private ParticleEffect.Particle particle(JsonObject components) {
      JsonElement speedJson = componentElement(components, "particle_initial_speed");
      MolangProgram speed = null;
      MolangProgram[] speedVector = null;
      if (speedJson instanceof JsonArray) {
         speedVector = this.programs(speedJson, 3, 0.0F);
      } else {
         speed = this.program(speedJson, 0.0F);
      }

      JsonObject spin = component(components, "particle_initial_spin");
      JsonObject initialization = component(components, "particle_initialization");
      JsonObject lifetime = component(components, "particle_lifetime_expression");
      MolangProgram expiration = this.optional(lifetime, "expiration_expression");
      MolangProgram maxLifetime = this.optional(lifetime, "max_lifetime");
      JsonObject events = component(components, "particle_lifetime_events");
      JsonElement kill = componentElement(components, "particle_kill_plane");
      float[] killPlane = null;
      if (kill instanceof JsonArray plane && plane.size() >= 4) {
         killPlane = new float[4];

         for (int i = 0; i < 4; i++) {
            killPlane[i] = ModelJson.number(plane.get(i), 0.0F);
         }
      }

      JsonObject dynamic = component(components, "particle_motion_dynamic");
      JsonObject parametric = component(components, "particle_motion_parametric");
      JsonObject collisionJson = component(components, "particle_motion_collision");
      ParticleEffect.Collision collision = null;
      if (collisionJson != null) {
         List<ParticleEffect.CollisionEvent> collisionEvents = new ArrayList<>();
         JsonElement eventsJson = collisionJson.get("events");
         List<JsonElement> list = new ArrayList<>();
         if (eventsJson instanceof JsonArray array) {
            array.forEach(list::add);
         } else if (eventsJson != null) {
            list.add(eventsJson);
         }

         for (JsonElement element : list) {
            if (element instanceof JsonObject event) {
               String var24 = ModelJson.string(event, "event");
               if (var24 instanceof String) {
                  collisionEvents.add(new ParticleEffect.CollisionEvent(var24, ModelJson.number(event, "min_speed", 2.0F)));
               }
            }
         }

         collision = new ParticleEffect.Collision(
            this.program(collisionJson.get("enabled"), 1.0F),
            ModelJson.number(collisionJson, "collision_drag", 0.0F),
            ModelJson.number(collisionJson, "coefficient_of_restitution", 0.0F),
            Math.min(0.5F, ModelJson.number(collisionJson, "collision_radius", 0.0F)),
            ModelJson.bool(collisionJson, "expire_on_contact", false),
            List.copyOf(collisionEvents)
         );
      }

      return new ParticleEffect.Particle(
         speed,
         speedVector,
         this.program(spin != null ? spin.get("rotation") : null, 0.0F),
         this.program(spin != null ? spin.get("rotation_rate") : null, 0.0F),
         this.optional(initialization, "creation_expression"),
         this.optional(initialization, "per_update_expression"),
         this.optional(initialization, "per_render_expression"),
         expiration,
         maxLifetime,
         names(events != null ? events.get("creation_event") : null),
         names(events != null ? events.get("expiration_event") : null),
         timeline(ModelJson.object(events, "timeline")),
         blocks(componentElement(components, "particle_expire_if_in_blocks")),
         blocks(componentElement(components, "particle_expire_if_not_in_blocks")),
         killPlane,
         this.programs(dynamic != null ? dynamic.get("linear_acceleration") : null, 3, 0.0F),
         this.program(dynamic != null ? dynamic.get("linear_drag_coefficient") : null, 0.0F),
         this.program(dynamic != null ? dynamic.get("rotation_acceleration") : null, 0.0F),
         this.program(dynamic != null ? dynamic.get("rotation_drag_coefficient") : null, 0.0F),
         parametric != null && parametric.has("relative_position") ? this.programs(parametric.get("relative_position"), 3, 0.0F) : null,
         parametric != null && parametric.has("direction") ? this.programs(parametric.get("direction"), 3, 0.0F) : null,
         parametric != null ? this.optional(parametric, "rotation") : null,
         collision,
         this.billboard(component(components, "particle_appearance_billboard")),
         this.tint(component(components, "particle_appearance_tinting")),
         component(components, "particle_appearance_lighting") != null
      );
   }

   @Nullable
   private ParticleEffect.Billboard billboard(@Nullable JsonObject json) {
      if (json == null) {
         return null;
      } else {
         ParticleEffect.Facing facing;
         try {
            facing = ParticleEffect.Facing.valueOf(String.valueOf(ModelJson.string(json, "facing_camera_mode")).toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var10) {
            facing = ParticleEffect.Facing.LOOKAT_XYZ;
         }

         JsonObject direction = ModelJson.object(json, "direction");
         boolean custom = "custom_direction".equalsIgnoreCase(ModelJson.string(direction, "mode"))
            || "custom".equalsIgnoreCase(ModelJson.string(direction, "mode"));
         JsonObject uv = ModelJson.object(json, "uv");
         float width = number(uv, 1.0F, "texture_width", "texturewidth");
         float height = number(uv, 1.0F, "texture_height", "textureheight");
         JsonObject flipbookJson = ModelJson.object(uv, "flipbook");
         ParticleEffect.Flipbook flipbook = null;
         if (flipbookJson != null) {
            flipbook = new ParticleEffect.Flipbook(
               this.programs(member(flipbookJson, "base_UV", "base_uv"), 2, 0.0F),
               constants(member(flipbookJson, "size_UV", "size_uv"), 2),
               constants(member(flipbookJson, "step_UV", "step_uv"), 2),
               this.program(flipbookJson.get("frames_per_second"), 8.0F),
               this.program(flipbookJson.get("max_frame"), 1.0F),
               ModelJson.bool(flipbookJson, "stretch_to_lifetime", false),
               ModelJson.bool(flipbookJson, "loop", false)
            );
         }

         return new ParticleEffect.Billboard(
            this.programs(json.get("size"), 2, 0.0F),
            facing,
            custom,
            ModelJson.number(direction, "min_speed_threshold", 0.01F),
            this.programs(direction != null ? direction.get("custom_direction") : null, 3, 0.0F),
            width <= 0.0F ? 1.0F : width,
            height <= 0.0F ? 1.0F : height,
            this.programs(uv != null ? uv.get("uv") : null, 2, 0.0F),
            uv != null && uv.has("uv_size")
               ? this.programs(uv.get("uv_size"), 2, 0.0F)
               : new MolangProgram[]{constant(width <= 0.0F ? 1.0F : width), constant(height <= 0.0F ? 1.0F : height)},
            flipbook
         );
      }
   }

   @Nullable
   private ParticleEffect.Tint tint(@Nullable JsonObject json) {
      if (json == null) {
         return null;
      } else {
         JsonElement color = json.get("color");
         if (!(color instanceof JsonObject gradientJson)) {
            return new ParticleEffect.Tint(this.color(color), new float[0], new MolangProgram[0][], null);
         } else {
            JsonElement gradient = gradientJson.get("gradient");
            TreeMap<Float, MolangProgram[]> stops = new TreeMap<>();
            if (gradient instanceof JsonArray array) {
               for (int i = 0; i < array.size(); i++) {
                  stops.put(array.size() == 1 ? 0.0F : (float)i / (array.size() - 1), this.color(array.get(i)));
               }
            } else if (gradient instanceof JsonObject map) {
               for (Entry<String, JsonElement> entry : map.entrySet()) {
                  float key = parseFloat(entry.getKey(), Float.NaN);
                  if (!Float.isNaN(key)) {
                     stops.put(key, this.color(entry.getValue()));
                  }
               }
            }

            float[] keys = new float[stops.size()];
            MolangProgram[][] colors = new MolangProgram[stops.size()][];
            int index = 0;

            for (Entry<Float, MolangProgram[]> stop : stops.entrySet()) {
               keys[index] = stop.getKey();
               colors[index++] = stop.getValue();
            }

            return new ParticleEffect.Tint(null, keys, colors, this.program(gradientJson.get("interpolant"), 0.0F));
         }
      }
   }

   private MolangProgram[] color(@Nullable JsonElement element) {
      if (element instanceof JsonPrimitive primitive && primitive.isString() && primitive.getAsString().startsWith("#")) {
         String hex = primitive.getAsString().substring(1);

         try {
            long value = Long.parseLong(hex, 16);
            float a = hex.length() >= 8 ? (float)(value >> 24 & 255L) / 255.0F : 1.0F;
            return new MolangProgram[]{
               constant((float)(value >> 16 & 255L) / 255.0F),
               constant((float)(value >> 8 & 255L) / 255.0F),
               constant((float)(value & 255L) / 255.0F),
               constant(a)
            };
         } catch (NumberFormatException var7) {
            return new MolangProgram[]{constant(1.0F), constant(1.0F), constant(1.0F), constant(1.0F)};
         }
      } else if (!(element instanceof JsonArray array)) {
         return new MolangProgram[]{constant(1.0F), constant(1.0F), constant(1.0F), constant(1.0F)};
      } else {
         MolangProgram[] rgba = new MolangProgram[]{constant(1.0F), constant(1.0F), constant(1.0F), constant(1.0F)};

         for (int i = 0; i < Math.min(4, array.size()); i++) {
            rgba[i] = this.program(array.get(i), 1.0F);
         }

         return rgba;
      }
   }

   private List<ParticleCurve> curves(@Nullable JsonObject json) {
      List<ParticleCurve> curves = new ArrayList<>();
      if (json == null) {
         return curves;
      } else {
         for (Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue() instanceof JsonObject curve) {
               String name = entry.getKey().toLowerCase(Locale.ROOT);
               String variable = name.startsWith("variable.") ? name.substring(9) : (name.startsWith("v.") ? name.substring(2) : name);
               String nodes = String.valueOf(ModelJson.string(curve, "type")).toLowerCase(Locale.ROOT);

               ParticleCurve.Type type = switch (nodes) {
                  case "bezier" -> ParticleCurve.Type.BEZIER;
                  case "catmull_rom" -> ParticleCurve.Type.CATMULL_ROM;
                  case "bezier_chain" -> ParticleCurve.Type.BEZIER_CHAIN;
                  default -> ParticleCurve.Type.LINEAR;
               };
               MolangProgram[] nodes = new MolangProgram[0];
               List<ParticleCurve.ChainNode> chain = new ArrayList<>();
               JsonElement nodesJson = curve.get("nodes");
               if (nodesJson instanceof JsonArray array) {
                  nodes = new MolangProgram[array.size()];

                  for (int i = 0; i < array.size(); i++) {
                     nodes[i] = this.program(array.get(i), 0.0F);
                  }
               } else if (nodesJson instanceof JsonObject keyed) {
                  for (Entry<String, JsonElement> node : keyed.entrySet()) {
                     float key = parseFloat(node.getKey(), Float.NaN);
                     if (!Float.isNaN(key) && node.getValue() instanceof JsonObject point) {
                        MolangProgram value = this.program(point.get("value"), 0.0F);
                        MolangProgram slope = this.program(point.get("slope"), 0.0F);
                        chain.add(
                           new ParticleCurve.ChainNode(
                              key,
                              point.has("left_value") ? this.program(point.get("left_value"), 0.0F) : value,
                              point.has("right_value") ? this.program(point.get("right_value"), 0.0F) : value,
                              point.has("left_slope") ? this.program(point.get("left_slope"), 0.0F) : slope,
                              point.has("right_slope") ? this.program(point.get("right_slope"), 0.0F) : slope
                           )
                        );
                     }
                  }

                  chain.sort((a, b) -> Float.compare(a.key(), b.key()));
                  if (type != ParticleCurve.Type.BEZIER_CHAIN) {
                     nodes = chain.stream().map(ParticleCurve.ChainNode::rightValue).toArray(MolangProgram[]::new);
                  }
               }

               curves.add(
                  new ParticleCurve(
                     variable,
                     MolangNames.id(variable),
                     type,
                     this.program(curve.get("input"), 0.0F),
                     this.program(curve.get("horizontal_range"), 1.0F),
                     nodes,
                     chain.toArray(new ParticleCurve.ChainNode[0])
                  )
               );
            }
         }

         return curves;
      }
   }

   private Map<String, ParticleEvent> events(@Nullable JsonObject json) {
      Map<String, ParticleEvent> events = new HashMap<>();
      if (json != null) {
         for (Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue() instanceof JsonObject event) {
               events.put(entry.getKey(), this.event(event, 0));
            }
         }
      }

      return events;
   }

   private ParticleEvent event(JsonObject json, int depth) {
      if (depth > 16) {
         return ParticleEvent.EMPTY;
      } else {
         List<ParticleEvent> sequence = new ArrayList<>();
         JsonElement effect = json.get("sequence");
         if (effect instanceof JsonArray) {
            for (JsonElement element : (JsonArray)effect) {
               if (element instanceof JsonObject child) {
                  sequence.add(this.event(child, depth + 1));
               }
            }
         }

         List<ParticleEvent.Weighted> randomize = new ArrayList<>();
         JsonElement var15 = json.get("randomize");
         if (var15 instanceof JsonArray) {
            for (JsonElement elementx : (JsonArray)var15) {
               if (elementx instanceof JsonObject child) {
                  randomize.add(new ParticleEvent.Weighted(ModelJson.number(child, "weight", 1.0F), this.event(child, depth + 1)));
               }
            }
         }

         ParticleEvent.Effect effectx = null;
         JsonObject particle = ModelJson.object(json, "particle_effect");
         if (particle != null) {
            String var21 = ModelJson.string(particle, "effect");
            if (var21 instanceof String) {
               String var9 = String.valueOf(ModelJson.string(particle, "type")).toLowerCase(Locale.ROOT);

               ParticleEvent.EffectType type = switch (var9) {
                  case "emitter_bound" -> ParticleEvent.EffectType.EMITTER_BOUND;
                  case "particle" -> ParticleEvent.EffectType.PARTICLE;
                  case "particle_with_velocity" -> ParticleEvent.EffectType.PARTICLE_WITH_VELOCITY;
                  default -> ParticleEvent.EffectType.EMITTER;
               };
               effectx = new ParticleEvent.Effect(var21.toLowerCase(Locale.ROOT), type, this.optional(particle, "pre_effect_expression"));
            }
         }

         JsonObject sound = ModelJson.object(json, "sound_effect");
         String soundName = ModelJson.string(sound, "event_name");
         return new ParticleEvent(
            List.copyOf(sequence),
            List.copyOf(randomize),
            effectx,
            soundName != null ? soundName.toLowerCase(Locale.ROOT) : null,
            this.optional(json, "expression"),
            ModelJson.string(json, "log")
         );
      }
   }

   private static ParticleEffect.Timeline timeline(@Nullable JsonObject json) {
      if (json != null && !json.isEmpty()) {
         TreeMap<Float, String[]> entries = new TreeMap<>();

         for (Entry<String, JsonElement> entry : json.entrySet()) {
            float time = parseFloat(entry.getKey(), Float.NaN);
            if (!Float.isNaN(time)) {
               entries.merge(time, names(entry.getValue()), (a, b) -> {
                  String[] merged = Arrays.copyOf(a, a.length + b.length);
                  System.arraycopy(b, 0, merged, a.length, b.length);
                  return merged;
               });
            }
         }

         float[] times = new float[entries.size()];
         String[][] names = new String[entries.size()][];
         int index = 0;

         for (Entry<Float, String[]> entryx : entries.entrySet()) {
            times[index] = entryx.getKey();
            names[index++] = entryx.getValue();
         }

         return new ParticleEffect.Timeline(times, names);
      } else {
         return ParticleEffect.Timeline.EMPTY;
      }
   }

   private static String[] names(@Nullable JsonElement element) {
      if (element instanceof JsonPrimitive primitive && primitive.isString()) {
         return new String[]{primitive.getAsString()};
      } else if (element instanceof JsonArray array) {
         List<String> names = new ArrayList<>();

         for (JsonElement entry : array) {
            if (entry instanceof JsonPrimitive primitive && primitive.isString()) {
               names.add(primitive.getAsString());
            }
         }

         return names.toArray(new String[0]);
      } else {
         return new String[0];
      }
   }

   private static Set<String> blocks(@Nullable JsonElement element) {
      Set<String> blocks = new HashSet<>();

      for (String name : names(element)) {
         String lower = name.toLowerCase(Locale.ROOT);
         blocks.add(lower.contains(":") ? lower : "minecraft:" + lower);
      }

      return Set.copyOf(blocks);
   }

   @Nullable
   private static JsonObject component(JsonObject components, String name) {
      return componentElement(components, name) instanceof JsonObject object ? object : null;
   }

   @Nullable
   private static JsonElement componentElement(JsonObject components, String name) {
      JsonElement element = components.get("minecraft:" + name);
      return element != null ? element : components.get(name);
   }

   @Nullable
   private static JsonElement member(JsonObject json, String... names) {
      for (String name : names) {
         if (json.has(name)) {
            return json.get(name);
         }
      }

      for (Entry<String, JsonElement> entry : json.entrySet()) {
         for (String namex : names) {
            if (entry.getKey().equalsIgnoreCase(namex)) {
               return entry.getValue();
            }
         }
      }

      return null;
   }

   private static float number(@Nullable JsonObject json, float fallback, String... names) {
      if (json == null) {
         return fallback;
      } else {
         JsonElement element = member(json, names);
         return element != null ? ModelJson.number(element, fallback) : fallback;
      }
   }

   private static float[] constants(@Nullable JsonElement element, int count) {
      float[] values = new float[count];
      if (element instanceof JsonArray array) {
         for (int i = 0; i < Math.min(count, array.size()); i++) {
            values[i] = ModelJson.number(array.get(i), 0.0F);
         }
      } else if (element != null) {
         Arrays.fill(values, ModelJson.number(element, 0.0F));
      }

      return values;
   }

   @Nullable
   private MolangProgram optional(@Nullable JsonObject json, String key) {
      return json != null && json.has(key) && !json.get(key).isJsonNull() ? this.program(json.get(key), 0.0F) : null;
   }

   MolangProgram program(@Nullable JsonElement element, float fallback) {
      if (element instanceof JsonPrimitive primitive) {
         if (primitive.isNumber()) {
            return constant(primitive.getAsFloat());
         } else if (primitive.isBoolean()) {
            return constant(primitive.getAsBoolean() ? 1.0F : 0.0F);
         } else {
            String source = primitive.getAsString().trim();
            if (source.isEmpty()) {
               return constant(fallback);
            } else {
               float number = parseFloat(source, Float.NaN);
               if (!Float.isNaN(number)) {
                  return constant(number);
               } else {
                  MolangProgram program = this.molang.compile(source, this.version);
                  this.collecting.add(program);
                  return program;
               }
            }
         }
      } else {
         return element instanceof JsonArray array && array.size() == 1 ? this.program(array.get(0), fallback) : constant(fallback);
      }
   }

   private MolangProgram[] programs(@Nullable JsonElement element, int count, float fallback) {
      MolangProgram[] programs = new MolangProgram[count];
      if (element instanceof JsonArray array) {
         for (int i = 0; i < count; i++) {
            programs[i] = i < array.size() ? this.program(array.get(i), fallback) : constant(fallback);
         }
      } else if (element != null && !element.isJsonNull()) {
         MolangProgram single = this.program(element, fallback);
         Arrays.fill(programs, single);
      } else {
         Arrays.fill(programs, constant(fallback));
      }

      return programs;
   }

   private static MolangProgram constant(float value) {
      return MolangProgram.constant(value);
   }

   private static float parseFloat(String text, float fallback) {
      try {
         return Float.parseFloat(text.trim());
      } catch (NumberFormatException var3) {
         return fallback;
      }
   }
}
