package net.bedrocknative.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PackFeatures {
   static final int MAX_SEARCHED_BYTES = 262144;
   private static final List<String> ITEM_COMPONENTS = List.of("use_animation", "use_duration", "stacked_by_data");
   private static final List<String> LIGHTING_FOLDERS = List.of(
      "atmospherics", "color_grading", "lighting", "pbr", "point_lights", "shadows", "water", "local_lighting"
   );

   private PackFeatures() {
   }

   public static List<String> notRead(PackSource pack) {
      List<String> paths;
      try {
         paths = pack.list("");
      } catch (RuntimeException var14) {
         return List.of();
      }

      Map<String, Integer> lighting = new LinkedHashMap<>();
      int textureSets = 0;
      int clientBiomes = 0;
      Map<String, Integer> itemComponents = new LinkedHashMap<>();

      for (String path : paths) {
         String lower = path.toLowerCase(Locale.ROOT);
         int slash = lower.indexOf(47);
         String top = slash < 0 ? "" : lower.substring(0, slash);
         if (LIGHTING_FOLDERS.contains(top) && lower.endsWith(".json")) {
            lighting.merge(top, 1, Integer::sum);
         } else if (lower.endsWith(".texture_set.json")) {
            textureSets++;
         } else if (!lower.equals("biomes_client.json") && !lower.endsWith(".client_biome.json")) {
            if (top.equals("items") && lower.endsWith(".json")) {
               String item = text(pack, path);

               for (String component : ITEM_COMPONENTS) {
                  if (item.contains("\"minecraft:" + component + "\"")) {
                     itemComponents.merge(component, 1, Integer::sum);
                  }
               }
            }
         } else {
            clientBiomes++;
         }
      }

      List<String> features = new ArrayList<>();
      if (!lighting.isEmpty()) {
         features.add("Vibrant Visuals settings " + lighting + " (Java's lighting and sky kept)");
      }

      if (textureSets > 0) {
         features.add(textureSets + " texture sets (PBR layers; the colour texture is used)");
      }

      if (clientBiomes > 0) {
         features.add(clientBiomes + " client biome files (Java's sky and water colours kept)");
      }

      if (!itemComponents.isEmpty()) {
         features.add("items/*.json components " + itemComponents);
      }

      return features;
   }

   private static String text(PackSource pack, String path) {
      try {
         Optional<byte[]> data = pack.read(path);
         return !data.isEmpty() && ((byte[])data.get()).length <= 262144 ? new String(data.get(), StandardCharsets.UTF_8) : "";
      } catch (RuntimeException | IOException var3) {
         return "";
      }
   }
}
