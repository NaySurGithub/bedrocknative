package net.bedrocknative.world.particle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.world.NameMatch;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ParticleMappings {
   private static final String RESOURCE = "/assets/bedrocknative/mappings/geyser-mappings/particles.json";
   private static final Map<String, String> EXTRA = Map.of(
      "minecraft:crop_growth_emitter",
      "happy_villager",
      "minecraft:crop_growth_area_emitter",
      "happy_villager",
      "minecraft:explosion_particle",
      "poof",
      "minecraft:knockback_roar_particle",
      "poof",
      "minecraft:mobflame_emitter",
      "flame",
      "minecraft:water_evaporation_manual",
      "cloud",
      "minecraft:enchanted_hit_multiple_emitter",
      "enchanted_hit"
   );
   private static volatile ParticleMappings instance;
   private final Map<String, ParticleType<?>> byBedrockId;
   private final Map<String, ParticleType<?>> byEventType;

   private ParticleMappings(Map<String, ParticleType<?>> byBedrockId, Map<String, ParticleType<?>> byEventType) {
      this.byBedrockId = byBedrockId;
      this.byEventType = byEventType;
   }

   public static ParticleMappings get() {
      ParticleMappings result = instance;
      if (result == null) {
         synchronized (ParticleMappings.class) {
            result = instance;
            if (result == null) {
               instance = result = load();
            }
         }
      }

      return result;
   }

   public ParticleType<?> byBedrockId(String identifier) {
      return this.byBedrockId.get(identifier);
   }

   public ParticleType<?> byEventType(String type) {
      return this.byEventType.get(type);
   }

   public int size() {
      return this.byBedrockId.size() + this.byEventType.size();
   }

   private static ParticleMappings load() {
      try {
         ParticleMappings var1;
         try (InputStream in = ParticleMappings.class.getResourceAsStream("/assets/bedrocknative/mappings/geyser-mappings/particles.json")) {
            if (in == null) {
               throw new IllegalStateException("Missing /assets/bedrocknative/mappings/geyser-mappings/particles.json");
            }

            var1 = build(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
         }

         return var1;
      } catch (IOException var5) {
         throw new UncheckedIOException(var5);
      }
   }

   static ParticleMappings build(JsonObject root) {
      Map<String, List<String>> byBedrockId = new HashMap<>();
      Map<String, List<String>> byEventType = new HashMap<>();

      for (Entry<String, JsonElement> entry : root.entrySet()) {
         String java = entry.getKey().toLowerCase(Locale.ROOT);
         if (java(java) != null) {
            JsonObject mapping = entry.getValue().getAsJsonObject();
            JsonElement var8 = mapping.get("bedrockId");
            if (var8 instanceof JsonElement && var8.getAsString().startsWith("minecraft:")) {
               byBedrockId.computeIfAbsent(var8.getAsString(), k -> new ArrayList<>()).add(java);
            }

            var8 = mapping.get("eventType");
            if (var8 instanceof JsonElement) {
               byEventType.computeIfAbsent(var8.getAsString(), k -> new ArrayList<>()).add(java);
            }
         }
      }

      Map<String, ParticleType<?>> ids = best(byBedrockId);
      EXTRA.forEach((bedrock, javax) -> ids.putIfAbsent(bedrock, java(javax)));
      return new ParticleMappings(ids, best(byEventType));
   }

   private static Map<String, ParticleType<?>> best(Map<String, List<String>> candidates) {
      Map<String, ParticleType<?>> result = new HashMap<>();
      candidates.forEach((bedrock, java) -> result.put(bedrock, java(NameMatch.best(bedrock, java))));
      return result;
   }

   private static ParticleType<?> java(String name) {
      return (ParticleType<?>)BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.withDefaultNamespace(name));
   }
}
