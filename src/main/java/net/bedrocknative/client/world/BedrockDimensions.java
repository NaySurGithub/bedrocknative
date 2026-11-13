package net.bedrocknative.client.world;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.DimensionHeights;
import net.bedrocknative.world.chunk.DimensionRange;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.EnvironmentAttributeMap.Builder;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockDimensions {
   private static final int FLAT_GENERATOR = 2;
   private final Map<Integer, DimensionDefinition> definitions = new ConcurrentHashMap<>();
   private final Set<String> unusable = ConcurrentHashMap.newKeySet();

   public void apply(DimensionDataPacket packet) {
      for (DimensionDefinition definition : packet.getDefinitions()) {
         int dimension = dimensionOf(definition.getId());
         if (dimension < 0) {
            BedrockNative.LOGGER
               .info(
                  "Dimension {} (heights {} to {}, generator {}) has no Java dimension; ignored",
                  new Object[]{definition.getId(), definition.getMinimumHeight(), definition.getMaximumHeight(), definition.getGeneratorType()}
               );
         } else {
            this.definitions.put(dimension, definition);
            BedrockNative.LOGGER
               .info(
                  "Dimension {}: heights {} to {}, generator {}",
                  new Object[]{definition.getId(), definition.getMinimumHeight(), definition.getMaximumHeight(), definition.getGeneratorType()}
               );
         }
      }
   }

   public Holder<DimensionType> typeOf(RegistryAccess access, int dimension) {
      ResourceKey<DimensionType> key = switch (dimension) {
         case 1 -> BuiltinDimensionTypes.NETHER;
         case 2 -> BuiltinDimensionTypes.END;
         default -> BuiltinDimensionTypes.OVERWORLD;
      };
      Holder<DimensionType> builtin = access.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(key);
      DimensionDefinition definition = this.definitions.get(dimension);
      if (definition == null) {
         return builtin;
      } else {
         DimensionType type = withHeights(
            builtin.value(), definition.getMinimumHeight(), definition.getMaximumHeight(), definition.getCloudHeight(), definition.isRenderClouds()
         );
         if (type == null) {
            this.reportUnusableOnce(dimension, definition.getMinimumHeight(), definition.getMaximumHeight());
            return builtin;
         } else {
            return type.equals(builtin.value()) ? builtin : Holder.direct(type);
         }
      }
   }

   private void reportUnusableOnce(int dimension, int minimum, int maximum) {
      if (this.unusable.add(dimension + ":" + minimum + ":" + maximum)) {
         BedrockNative.LOGGER
            .warn("Dimension {} has heights {} to {}, which Java cannot hold; using the built-in dimension type", new Object[]{dimension, minimum, maximum});
      }
   }

   public DimensionRange rangeOf(DimensionType javaType, int dimension) {
      DimensionDefinition definition = this.definitions.get(dimension);
      return DimensionRange.of(
         dimension,
         definition == null ? null : definition.getMinimumHeight(),
         definition == null ? null : definition.getMaximumHeight(),
         javaType.minY(),
         javaType.height()
      );
   }

   public boolean flat(int dimension) {
      DimensionDefinition definition = this.definitions.get(dimension);
      return definition != null && definition.getGeneratorType() == 2;
   }

   public @Nullable DimensionDefinition definitionOf(int dimension) {
      return this.definitions.get(dimension);
   }

   static @Nullable DimensionType withHeights(DimensionType base, int minimum, int maximum) {
      return withHeights(base, minimum, maximum, Integer.MIN_VALUE, true);
   }

   static @Nullable DimensionType withHeights(DimensionType base, int minimum, int maximum, int cloudHeight, boolean renderClouds) {
      DimensionHeights heights = DimensionHeights.of(minimum, maximum);
      if (heights == null) {
         return null;
      }

      int logicalHeight = base.logicalHeight() == base.height() ? heights.height() : Math.min(base.logicalHeight(), heights.height());
      EnvironmentAttributeMap attributes = base.attributes();
      if (cloudHeight > 0 || !renderClouds) {
         Builder builder = EnvironmentAttributeMap.builder().putAll(attributes);
         if (cloudHeight > 0) {
            builder.set(EnvironmentAttributes.CLOUD_HEIGHT, (float)cloudHeight);
         }

         if (!renderClouds) {
            builder.set(EnvironmentAttributes.CLOUD_COLOR, ARGB.vector4fFromARGB32(0));
         }

         attributes = builder.build();
      }

      try {
         return new DimensionType(
            base.hasFixedTime(),
            base.hasSkyLight(),
            base.hasCeiling(),
            base.hasEnderDragonFight(),
            base.coordinateScale(),
            heights.minY(),
            heights.height(),
            logicalHeight,
            base.infiniburn(),
            base.ambientLight(),
            base.monsterSettings(),
            base.skybox(),
            base.cardinalLightType(),
            attributes,
            base.timelines(),
            base.defaultClock()
         );
      } catch (IllegalStateException | IllegalArgumentException e) {
         return null;
      }
   }

   private static int dimensionOf(String id) {
      String name = id.substring(id.indexOf(58) + 1);

      return switch (name) {
         case "overworld" -> 0;
         case "nether" -> 1;
         case "the_end", "end" -> 2;
         default -> -1;
      };
   }
}
