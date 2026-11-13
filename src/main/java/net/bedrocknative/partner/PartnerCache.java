package net.bedrocknative.partner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class PartnerCache {
   public static final String FILE = "partner-servers-cache.json";
   public static final String PICTURES = "partner-servers-pictures";
   public static final Duration MAX_AGE = Duration.ofDays(30L);
   static final int MAX_FILE = 589824;
   private static final Pattern PICTURE_NAME = Pattern.compile("[0-9a-f]{64}\\.png");

   private PartnerCache() {
   }

   @Nullable
   public static PartnerCache.Cached load(Path configFolder, String url, Instant now) {
      Path file = configFolder.resolve("partner-servers-cache.json");

      try {
         if (Files.isRegularFile(file) && Files.size(file) <= 589824L) {
            if (JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)) instanceof JsonObject object
               && object.get("url") instanceof JsonPrimitive cachedUrl
               && cachedUrl.getAsString().equals(url)
               && object.get("fetched") instanceof JsonPrimitive fetchedText
               && object.get("body") instanceof JsonPrimitive body) {
               Instant fetched = Instant.parse(fetchedText.getAsString());
               if (!fetched.isAfter(now.plus(Duration.ofDays(1L))) && !fetched.plus(MAX_AGE).isBefore(now)) {
                  Map<String, String> pictures = new HashMap<>();
                  if (object.get("pictures") instanceof JsonObject map) {
                     for (Entry<String, JsonElement> entry : map.entrySet()) {
                        if (entry.getValue() instanceof JsonPrimitive name && PICTURE_NAME.matcher(name.getAsString()).matches()) {
                           pictures.put(entry.getKey(), name.getAsString());
                        }
                     }
                  }

                  return new PartnerCache.Cached(body.getAsString(), fetched, pictures);
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (JsonParseException | IllegalStateException | DateTimeParseException | UnsupportedOperationException | IOException var16) {
         return null;
      }
   }

   public static void save(Path configFolder, String url, Instant fetched, String body, Map<String, String> pictures) throws IOException {
      JsonObject object = new JsonObject();
      object.addProperty("url", url);
      object.addProperty("fetched", fetched.toString());
      object.addProperty("body", body);
      JsonObject map = new JsonObject();
      pictures.forEach(map::addProperty);
      object.add("pictures", map);
      Files.createDirectories(configFolder);
      Path temp = configFolder.resolve("partner-servers-cache.json.tmp");
      Files.writeString(temp, object + "\n", StandardCharsets.UTF_8);
      Files.move(temp, configFolder.resolve("partner-servers-cache.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }

   public static String storePicture(Path configFolder, byte[] png) throws IOException {
      String name = sha256(png) + ".png";
      Path folder = configFolder.resolve("partner-servers-pictures");
      Files.createDirectories(folder);
      Path file = folder.resolve(name);
      if (!Files.isRegularFile(file) || !name.equals(sha256(Files.readAllBytes(file)) + ".png")) {
         Path temp = folder.resolve(name + ".tmp");
         Files.write(temp, png);
         Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }

      return name;
   }

   @Nullable
   public static Path picture(Path configFolder, String name, PartnerImage.Kind kind) {
      if (!PICTURE_NAME.matcher(name).matches()) {
         return null;
      } else {
         Path file = configFolder.resolve("partner-servers-pictures").resolve(name);

         try {
            if (Files.isRegularFile(file) && Files.size(file) <= kind.maxUrlBytes) {
               byte[] bytes = Files.readAllBytes(file);
               if (!name.equals(sha256(bytes) + ".png")) {
                  return null;
               } else {
                  PartnerImage.check(bytes, kind, kind.maxUrlBytes);
                  return file;
               }
            } else {
               return null;
            }
         } catch (IOException var5) {
            return null;
         }
      }
   }

   public static void prunePictures(Path configFolder, Set<String> keep) {
      Path folder = configFolder.resolve("partner-servers-pictures");
      if (Files.isDirectory(folder)) {
         try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
               String name = file.getFileName().toString();
               if ((PICTURE_NAME.matcher(name).matches() || name.endsWith(".png.tmp")) && !keep.contains(name)) {
                  Files.deleteIfExists(file);
               }
            }
         } catch (IOException var9) {
         }
      }
   }

   public static void clear(Path configFolder) {
      try {
         Files.deleteIfExists(configFolder.resolve("partner-servers-cache.json"));
      } catch (IOException var2) {
      }

      prunePictures(configFolder, Set.of());
   }

   static String sha256(byte[] bytes) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (NoSuchAlgorithmException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public record Cached(String body, Instant fetched, Map<String, String> pictures) {
      public Cached(String body, Instant fetched, Map<String, String> pictures) {
         pictures = Map.copyOf(pictures);
         this.body = body;
         this.fetched = fetched;
         this.pictures = pictures;
      }
   }
}
