package net.bedrocknative.world.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public final class BlockStateUpgrader {
   static final String SCHEMAS = "/assets/bedrocknative/mappings/block-upgrade-schema/schemas.json.gz";
   private final List<BlockStateUpgrader.Schema> schemas;
   private final Map<Integer, Integer> perVersion = new HashMap<>();

   BlockStateUpgrader(JsonObject root) {
      List<BlockStateUpgrader.Schema> parsed = new ArrayList<>();

      for (JsonElement element : root.getAsJsonArray("schemas")) {
         BlockStateUpgrader.Schema schema = parse(element.getAsJsonObject());
         parsed.add(schema);
         this.perVersion.merge(schema.version(), 1, Integer::sum);
      }

      this.schemas = List.copyOf(parsed);
   }

   public static BlockStateUpgrader bundled() {
      return BlockStateUpgrader.Holder.BUNDLED;
   }

   public int schemaCount() {
      return this.schemas.size();
   }

   public int latestVersion() {
      return this.schemas.isEmpty() ? 0 : this.schemas.getLast().version();
   }

   public static int version(int major, int minor, int patch, int revision) {
      return major << 24 | minor << 16 | patch << 8 | revision;
   }

   public BlockStateUpgrader.State upgrade(String name, SortedMap<String, Object> states, int version) {
      String currentName = name;
      SortedMap<String, Object> current = states;

      for (BlockStateUpgrader.Schema schema : this.schemas) {
         if (version <= schema.version() && (version != schema.version() || this.perVersion.get(schema.version()) != 1)) {
            BlockStateUpgrader.State next = apply(schema, currentName, current);
            currentName = next.name();
            current = next.states();
         }
      }

      return new BlockStateUpgrader.State(currentName, current);
   }

   private static BlockStateUpgrader.State apply(BlockStateUpgrader.Schema schema, String name, SortedMap<String, Object> states) {
      List<BlockStateUpgrader.Remap> remaps = schema.remappedStates().get(name);
      if (remaps != null) {
         for (BlockStateUpgrader.Remap remap : remaps) {
            if (matches(remap.filter(), states)) {
               String newName = remap.newName();
               SortedMap<String, Object> source = states;
               if (newName == null) {
                  BlockStateUpgrader.State flattened = flatten(remap.newFlattenedName(), name, states);
                  newName = flattened.name();
                  source = flattened.states();
               }

               TreeMap<String, Object> result = new TreeMap<>(remap.newState());

               for (String copied : remap.copied()) {
                  Object value = source.get(copied);
                  if (value != null) {
                     result.put(copied, value);
                  }
               }

               return new BlockStateUpgrader.State(newName, result);
            }
         }
      }

      String newName = name;
      SortedMap<String, Object> result = states;
      String renamed = schema.renamedIds().get(name);
      BlockStateUpgrader.Flatten flatten = schema.flattened().get(name);
      if (renamed != null) {
         newName = renamed;
      } else if (flatten != null) {
         BlockStateUpgrader.State flattened = flatten(flatten, name, states);
         newName = flattened.name();
         result = flattened.states();
      }

      Map<String, Object> added = schema.addedProperties().get(name);
      List<String> removed = schema.removedProperties().get(name);
      Map<String, String> renamedProperties = schema.renamedProperties().get(name);
      Map<String, List<BlockStateUpgrader.ValueRemap>> remappedValues = schema.remappedValues().get(name);
      if (added == null && removed == null && renamedProperties == null && remappedValues == null) {
         return new BlockStateUpgrader.State(newName, result);
      }

      TreeMap<String, Object> changed = new TreeMap<>(result);
      if (added != null) {
         added.forEach(changed::putIfAbsent);
      }

      if (removed != null) {
         removed.forEach(changed::remove);
      }

      if (renamedProperties != null) {
         renamedProperties.forEach((oldProperty, newProperty) -> {
            Object value = changed.remove(oldProperty);
            if (value != null) {
               changed.put(newProperty, remapValue(remappedValues, oldProperty, value));
            }
         });
      }

      if (remappedValues != null) {
         remappedValues.forEach((property, pairs) -> {
            boolean renamedAway = renamedProperties != null && renamedProperties.containsKey(property);
            Object value = changed.get(property);
            if (value != null && !renamedAway) {
               changed.put(property, remapValue(remappedValues, property, value));
            }
         });
      }

      return new BlockStateUpgrader.State(newName, changed);
   }

   private static Object remapValue(@Nullable Map<String, List<BlockStateUpgrader.ValueRemap>> remappedValues, String property, Object value) {
      List<BlockStateUpgrader.ValueRemap> pairs = remappedValues == null ? null : remappedValues.get(property);
      if (pairs != null) {
         for (BlockStateUpgrader.ValueRemap pair : pairs) {
            if (pair.from().equals(value)) {
               return pair.to();
            }
         }
      }

      return value;
   }

   private static BlockStateUpgrader.State flatten(BlockStateUpgrader.Flatten flatten, String name, SortedMap<String, Object> states) {
      Object value = states.get(flatten.property());

      boolean typed = switch (flatten.type()) {
         case "byte" -> value instanceof Byte;
         case "int" -> value instanceof Integer;
         default -> value instanceof String;
      };
      if (!typed) {
         return new BlockStateUpgrader.State(name, states);
      }

      String embedded = String.valueOf(value);
      TreeMap<String, Object> rest = new TreeMap<>(states);
      rest.remove(flatten.property());
      return new BlockStateUpgrader.State(flatten.prefix() + flatten.valueRemaps().getOrDefault(embedded, embedded) + flatten.suffix(), rest);
   }

   private static boolean matches(Map<String, Object> filter, SortedMap<String, Object> states) {
      for (Entry<String, Object> wanted : filter.entrySet()) {
         if (!wanted.getValue().equals(states.get(wanted.getKey()))) {
            return false;
         }
      }

      return true;
   }

   private static BlockStateUpgrader.Schema parse(JsonObject json) {
      int version = version(
         json.get("maxVersionMajor").getAsInt(),
         json.get("maxVersionMinor").getAsInt(),
         json.get("maxVersionPatch").getAsInt(),
         json.get("maxVersionRevision").getAsInt()
      );
      Map<String, String> renamedIds = new HashMap<>();
      object(json, "renamedIds").entrySet().forEach(entry -> renamedIds.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString()));
      Map<String, Map<String, String>> renamedProperties = new HashMap<>();
      object(json, "renamedProperties")
         .entrySet()
         .forEach(
            block -> {
               Map<String, String> renames = new LinkedHashMap<>();
               ((JsonElement)block.getValue())
                  .getAsJsonObject()
                  .entrySet()
                  .forEach(entry -> renames.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString()));
               renamedProperties.put((String)block.getKey(), renames);
            }
         );
      Map<String, Map<String, Object>> addedProperties = new HashMap<>();
      object(json, "addedProperties")
         .entrySet()
         .forEach(block -> addedProperties.put((String)block.getKey(), values(((JsonElement)block.getValue()).getAsJsonObject())));
      Map<String, List<String>> removedProperties = new HashMap<>();
      object(json, "removedProperties").entrySet().forEach(block -> {
         List<String> names = new ArrayList<>();
         ((JsonElement)block.getValue()).getAsJsonArray().forEach(name -> names.add(name.getAsString()));
         removedProperties.put((String)block.getKey(), names);
      });
      Map<String, List<BlockStateUpgrader.ValueRemap>> index = new HashMap<>();
      object(json, "remappedPropertyValuesIndex")
         .entrySet()
         .forEach(
            entry -> {
               List<BlockStateUpgrader.ValueRemap> pairs = new ArrayList<>();
               ((JsonElement)entry.getValue())
                  .getAsJsonArray()
                  .forEach(
                     pair -> pairs.add(
                        new BlockStateUpgrader.ValueRemap(
                           value(pair.getAsJsonObject().getAsJsonObject("old")), value(pair.getAsJsonObject().getAsJsonObject("new"))
                        )
                     )
                  );
               index.put((String)entry.getKey(), pairs);
            }
         );
      Map<String, Map<String, List<BlockStateUpgrader.ValueRemap>>> remappedValues = new HashMap<>();
      object(json, "remappedPropertyValues").entrySet().forEach(block -> {
         Map<String, List<BlockStateUpgrader.ValueRemap>> byProperty = new LinkedHashMap<>();
         ((JsonElement)block.getValue()).getAsJsonObject().entrySet().forEach(entry -> {
            List<BlockStateUpgrader.ValueRemap> pairs = index.get(((JsonElement)entry.getValue()).getAsString());
            if (pairs == null) {
               throw new IllegalArgumentException("Schema " + json.get("id") + ": no value remap " + entry.getValue());
            }

            byProperty.put((String)entry.getKey(), pairs);
         });
         remappedValues.put((String)block.getKey(), byProperty);
      });
      Map<String, BlockStateUpgrader.Flatten> flattened = new HashMap<>();
      object(json, "flattenedProperties")
         .entrySet()
         .forEach(entry -> flattened.put((String)entry.getKey(), flatten(((JsonElement)entry.getValue()).getAsJsonObject())));
      Map<String, List<BlockStateUpgrader.Remap>> remappedStates = new HashMap<>();
      object(json, "remappedStates")
         .entrySet()
         .forEach(
            block -> {
               List<BlockStateUpgrader.Remap> remaps = new ArrayList<>();
               ((JsonElement)block.getValue())
                  .getAsJsonArray()
                  .forEach(
                     element -> {
                        JsonObject remap = element.getAsJsonObject();
                        List<String> copied = new ArrayList<>();
                        if (remap.has("copiedState")) {
                           remap.getAsJsonArray("copiedState").forEach(name -> copied.add(name.getAsString()));
                        }

                        remaps.add(
                           new BlockStateUpgrader.Remap(
                              values(object(remap, "oldState")),
                              remap.has("newName") ? remap.get("newName").getAsString() : null,
                              remap.has("newFlattenedName") ? flatten(remap.getAsJsonObject("newFlattenedName")) : null,
                              values(object(remap, "newState")),
                              List.copyOf(copied)
                           )
                        );
                     }
                  );
               remappedStates.put((String)block.getKey(), List.copyOf(remaps));
            }
         );
      return new BlockStateUpgrader.Schema(
         json.has("id") ? json.get("id").getAsString() : "?",
         version,
         renamedIds,
         renamedProperties,
         addedProperties,
         removedProperties,
         remappedValues,
         flattened,
         remappedStates
      );
   }

   private static BlockStateUpgrader.Flatten flatten(JsonObject json) {
      Map<String, String> remaps = new HashMap<>();
      if (json.has("flattenedValueRemaps")) {
         json.getAsJsonObject("flattenedValueRemaps")
            .entrySet()
            .forEach(entry -> remaps.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString()));
      }

      return new BlockStateUpgrader.Flatten(
         json.get("prefix").getAsString(),
         json.get("flattenedProperty").getAsString(),
         json.has("flattenedPropertyType") ? json.get("flattenedPropertyType").getAsString() : "string",
         json.get("suffix").getAsString(),
         remaps
      );
   }

   private static JsonObject object(JsonObject json, String key) {
      return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
   }

   private static Map<String, Object> values(JsonObject properties) {
      Map<String, Object> values = new LinkedHashMap<>();
      properties.entrySet().forEach(entry -> values.put((String)entry.getKey(), value(((JsonElement)entry.getValue()).getAsJsonObject())));
      return values;
   }

   private static Object value(JsonObject tagged) {
      if (tagged.has("int")) {
         return tagged.get("int").getAsInt();
      } else if (tagged.has("byte")) {
         return (byte)tagged.get("byte").getAsInt();
      } else if (tagged.has("string")) {
         return tagged.get("string").getAsString();
      } else {
         throw new IllegalArgumentException("Unknown block state value " + tagged);
      }
   }

   private record Flatten(String prefix, String property, String type, String suffix, Map<String, String> valueRemaps) {
   }

   private static final class Holder {
      static final BlockStateUpgrader BUNDLED = new BlockStateUpgrader(
         BedrockBlockPalette.readGzipJson("/assets/bedrocknative/mappings/block-upgrade-schema/schemas.json.gz").getAsJsonObject()
      );
   }

   private record Remap(
      Map<String, Object> filter,
      @Nullable String newName,
      BlockStateUpgrader.@Nullable Flatten newFlattenedName,
      Map<String, Object> newState,
      List<String> copied
   ) {
   }

   private record Schema(
      String id,
      int version,
      Map<String, String> renamedIds,
      Map<String, Map<String, String>> renamedProperties,
      Map<String, Map<String, Object>> addedProperties,
      Map<String, List<String>> removedProperties,
      Map<String, Map<String, List<BlockStateUpgrader.ValueRemap>>> remappedValues,
      Map<String, BlockStateUpgrader.Flatten> flattened,
      Map<String, List<BlockStateUpgrader.Remap>> remappedStates
   ) {
   }

   public record State(String name, SortedMap<String, Object> states) {
   }

   private record ValueRemap(Object from, Object to) {
   }
}
