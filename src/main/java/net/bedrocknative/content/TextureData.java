package net.bedrocknative.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class TextureData {
   public static final String ITEMS = "textures/item_texture.json";
   public static final String TERRAIN = "textures/terrain_texture.json";

   private TextureData() {
   }

   public static Map<String, List<String>> read(PackStack stack, String file) {
      return merge(stack, file, null).all();
   }

   public static TextureData.Views readViews(PackStack stack, String file, Predicate<PackSource> server) {
      return merge(stack, file, server);
   }

   private static TextureData.Views merge(PackStack stack, String file, @Nullable Predicate<PackSource> server) {
      Map<String, List<String>> names = new HashMap<>();
      Map<String, List<String>> base = new HashMap<>();

      for (PackSource pack : stack.packs()) {
         Optional<byte[]> data;
         try {
            data = pack.read(file);
         } catch (IOException var17) {
            BedrockNative.LOGGER.warn("Couldn't read {} from {}", new Object[]{file, pack, var17});
            continue;
         }

         if (!data.isEmpty()) {
            JsonObject root = json(data.get(), file + " in " + pack);
            JsonElement textureData = root == null ? null : root.get("texture_data");
            if (textureData != null && textureData.isJsonObject()) {
               boolean basePack = server != null && !server.test(pack);

               for (Entry<String, JsonElement> entry : textureData.getAsJsonObject().entrySet()) {
                  boolean inAll = !names.containsKey(entry.getKey());
                  boolean inBase = basePack && !base.containsKey(entry.getKey());
                  if ((inAll || inBase) && entry.getValue().isJsonObject()) {
                     List<String> paths = paths(entry.getValue().getAsJsonObject().get("textures"));
                     if (!paths.isEmpty()) {
                        List<String> copy = List.copyOf(paths);
                        if (inAll) {
                           names.put(entry.getKey(), copy);
                        }

                        if (inBase) {
                           base.put(entry.getKey(), copy);
                        }
                     }
                  }
               }
            }
         }
      }

      return new TextureData.Views(names, base);
   }

   public static List<String> unreadable(PackStack stack, String file) {
      List<String> unreadable = new ArrayList<>();

      for (PackSource pack : stack.packs()) {
         try {
            Optional<byte[]> data = pack.read(file);
            if (data.isPresent() && quietJson(data.get()) == null) {
               unreadable.add(file + " in " + pack);
            }
         } catch (IOException var6) {
            unreadable.add(file + " in " + pack + " (" + var6.getMessage() + ")");
         }
      }

      return unreadable;
   }

   private static JsonObject quietJson(byte[] data) {
      try {
         JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));

         JsonObject var3;
         try {
            reader.setStrictness(Strictness.LENIENT);
            JsonElement element = JsonParser.parseReader(reader);
            var3 = element.isJsonObject() ? element.getAsJsonObject() : null;
         } catch (Throwable var5) {
            try {
               reader.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }

            throw var5;
         }

         reader.close();
         return var3;
      } catch (RuntimeException | IOException var6) {
         return null;
      }
   }

   public static JsonObject json(byte[] data, String what) {
      try {
         JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));

         JsonObject var4;
         try {
            reader.setStrictness(Strictness.LENIENT);
            JsonElement element = JsonParser.parseReader(reader);
            var4 = element.isJsonObject() ? element.getAsJsonObject() : null;
         } catch (Throwable var6) {
            try {
               reader.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         reader.close();
         return var4;
      } catch (RuntimeException | IOException var7) {
         BedrockNative.LOGGER.warn("Unreadable Bedrock JSON {}: {}", what, var7.getMessage());
         return null;
      }
   }

   private static List<String> paths(JsonElement textures) {
      List<String> paths = new ArrayList<>();
      if (textures != null && !textures.isJsonNull()) {
         if (textures.isJsonPrimitive()) {
            paths.add(textures.getAsString());
         } else if (textures.isJsonArray()) {
            for (JsonElement element : textures.getAsJsonArray()) {
               List<String> inner = paths(element);
               if (!inner.isEmpty()) {
                  paths.add(inner.getFirst());
               }
            }
         } else if (textures.isJsonObject()) {
            JsonObject object = textures.getAsJsonObject();
            if (object.has("path")) {
               paths.addAll(paths(object.get("path")));
            } else if (object.get("variations") instanceof JsonArray variations && !variations.isEmpty()) {
               paths.addAll(paths(variations.get(0)));
            }
         }

         return paths;
      } else {
         return paths;
      }
   }

   public record Views(Map<String, List<String>> all, Map<String, List<String>> base) {
   }
}
