package net.bedrocknative.model.entity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityProperties;
import org.jspecify.annotations.Nullable;

public final class ActorData {
   public static final String MAIN_HAND = "slot.weapon.mainhand";
   public static final String OFF_HAND = "slot.weapon.offhand";
   public static final String HEAD = "slot.armor.head";
   public static final String CHEST = "slot.armor.chest";
   public static final String LEGS = "slot.armor.legs";
   public static final String FEET = "slot.armor.feet";
   public static final String BODY = "slot.armor.body";
   private static final EntityFlag[] FLAGS = EntityFlag.values();
   private final String identifier;
   private final boolean[] flags = new boolean[FLAGS.length];
   private final Map<EntityDataType<?>, Object> values = new HashMap<>();
   private final ActorPropertyTypes.Values properties;
   private final Map<String, String> items = new HashMap<>();

   public ActorData(String identifier, ActorPropertyTypes types) {
      this.identifier = identifier;
      this.properties = new ActorPropertyTypes.Values(types, identifier);
   }

   public String identifier() {
      return this.identifier;
   }

   public void apply(@Nullable EntityDataMap data, @Nullable EntityProperties changed) {
      if (data != null) {
         for (Entry<EntityDataType<?>, Object> entry : data.entrySet()) {
            EntityDataType<?> type = entry.getKey();
            if (type != EntityDataTypes.FLAGS && type != EntityDataTypes.FLAGS_2 && entry.getValue() != null) {
               this.values.put(type, entry.getValue());
            }
         }

         EnumMap<EntityFlag, Boolean> received = data.getFlags();
         if (received != null) {
            for (Entry<EntityFlag, Boolean> flag : received.entrySet()) {
               this.flags[flag.getKey().ordinal()] = Boolean.TRUE.equals(flag.getValue());
            }
         }
      }

      this.properties.apply(changed);
   }

   public boolean flag(EntityFlag flag) {
      return this.flags[flag.ordinal()];
   }

   public void setFlag(EntityFlag flag, boolean value) {
      this.flags[flag.ordinal()] = value;
   }

   @Nullable
   public Object value(EntityDataType<?> type) {
      return this.values.get(type);
   }

   public float number(EntityDataType<?> type) {
      return switch (this.values.get(type)) {
         case Number number -> number.floatValue();
         case Boolean bool -> bool ? 1.0F : 0.0F;
         case null, default -> 0.0F;
      };
   }

   public float scale() {
      return this.values.get(EntityDataTypes.SCALE) instanceof Number number ? number.floatValue() : 1.0F;
   }

   public float width() {
      return this.values.get(EntityDataTypes.WIDTH) instanceof Number number ? number.floatValue() : Float.NaN;
   }

   public float height() {
      return this.values.get(EntityDataTypes.HEIGHT) instanceof Number number ? number.floatValue() : Float.NaN;
   }

   public String name() {
      return this.values.get(EntityDataTypes.NAME) instanceof CharSequence name ? name.toString() : "";
   }

   @Nullable
   public Object property(String name) {
      return this.properties.get(name);
   }

   public ActorPropertyTypes.Values properties() {
      return this.properties;
   }

   public void equip(String slot, @Nullable String item) {
      if (item != null && !item.isEmpty() && !item.equals("minecraft:air")) {
         this.items.put(slot, item);
      } else {
         this.items.remove(slot);
      }
   }

   public String item(String slot) {
      return this.items.getOrDefault(slot, "");
   }

   public int equipment() {
      return this.items.size();
   }
}
