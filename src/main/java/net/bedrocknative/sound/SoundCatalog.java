package net.bedrocknative.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackPaths;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.model.ModelJson;
import org.jspecify.annotations.Nullable;

public final class SoundCatalog {
   public static final List<String> EXTENSIONS = List.of(".ogg", ".fsb", ".wav");
   private final List<PackSource> topFirst;
   private final int serverPacks;
   private final SoundDefinitions definitions;
   private final SoundEventTable events;
   private final MusicDefinitions music;
   private final Map<String, String> blockSounds;
   private final boolean baseGame;
   public static final byte[] NOT_READ = new byte[0];

   private SoundCatalog(
      List<PackSource> topFirst,
      int serverPacks,
      SoundDefinitions definitions,
      SoundEventTable events,
      MusicDefinitions music,
      Map<String, String> blockSounds,
      boolean baseGame
   ) {
      this.topFirst = topFirst;
      this.serverPacks = serverPacks;
      this.definitions = definitions;
      this.events = events;
      this.music = music;
      this.blockSounds = blockSounds;
      this.baseGame = baseGame;
   }

   public static SoundCatalog empty() {
      return new SoundCatalog(List.of(), 0, SoundDefinitions.empty(), SoundEventTable.empty(), MusicDefinitions.empty(), Map.of(), false);
   }

   public static SoundCatalog load(List<? extends PackSource> topFirst, int serverPacks) {
      List<SoundDefinitions.PackJson> definitionFiles = new ArrayList<>();
      List<SoundDefinitions.PackJson> eventFiles = new ArrayList<>();
      List<SoundDefinitions.PackJson> musicFiles = new ArrayList<>();
      Map<String, String> blockSounds = new HashMap<>();
      int count = topFirst.size();

      for (int index = count - 1; index >= 0; index--) {
         PackSource pack = topFirst.get(index);
         int stackIndex = count - 1 - index;
         boolean server = index < serverPacks;
         read(pack, "sounds/sound_definitions.json").ifPresent(json -> definitionFiles.add(new SoundDefinitions.PackJson(json, stackIndex, server)));
         read(pack, "sounds.json").ifPresent(json -> eventFiles.add(new SoundDefinitions.PackJson(json, stackIndex, server)));
         read(pack, "sounds/music_definitions.json").ifPresent(json -> musicFiles.add(new SoundDefinitions.PackJson(json, stackIndex, server)));
         read(pack, "blocks.json").ifPresent(json -> {
            for (Entry<String, JsonElement> entry : json.entrySet()) {
               if (entry.getValue() instanceof JsonObject block && ModelJson.string(block, "sound") instanceof String sound && !sound.isBlank()) {
                  blockSounds.put(entry.getKey().toLowerCase(Locale.ROOT), sound.toLowerCase(Locale.ROOT));
               }
            }
         });
      }

      boolean baseGame = definitionFiles.stream().anyMatch(file -> !file.serverPack());
      return new SoundCatalog(
         List.copyOf(topFirst),
         serverPacks,
         SoundDefinitions.of(definitionFiles),
         SoundEventTable.of(eventFiles),
         MusicDefinitions.of(musicFiles),
         Map.copyOf(blockSounds),
         baseGame
      );
   }

   public boolean baseGame() {
      return this.baseGame;
   }

   private static Optional<JsonObject> read(PackSource pack, String path) {
      try {
         Optional<byte[]> data = pack.read(path);
         return data.isEmpty() ? Optional.empty() : Optional.ofNullable(ModelJson.parse(data.get(), path + " in " + pack));
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Couldn't read {} from {}", new Object[]{path, pack, e});
         return Optional.empty();
      }
   }

   public SoundDefinitions definitions() {
      return this.definitions;
   }

   public SoundEventTable events() {
      return this.events;
   }

   public MusicDefinitions music() {
      return this.music;
   }

   public @Nullable String blockSound(String blockName) {
      String key = blockName.toLowerCase(Locale.ROOT);
      String sound = this.blockSounds.get(key);
      if (sound == null && key.startsWith("minecraft:")) {
         sound = this.blockSounds.get(key.substring(10));
      }

      return sound;
   }

   public int blockSoundCount() {
      return this.blockSounds.size();
   }

   public SoundCatalog.@Nullable SoundFile file(String path, List<? extends PackSource> extra) {
      String clean = path.replace('\\', '/');
      if (PackPaths.escapes(clean)) {
         return null;
      }

      String lower = clean.toLowerCase(Locale.ROOT);
      boolean hasExtension = EXTENSIONS.stream().anyMatch(lower::endsWith);

      for (int index = 0; index < this.topFirst.size() + extra.size(); index++) {
         PackSource pack = index < this.topFirst.size() ? this.topFirst.get(index) : extra.get(index - this.topFirst.size());

         for (String extension : hasExtension ? List.of("") : EXTENSIONS) {
            try {
               Optional<byte[]> data = pack.read(clean + extension);
               if (data.isPresent()) {
                  return new SoundCatalog.SoundFile(clean + extension, data.get(), pack, index < this.serverPacks);
               }
            } catch (IOException | RuntimeException e) {
               BedrockNative.LOGGER.debug("Couldn't read {} from {}: {}", new Object[]{clean + extension, pack, e.toString()});
            }
         }
      }

      return null;
   }

   public SoundCatalog.@Nullable SoundFile foundFile(String path, List<? extends PackSource> extra) {
      String clean = path.replace('\\', '/');
      if (PackPaths.escapes(clean)) {
         return null;
      }

      String lower = clean.toLowerCase(Locale.ROOT);
      boolean hasExtension = EXTENSIONS.stream().anyMatch(lower::endsWith);

      for (int index = 0; index < this.topFirst.size() + extra.size(); index++) {
         PackSource pack = index < this.topFirst.size() ? this.topFirst.get(index) : extra.get(index - this.topFirst.size());

         for (String extension : hasExtension ? List.of("") : EXTENSIONS) {
            String candidate = clean + extension;

            try {
               if (pack.has(candidate)) {
                  return new SoundCatalog.SoundFile(candidate, NOT_READ, pack, index < this.serverPacks);
               }
            } catch (IOException | RuntimeException e) {
               BedrockNative.LOGGER.debug("Couldn't look up {} in {}: {}", new Object[]{candidate, pack, e.toString()});
            }
         }
      }

      return null;
   }

   public List<PackSource> packs() {
      return this.topFirst;
   }

   public int serverPacks() {
      return this.serverPacks;
   }

   public record SoundFile(String path, byte[] data, PackSource pack, boolean serverPack) {
   }
}
