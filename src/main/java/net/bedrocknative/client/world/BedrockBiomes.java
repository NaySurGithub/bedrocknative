package net.bedrocknative.client.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.biome.BiomeClimates;
import net.bedrocknative.world.biome.BiomeMappings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

public final class BedrockBiomes {
   private static final List<ResourceKey<Biome>> CLIMATE_CANDIDATES = List.of(
      Biomes.PLAINS,
      Biomes.FOREST,
      Biomes.BIRCH_FOREST,
      Biomes.TAIGA,
      Biomes.SNOWY_TAIGA,
      Biomes.SNOWY_PLAINS,
      Biomes.OLD_GROWTH_PINE_TAIGA,
      Biomes.WINDSWEPT_HILLS,
      Biomes.DESERT,
      Biomes.SAVANNA,
      Biomes.JUNGLE,
      Biomes.SPARSE_JUNGLE,
      Biomes.SWAMP,
      Biomes.MUSHROOM_FIELDS,
      Biomes.MEADOW,
      Biomes.STONY_PEAKS,
      Biomes.OCEAN,
      Biomes.COLD_OCEAN,
      Biomes.FROZEN_OCEAN,
      Biomes.WARM_OCEAN
   );
   private final Registry<Biome> registry;
   private final Holder<Biome> fallback;
   private final Int2ObjectMap<Holder<Biome>> holders = new Int2ObjectOpenHashMap();
   private final Int2ObjectMap<String> serverNames;
   private final Int2ObjectMap<BiomeClimates.Climate> serverClimates;
   private List<BiomeClimates.Candidate<Holder<Biome>>> climateCandidates;

   public BedrockBiomes(RegistryAccess access, Int2ObjectMap<String> serverNames, Int2ObjectMap<BiomeClimates.Climate> serverClimates) {
      this.registry = access.lookupOrThrow(Registries.BIOME);
      this.fallback = this.registry.getOrThrow(Biomes.PLAINS);
      this.serverNames = serverNames;
      this.serverClimates = serverClimates;
   }

   public PalettedContainer<Holder<Biome>> container(PalettedContainerFactory factory, int[] bedrockBiomes) {
      PalettedContainer<Holder<Biome>> container = factory.createForBiomes();

      for (int y = 0; y < 4; y++) {
         for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
               int id = bedrockBiomes[y * 4 + 2 << 8 | z * 4 + 2 << 4 | x * 4 + 2];
               container.getAndSetUnchecked(x, y, z, this.holder(id));
            }
         }
      }

      return container;
   }

   private synchronized Holder<Biome> holder(int bedrockId) {
      Holder<Biome> holder = (Holder<Biome>)this.holders.get(bedrockId);
      if (holder == null) {
         holder = this.resolve(bedrockId);
         this.holders.put(bedrockId, holder);
      }

      return holder;
   }

   private Holder<Biome> resolve(int bedrockId) {
      ResourceKey<Biome> key = BiomeMappings.toJava(bedrockId);
      String serverName = (String)this.serverNames.get(bedrockId);
      if (key == null && serverName != null) {
         key = BiomeMappings.toJava(serverName);
         if (key == null) {
            Identifier id = Identifier.tryParse(serverName);
            key = id == null ? null : ResourceKey.create(Registries.BIOME, id);
         }
      }

      Holder<Biome> holder = key == null ? null : this.registry.get(key).orElse(null);
      if (holder != null) {
         if (BiomeMappings.isApproximate(bedrockId)) {
            BedrockNative.LOGGER
               .debug("Bedrock biome {} ({}) has no Java biome, showing {}", new Object[]{bedrockId, BiomeMappings.bedrockName(bedrockId), key.identifier()});
         }

         return holder;
      } else {
         BiomeClimates.Climate climate = (BiomeClimates.Climate)this.serverClimates.get(bedrockId);
         if (climate != null) {
            holder = BiomeClimates.closest(climate, this.climateCandidates());
            if (holder != null) {
               BedrockNative.LOGGER
                  .info(
                     "Unknown Bedrock biome id {} ({}), showing {}, the nearest climate (temperature {}, downfall {})",
                     new Object[]{bedrockId, serverName, holder.getRegisteredName(), climate.temperature(), climate.downfall()}
                  );
               return holder;
            }
         }

         BedrockNative.LOGGER.warn("Unknown Bedrock biome id {} ({}), showing plains", bedrockId, serverName != null ? serverName : key);
         return this.fallback;
      }
   }

   private List<BiomeClimates.Candidate<Holder<Biome>>> climateCandidates() {
      if (this.climateCandidates == null) {
         List<BiomeClimates.Candidate<Holder<Biome>>> candidates = new ArrayList<>();

         for (ResourceKey<Biome> key : CLIMATE_CANDIDATES) {
            this.registry
               .get(key)
               .ifPresent(
                  holder -> candidates.add(
                     new BiomeClimates.Candidate<>(
                        holder, new BiomeClimates.Climate(holder.value().getBaseTemperature(), holder.value().climateSettings.downfall())
                     )
                  )
               );
         }

         this.climateCandidates = candidates;
      }

      return this.climateCandidates;
   }
}
