package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class UiDataMap implements UiDataSource {
   private final Map<String, UiValue> globals = new HashMap<>();
   private final Map<String, List<UiDataMap.Item>> collections = new HashMap<>();
   private long version;

   public UiDataMap set(String name, UiValue value) {
      UiValue previous = this.globals.put(name, value);
      if (!value.equals(previous)) {
         this.version++;
      }

      return this;
   }

   public UiDataMap set(String name, String value) {
      return this.set(name, UiValue.of(value));
   }

   public UiDataMap set(String name, double value) {
      return this.set(name, UiValue.of(value));
   }

   public UiDataMap set(String name, boolean value) {
      return this.set(name, UiValue.of(value));
   }

   public UiDataMap remove(String name) {
      if (this.globals.remove(name) != null) {
         this.version++;
      }

      return this;
   }

   public UiDataMap collection(String name, List<UiDataMap.Item> items) {
      List<UiDataMap.Item> previous = this.collections.get(name);
      if (previous != null && previous.equals(items)) {
         return this;
      }

      this.collections.put(name, new ArrayList<>(items));
      this.version++;
      return this;
   }

   public UiDataMap clearCollection(String name) {
      if (this.collections.remove(name) != null) {
         this.version++;
      }

      return this;
   }

   public void touch() {
      this.version++;
   }

   @Override
   public UiValue global(String name) {
      UiValue value = this.globals.get(name);
      return value == null ? UiValue.NULL : value;
   }

   @Override
   public int length(String collection) {
      List<UiDataMap.Item> items = this.collections.get(collection);
      return items == null ? 0 : items.size();
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      List<UiDataMap.Item> items = this.collections.get(collection);
      if (items != null && index >= 0 && index < items.size()) {
         UiValue value = items.get(index).values().get(name);
         return value == null ? UiValue.NULL : value;
      } else {
         return UiValue.NULL;
      }
   }

   @Override
   public @Nullable String kind(String collection, int index) {
      List<UiDataMap.Item> items = this.collections.get(collection);
      return items != null && index >= 0 && index < items.size() ? items.get(index).kind() : null;
   }

   @Override
   public long version() {
      return this.version;
   }

   public record Item(@Nullable String kind, Map<String, UiValue> values) {
   }
}
