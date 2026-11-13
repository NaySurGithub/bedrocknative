package net.bedrocknative.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityProperties;
import org.cloudburstmc.protocol.bedrock.data.entity.FloatEntityProperty;
import org.cloudburstmc.protocol.bedrock.data.entity.IntEntityProperty;
import org.jspecify.annotations.Nullable;

public final class ActorPropertyTypes {
   private final Map<String, List<ActorPropertyTypes.Property>> types = new ConcurrentHashMap<>();

   public void register(NbtMap data) {
      String type = data.getString("type", "");
      if (!type.isEmpty()) {
         List<ActorPropertyTypes.Property> properties = new ArrayList<>();

         for (NbtMap property : data.getList("properties", NbtType.COMPOUND, List.of())) {
            int kind = property.getInt("type", 0);
            properties.add(
               new ActorPropertyTypes.Property(
                  property.getString("name", ""),
                  kind >= 0 && kind < ActorPropertyTypes.Kind.values().length ? ActorPropertyTypes.Kind.values()[kind] : ActorPropertyTypes.Kind.INT,
                  List.copyOf(property.getList("enum", NbtType.STRING, List.of())),
                  number(property.get("min")),
                  number(property.get("max"))
               )
            );
         }

         this.types.put(type, List.copyOf(properties));
      }
   }

   public List<ActorPropertyTypes.Property> properties(String type) {
      return this.types.getOrDefault(type, List.of());
   }

   public void clear() {
      this.types.clear();
   }

   private static float number(@Nullable Object value) {
      return value instanceof Number number ? number.floatValue() : 0.0F;
   }

   public enum Kind {
      INT,
      FLOAT,
      BOOL,
      ENUM;
   }

   public record Property(String name, ActorPropertyTypes.Kind kind, List<String> values, float min, float max) {
   }

   public static final class Values {
      private final ActorPropertyTypes types;
      private final String type;
      private final Map<Integer, Number> values = new ConcurrentHashMap<>();
      private final Map<Integer, Number> overrides = new ConcurrentHashMap<>();

      public Values(ActorPropertyTypes types, String type) {
         this.types = types;
         this.type = type;
      }

      public void apply(@Nullable EntityProperties properties) {
         if (properties != null) {
            for (IntEntityProperty property : properties.getIntProperties()) {
               this.values.put(property.getIndex(), property.getValue());
            }

            for (FloatEntityProperty property : properties.getFloatProperties()) {
               this.values.put(property.getIndex(), property.getValue());
            }
         }
      }

      public void set(int index, Number value) {
         this.values.put(index, value);
      }

      public boolean change(String name, boolean boolValue, @Nullable String stringValue, int intValue, float floatValue) {
         List<ActorPropertyTypes.Property> properties = this.types.properties(this.type);

         for (int i = 0; i < properties.size(); i++) {
            ActorPropertyTypes.Property property = properties.get(i);
            if (property.name().equals(name)) {
               switch (property.kind()) {
                  case INT:
                     this.values.put(i, intValue);
                     break;
                  case FLOAT:
                     this.values.put(i, floatValue);
                     break;
                  case BOOL:
                     this.values.put(i, boolValue ? 1 : 0);
                     break;
                  case ENUM:
                     int index = stringValue == null ? -1 : property.values().indexOf(stringValue);
                     if (index < 0) {
                        return false;
                     }

                     this.values.put(i, index);
               }

               return true;
            }
         }

         return false;
      }

      public void override(int index, Number value) {
         this.overrides.put(index, value);
      }

      public void removeOverride(int index) {
         this.overrides.remove(index);
      }

      public void clearOverrides() {
         this.overrides.clear();
      }

      public int overrideCount() {
         return this.overrides.size();
      }

      public @Nullable Object get(String name) {
         List<ActorPropertyTypes.Property> properties = this.types.properties(this.type);

         for (int i = 0; i < properties.size(); i++) {
            ActorPropertyTypes.Property property = properties.get(i);
            if (property.name().equals(name)) {
               Number value = this.overrides.containsKey(i) ? this.overrides.get(i) : this.values.get(i);

               return switch (property.kind()) {
                  case INT -> value != null ? value.intValue() : (int)property.min();
                  case FLOAT -> value != null ? value.floatValue() : property.min();
                  case BOOL -> value != null && value.intValue() != 0;
                  case ENUM -> {
                     int index = value != null ? value.intValue() : 0;
                     yield index >= 0 && index < property.values().size() ? property.values().get(index) : "";
                  }
               };
            }
         }

         return null;
      }
   }
}
