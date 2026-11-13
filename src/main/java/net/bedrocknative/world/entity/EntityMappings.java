package net.bedrocknative.world.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public final class EntityMappings {
   private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("minecraft:boat", "minecraft:oak_boat"),
      Map.entry("minecraft:breeze_wind_charge_projectile", "minecraft:breeze_wind_charge"),
      Map.entry("minecraft:chest_boat", "minecraft:oak_chest_boat"),
      Map.entry("minecraft:ender_crystal", "minecraft:end_crystal"),
      Map.entry("minecraft:evocation_fang", "minecraft:evoker_fangs"),
      Map.entry("minecraft:evocation_illager", "minecraft:evoker"),
      Map.entry("minecraft:eye_of_ender_signal", "minecraft:eye_of_ender"),
      Map.entry("minecraft:fireworks_rocket", "minecraft:firework_rocket"),
      Map.entry("minecraft:fishing_hook", "minecraft:fishing_bobber"),
      Map.entry("minecraft:npc", "minecraft:villager"),
      Map.entry("minecraft:thrown_trident", "minecraft:trident"),
      Map.entry("minecraft:tropicalfish", "minecraft:tropical_fish"),
      Map.entry("minecraft:villager_v2", "minecraft:villager"),
      Map.entry("minecraft:wind_charge_projectile", "minecraft:wind_charge"),
      Map.entry("minecraft:wither_skull_dangerous", "minecraft:wither_skull"),
      Map.entry("minecraft:xp_bottle", "minecraft:experience_bottle"),
      Map.entry("minecraft:xp_orb", "minecraft:experience_orb"),
      Map.entry("minecraft:zombie_pigman", "minecraft:zombified_piglin"),
      Map.entry("minecraft:zombie_villager_v2", "minecraft:zombie_villager")
   );
   private static final Map<String, Optional<EntityType<?>>> CACHE = new ConcurrentHashMap<>();
   private static final Map<String, String> REVERSE = reverse();

   private EntityMappings() {
   }

   public static String toBedrock(EntityType<?> type) {
      String java = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
      return REVERSE.getOrDefault(java, java);
   }

   private static Map<String, String> reverse() {
      Map<String, String> reverse = new HashMap<>();
      ALIASES.entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry -> reverse.putIfAbsent(entry.getValue(), entry.getKey()));
      reverse.put("minecraft:villager", "minecraft:villager_v2");
      reverse.put("minecraft:zombie_villager", "minecraft:zombie_villager_v2");
      return Map.copyOf(reverse);
   }

   public static Optional<EntityType<?>> toJava(String bedrockIdentifier) {
      return CACHE.computeIfAbsent(bedrockIdentifier, name -> {
         Identifier id = Identifier.tryParse(ALIASES.getOrDefault(name, name));
         Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
         if (type.isEmpty()) {
            BedrockNative.LOGGER.info("No Java entity type for Bedrock entity {}: shown as a custom entity", name);
         }

         return type;
      });
   }
}
