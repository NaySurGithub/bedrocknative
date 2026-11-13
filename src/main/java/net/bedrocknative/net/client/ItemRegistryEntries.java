package net.bedrocknative.net.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;

record ItemRegistryEntries(List<ItemDefinition> definitions, List<ItemRegistryEntries.Duplicate> duplicates) {
   static ItemRegistryEntries of(Collection<? extends ItemDefinition> items) {
      Map<Integer, ItemDefinition> byRuntimeId = new LinkedHashMap<>();
      List<ItemRegistryEntries.Duplicate> duplicates = new ArrayList<>();

      for (ItemDefinition item : items) {
         if (item != null && item.getIdentifier() != null) {
            ItemDefinition previous = byRuntimeId.put(item.getRuntimeId(), item);
            if (previous != null) {
               duplicates.add(new ItemRegistryEntries.Duplicate(item.getRuntimeId(), previous.getIdentifier(), item.getIdentifier()));
            }
         }
      }

      return new ItemRegistryEntries(List.copyOf(byRuntimeId.values()), List.copyOf(duplicates));
   }

   Map<Integer, String> identifiers() {
      Map<Integer, String> map = new HashMap<>();
      this.definitions.forEach(definition -> map.put(definition.getRuntimeId(), definition.getIdentifier()));
      return map;
   }

   static String changes(Map<Integer, String> before, Map<Integer, String> after) {
      int added = 0;
      int removed = 0;
      int renamed = 0;

      for (Entry<Integer, String> entry : after.entrySet()) {
         String old = before.get(entry.getKey());
         if (old == null) {
            added++;
         } else if (!Objects.equals(old, entry.getValue())) {
            renamed++;
         }
      }

      for (Integer id : before.keySet()) {
         if (!after.containsKey(id)) {
            removed++;
         }
      }

      return added == 0 && removed == 0 && renamed == 0
         ? "the same ids"
         : added + " ids added, " + removed + " removed, " + renamed + " given another identifier";
   }

   record Duplicate(int runtimeId, String dropped, String kept) {
      @Override
      public String toString() {
         return this.runtimeId + " (" + this.dropped + (this.dropped.equals(this.kept) ? " twice" : " and " + this.kept + ", kept") + ")";
      }
   }
}
