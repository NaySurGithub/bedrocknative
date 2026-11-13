package net.bedrocknative.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class BedrockTextureMap {
   private static final String MAPPINGS = "/assets/bedrocknative/mappings/bedrock-textures/";
   private final BedrockTextureMap.Atlas atlas;
   private final Map<String, BedrockTextureMap.Entry> entries;

   private BedrockTextureMap(BedrockTextureMap.Atlas atlas, Map<String, BedrockTextureMap.Entry> entries) {
      this.atlas = atlas;
      this.entries = entries;
   }

   public static BedrockTextureMap load(BedrockTextureMap.Atlas atlas) {
      try {
         BedrockTextureMap var2;
         try (InputStream input = BedrockTextureMap.class.getResourceAsStream("/assets/bedrocknative/mappings/bedrock-textures/" + atlas.id() + ".json")) {
            if (input == null) {
               throw new IllegalStateException("missing /assets/bedrocknative/mappings/bedrock-textures/" + atlas.id() + ".json");
            }

            var2 = parse(atlas, new InputStreamReader(input, StandardCharsets.UTF_8));
         }

         return var2;
      } catch (IOException var6) {
         throw new UncheckedIOException(var6);
      }
   }

   public static BedrockTextureMap parse(BedrockTextureMap.Atlas atlas, Reader reader) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      Map<String, BedrockTextureMap.Entry> entries = new LinkedHashMap<>();

      for (Map.Entry<String, JsonElement> sprite : root.entrySet()) {
         JsonObject entry = sprite.getValue().getAsJsonObject();
         List<String> paths = new ArrayList<>();
         if (entry.get("paths") instanceof JsonArray list) {
            list.forEach(path -> paths.add(path.getAsString()));
         }

         List<String> shortNames = new ArrayList<>();
         if (entry.get("terrains") instanceof JsonArray list) {
            list.forEach(name -> shortNames.add(name.getAsString()));
         }

         entries.put(
            sprite.getKey(),
            new BedrockTextureMap.Entry(
               sprite.getKey(),
               string(entry, "keep_java"),
               string(entry, atlas.shortKey),
               entry.has("index") ? entry.get("index").getAsInt() : 0,
               string(entry, "path"),
               List.copyOf(paths),
               List.copyOf(shortNames),
               string(entry, "op"),
               string(entry, "transform"),
               entry.has("frame") ? entry.get("frame").getAsInt() : 0,
               entry.has("frames") ? entry.get("frames").getAsInt() : 0,
               bool(entry, "flipbook"),
               bool(entry, "keep_java_alpha")
            )
         );
      }

      return new BedrockTextureMap(atlas, Collections.unmodifiableMap(entries));
   }

   public BedrockTextureMap.Atlas atlas() {
      return this.atlas;
   }

   public Map<String, BedrockTextureMap.Entry> entries() {
      return this.entries;
   }

   @Nullable
   private static String string(JsonObject entry, String key) {
      JsonElement value = entry.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }

   private static boolean bool(JsonObject entry, String key) {
      JsonElement value = entry.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsBoolean();
   }

   public static enum Atlas {
      BLOCKS("blocks", "textures/terrain_texture.json", "terrain"),
      ITEMS("items", "textures/item_texture.json", "item_texture");

      private final String id;
      private final String table;
      private final String shortKey;

      private Atlas(String id, String table, String shortKey) {
         this.id = id;
         this.table = table;
         this.shortKey = shortKey;
      }

      public String id() {
         return this.id;
      }

      public String table() {
         return this.table;
      }
   }

   public record Entry(
      String sprite,
      @Nullable String keepJava,
      @Nullable String shortName,
      int index,
      @Nullable String path,
      List<String> paths,
      List<String> shortNames,
      @Nullable String op,
      @Nullable String transform,
      int frame,
      int frames,
      boolean flipbook,
      boolean keepJavaAlpha
   ) {
   }
}
