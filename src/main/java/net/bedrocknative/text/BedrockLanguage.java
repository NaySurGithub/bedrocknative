package net.bedrocknative.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.own.OwnUiPack;

public final class BedrockLanguage {
   private static final Map<String, Map<String, String>> CACHED = new ConcurrentHashMap<>();
   static final int CACHED_MOST = 8;

   private BedrockLanguage() {
   }

   public static Optional<Path> textsFolder() {
      return BaseContent.textsFolder();
   }

   public static Map<String, String> load(PackStack stack, String javaCode) {
      Map<String, String> entries = new HashMap<>();
      List<PackSource> bottomFirst = new ArrayList<>(stack.packs());
      Collections.reverse(bottomFirst);

      for (PackSource pack : bottomFirst) {
         read(pack, javaCode, entries);
      }

      return entries;
   }

   private static void read(PackSource pack, String javaCode, Map<String, String> entries) {
      try {
         Optional<byte[]> file = pack.read("texts/" + bedrockCode(javaCode) + ".lang");
         if (file.isEmpty()) {
            file = pack.read("texts/en_US.lang");
         }

         file.ifPresent(data -> new String(data, StandardCharsets.UTF_8).lines().forEach(line -> parse(line, entries)));
      } catch (IOException var4) {
         BedrockNative.LOGGER.warn("Couldn't read Bedrock texts from {}", pack, var4);
      }
   }

   public static Optional<Map<String, String>> baseTexts(String javaCode) {
      Optional<Path> folder = textsFolder();
      if (folder.isPresent()) {
         return Optional.of(loadCached(folder.get(), javaCode));
      } else if (BaseContent.ownUi()) {
         Map<String, String> entries = new HashMap<>();
         read(OwnUiPack.INSTANCE, javaCode, entries);
         return Optional.of(Map.copyOf(entries));
      } else {
         return Optional.empty();
      }
   }

   public static Map<String, String> load(Path texts, String javaCode) {
      Path file = texts.resolve(bedrockCode(javaCode) + ".lang");
      if (!Files.isRegularFile(file)) {
         file = texts.resolve("en_US.lang");
      }

      Map<String, String> entries = new HashMap<>();

      String line;
      try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         while ((line = reader.readLine()) != null) {
            parse(line, entries);
         }
      } catch (IOException var9) {
         BedrockNative.LOGGER.warn("Couldn't read Bedrock language file {}", file, var9);
         return Map.of();
      }

      BedrockNative.LOGGER.info("Loaded {} Bedrock texts from {}", entries.size(), file.getFileName());
      return entries;
   }

   public static Map<String, String> loadCached(Path texts, String javaCode) {
      String key = texts.toAbsolutePath().normalize() + "|" + bedrockCode(javaCode);
      Map<String, String> cached = CACHED.get(key);
      if (cached != null) {
         return cached;
      } else {
         Map<String, String> loaded = Map.copyOf(load(texts, javaCode));
         if (!loaded.isEmpty()) {
            if (CACHED.size() >= 8) {
               CACHED.clear();
            }

            CACHED.put(key, loaded);
         }

         return loaded;
      }
   }

   public static void forgetCached() {
      CACHED.clear();
   }

   private static String bedrockCode(String javaCode) {
      return BedrockLocale.of(javaCode);
   }

   public static void parseLine(String line, Map<String, String> entries) {
      parse(line, entries);
   }

   static void parse(String line, Map<String, String> entries) {
      if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
         line = line.substring(1);
      }

      int equals = line.indexOf(61);
      if (!line.startsWith("##") && equals > 0) {
         String value = line.substring(equals + 1);
         int note = value.indexOf("\t#");
         if (note >= 0) {
            value = value.substring(0, note);
         }

         entries.put(line.substring(0, equals).trim(), value.strip());
      }
   }
}
