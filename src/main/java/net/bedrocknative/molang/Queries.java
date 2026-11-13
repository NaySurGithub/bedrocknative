package net.bedrocknative.molang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

final class Queries {
   static final int ANY = -1;
   private static final Map<String, String> SHORT_NAMES = new ConcurrentHashMap<>();
   static final Map<String, EntityFlag> FLAGS = flags();
   static final Map<String, EntityDataType<?>> DATA = Map.of(
      "variant",
      EntityDataTypes.VARIANT,
      "mark_variant",
      EntityDataTypes.MARK_VARIANT,
      "skin_id",
      EntityDataTypes.SKIN_ID,
      "structural_integrity",
      EntityDataTypes.STRUCTURAL_INTEGRITY,
      "hurt_time",
      EntityDataTypes.HURT_TICKS,
      "hurt_direction",
      EntityDataTypes.HURT_DIRECTION,
      "invulnerable_ticks",
      EntityDataTypes.WITHER_INVULNERABLE_TICKS,
      "trade_tier",
      EntityDataTypes.TRADE_TIER,
      "max_trade_tier",
      EntityDataTypes.MAX_TRADE_TIER
   );

   private Queries() {
   }

   static void register(QueryRegistry.Registrar r) {
      language(r);
      timing(r);
      world(r);
      transform(r);
      data(r);
      items(r);
      geometry(r);
   }

   private static void language(QueryRegistry.Registrar r) {
      r.register("all", 1, -1, (env, args) -> matchAll(env, args, true));
      r.register("any", 1, -1, (env, args) -> matchAll(env, args, false));
      r.register("approx_eq", 0, -1, (env, args) -> {
         if (args.count() < 2) {
            return 1.0F;
         }

         float first = args.number(env, 0);

         for (int i = 1; i < args.count(); i++) {
            if (Math.abs(args.number(env, i) - first) > 1.0E-6F) {
               return 0.0F;
            }
         }

         return 1.0F;
      });
      r.register("in_range", 3, 3, (env, args) -> {
         float value = args.number(env, 0);
         return value >= args.number(env, 1) && value <= args.number(env, 2) ? 1.0F : 0.0F;
      });
      r.register("count", 0, -1, (env, args) -> {
         int count = 0;

         for (int i = 0; i < args.count(); i++) {
            count += args.object(env, i) instanceof Object[] array ? array.length : 1;
         }

         return count;
      });
      r.register("combine_entities", 0, -1, (env, args) -> {
         List<MolangActor> actors = new ArrayList<>();

         for (int i = 0; i < args.count(); i++) {
            Object value = args.object(env, i);
            if (value instanceof MolangActor actor && actor.valid() && !actors.contains(actor)) {
               actors.add(actor);
            } else if (value instanceof Object[] array) {
               for (Object element : array) {
                  if (element instanceof MolangActor actor && actor.valid() && !actors.contains(actor)) {
                     actors.add(actor);
                  }
               }
            }
         }

         env.setResult(actors.toArray(new Object[0]));
         return 0.0F;
      });
      r.register("log", 0, -1, (env, args) -> 0.0F);
      r.register("debug_output", 0, -1, (env, args) -> 0.0F);
   }

   private static float matchAll(MolangEnv env, QueryArgs args, boolean all) {
      if (args.count() < 2) {
         return 0.0F;
      }

      float first = args.number(env, 0);
      Object firstObject = env.object;

      for (int i = 1; i < args.count(); i++) {
         float value = args.number(env, i);
         Object object = env.object;
         boolean equal = firstObject == null && object == null ? value == first : firstObject != null && firstObject.equals(object);
         if (equal != all) {
            return all ? 0.0F : 1.0F;
         }
      }

      return all ? 1.0F : 0.0F;
   }

   private static void timing(QueryRegistry.Registrar r) {
      r.register("anim_time", 0, -1, (env, args) -> env.animTime);
      r.register("delta_time", 0, -1, (env, args) -> env.deltaTime);
      r.register("frame_alpha", 0, -1, (env, args) -> env.frameAlpha);
      r.register("key_frame_lerp_time", 0, -1, (env, args) -> env.keyFrameLerpTime);
      r.register("life_time", 0, -1, (env, args) -> env.actor.lifeTime());
      r.register("time_stamp", 0, -1, (env, args) -> env.actor.timeStamp());
      r.register("all_animations_finished", 0, -1, (env, args) -> env.controller != null && env.controller.allAnimationsFinished() ? 1.0F : 0.0F);
      r.register("any_animation_finished", 0, -1, (env, args) -> env.controller != null && env.controller.anyAnimationFinished() ? 1.0F : 0.0F);
      r.register("state_time", 0, 0, (env, args) -> env.controller != null ? env.controller.stateTime() : 0.0F);
   }

   private static void world(QueryRegistry.Registrar r) {
      r.register("time_of_day", 0, 0, (env, args) -> env.actor.timeOfDay());
      r.register("day", 0, 0, (env, args) -> env.actor.day());
      r.register("moon_phase", 0, 0, (env, args) -> env.actor.moonPhase());
      r.register("moon_brightness", 0, 0, (env, args) -> env.actor.moonBrightness());
      r.register("is_in_ui", 0, -1, (env, args) -> bool(env.actor.isInUi()));
      r.register("is_first_person", 0, -1, (env, args) -> bool(env.actor.isFirstPerson()));
      r.register("is_local_player", 0, -1, (env, args) -> bool(env.actor.isLocalPlayer()));
      r.register("distance_from_camera", 0, -1, (env, args) -> env.actor.distanceFromCamera());
      r.register("camera_rotation", 1, 1, (env, args) -> env.actor.cameraRotation(args.integer(env, 0)));
      r.register("rotation_to_camera", 1, 1, (env, args) -> env.actor.rotationToCamera(args.integer(env, 0)));
      r.register("actor_count", 0, -1, (env, args) -> env.actor.actorCount());
      r.register("lod_index", 0, -1, (env, args) -> {
         float distance = env.actor.distanceFromCamera();

         for (int i = 0; i < args.count(); i++) {
            if (distance <= args.number(env, i)) {
               return i;
            }
         }

         return args.count();
      });
   }

   private static void transform(QueryRegistry.Registrar r) {
      r.register("body_x_rotation", 0, -1, (env, args) -> env.actor.bodyXRotation());
      r.register("body_y_rotation", 0, -1, (env, args) -> env.actor.bodyYRotation());
      r.register("head_x_rotation", 0, -1, (env, args) -> env.actor.headXRotation(args.count() > 0 ? args.integer(env, 0) : 0));
      r.register("head_y_rotation", 0, -1, (env, args) -> env.actor.headYRotation(args.count() > 0 ? args.integer(env, 0) : 0));
      r.register("target_x_rotation", 0, -1, (env, args) -> env.actor.targetXRotation());
      r.register("target_y_rotation", 0, -1, (env, args) -> env.actor.targetYRotation());
      r.register("position", 1, 1, (env, args) -> env.actor.position(args.integer(env, 0)));
      r.register("position_delta", 1, 1, (env, args) -> env.actor.positionDelta(args.integer(env, 0)));
      r.register("movement_direction", 1, 1, (env, args) -> {
         int axis = args.integer(env, 0);
         MolangActor actor = env.actor;
         float x = actor.positionDelta(0);
         float y = actor.positionDelta(1);
         float z = actor.positionDelta(2);
         float length = (float)Math.sqrt(x * x + y * y + z * z);
         return length != 0.0F && axis >= 0 && axis <= 2 ? (axis == 0 ? x : (axis == 1 ? y : z)) / length : 0.0F;
      });
      r.register("ground_speed", 0, -1, (env, args) -> env.actor.groundSpeed());
      r.register("vertical_speed", 0, -1, (env, args) -> env.actor.verticalSpeed());
      r.register("yaw_speed", 0, -1, (env, args) -> env.actor.yawSpeed());
      r.register("modified_distance_moved", 0, -1, (env, args) -> env.actor.modifiedDistanceMoved());
      r.register("modified_move_speed", 0, -1, (env, args) -> env.actor.modifiedMoveSpeed());
      r.register("walk_distance", 0, -1, (env, args) -> env.actor.walkDistance());
      r.register("is_on_ground", 0, -1, (env, args) -> bool(env.actor.isOnGround()));
      r.register("is_moving", 0, -1, (env, args) -> bool(env.actor.isMoving()));
      r.register("is_jumping", 0, -1, (env, args) -> bool(env.actor.isJumping()));
      r.register("swim_amount", 0, 0, (env, args) -> env.actor.swimAmount());
      r.register("is_alive", 0, -1, (env, args) -> bool(env.actor.isAlive()));
      r.register("is_spectator", 0, 0, (env, args) -> bool(env.actor.isSpectator()));
      r.register("is_in_water", 0, -1, (env, args) -> bool(env.actor.isInWater()));
      r.register("is_in_lava", 0, -1, (env, args) -> bool(env.actor.isInLava()));
      r.register("is_in_water_or_rain", 0, -1, (env, args) -> bool(env.actor.isInWater()));
   }

   private static void data(QueryRegistry.Registrar r) {
      FLAGS.forEach((name, flag) -> r.register(name, 0, -1, (env, args) -> bool(env.actor.flag(flag))));
      DATA.forEach((name, type) -> r.register(name, 0, -1, (env, args) -> env.actor.data(type)));
      r.register("health", 0, 0, (env, args) -> env.actor.health());
      r.register("max_health", 0, 0, (env, args) -> env.actor.maxHealth());
      r.register("death_ticks", 0, -1, (env, args) -> env.actor.deathTicks());
      r.register("swell_amount", 0, -1, (env, args) -> env.actor.swellAmount());
      r.register("swelling_dir", 0, -1, (env, args) -> env.actor.swellingDir());
      r.register("shake_angle", 0, -1, (env, args) -> env.actor.shakeAngle());
      r.register("is_shaking_wetness", 0, -1, (env, args) -> bool(env.actor.isShakingWetness()));
      r.register("head_roll_angle", 0, -1, (env, args) -> env.actor.headRollAngle());
      r.register("tail_angle", 0, -1, (env, args) -> env.actor.tailAngle());
      r.register("is_grazing", 0, -1, (env, args) -> bool(env.actor.isGrazing()));
      r.register("is_levitating", 0, -1, (env, args) -> bool(env.actor.isLevitating()));
      r.register("eye_target_x_rotation", 0, -1, (env, args) -> env.actor.eyeTargetXRotation());
      r.register("eye_target_y_rotation", 0, -1, (env, args) -> env.actor.eyeTargetYRotation());
      r.register("is_ghost", 0, -1, (env, args) -> bool(env.actor.isGhost()));
      r.register("wing_flap_position", 0, -1, (env, args) -> env.actor.wingFlapPosition());
      r.register("wing_flap_speed", 0, -1, (env, args) -> env.actor.wingFlapSpeed());
      r.register("is_carrying_block", 0, -1, (env, args) -> bool(env.actor.isCarryingBlock()));
      r.register("has_any_leashed_entity_of_type", 1, -1, (env, args) -> {
         for (int i = 0; i < args.count(); i++) {
            if (env.actor.leashes(args.string(env, i))) {
               return 1.0F;
            }
         }

         return 0.0F;
      });
      r.register("current_squish_value", 0, -1, (env, args) -> env.actor.squish(true));
      r.register("previous_squish_value", 0, -1, (env, args) -> env.actor.squish(false));
      r.register("fuse_time", 0, -1, (env, args) -> env.actor.fuseTime());
      r.register("sit_amount", 0, -1, (env, args) -> env.actor.sitAmount());
      r.register("lie_amount", 0, -1, (env, args) -> env.actor.lieAmount());
      r.register("roll_counter", 0, -1, (env, args) -> env.actor.rollCounter());
      r.register("sneeze_counter", 0, -1, (env, args) -> env.actor.sneezeCounter());
      r.register("unhappy_counter", 0, -1, (env, args) -> 0.0F);
      r.register("standing_scale", 0, -1, (env, args) -> env.actor.standingScale());
      r.register("heartbeat_phase", 0, 0, (env, args) -> env.actor.heartbeatPhase());
      r.register("heartbeat_interval", 0, 0, (env, args) -> env.actor.heartbeatInterval());
      r.register("time_since_last_vibration_detection", 0, 0, (env, args) -> env.actor.timeSinceLastVibration());
      r.register("is_shield_powered", 0, -1, (env, args) -> bool(env.actor.isShieldPowered()));
      r.register("overlay_alpha", 0, -1, (env, args) -> 0.0F);
      r.register("shake_time", 0, -1, (env, args) -> env.actor.shakeTime());
      r.register("life_span", 0, -1, (env, args) -> env.actor.lifeSpan());
      r.register("is_attached_to_entity", 0, -1, (env, args) -> bool(env.actor.isAttachedToEntity()));
      r.register("texture_frame_index", 0, -1, (env, args) -> env.actor.textureFrameIndex());
      r.register("model_scale", 0, -1, (env, args) -> env.actor.modelScale());
      r.register("has_target", 0, -1, (env, args) -> bool(env.actor.hasTarget()));
      r.register("has_owner", 0, -1, (env, args) -> bool(env.actor.hasOwner()));
      r.register("has_rider", 0, -1, (env, args) -> bool(env.actor.hasRider()));
      r.register("property", 1, 1, (env, args) -> {
         Object value = env.actor.property(args.string(env, 0));

         return switch (value) {
            case Number number -> number.floatValue();
            case Boolean bool -> bool(bool);
            case String text -> {
               env.setResult(text);
               yield 0.0F;
            }
            case null, default -> 0.0F;
         };
      });
      r.register("has_property", 1, 1, (env, args) -> bool(env.actor.property(args.string(env, 0)) != null));
      r.register("get_name", 0, 1, (env, args) -> string(env, env.actor.name()));
      r.register("is_name_any", 1, -1, (env, args) -> anyString(env, args, 0, env.actor.name()));
      r.register("owner_identifier", 0, 0, (env, args) -> string(env, owner(env)));
      r.register("is_owner_identifier_any", 1, -1, (env, args) -> anyString(env, args, 0, owner(env)));
      r.register("is_riding_any_entity_of_type", 0, -1, (env, args) -> {
         String vehicle = env.actor.vehicleIdentifier();
         return vehicle.isEmpty() ? 0.0F : anyString(env, args, 0, vehicle);
      });
      r.register("cape_flap_amount", 0, -1, (env, args) -> env.actor.capeFlapAmount());
      r.register("has_cape", 0, -1, (env, args) -> bool(env.actor.hasCape()));
      r.register("is_persona_or_premium_skin", 0, 0, (env, args) -> bool(env.actor.isPersonaOrPremiumSkin()));
      r.register("sleep_rotation", 0, 0, (env, args) -> env.actor.sleepRotation());
      r.register("base_swing_duration", 0, -1, (env, args) -> env.actor.baseSwingDuration());
   }

   private static String owner(MolangEnv env) {
      return env.attachable ? env.actor.identifier() : env.actor.ownerIdentifier();
   }

   private static void items(QueryRegistry.Registrar r) {
      r.register("is_item_name_any", 1, -1, (env, args) -> {
         String slot = args.string(env, 0);
         int index = 0;
         int first = 1;
         if (args.count() > 1) {
            float number = args.number(env, 1);
            if (env.object == null) {
               index = (int)number;
               first = 2;
            }
         }

         return anyString(env, args, first, env.actor.itemName(slot, index));
      });
      r.register(
         "get_equipped_item_name",
         0,
         2,
         (env, args) -> string(
            env, SHORT_NAMES.computeIfAbsent(env.actor.itemName(handSlot(args.count() > 0 ? args.slot(env, 0) : "main_hand"), 0), Queries::withoutNamespace)
         )
      );
      r.register("is_item_equipped", 0, 1, (env, args) -> bool(!env.actor.itemName(handSlot(args.count() > 0 ? args.slot(env, 0) : "main_hand"), 0).isEmpty()));
      r.register("equipped_item_any_tag", 1, -1, (env, args) -> itemTags(env, args, false));
      r.register("equipped_item_all_tags", 1, -1, (env, args) -> itemTags(env, args, true));
      r.register("equipment_count", 0, 0, (env, args) -> env.actor.equipmentCount());
      r.register("has_armor_slot", 1, 1, (env, args) -> bool(env.actor.hasArmorSlot(args.integer(env, 0))));
      r.register("armor_texture_slot", 1, 1, (env, args) -> env.actor.armorTextureSlot(args.integer(env, 0)));
      r.register("armor_color_slot", 2, 2, (env, args) -> env.actor.armorColorSlot(args.integer(env, 0), args.integer(env, 1)));
      r.register("has_head_gear", 0, -1, (env, args) -> bool(!env.actor.itemName("slot.armor.head", 0).isEmpty()));
      r.register("main_hand_item_use_duration", 0, -1, (env, args) -> env.actor.mainHandItemUseDuration());
      r.register("main_hand_item_max_duration", 0, -1, (env, args) -> env.actor.mainHandItemMaxDuration());
      r.register("item_is_charged", 0, 1, (env, args) -> bool(env.actor.itemIsCharged(handSlot(args.count() > 0 ? args.slot(env, 0) : "main_hand"))));
      r.register("item_remaining_use_duration", 0, -1, (env, args) -> {
         String slot = handSlot(args.count() > 0 ? args.slot(env, 0) : "main_hand");
         float remaining = env.actor.itemRemainingUseTicks(slot);
         if (args.count() > 1) {
            float max = env.actor.itemMaxUseTicks(slot);
            return max > 0.0F ? remaining / max * args.number(env, 1) : 0.0F;
         } else {
            return remaining / 20.0F;
         }
      });
      r.register("item_slot_to_bone_name", 1, 1, (env, args) -> string(env, env.actor.itemSlotToBoneName(args.string(env, 0))));
   }

   private static void geometry(QueryRegistry.Registrar r) {
      r.register("bone_origin", 1, 1, (env, args) -> boneStruct(env, "origin", args.string(env, 0)));
      r.register("bone_rotation", 1, 1, (env, args) -> boneStruct(env, "rotation", args.string(env, 0)));
      r.register("bone_aabb", 1, 1, (env, args) -> boneStruct(env, "aabb", args.string(env, 0)));
      r.register("get_default_bone_pivot", 0, -1, (env, args) -> {
         String bone = args.string(env, 0);
         int axis = args.integer(env, 1);
         return env.geometry != null ? env.geometry.defaultBonePivot(bone, axis) : 0.0F;
      });
      r.register("get_locator_offset", 0, -1, (env, args) -> {
         String locator = args.string(env, 0);
         int axis = args.integer(env, 1);
         return env.geometry != null ? env.geometry.locatorOffset(locator, axis) : 0.0F;
      });
      r.register("get_root_locator_offset", 2, 2, (env, args) -> {
         String locator = args.string(env, 0);
         int axis = args.integer(env, 1);
         return env.geometry != null ? env.geometry.rootLocatorOffset(locator, axis) : 0.0F;
      });
   }

   private static float boneStruct(MolangEnv env, String query, String bone) {
      MolangStruct struct = env.geometry != null ? env.geometry.bone(query, bone) : null;
      if (struct != null) {
         env.setResult(struct);
      }

      return 0.0F;
   }

   private static float itemTags(MolangEnv env, QueryArgs args, boolean all) {
      String slot = args.string(env, 0);
      if (args.count() < 2) {
         return 0.0F;
      }

      for (int i = 1; i < args.count(); i++) {
         boolean has = env.actor.itemHasTag(slot, 0, args.string(env, i));
         if (has != all) {
            return all ? 0.0F : 1.0F;
         }
      }

      return all ? 1.0F : 0.0F;
   }

   private static String withoutNamespace(String item) {
      int colon = item.indexOf(58);
      return colon >= 0 ? item.substring(colon + 1) : item;
   }

   private static String handSlot(String hand) {
      return switch (hand) {
         case "main_hand" -> "slot.weapon.mainhand";
         case "off_hand" -> "slot.weapon.offhand";
         default -> hand;
      };
   }

   private static float anyString(MolangEnv env, QueryArgs args, int first, String value) {
      for (int i = first; i < args.count(); i++) {
         if (args.string(env, i).equals(value)) {
            return 1.0F;
         }
      }

      return 0.0F;
   }

   private static float string(MolangEnv env, @Nullable String value) {
      env.setResult(value != null ? value : "");
      return 0.0F;
   }

   private static float bool(boolean value) {
      return value ? 1.0F : 0.0F;
   }

   private static Map<String, EntityFlag> flags() {
      Map<String, EntityFlag> flags = new LinkedHashMap<>();
      flags.put("is_on_fire", EntityFlag.ON_FIRE);
      flags.put("is_onfire", EntityFlag.ON_FIRE);
      flags.put("is_sneaking", EntityFlag.SNEAKING);
      flags.put("is_riding", EntityFlag.RIDING);
      flags.put("is_sprinting", EntityFlag.SPRINTING);
      flags.put("is_using_item", EntityFlag.USING_ITEM);
      flags.put("is_invisible", EntityFlag.INVISIBLE);
      flags.put("is_in_love", EntityFlag.IN_LOVE);
      flags.put("is_saddled", EntityFlag.SADDLED);
      flags.put("is_powered", EntityFlag.POWERED);
      flags.put("is_ignited", EntityFlag.IGNITED);
      flags.put("is_baby", EntityFlag.BABY);
      flags.put("is_critical", EntityFlag.CRITICAL);
      flags.put("is_silent", EntityFlag.SILENT);
      flags.put("is_wall_climbing", EntityFlag.WALL_CLIMBING);
      flags.put("can_climb", EntityFlag.CAN_CLIMB);
      flags.put("can_swim", EntityFlag.CAN_SWIM);
      flags.put("can_fly", EntityFlag.CAN_FLY);
      flags.put("can_walk", EntityFlag.CAN_WALK);
      flags.put("is_resting", EntityFlag.RESTING);
      flags.put("is_sitting", EntityFlag.SITTING);
      flags.put("is_angry", EntityFlag.ANGRY);
      flags.put("is_interested", EntityFlag.INTERESTED);
      flags.put("is_charged", EntityFlag.CHARGED);
      flags.put("is_tamed", EntityFlag.TAMED);
      flags.put("is_orphaned", EntityFlag.ORPHANED);
      flags.put("is_leashed", EntityFlag.LEASHED);
      flags.put("is_sheared", EntityFlag.SHEARED);
      flags.put("is_gliding", EntityFlag.GLIDING);
      flags.put("is_elder", EntityFlag.ELDER);
      flags.put("is_breathing", EntityFlag.BREATHING);
      flags.put("is_chested", EntityFlag.CHESTED);
      flags.put("is_stackable", EntityFlag.STACKABLE);
      flags.put("show_bottom", EntityFlag.SHOW_BOTTOM);
      flags.put("is_standing", EntityFlag.STANDING);
      flags.put("is_shaking", EntityFlag.SHAKING);
      flags.put("is_idling", EntityFlag.IDLING);
      flags.put("is_casting", EntityFlag.CASTING);
      flags.put("is_charging", EntityFlag.CHARGING);
      flags.put("can_power_jump", EntityFlag.CAN_POWER_JUMP);
      flags.put("is_lingering", EntityFlag.LINGERING);
      flags.put("has_collision", EntityFlag.HAS_COLLISION);
      flags.put("has_gravity", EntityFlag.HAS_GRAVITY);
      flags.put("is_fire_immune", EntityFlag.FIRE_IMMUNE);
      flags.put("is_dancing", EntityFlag.DANCING);
      flags.put("is_enchanted", EntityFlag.ENCHANTED);
      flags.put("is_transforming", EntityFlag.IS_TRANSFORMING);
      flags.put("can_damage_nearby_mobs", EntityFlag.DAMAGE_NEARBY_MOBS);
      flags.put("is_swimming", EntityFlag.SWIMMING);
      flags.put("is_bribed", EntityFlag.BRIBED);
      flags.put("is_pregnant", EntityFlag.IS_PREGNANT);
      flags.put("is_laying_egg", EntityFlag.LAYING_EGG);
      flags.put("is_eating", EntityFlag.EATING);
      flags.put("is_laying_down", EntityFlag.LAYING_DOWN);
      flags.put("is_sneezing", EntityFlag.SNEEZING);
      flags.put("is_rolling", EntityFlag.ROLLING);
      flags.put("is_scared", EntityFlag.SCARED);
      flags.put("blocking", EntityFlag.BLOCKING);
      flags.put("is_sleeping", EntityFlag.SLEEPING);
      flags.put("is_illager_captain", EntityFlag.IS_ILLAGER_CAPTAIN);
      flags.put("is_stunned", EntityFlag.STUNNED);
      flags.put("is_roaring", EntityFlag.ROARING);
      flags.put("is_delayed_attacking", EntityFlag.DELAYED_ATTACK);
      flags.put("is_avoiding_mobs", EntityFlag.IS_AVOIDING_MOBS);
      flags.put("is_avoiding_block", EntityFlag.IS_AVOIDING_BLOCK);
      flags.put("facing_target_to_range_attack", EntityFlag.FACING_TARGET_TO_RANGE_ATTACK);
      flags.put("is_stalking", EntityFlag.STALKING);
      flags.put("is_emoting", EntityFlag.EMOTING);
      flags.put("is_celebrating", EntityFlag.CELEBRATING);
      flags.put("is_admiring", EntityFlag.ADMIRING);
      flags.put("is_celebrating_special", EntityFlag.CELEBRATING_SPECIAL);
      flags.put("out_of_control", EntityFlag.OUT_OF_CONTROL);
      flags.put("is_ram_attacking", EntityFlag.RAM_ATTACK);
      flags.put("is_playing_dead", EntityFlag.PLAYING_DEAD);
      flags.put("is_croaking", EntityFlag.CROAKING);
      flags.put("is_eating_mob", EntityFlag.EAT_MOB);
      flags.put("is_jump_goal_jumping", EntityFlag.JUMP_GOAL_JUMP);
      flags.put("is_emerging", EntityFlag.EMERGING);
      flags.put("is_sniffing", EntityFlag.SNIFFING);
      flags.put("is_digging", EntityFlag.DIGGING);
      flags.put("is_sonic_boom", EntityFlag.SONIC_BOOM);
      flags.put("can_dash", EntityFlag.CAN_DASH);
      flags.put("has_dash_cooldown", EntityFlag.HAS_DASH_COOLDOWN);
      flags.put("is_scenting", EntityFlag.SCENTING);
      flags.put("is_rising", EntityFlag.RISING);
      flags.put("is_feeling_happy", EntityFlag.FEELING_HAPPY);
      flags.put("is_searching", EntityFlag.SEARCHING);
      flags.put("is_crawling", EntityFlag.CRAWLING);
      flags.put("timer_flag_1", EntityFlag.TIMER_FLAG_1);
      flags.put("timer_flag_2", EntityFlag.TIMER_FLAG_2);
      flags.put("timer_flag_3", EntityFlag.TIMER_FLAG_3);
      return flags;
   }
}
