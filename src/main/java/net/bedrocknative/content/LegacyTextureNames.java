package net.bedrocknative.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class LegacyTextureNames {
   private static final String NAMES = "/assets/bedrocknative/mappings/legacy-java-textures/names.txt";
   private static final Set<String> KEPT_ELIGIBLE = Set.of("standalone_texture", "java_only");
   private static final Set<String> EXCLUDED = Set.of("block/water_still", "block/water_flow", "block/water_overlay");
   private static final String[] COLORS = new String[]{
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   };
   private static final Map<BedrockTextureMap.Atlas, Map<String, List<LegacyTextureNames.Candidate>>> BUNDLED = new ConcurrentHashMap<>();
   @Nullable
   private static volatile Map<String, List<String>> bundledNames;

   private LegacyTextureNames() {
   }

   public static Map<String, List<LegacyTextureNames.Candidate>> bundled(BedrockTextureMap.Atlas atlas) {
      return BUNDLED.computeIfAbsent(atlas, key -> candidates(BedrockTextureMap.load(key), bundledNames(), EnumSet.allOf(LegacyTextureNames.Route.class)));
   }

   public static Map<String, List<String>> bundledNames() {
      Map<String, List<String>> names = bundledNames;
      if (names == null) {
         try (InputStream input = LegacyTextureNames.class.getResourceAsStream("/assets/bedrocknative/mappings/legacy-java-textures/names.txt")) {
            if (input == null) {
               throw new IllegalStateException("missing /assets/bedrocknative/mappings/legacy-java-textures/names.txt");
            }

            names = parse(new InputStreamReader(input, StandardCharsets.UTF_8));
         } catch (IOException var6) {
            throw new UncheckedIOException(var6);
         }

         bundledNames = names;
      }

      return names;
   }

   public static Map<String, List<String>> parse(Reader reader) throws IOException {
      Map<String, List<String>> names = new LinkedHashMap<>();
      BufferedReader lines = new BufferedReader(reader);

      for (String line = lines.readLine(); line != null; line = lines.readLine()) {
         line = line.strip();
         if (!line.isEmpty() && !line.startsWith("#")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
               throw new IOException("legacy texture name line without an old name: " + line);
            }

            if (!parts[0].contains("{color}")) {
               names.merge(parts[0], List.of(parts).subList(1, parts.length), LegacyTextureNames::concat);
            } else {
               for (String color : COLORS) {
                  String old = color.equals("light_gray") ? "silver" : color;
                  List<String> olds = new ArrayList<>();

                  for (int i = 1; i < parts.length; i++) {
                     olds.add(parts[i].replace("{old}", old).replace("{color}", color));
                  }

                  names.merge(parts[0].replace("{color}", color), olds, LegacyTextureNames::concat);
               }
            }
         }
      }

      Map<String, List<String>> frozen = new LinkedHashMap<>();
      names.forEach((sprite, oldsx) -> frozen.put(sprite, List.copyOf(oldsx)));
      return Collections.unmodifiableMap(frozen);
   }

   private static List<String> concat(List<String> a, List<String> b) {
      List<String> joined = new ArrayList<>(a);
      joined.addAll(b);
      return joined;
   }

   public static boolean eligible(BedrockTextureMap.Entry entry) {
      return !EXCLUDED.contains(entry.sprite()) && (entry.keepJava() == null || KEPT_ELIGIBLE.contains(entry.keepJava()));
   }

   public static Map<String, List<LegacyTextureNames.Candidate>> candidates(
      BedrockTextureMap map, Map<String, List<String>> names, Set<LegacyTextureNames.Route> routes
   ) {
      Map<String, List<LegacyTextureNames.Candidate>> all = new LinkedHashMap<>();

      for (BedrockTextureMap.Entry entry : map.entries().values()) {
         if (eligible(entry)) {
            List<LegacyTextureNames.Candidate> candidates = candidates(entry, names, routes);
            if (!candidates.isEmpty()) {
               all.put(entry.sprite(), List.copyOf(candidates));
            }
         }
      }

      return Collections.unmodifiableMap(all);
   }

   static List<LegacyTextureNames.Candidate> candidates(BedrockTextureMap.Entry entry, Map<String, List<String>> names, Set<LegacyTextureNames.Route> routes) {
      String sprite = entry.sprite();
      int slash = sprite.indexOf(47);
      String folder = oldFolder(sprite.substring(0, slash + 1));
      if (folder == null) {
         return List.of();
      } else {
         String name = sprite.substring(slash + 1);
         Map<String, LegacyTextureNames.Candidate> found = new LinkedHashMap<>();
         if (entry.frames() > 0) {
            int cut = name.lastIndexOf(95);
            if (cut > 0 && routes.contains(LegacyTextureNames.Route.TABLE) && name.substring(cut + 1).chars().allMatch(Character::isDigit)) {
               String file = folder + name.substring(0, cut) + ".png";
               found.put(
                  file, new LegacyTextureNames.Candidate(file, LegacyTextureNames.Route.TABLE, Integer.parseInt(name.substring(cut + 1)), entry.frames())
               );
            }

            return new ArrayList<>(found.values());
         } else {
            if (routes.contains(LegacyTextureNames.Route.TABLE)) {
               for (String old : names.getOrDefault(sprite, List.of())) {
                  found.putIfAbsent(folder + old + ".png", new LegacyTextureNames.Candidate(folder + old + ".png", LegacyTextureNames.Route.TABLE, 0, 0));
               }
            }

            if (routes.contains(LegacyTextureNames.Route.BEDROCK_NAME)
               && !names.containsKey(sprite)
               && entry.keepJava() == null
               && entry.op() == null
               && entry.transform() == null
               && entry.paths().isEmpty()
               && entry.path() != null
               && entry.path().startsWith(folder)) {
               String file = entry.path() + ".png";
               found.putIfAbsent(file, new LegacyTextureNames.Candidate(file, LegacyTextureNames.Route.BEDROCK_NAME, 0, 0));
            }

            if (routes.contains(LegacyTextureNames.Route.JAVA_NAME)) {
               found.putIfAbsent(folder + name + ".png", new LegacyTextureNames.Candidate(folder + name + ".png", LegacyTextureNames.Route.JAVA_NAME, 0, 0));
            }

            return new ArrayList<>(found.values());
         }
      }
   }

   @Nullable
   public static String oldFolder(String javaFolder) {
      return switch (javaFolder) {
         case "block/" -> "textures/blocks/";
         case "item/" -> "textures/items/";
         default -> null;
      };
   }

   public static boolean isLegacyFormat(int maxFormat) {
      return maxFormat >= 1 && maxFormat <= 3;
   }

   public record Candidate(String file, LegacyTextureNames.Route route, int frame, int frames) {
      public boolean strip() {
         return this.frames > 0;
      }
   }

   public static enum Route {
      TABLE,
      BEDROCK_NAME,
      JAVA_NAME;
   }
}
