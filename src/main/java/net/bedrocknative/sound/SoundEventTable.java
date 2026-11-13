package net.bedrocknative.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.random.RandomGenerator;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.Molang;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class SoundEventTable {
   public static final String FILE = "sounds.json";
   private static final SoundEventTable.Range ONE = new SoundEventTable.Range(1.0F, 1.0F);
   private final Map<String, SoundEventTable.Group> blocks = new HashMap<>();
   private final Map<String, SoundEventTable.Group> interactiveBlocks = new HashMap<>();
   private final Map<String, SoundEventTable.Group> entities = new HashMap<>();
   private SoundEventTable.Group entityDefaults = SoundEventTable.Group.EMPTY;
   private final Map<String, SoundEventTable.Interactive> interactiveEntities = new HashMap<>();
   private SoundEventTable.Interactive interactiveDefaults = SoundEventTable.Interactive.EMPTY;
   private final Map<String, SoundEventTable.Event> individual = new HashMap<>();
   private final Map<String, SoundEventTable.Event> named = new HashMap<>();
   private final Map<String, Boolean> serverEntities = new HashMap<>();
   @Nullable
   private volatile Set<String> blockEvents;

   private SoundEventTable() {
   }

   public static SoundEventTable empty() {
      return new SoundEventTable();
   }

   public static SoundEventTable of(List<SoundDefinitions.PackJson> bottomFirst) {
      SoundEventTable table = new SoundEventTable();

      for (SoundDefinitions.PackJson pack : bottomFirst) {
         table.merge(pack.json(), pack.serverPack());
      }

      return table;
   }

   private void merge(JsonObject root, boolean server) {
      JsonObject blockSounds = ModelJson.object(root, "block_sounds");
      mergeGroups(this.blocks, blockSounds, server);
      JsonObject entitySounds = ModelJson.object(root, "entity_sounds");
      if (entitySounds != null) {
         if (entitySounds.get("defaults") instanceof JsonObject defaults) {
            this.entityDefaults = mergeGroup(this.entityDefaults, defaults, server);
         }

         JsonObject list = ModelJson.object(entitySounds, "entities");
         mergeGroups(this.entities, list, server);
         if (list != null && server) {
            list.keySet().forEach(name -> this.serverEntities.put(name.toLowerCase(Locale.ROOT), true));
         }
      }

      JsonObject interactive = ModelJson.object(root, "interactive_sounds");
      if (interactive != null) {
         mergeGroups(this.interactiveBlocks, ModelJson.object(interactive, "block_sounds"), server);
         JsonObject interactiveEntitySounds = ModelJson.object(interactive, "entity_sounds");
         if (interactiveEntitySounds != null) {
            if (interactiveEntitySounds.get("defaults") instanceof JsonObject defaults) {
               this.interactiveDefaults = mergeInteractive(this.interactiveDefaults, defaults, server);
            }

            JsonObject list = ModelJson.object(interactiveEntitySounds, "entities");
            if (list != null) {
               for (Entry<String, JsonElement> entry : list.entrySet()) {
                  if (entry.getValue() instanceof JsonObject object) {
                     String key = entry.getKey().toLowerCase(Locale.ROOT);
                     this.interactiveEntities
                        .put(key, mergeInteractive(this.interactiveEntities.getOrDefault(key, SoundEventTable.Interactive.EMPTY), object, server));
                  }
               }
            }
         }
      }

      mergeEvents(this.individual, ModelJson.object(ModelJson.object(root, "individual_event_sounds"), "events"), server);
      mergeEvents(this.named, ModelJson.object(ModelJson.object(root, "individual_named_sounds"), "sounds"), server);
   }

   private static void mergeGroups(Map<String, SoundEventTable.Group> into, @Nullable JsonObject groups, boolean server) {
      if (groups != null) {
         for (Entry<String, JsonElement> entry : groups.entrySet()) {
            if (entry.getValue() instanceof JsonObject object) {
               String key = entry.getKey().toLowerCase(Locale.ROOT);
               into.put(key, mergeGroup(into.getOrDefault(key, SoundEventTable.Group.EMPTY), object, server));
            }
         }
      }
   }

   private static SoundEventTable.Group mergeGroup(SoundEventTable.Group below, JsonObject json, boolean server) {
      Map<String, SoundEventTable.Event> events = new HashMap<>(below.events());
      mergeEvents(events, ModelJson.object(json, "events"), server);
      SoundEventTable.Range volume = range(json.get("volume"));
      SoundEventTable.Range pitch = range(json.get("pitch"));
      return new SoundEventTable.Group(
         volume != null ? volume : below.volume(),
         pitch != null ? pitch : below.pitch(),
         Map.copyOf(events),
         mergeVariants(below.variants(), ModelJson.object(json, "variants"), json, server)
      );
   }

   @Nullable
   private static SoundEventTable.Variants mergeVariants(@Nullable SoundEventTable.Variants below, @Nullable JsonObject json, JsonObject entity, boolean server) {
      if (json == null) {
         return below;
      } else {
         String source = ModelJson.string(json, "key");
         MolangVersion version = entity.get("__molang_version") instanceof JsonPrimitive number && number.isNumber()
            ? MolangVersion.ofProtocol(number.getAsInt())
            : MolangVersion.LATEST;
         MolangProgram key = source != null && !source.isBlank() ? Molang.compile(source, version) : (below != null ? below.key() : null);
         Map<String, SoundEventTable.Group> map = new HashMap<>(below != null ? below.map() : Map.of());
         JsonObject variants = ModelJson.object(json, "map");
         if (variants != null) {
            for (Entry<String, JsonElement> entry : variants.entrySet()) {
               if (entry.getValue() instanceof JsonObject object) {
                  String name = entry.getKey().toLowerCase(Locale.ROOT);
                  map.put(name, mergeGroup(map.getOrDefault(name, SoundEventTable.Group.EMPTY), object, server));
               }
            }
         }

         return new SoundEventTable.Variants(key, Map.copyOf(map));
      }
   }

   private static SoundEventTable.Interactive mergeInteractive(SoundEventTable.Interactive below, JsonObject json, boolean server) {
      Map<String, Map<String, SoundEventTable.Event>> events = new HashMap<>(below.events());
      JsonObject list = ModelJson.object(json, "events");
      if (list != null) {
         for (Entry<String, JsonElement> entry : list.entrySet()) {
            String event = entry.getKey().toLowerCase(Locale.ROOT);
            Map<String, SoundEventTable.Event> byBlock = new HashMap<>(events.getOrDefault(event, Map.of()));
            if (entry.getValue() instanceof JsonObject blocks) {
               mergeEvents(byBlock, blocks, server);
            } else {
               SoundEventTable.Event single = event(entry.getValue(), server);
               if (single != null) {
                  byBlock.put("default", single);
               }
            }

            events.put(event, Map.copyOf(byBlock));
         }
      }

      SoundEventTable.Range volume = range(json.get("volume"));
      SoundEventTable.Range pitch = range(json.get("pitch"));
      return new SoundEventTable.Interactive(volume != null ? volume : below.volume(), pitch != null ? pitch : below.pitch(), Map.copyOf(events));
   }

   private static void mergeEvents(Map<String, SoundEventTable.Event> into, @Nullable JsonObject events, boolean server) {
      if (events != null) {
         for (Entry<String, JsonElement> entry : events.entrySet()) {
            SoundEventTable.Event event = event(entry.getValue(), server);
            if (event != null) {
               into.put(entry.getKey().toLowerCase(Locale.ROOT), event);
            }
         }
      }
   }

   @Nullable
   private static SoundEventTable.Event event(JsonElement element, boolean server) {
      if (element instanceof JsonPrimitive primitive && primitive.isString()) {
         return new SoundEventTable.Event(primitive.getAsString().toLowerCase(Locale.ROOT), null, null, server);
      } else if (element instanceof JsonObject object) {
         String sound = ModelJson.string(object, "sound");
         if (sound == null) {
            sound = ModelJson.string(object, "sounds");
         }

         return new SoundEventTable.Event(sound == null ? "" : sound.toLowerCase(Locale.ROOT), range(object.get("volume")), range(object.get("pitch")), server);
      } else {
         return null;
      }
   }

   @Nullable
   static SoundEventTable.Range range(@Nullable JsonElement element) {
      if (element instanceof JsonArray array && array.size() >= 2) {
         float min = ModelJson.number(array.get(0), Float.NaN);
         float max = ModelJson.number(array.get(1), Float.NaN);
         return !Float.isNaN(min) && !Float.isNaN(max) ? new SoundEventTable.Range(Math.min(min, max), Math.max(min, max)) : null;
      } else {
         float value = element == null ? Float.NaN : ModelJson.number(element, Float.NaN);
         return Float.isNaN(value) ? null : new SoundEventTable.Range(value, value);
      }
   }

   @Nullable
   public SoundEventTable.Resolved entity(String identifier, String event, @Nullable String blockType, RandomGenerator random) {
      return this.entity(identifier, event, blockType, SoundEventTable.VariantKeys.NONE, random);
   }

   @Nullable
   public SoundEventTable.Resolved entity(String identifier, String event, @Nullable String blockType, SoundEventTable.VariantKeys keys, RandomGenerator random) {
      String key = event.toLowerCase(Locale.ROOT);
      SoundEventTable.Group own = lookup(this.entities, identifier);
      SoundEventTable.Variants variants = own != null ? own.variants() : null;
      if (variants != null && variants.key() != null) {
         String name = keys.evaluate(variants.key());
         SoundEventTable.Group variant = name != null ? variants.map().get(name.toLowerCase(Locale.ROOT)) : null;
         SoundEventTable.Event chosen = variant != null ? variant.events().get(key) : null;
         if (chosen != null) {
            return resolve(chosen, variant.volume() != null ? variant.volume() : own.volume(), variant.pitch() != null ? variant.pitch() : own.pitch(), random);
         }
      }

      SoundEventTable.Event found = own != null ? own.events().get(key) : null;
      if (found != null) {
         return resolve(found, own.volume(), own.pitch(), random);
      } else {
         if (blockType != null) {
            SoundEventTable.Interactive interactive = lookup(this.interactiveEntities, identifier);
            Map<String, SoundEventTable.Event> byBlock = interactive != null ? interactive.events().get(key) : null;
            SoundEventTable.Interactive scope = interactive;
            if (byBlock == null) {
               byBlock = this.interactiveDefaults.events().get(key);
               scope = this.interactiveDefaults;
            }

            if (byBlock != null) {
               SoundEventTable.Event chosen = byBlock.get(blockType.toLowerCase(Locale.ROOT));
               if (chosen == null) {
                  chosen = byBlock.get("default");
               }

               if (chosen != null) {
                  if (!chosen.sound().isEmpty()) {
                     return resolve(chosen, scope.volume(), scope.pitch(), random);
                  }

                  SoundEventTable.Resolved block = this.interactiveBlock(blockType, key, random);
                  if (block != null && !block.silent()) {
                     float volume = (chosen.volume() != null ? chosen.volume() : (scope.volume() != null ? scope.volume() : ONE)).pick(random);
                     float pitch = (chosen.pitch() != null ? chosen.pitch() : (scope.pitch() != null ? scope.pitch() : ONE)).pick(random);
                     return new SoundEventTable.Resolved(block.sound(), block.volume() * volume, block.pitch() * pitch, block.serverPack());
                  }
               }
            }

            SoundEventTable.Resolved block = this.interactiveBlock(blockType, key, random);
            if (block != null) {
               return block;
            }
         }

         SoundEventTable.Event fallback = this.entityDefaults.events().get(key);
         return fallback == null
            ? null
            : resolve(
               fallback,
               own != null && own.volume() != null ? own.volume() : this.entityDefaults.volume(),
               own != null && own.pitch() != null ? own.pitch() : this.entityDefaults.pitch(),
               random
            );
      }
   }

   public boolean hasEntity(String identifier) {
      return lookup(this.entities, identifier) != null || lookup(this.interactiveEntities, identifier) != null;
   }

   public boolean serverEntity(String identifier) {
      String key = identifier.toLowerCase(Locale.ROOT);
      return this.serverEntities.containsKey(key) || key.startsWith("minecraft:") && this.serverEntities.containsKey(key.substring(10));
   }

   @Nullable
   public SoundEventTable.Resolved block(String blockType, String event, RandomGenerator random) {
      String key = event.toLowerCase(Locale.ROOT);
      SoundEventTable.Group group = this.blocks.get(blockType.toLowerCase(Locale.ROOT));
      if (group == null) {
         group = this.blocks.get("normal");
      }

      if (group == null) {
         return null;
      } else {
         SoundEventTable.Event found = group.events().get(key);
         if (found == null) {
            found = group.events().get("default");
         }

         return found == null ? null : resolve(found, group.volume(), group.pitch(), random);
      }
   }

   @Nullable
   public SoundEventTable.Resolved interactiveBlock(String blockType, String event, RandomGenerator random) {
      String key = event.toLowerCase(Locale.ROOT);
      SoundEventTable.Group group = this.interactiveBlocks.get(blockType.toLowerCase(Locale.ROOT));
      if (group == null) {
         group = this.interactiveBlocks.get("normal");
      }

      SoundEventTable.Event found = group == null ? null : group.events().get(key);
      if (found == null && group != null) {
         found = group.events().get("default");
      }

      return found == null ? null : resolve(found, group.volume(), group.pitch(), random);
   }

   public boolean hasBlockType(String blockType) {
      String key = blockType.toLowerCase(Locale.ROOT);
      return this.blocks.containsKey(key) || this.interactiveBlocks.containsKey(key);
   }

   public boolean blockEvent(String event) {
      Set<String> names = this.blockEvents;
      if (names == null) {
         Set<String> collected = new HashSet<>();
         this.blocks.values().forEach(group -> collected.addAll(group.events().keySet()));
         this.interactiveBlocks.values().forEach(group -> collected.addAll(group.events().keySet()));
         collected.remove("default");
         this.blockEvents = names = Set.copyOf(collected);
      }

      return names.contains(event.toLowerCase(Locale.ROOT));
   }

   @Nullable
   public SoundEventTable.Resolved individual(String event, RandomGenerator random) {
      SoundEventTable.Event found = this.individual.get(event.toLowerCase(Locale.ROOT));
      return found == null ? null : resolve(found, null, null, random);
   }

   @Nullable
   public SoundEventTable.Resolved named(String name, RandomGenerator random) {
      SoundEventTable.Event found = this.named.get(name.toLowerCase(Locale.ROOT));
      return found == null ? null : resolve(found, null, null, random);
   }

   public int size() {
      return this.blocks.size()
         + this.interactiveBlocks.size()
         + this.entities.size()
         + this.interactiveEntities.size()
         + this.individual.size()
         + this.named.size();
   }

   private static SoundEventTable.Resolved resolve(
      SoundEventTable.Event event, @Nullable SoundEventTable.Range groupVolume, @Nullable SoundEventTable.Range groupPitch, RandomGenerator random
   ) {
      SoundEventTable.Range volume = event.volume() != null ? event.volume() : (groupVolume != null ? groupVolume : ONE);
      SoundEventTable.Range pitch = event.pitch() != null ? event.pitch() : (groupPitch != null ? groupPitch : ONE);
      return new SoundEventTable.Resolved(event.sound(), volume.pick(random), pitch.pick(random), event.serverPack());
   }

   @Nullable
   private static <T> T lookup(Map<String, T> map, String identifier) {
      String key = identifier.toLowerCase(Locale.ROOT);
      T found = map.get(key);
      if (found == null && key.startsWith("minecraft:")) {
         found = map.get(key.substring(10));
      }

      return found;
   }

   public record Event(String sound, @Nullable SoundEventTable.Range volume, @Nullable SoundEventTable.Range pitch, boolean serverPack) {
   }

   record Group(
      @Nullable SoundEventTable.Range volume,
      @Nullable SoundEventTable.Range pitch,
      Map<String, SoundEventTable.Event> events,
      @Nullable SoundEventTable.Variants variants
   ) {
      static final SoundEventTable.Group EMPTY = new SoundEventTable.Group(null, null, Map.of(), null);
   }

   record Interactive(@Nullable SoundEventTable.Range volume, @Nullable SoundEventTable.Range pitch, Map<String, Map<String, SoundEventTable.Event>> events) {
      static final SoundEventTable.Interactive EMPTY = new SoundEventTable.Interactive(null, null, Map.of());
   }

   public record Range(float min, float max) {
      public float pick(RandomGenerator random) {
         return this.max > this.min ? this.min + random.nextFloat() * (this.max - this.min) : this.min;
      }
   }

   public record Resolved(String sound, float volume, float pitch, boolean serverPack) {
      public boolean silent() {
         return this.sound.isEmpty();
      }
   }

   @FunctionalInterface
   public interface VariantKeys {
      SoundEventTable.VariantKeys NONE = key -> null;

      @Nullable
      String evaluate(MolangProgram var1);
   }

   record Variants(@Nullable MolangProgram key, Map<String, SoundEventTable.Group> map) {
   }
}
