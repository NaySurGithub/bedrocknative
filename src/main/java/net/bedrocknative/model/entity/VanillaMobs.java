package net.bedrocknative.model.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.bedrocknative.molang.QueryRegistry;

public final class VanillaMobs {
   public static final Set<String> COMMON = Set.of(
      "attack_time",
      "is_first_person",
      "is_paperdoll",
      "map_face_icon",
      "gliding_speed_value",
      "swim_amount",
      "is_holding_right",
      "is_holding_left",
      "player_x_rotation",
      "has_target",
      "damage_nearby_mobs",
      "charge_amount",
      "is_brandishing_spear",
      "is_holding_spyglass",
      "use_item_interval_progress",
      "use_item_startup_progress",
      "raise_arms",
      "left_arm_swim_amount",
      "right_arm_swim_amount",
      "leftarmswim_amount",
      "rightarmswim_amount"
   );
   public static final int DRAGON_HISTORY = 64;
   public static final Map<String, Set<String>> BY_TYPE = Map.ofEntries(
      Map.entry("minecraft:chicken", Set.of("wing_flap")),
      Map.entry("minecraft:iron_golem", Set.of("attack_animation_tick", "offer_flower_tick")),
      Map.entry("minecraft:cat", Set.of("state", "liedownamount", "liedownamounttail", "lieonplayer")),
      Map.entry("minecraft:ocelot", Set.of("state", "liedownamount", "liedownamounttail", "lieonplayer")),
      Map.entry("minecraft:horse", Set.of("stand_anim", "shake_tail")),
      Map.entry("minecraft:donkey", Set.of("stand_anim", "shake_tail")),
      Map.entry("minecraft:mule", Set.of("stand_anim", "shake_tail")),
      Map.entry("minecraft:skeleton_horse", Set.of("stand_anim", "shake_tail")),
      Map.entry("minecraft:zombie_horse", Set.of("stand_anim", "shake_tail")),
      Map.entry("minecraft:witch", Set.of("isholdingitem")),
      Map.entry("minecraft:ghast", Set.of("ischarging")),
      Map.entry("minecraft:cod", Set.of("animationamount", "animationamountprev")),
      Map.entry("minecraft:salmon", Set.of("animationamount", "animationamountprev")),
      Map.entry("minecraft:pufferfish", Set.of("animationamount", "animationamountprev")),
      Map.entry("minecraft:tropicalfish", Set.of("animationamount", "animationamountprev", "tropicalfish")),
      Map.entry("minecraft:squid", Set.of("squid")),
      Map.entry("minecraft:glow_squid", Set.of("squid", "glow_alpha")),
      Map.entry("minecraft:goat", Set.of("goat_has_left_horn", "goat_has_right_horn", "should_bow_head")),
      Map.entry("minecraft:llama", Set.of("decortextureindex")),
      Map.entry("minecraft:trader_llama", Set.of("decortextureindex")),
      Map.entry("minecraft:piglin", Set.of("attack_state", "crossbow_charge")),
      Map.entry("minecraft:piglin_brute", Set.of("attack_state", "crossbow_charge")),
      Map.entry("minecraft:pillager", Set.of("attack_state", "crossbow_charge")),
      Map.entry("minecraft:ender_dragon", dragonHistory())
   );
   public static final Set<String> ACTORS = Set.of(
      "minecraft:armor_stand",
      "minecraft:snowball",
      "minecraft:egg",
      "minecraft:ender_pearl",
      "minecraft:splash_potion",
      "minecraft:lingering_potion",
      "minecraft:xp_bottle",
      "minecraft:fireball",
      "minecraft:small_fireball",
      "minecraft:dragon_fireball",
      "minecraft:eye_of_ender_signal",
      "minecraft:xp_orb",
      "minecraft:shulker_bullet",
      "minecraft:wind_charge_projectile",
      "minecraft:breeze_wind_charge_projectile",
      "minecraft:ender_crystal",
      "minecraft:leash_knot",
      "minecraft:arrow",
      "minecraft:fireworks_rocket",
      "minecraft:wither_skull",
      "minecraft:wither_skull_dangerous",
      "minecraft:llama_spit"
   );
   public static final Set<String> HARMLESS_QUERIES = Set.of(
      "kinetic_weapon_damage_duration", "kinetic_weapon_delay", "kinetic_weapon_dismount_duration", "kinetic_weapon_knockback_duration"
   );

   private static Set<String> dragonHistory() {
      Set<String> names = new HashSet<>();

      for (int i = 0; i < 64; i++) {
         names.add("historical_frame_" + i);
      }

      return Set.copyOf(names);
   }

   private VanillaMobs() {
   }

   public static VanillaMobs.Support decide(ModelRegistry registry, String identifier) {
      ClientEntityDef definition = registry.entity(identifier);
      return definition == null
         ? new VanillaMobs.Support(identifier, false, List.of("no client entity in the pack stack"), List.of())
         : decide(registry, definition, BY_TYPE.getOrDefault(identifier, Set.of()));
   }

   public static VanillaMobs.Support decide(ModelRegistry registry, ClientEntityDef definition, Set<String> kindVariables) {
      String identifier = definition.identifier();
      if (definition.renderControllers().isEmpty()) {
         return new VanillaMobs.Support(identifier, false, List.of("no render controller"), List.of());
      } else {
         DefinitionNeeds.Needs needs = DefinitionNeeds.of(registry, definition);
         List<String> missing = new ArrayList<>();
         if (!needs.anyGeometry()) {
            missing.add("geometry " + definition.geometries().values());
         }

         needs.missingRenderControllers().forEach(name -> missing.add("render controller " + name));

         for (String query : needs.queries()) {
            QueryRegistry.Entry entry = QueryRegistry.get(query);
            if ((entry == null || entry.stub()) && !HARMLESS_QUERIES.contains(query)) {
               missing.add("query." + query);
            }
         }

         Set<String> engine = new TreeSet<>(needs.engineVariables());
         engine.removeAll(COMMON);
         engine.removeAll(kindVariables);
         engine.forEach(name -> missing.add("variable." + name));
         return new VanillaMobs.Support(identifier, missing.isEmpty(), List.copyOf(missing), needs.missingAnimations());
      }
   }

   public record Support(String identifier, boolean drawn, List<String> missing, List<String> skipped) {
      public String reason() {
         if (!this.drawn) {
            return "drawn by Java: " + String.join(", ", this.missing);
         } else {
            return this.skipped.isEmpty() ? "drawn by the model runtime" : "drawn by the model runtime (no animation " + String.join(", ", this.skipped) + ")";
         }
      }
   }
}
