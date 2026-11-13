package net.bedrocknative.world.biome;

import java.util.List;
import java.util.Map;

public final class BiomeFallbacks {
   private static final Map<String, String> JAVA_BY_BEDROCK_NAME = Map.ofEntries(
      Map.entry("legacy_frozen_ocean", "frozen_ocean"),
      Map.entry("deep_warm_ocean", "warm_ocean"),
      Map.entry("ice_mountains", "snowy_plains"),
      Map.entry("cold_taiga_hills", "snowy_taiga"),
      Map.entry("cold_taiga_mutated", "snowy_taiga"),
      Map.entry("mushroom_island_shore", "mushroom_fields"),
      Map.entry("desert_hills", "desert"),
      Map.entry("desert_mutated", "desert"),
      Map.entry("mesa_plateau", "desert"),
      Map.entry("mesa_plateau_mutated", "badlands"),
      Map.entry("mesa_plateau_stone_mutated", "wooded_badlands"),
      Map.entry("forest_hills", "forest"),
      Map.entry("birch_forest_hills_mutated", "forest"),
      Map.entry("roofed_forest_mutated", "forest"),
      Map.entry("birch_forest_hills", "birch_forest"),
      Map.entry("taiga_hills", "taiga"),
      Map.entry("taiga_mutated", "taiga"),
      Map.entry("mega_taiga_hills", "old_growth_pine_taiga"),
      Map.entry("redwood_taiga_hills_mutated", "old_growth_pine_taiga"),
      Map.entry("extreme_hills_edge", "windswept_hills"),
      Map.entry("extreme_hills_plus_trees_mutated", "windswept_gravelly_hills"),
      Map.entry("jungle_hills", "jungle"),
      Map.entry("jungle_mutated", "jungle"),
      Map.entry("bamboo_jungle_hills", "bamboo_jungle"),
      Map.entry("jungle_edge_mutated", "sparse_jungle"),
      Map.entry("swampland_mutated", "swamp"),
      Map.entry("savanna_plateau_mutated", "stony_peaks")
   );

   private BiomeFallbacks() {
   }

   public static String javaFor(String bedrockName) {
      return JAVA_BY_BEDROCK_NAME.get(withoutNamespace(bedrockName));
   }

   public static List<String> bedrockNames() {
      return JAVA_BY_BEDROCK_NAME.keySet().stream().sorted().toList();
   }

   static String withoutNamespace(String name) {
      return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
   }
}
