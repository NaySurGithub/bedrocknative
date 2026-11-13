package net.bedrocknative.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class FlipbookData {
   public static final String FILE = "textures/flipbook_textures.json";
   private final Map<String, FlipbookData.Flipbook> byTile;
   private final Set<String> allPaths;

   private FlipbookData(Map<String, FlipbookData.Flipbook> byTile, Set<String> allPaths) {
      this.byTile = byTile;
      this.allPaths = allPaths;
   }

   public static FlipbookData read(PackStack stack) {
      return merge(stack, null).all();
   }

   public static FlipbookData.Views readViews(PackStack stack, Predicate<PackSource> server) {
      return merge(stack, server);
   }

   private static FlipbookData.Views merge(PackStack stack, @Nullable Predicate<PackSource> server) {
      Map<String, FlipbookData.Flipbook> byTile = new HashMap<>();
      Set<String> allPaths = new HashSet<>();
      Map<String, FlipbookData.Flipbook> baseByTile = new HashMap<>();
      Set<String> basePaths = new HashSet<>();

      for (PackSource pack : stack.packs()) {
         Optional<byte[]> data;
         try {
            data = pack.read("textures/flipbook_textures.json");
         } catch (IOException var16) {
            BedrockNative.LOGGER.warn("Couldn't read {} from {}", new Object[]{"textures/flipbook_textures.json", pack, var16});
            continue;
         }

         if (!data.isEmpty()) {
            JsonElement root;
            try {
               JsonReader reader = new JsonReader(new StringReader(new String(data.get(), StandardCharsets.UTF_8)));
               reader.setStrictness(Strictness.LENIENT);
               root = JsonParser.parseReader(reader);
            } catch (RuntimeException var15) {
               BedrockNative.LOGGER.warn("Unreadable Bedrock JSON {} in {}: {}", new Object[]{"textures/flipbook_textures.json", pack, var15.getMessage()});
               continue;
            }

            if (root instanceof JsonArray entries) {
               boolean basePack = server != null && !server.test(pack);

               for (JsonElement element : entries) {
                  if (element instanceof JsonObject entry) {
                     parse(entry, byTile, allPaths);
                     if (basePack) {
                        parse(entry, baseByTile, basePaths);
                     }
                  }
               }
            }
         }
      }

      return new FlipbookData.Views(new FlipbookData(Map.copyOf(byTile), Set.copyOf(allPaths)), new FlipbookData(Map.copyOf(baseByTile), Set.copyOf(basePaths)));
   }

   @Nullable
   public FlipbookData.Flipbook get(String tile, int index) {
      return this.byTile.get(key(tile, index));
   }

   public Set<String> paths() {
      return this.allPaths;
   }

   public int size() {
      return this.byTile.size();
   }

   private static void parse(JsonObject entry, Map<String, FlipbookData.Flipbook> byTile, Set<String> allPaths) {
      String texture = string(entry.get("flipbook_texture"));
      if (texture != null) {
         allPaths.add(texture);
      }

      String tile = string(entry.get("atlas_tile"));
      if (texture != null && tile != null) {
         int index = integer(entry.get("atlas_index"), 0);
         int ticks = Math.max(1, integer(entry.get("ticks_per_frame"), 1));
         List<Integer> frames = new ArrayList<>();
         JsonElement blendFrames = entry.get("frames");
         if (blendFrames instanceof JsonArray) {
            for (JsonElement frame : (JsonArray)blendFrames) {
               int value = integer(frame, -1);
               if (value >= 0) {
                  frames.add(value);
               }
            }
         }

         JsonElement var14 = entry.get("blend_frames");
         boolean blend = !(var14 instanceof JsonElement) || !var14.isJsonPrimitive() || bool(var14);
         byTile.putIfAbsent(key(tile, index), new FlipbookData.Flipbook(texture, ticks, List.copyOf(frames), blend));
      }
   }

   private static String key(String tile, int index) {
      return tile + "#" + index;
   }

   @Nullable
   private static String string(@Nullable JsonElement element) {
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   private static int integer(@Nullable JsonElement element, int fallback) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            return (int)element.getAsDouble();
         } catch (NumberFormatException var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static boolean bool(JsonElement element) {
      if (element.getAsJsonPrimitive().isBoolean()) {
         return element.getAsBoolean();
      } else {
         String text = element.getAsString();
         return !text.equalsIgnoreCase("false") && !text.equals("0");
      }
   }

   public record Flipbook(String texture, int ticksPerFrame, List<Integer> frames, boolean blendFrames) {
      public List<Integer> order(int frameCount) {
         List<Integer> order = new ArrayList<>();
         if (this.frames.isEmpty()) {
            for (int frame = 0; frame < frameCount; frame++) {
               order.add(frame);
            }
         } else {
            for (int frame : this.frames) {
               if (frame < frameCount) {
                  order.add(frame);
               }
            }
         }

         return order;
      }
   }

   public record Views(FlipbookData all, FlipbookData base) {
   }
}
