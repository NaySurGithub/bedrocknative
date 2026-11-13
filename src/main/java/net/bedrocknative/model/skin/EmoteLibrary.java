package net.bedrocknative.model.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.AnimationParser;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class EmoteLibrary {
   public static final EmoteLibrary EMPTY = new EmoteLibrary(Map.of(), Map.of());
   private static final String EMOTE = "persona_emote";
   private final Map<String, EmoteLibrary.Emote> byId;
   private final Map<String, String> names;

   private EmoteLibrary(Map<String, EmoteLibrary.Emote> byId, Map<String, String> names) {
      this.byId = byId;
      this.names = names;
   }

   public static EmoteLibrary load(PackSource pack) {
      MolangCache molang = new MolangCache();
      AnimationParser parser = new AnimationParser(molang, MolangVersion.LATEST);
      Map<String, EmoteLibrary.Emote> byId = new HashMap<>();
      Map<String, String> names = new HashMap<>();

      for (String path : pack.list("pieces")) {
         if (path.toLowerCase(Locale.ROOT).endsWith(".meta.json")) {
            try {
               JsonObject meta = json(pack, path);
               if (meta != null && "persona_emote".equals(ModelJson.string(meta, "piece_type"))) {
                  String id = ModelJson.string(meta, "piece_id");
                  String name = ModelJson.string(meta, "piece_name");
                  JsonArray sources = ModelJson.array(meta, "animation_sources");
                  if (id != null && sources != null) {
                     String folder = path.substring(0, path.lastIndexOf(47) + 1);

                     for (JsonElement element : sources) {
                        if (element instanceof JsonObject source) {
                           String animationName = ModelJson.string(source, "name");
                           String file = ModelJson.string(source, "animationFile");
                           JsonObject animations = file != null ? json(pack, folder + file) : null;
                           AnimationDef animation = animations != null && animationName != null ? parser.parse(animations).get(animationName) : null;
                           if (animation != null) {
                              byId.put(id.toLowerCase(Locale.ROOT), new EmoteLibrary.Emote(id, name != null ? name : animationName, animation));
                              names.put(id.toLowerCase(Locale.ROOT), name != null ? name : animationName);
                              break;
                           }
                        }
                     }
                  }
               }
            } catch (RuntimeException | IOException var19) {
               BedrockNative.LOGGER.debug("Couldn't read the persona emote {}: {}", path, var19.toString());
            }
         }
      }

      return new EmoteLibrary(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(names));
   }

   @Nullable
   private static JsonObject json(PackSource pack, String path) throws IOException {
      Optional<byte[]> data = pack.read(path);
      return data.isPresent() ? ModelJson.parse(data.get(), path) : null;
   }

   @Nullable
   public EmoteLibrary.Emote emote(String pieceId) {
      return this.byId.get(pieceId.toLowerCase(Locale.ROOT));
   }

   public int size() {
      return this.byId.size();
   }

   public List<EmoteLibrary.Emote> all() {
      return this.byId
         .values()
         .stream()
         .sorted(Comparator.<EmoteLibrary.Emote, String>comparing(emote -> emote.name().toLowerCase(Locale.ROOT)).thenComparing(EmoteLibrary.Emote::pieceId))
         .toList();
   }

   public List<String> describe() {
      return this.names.entrySet().stream().map(entry -> entry.getValue() + " " + entry.getKey()).sorted().toList();
   }

   public record Emote(String pieceId, String name, AnimationDef animation) {
   }
}
