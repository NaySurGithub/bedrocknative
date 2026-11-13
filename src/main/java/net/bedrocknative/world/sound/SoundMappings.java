package net.bedrocknative.world.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import net.bedrocknative.world.NameMatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;

public final class SoundMappings {
   private static final String RESOURCE = "/assets/bedrocknative/mappings/geyser-mappings/sounds.json.gz";
   private static final Set<String> GENERIC_KINDS = Set.of("player", "generic", "item");
   private static final Map<LevelEvent, String> LEVEL_EVENTS = Map.ofEntries(
      Map.entry(LevelEvent.SOUND_CLICK, "block.dispenser.dispense"),
      Map.entry(LevelEvent.SOUND_CLICK_FAIL, "block.dispenser.fail"),
      Map.entry(LevelEvent.SOUND_LAUNCH, "block.dispenser.launch"),
      Map.entry(LevelEvent.SOUND_DOOR_OPEN, "block.wooden_door.open"),
      Map.entry(LevelEvent.SOUND_FIZZ, "block.fire.extinguish"),
      Map.entry(LevelEvent.SOUND_FUSE, "entity.tnt.primed"),
      Map.entry(LevelEvent.SOUND_GHAST_WARNING, "entity.ghast.warn"),
      Map.entry(LevelEvent.SOUND_GHAST_FIREBALL, "entity.ghast.shoot"),
      Map.entry(LevelEvent.SOUND_BLAZE_FIREBALL, "entity.blaze.shoot"),
      Map.entry(LevelEvent.SOUND_ZOMBIE_DOOR_BUMP, "entity.zombie.attack_wooden_door"),
      Map.entry(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, "entity.zombie.break_wooden_door"),
      Map.entry(LevelEvent.SOUND_ZOMBIE_INFECTED, "entity.zombie.infect"),
      Map.entry(LevelEvent.SOUND_ZOMBIE_CONVERTED, "entity.zombie_villager.converted"),
      Map.entry(LevelEvent.SOUND_ENDERMAN_TELEPORT, "entity.enderman.teleport"),
      Map.entry(LevelEvent.SOUND_ANVIL_BROKEN, "block.anvil.destroy"),
      Map.entry(LevelEvent.SOUND_ANVIL_USED, "block.anvil.use"),
      Map.entry(LevelEvent.SOUND_ANVIL_LAND, "block.anvil.land"),
      Map.entry(LevelEvent.SOUND_INFINITY_ARROW_PICKUP, "entity.item.pickup"),
      Map.entry(LevelEvent.SOUND_TELEPORT_ENDERPEARL, "entity.player.teleport"),
      Map.entry(LevelEvent.SOUND_ITEMFRAME_ITEM_ADD, "entity.item_frame.add_item"),
      Map.entry(LevelEvent.SOUND_ITEMFRAME_BREAK, "entity.item_frame.break"),
      Map.entry(LevelEvent.SOUND_ITEMFRAME_PLACE, "entity.item_frame.place"),
      Map.entry(LevelEvent.SOUND_ITEMFRAME_ITEM_REMOVE, "entity.item_frame.remove_item"),
      Map.entry(LevelEvent.SOUND_ITEMFRAME_ITEM_ROTATE, "entity.item_frame.rotate_item"),
      Map.entry(LevelEvent.SOUND_EXPERIENCE_ORB_PICKUP, "entity.experience_orb.pickup"),
      Map.entry(LevelEvent.SOUND_TOTEM_USED, "item.totem.use"),
      Map.entry(LevelEvent.SOUND_ARMOR_STAND_BREAK, "entity.armor_stand.break"),
      Map.entry(LevelEvent.SOUND_ARMOR_STAND_HIT, "entity.armor_stand.hit"),
      Map.entry(LevelEvent.SOUND_ARMOR_STAND_LAND, "entity.armor_stand.fall"),
      Map.entry(LevelEvent.SOUND_ARMOR_STAND_PLACE, "entity.armor_stand.place"),
      Map.entry(LevelEvent.SOUND_POINTED_DRIPSTONE_LAND, "block.pointed_dripstone.land"),
      Map.entry(LevelEvent.SOUND_DYE_USED, "item.dye.use"),
      Map.entry(LevelEvent.SOUND_INK_SACE_USED, "item.ink_sac.use"),
      Map.entry(LevelEvent.SOUND_AMETHYST_RESONATE, "block.amethyst_block.resonate")
   );
   private static final Map<String, String> GENERIC_OVERRIDES = Map.of("POP", "entity.item.pickup");
   private static volatile SoundMappings instance;
   private final Map<String, SoundEvent> byPlaySound;
   private final Map<String, Map<String, SoundEvent>> byEntity;
   private final Map<String, Map<String, SoundEvent>> byBabyEntity;
   private final Map<String, SoundEvent> generic;
   private final Map<Integer, SoundEvent> notes;
   private final Map<String, String> toBedrock;
   private final int unknownJava;

   private SoundMappings(
      Map<String, SoundEvent> byPlaySound,
      Map<String, Map<String, SoundEvent>> byEntity,
      Map<String, Map<String, SoundEvent>> byBabyEntity,
      Map<String, SoundEvent> generic,
      Map<Integer, SoundEvent> notes,
      Map<String, String> toBedrock,
      int unknownJava
   ) {
      this.byPlaySound = byPlaySound;
      this.byEntity = byEntity;
      this.byBabyEntity = byBabyEntity;
      this.generic = generic;
      this.notes = notes;
      this.toBedrock = toBedrock;
      this.unknownJava = unknownJava;
   }

   public static SoundMappings get() {
      SoundMappings result = instance;
      if (result == null) {
         synchronized (SoundMappings.class) {
            result = instance;
            if (result == null) {
               instance = result = load();
            }
         }
      }

      return result;
   }

   public SoundEvent playSound(String bedrockName) {
      SoundEvent sound = this.byPlaySound.get(bedrockName);
      return sound != null ? sound : javaSound(bedrockName);
   }

   public SoundEvent entitySound(String javaEntity, String event, boolean baby) {
      return baby && this.byBabyEntity.getOrDefault(javaEntity, Map.of()).get(event) instanceof SoundEvent sound
         ? sound
         : this.byEntity.getOrDefault(javaEntity, Map.of()).get(event);
   }

   public SoundEvent generic(String event) {
      return this.generic.get(event);
   }

   public SoundEvent note(int instrument) {
      return this.notes.get(instrument);
   }

   public String bedrockName(String javaSound) {
      return this.toBedrock.get(javaSound);
   }

   public SoundEvent levelEvent(LevelEvent event) {
      String name = LEVEL_EVENTS.get(event);
      return name == null ? null : javaSound(name);
   }

   public int playSoundCount() {
      return this.byPlaySound.size();
   }

   public int genericCount() {
      return this.generic.size();
   }

   public int entityCount() {
      return this.byEntity.values().stream().mapToInt(Map::size).sum();
   }

   public int unknownJavaCount() {
      return this.unknownJava;
   }

   private static SoundEvent javaSound(String name) {
      Identifier id = Identifier.tryParse(name);
      return id == null ? null : (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(id);
   }

   private static SoundMappings load() {
      try {
         SoundMappings var1;
         try (InputStream raw = SoundMappings.class.getResourceAsStream("/assets/bedrocknative/mappings/geyser-mappings/sounds.json.gz")) {
            if (raw == null) {
               throw new IllegalStateException("Missing /assets/bedrocknative/mappings/geyser-mappings/sounds.json.gz");
            }

            var1 = build(JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8)).getAsJsonObject());
         }

         return var1;
      } catch (IOException var5) {
         throw new UncheckedIOException(var5);
      }
   }

   static SoundMappings build(JsonObject root) {
      Map<String, List<String>> byPlaySound = new HashMap<>();
      Map<String, Map<String, List<String>>> byEntity = new HashMap<>();
      Map<String, Map<String, List<String>>> byBabyEntity = new HashMap<>();
      Map<String, List<String>> generic = new HashMap<>();
      Map<Integer, SoundEvent> notes = new HashMap<>();
      Map<String, String> toBedrock = new HashMap<>();
      int unknownJava = 0;

      for (Entry<String, JsonElement> entry : root.entrySet()) {
         String java = entry.getKey();
         if (javaSound(java) == null) {
            unknownJava++;
         } else {
            JsonObject mapping = entry.getValue().getAsJsonObject();
            JsonElement event = mapping.get("playsound_mapping");
            if (event instanceof JsonElement) {
               byPlaySound.computeIfAbsent(event.getAsString(), k -> new ArrayList<>()).add(java);
               toBedrock.put(java, event.getAsString());
            }

            event = mapping.get("bedrock_mapping");
            if (event instanceof JsonElement && !mapping.has("level_event")) {
               String eventx = event.getAsString();
               if (eventx.equals("NOTE")) {
                  JsonElement kind = mapping.get("extra_data");
                  if (kind instanceof JsonElement) {
                     notes.put(kind.getAsInt() >> 8, javaSound(java));
                     continue;
                  }
               }

               String[] parts = java.split("\\.");
               if (parts.length >= 3 && parts[0].equals("entity")) {
                  String kind = parts[1];
                  byEntity.computeIfAbsent(kind, k -> new HashMap<>()).computeIfAbsent(eventx, k -> new ArrayList<>()).add(java);
                  if (kind.startsWith("baby_")) {
                     byBabyEntity.computeIfAbsent(kind.substring(5), k -> new HashMap<>()).computeIfAbsent(eventx, k -> new ArrayList<>()).add(java);
                  }
               }

               generic.computeIfAbsent(eventx, k -> new ArrayList<>()).add(java);
            }
         }
      }

      Map<String, SoundEvent> genericBest = new HashMap<>();
      generic.forEach((eventxx, candidates) -> {
         List<String> usable = (List<String>)(candidates.size() == 1 ? candidates : candidates.stream().filter(SoundMappings::isGenericKind).toList());
         if (!usable.isEmpty()) {
            genericBest.put(eventxx, javaSound(NameMatch.best(eventxx, usable)));
         }
      });
      GENERIC_OVERRIDES.forEach((eventxx, javax) -> genericBest.put(eventxx, javaSound(javax)));
      return new SoundMappings(bestOf(byPlaySound), nestedBest(byEntity), nestedBest(byBabyEntity), genericBest, notes, Map.copyOf(toBedrock), unknownJava);
   }

   private static boolean isGenericKind(String java) {
      String[] parts = java.split("\\.");
      return parts.length >= 3 && parts[0].equals("entity") && GENERIC_KINDS.contains(parts[1]);
   }

   private static Map<String, Map<String, SoundEvent>> nestedBest(Map<String, Map<String, List<String>>> nested) {
      Map<String, Map<String, SoundEvent>> result = new HashMap<>();
      nested.forEach((kind, events) -> result.put(kind, bestOf((Map<String, List<String>>)events)));
      return result;
   }

   private static Map<String, SoundEvent> bestOf(Map<String, List<String>> candidates) {
      Map<String, SoundEvent> result = new HashMap<>();
      candidates.forEach((bedrock, java) -> result.put(bedrock, javaSound(NameMatch.best(bedrock, java))));
      return result;
   }
}
