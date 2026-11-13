package net.bedrocknative.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class UiScope {
   private final @Nullable UiScope parent;
   private final Map<String, UiValue> own;
   private final Set<String> weak;

   private UiScope(@Nullable UiScope parent, Map<String, UiValue> own, Set<String> weak) {
      this.parent = parent;
      this.own = own;
      this.weak = weak;
   }

   public static UiScope root(Map<String, UiValue> globals, Map<String, UiValue> engine) {
      UiScope global = new UiScope(null, new HashMap<>(globals), Set.of());
      return engine.isEmpty() ? global : new UiScope(global, new HashMap<>(engine), Set.of());
   }

   public UiScope child(Map<String, UiValue> variables) {
      return variables.isEmpty() ? this : new UiScope(this, new HashMap<>(variables), Set.of());
   }

   public UiScope weakened(Set<String> names) {
      Map<String, UiValue> values = null;

      for (String name : names) {
         UiValue value = this.get(name);
         if (value != null && !this.weak(name)) {
            if (values == null) {
               values = new HashMap<>();
            }

            values.put(name, value);
         }
      }

      return values == null ? this : new UiScope(this, values, Set.copyOf(values.keySet()));
   }

   public @Nullable UiValue get(String name) {
      for (UiScope scope = this; scope != null; scope = scope.parent) {
         UiValue value = scope.own.get(name);
         if (value != null) {
            return value;
         }
      }

      return null;
   }

   public boolean has(String name) {
      return this.get(name) != null;
   }

   public boolean weak(String name) {
      for (UiScope scope = this; scope != null; scope = scope.parent) {
         if (scope.own.containsKey(name)) {
            return scope.weak.contains(name);
         }
      }

      return false;
   }

   public UiScope.Builder builder() {
      return new UiScope.Builder(this);
   }

   public static final class Builder {
      private final UiScope parent;
      private final Map<String, UiValue> own = new HashMap<>();
      private final Set<String> weak = new HashSet<>();

      private Builder(UiScope parent) {
         this.parent = parent;
      }

      public @Nullable UiValue get(String name) {
         UiValue value = this.own.get(name);
         return value != null ? value : this.parent.get(name);
      }

      public boolean has(String name) {
         return this.get(name) != null;
      }

      public boolean defaultApplies(String name) {
         return this.own.containsKey(name) ? this.weak.contains(name) : !this.parent.has(name) || this.parent.weak(name);
      }

      public void put(String name, UiValue value) {
         this.own.put(name, value);
         this.weak.remove(name);
      }

      public void putDefault(String name, UiValue value) {
         this.own.put(name, value);
         this.weak.add(name);
      }

      public UiScope build() {
         return this.own.isEmpty() ? this.parent : new UiScope(this.parent, this.own, this.weak.isEmpty() ? Set.of() : Set.copyOf(this.weak));
      }
   }
}
