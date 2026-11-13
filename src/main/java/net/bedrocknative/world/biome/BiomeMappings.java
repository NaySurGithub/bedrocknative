package net.bedrocknative.world.biome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class BiomeMappings {
   private static final String RESOURCE = "/assets/bedrocknative/mappings/geyser-mappings/biomes.json";
   private static final String IDS = "/assets/bedrocknative/mappings/bedrock-biomes/biome_ids.json";
   private static final Map<Integer, String> NEWER = Map.of(195, "minecraft:dappled_forest");
   private static volatile BiomeMappings.Tables tables;

   private BiomeMappings() {
   }

   public static ResourceKey<Biome> toJava(int bedrockId) {
      return (ResourceKey<Biome>)tables().byBedrockId().get(bedrockId);
   }

   public static ResourceKey<Biome> toJava(String bedrockName) {
      Integer id = tables().idsByName().get(BiomeFallbacks.withoutNamespace(bedrockName));
      return id == null ? null : toJava(id);
   }

   public static boolean isApproximate(int bedrockId) {
      return tables().approximate().containsKey(bedrockId);
   }

   public static int[] bedrockIds() {
      return tables().names().keySet().intStream().sorted().toArray();
   }

   public static String bedrockName(int bedrockId) {
      return (String)tables().names().get(bedrockId);
   }

   private static BiomeMappings.Tables tables() {
      BiomeMappings.Tables result = tables;
      if (result == null) {
         synchronized (BiomeMappings.class) {
            result = tables;
            if (result == null) {
               tables = result = load();
            }
         }
      }

      return result;
   }

   private static BiomeMappings.Tables load() {
      Int2ObjectMap<ResourceKey<Biome>> map = new Int2ObjectOpenHashMap();

      for (Entry<String, JsonElement> entry : read("/assets/bedrocknative/mappings/geyser-mappings/biomes.json").entrySet()) {
         int bedrockId = entry.getValue().getAsJsonObject().get("bedrock_id").getAsInt();
         map.putIfAbsent(bedrockId, key(entry.getKey()));
      }

      NEWER.forEach((bedrockIdx, namex) -> map.putIfAbsent(bedrockIdx, key(namex)));
      Int2ObjectMap<String> names = new Int2ObjectOpenHashMap();
      Map<String, Integer> idsByName = new HashMap<>();
      Int2ObjectMap<ResourceKey<Biome>> approximate = new Int2ObjectOpenHashMap();

      for (Entry<String, JsonElement> entry : read("/assets/bedrocknative/mappings/bedrock-biomes/biome_ids.json").entrySet()) {
         int bedrockId = Integer.parseInt(entry.getKey());
         String name = entry.getValue().getAsString();
         names.put(bedrockId, name);
         idsByName.put(name, bedrockId);
         String closest = BiomeFallbacks.javaFor(name);
         if (closest != null && !map.containsKey(bedrockId)) {
            ResourceKey<Biome> key = key(closest);
            map.put(bedrockId, key);
            approximate.put(bedrockId, key);
         }
      }

      return new BiomeMappings.Tables(map, approximate, names, Map.copyOf(idsByName));
   }

   private static ResourceKey<Biome> key(String javaName) {
      return ResourceKey.create(Registries.BIOME, Identifier.parse(javaName.indexOf(58) >= 0 ? javaName : "minecraft:" + javaName));
   }

   private static JsonObject read(String resource) {
      try (InputStream in = BiomeMappings.class.getResourceAsStream(resource)) {
         if (in == null) {
            throw new IllegalStateException("Missing " + resource);
         } else {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
         }
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   private record Tables(
      Int2ObjectMap<ResourceKey<Biome>> byBedrockId, Int2ObjectMap<ResourceKey<Biome>> approximate, Int2ObjectMap<String> names, Map<String, Integer> idsByName
   ) {
   }
}
