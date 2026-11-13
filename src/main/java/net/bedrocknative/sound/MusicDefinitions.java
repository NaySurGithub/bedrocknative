package net.bedrocknative.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import org.jspecify.annotations.Nullable;

public final class MusicDefinitions {
   public static final String FILE = "sounds/music_definitions.json";
   private final Map<String, MusicDefinitions.Music> byKey;

   private MusicDefinitions(Map<String, MusicDefinitions.Music> byKey) {
      this.byKey = byKey;
   }

   public static MusicDefinitions empty() {
      return new MusicDefinitions(Map.of());
   }

   public static MusicDefinitions of(List<SoundDefinitions.PackJson> bottomFirst) {
      Map<String, MusicDefinitions.Music> merged = new HashMap<>();

      for (SoundDefinitions.PackJson pack : bottomFirst) {
         for (Entry<String, JsonElement> entry : pack.json().entrySet()) {
            if (entry.getValue() instanceof JsonObject object && ModelJson.string(object, "event_name") instanceof String event) {
               float min = ModelJson.number(object, "min_delay", 0.0F);
               merged.put(
                  entry.getKey().toLowerCase(Locale.ROOT),
                  new MusicDefinitions.Music(event.toLowerCase(Locale.ROOT), min, Math.max(min, ModelJson.number(object, "max_delay", min)))
               );
            }
         }
      }

      return new MusicDefinitions(Map.copyOf(merged));
   }

   public MusicDefinitions.@Nullable Music get(String key) {
      return this.byKey.get(key.toLowerCase(Locale.ROOT));
   }

   public int size() {
      return this.byKey.size();
   }

   public record Music(String event, float minDelay, float maxDelay) {
   }
}
