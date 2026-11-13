package net.bedrocknative.world.block.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.bedrocknative.world.block.BedrockBlockState;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;

public final class CustomBlockDefinition {
   private final String name;
   private final Map<String, Object> components;
   private final List<CustomBlockDefinition.Permutation> permutations;

   private CustomBlockDefinition(String name, Map<String, Object> components, List<CustomBlockDefinition.Permutation> permutations) {
      this.name = name;
      this.components = components;
      this.permutations = permutations;
   }

   public static CustomBlockDefinition read(String name, NbtMap properties) {
      List<CustomBlockDefinition.Permutation> permutations = new ArrayList<>();

      for (NbtMap permutation : properties.getList("permutations", NbtType.COMPOUND, List.of())) {
         permutations.add(new CustomBlockDefinition.Permutation(permutation.getString("condition", ""), permutation.getCompound("components", NbtMap.EMPTY)));
      }

      return new CustomBlockDefinition(name, properties.getCompound("components", NbtMap.EMPTY), List.copyOf(permutations));
   }

   public String name() {
      return this.name;
   }

   public Map<String, Object> components(Map<String, ?> state) {
      Map<String, Object> merged = new LinkedHashMap<>(this.components);

      for (CustomBlockDefinition.Permutation permutation : this.permutations) {
         if (!permutation.condition().isBlank() && StateCondition.test(permutation.condition(), state)) {
            merged.putAll(permutation.components());
         }
      }

      return merged;
   }

   public CustomBlockState resolve(BedrockBlockState state) {
      return CustomBlockState.read(state, this.components(state.states()));
   }

   private record Permutation(String condition, Map<String, Object> components) {
   }
}
