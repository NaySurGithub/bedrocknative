package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class UiGaps {
   static final int MAX_ENTRIES = 128;
   private final Map<String, Boolean> names = new LinkedHashMap<>();
   private final Set<String> renderers = new LinkedHashSet<>();
   private final Set<String> types = new LinkedHashSet<>();
   private final Set<String> fired = new LinkedHashSet<>();
   private long changes;

   UiValue observe(String name, UiValue value) {
      Boolean given = this.names.get(name);
      if (given == null) {
         if (this.names.size() < 128) {
            this.names.put(name, !value.isNull());
            this.changes++;
         }
      } else if (!given && !value.isNull()) {
         this.names.put(name, true);
         this.changes++;
      }

      return value;
   }

   public void undrawn(@Nullable String renderer) {
      if (renderer != null && this.renderers.size() < 128 && this.renderers.add(renderer)) {
         this.changes++;
      }
   }

   void unknownType(String type) {
      if (this.types.size() < 128 && this.types.add(type)) {
         this.changes++;
      }
   }

   void fired(String id) {
      if (this.fired.size() < 128 && this.fired.add(id)) {
         this.changes++;
      }
   }

   public long changes() {
      return this.changes;
   }

   public List<String> unprovided() {
      List<String> missing = new ArrayList<>();
      this.names.forEach((name, given) -> {
         if (!given) {
            missing.add(name);
         }
      });
      return missing;
   }

   public Set<String> undrawnRenderers() {
      return Set.copyOf(this.renderers);
   }

   public Set<String> unknownTypes() {
      return Set.copyOf(this.types);
   }

   public Set<String> firedIds() {
      return Set.copyOf(this.fired);
   }

   @Nullable
   public String line(Predicate<String> baseGameName) {
      List<String> parts = new ArrayList<>();
      if (!this.renderers.isEmpty()) {
         parts.add("renderers drawn as nothing " + this.renderers);
      }

      List<String> serverOnly = new ArrayList<>();
      int base = 0;

      for (String name : this.unprovided()) {
         int slash = name.lastIndexOf(47);
         if (baseGameName.test(slash >= 0 ? name.substring(slash + 1) : name)) {
            base++;
         } else {
            serverOnly.add(name);
         }
      }

      if (!serverOnly.isEmpty()) {
         parts.add("data only the server's packs read and no source here gives " + serverOnly);
      }

      if (base > 0) {
         parts.add(base + " names the base game reads too and no source here gives");
      }

      if (!this.types.isEmpty()) {
         parts.add("control types read as panels " + this.types);
      }

      return parts.isEmpty() ? null : String.join("; ", parts);
   }

   @Nullable
   public String baseGameLine(Predicate<String> baseGameName) {
      List<String> base = new ArrayList<>();

      for (String name : this.unprovided()) {
         int slash = name.lastIndexOf(47);
         if (baseGameName.test(slash >= 0 ? name.substring(slash + 1) : name)) {
            base.add(name);
         }
      }

      return base.isEmpty() ? null : "names the base game reads too and no source here gives " + base;
   }

   @Nullable
   public String firedLine() {
      return this.fired.isEmpty() ? null : "fired " + this.fired;
   }

   @Nullable
   public String readLine() {
      List<String> given = new ArrayList<>();
      this.names.forEach((name, wasGiven) -> {
         if (wasGiven) {
            given.add(name);
         }
      });
      return given.isEmpty() ? null : "read " + given;
   }

   UiDataSource observing(final UiDataSource source) {
      return new UiDataSource() {
         {
            Objects.requireNonNull(UiGaps.this);
         }

         @Override
         public UiValue global(String name) {
            return UiGaps.this.observe(name, source.global(name));
         }

         @Override
         public int length(String collection) {
            return source.length(collection);
         }

         @Override
         public UiValue item(String collection, int index, String name) {
            return UiGaps.this.observe(collection + "/" + name, source.item(collection, index, name));
         }

         @Nullable
         @Override
         public String kind(String collection, int index) {
            return source.kind(collection, index);
         }

         @Override
         public long version() {
            return source.version();
         }

         @Override
         public UiValue item(String collection, int index, String name, int outer) {
            return UiGaps.this.observe(collection + "/" + name, source.item(collection, index, name, outer));
         }

         @Override
         public void write(String collection, int index, String name, UiValue value) {
            source.write(collection, index, name, value);
         }

         @Override
         public void write(@Nullable String collection, int index, String name, UiValue value, int outer) {
            source.write(collection, index, name, value, outer);
         }

         @Override
         public void event(String buttonId, @Nullable String collection, int index) {
            source.event(buttonId, collection, index);
         }

         @Override
         public UiValue radioSelection(String toggleName) {
            return source.radioSelection(toggleName);
         }

         @Override
         public void radioSelected(String toggleName, int index) {
            source.radioSelected(toggleName, index);
         }

         @Override
         public UiValue global(String name, UiItemPath path) {
            return UiGaps.this.observe(name, source.global(name, path));
         }

         @Override
         public void event(String buttonId, UiItemPath path) {
            source.event(buttonId, path);
         }
      };
   }
}
