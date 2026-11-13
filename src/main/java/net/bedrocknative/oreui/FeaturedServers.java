package net.bedrocknative.oreui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class FeaturedServers {
   public static final String FILE = "featured-servers.json";
   static final int MAX_SERVERS = 32;
   static final int MAX_TEXT = 512;
   static final int DEFAULT_PORT = 19132;

   private FeaturedServers() {
   }

   public static List<FeaturedServers.Server> load(Path configFolder) {
      Path file = configFolder.resolve("featured-servers.json");
      if (!Files.isRegularFile(file)) {
         return List.of();
      }

      try {
         if (Files.size(file) > 262144L) {
            BedrockNative.LOGGER.warn("Ore UI: {} is too large, no featured servers", file);
            return List.of();
         } else {
            return parse(Files.readString(file, StandardCharsets.UTF_8), configFolder);
         }
      } catch (IOException | JsonParseException | IllegalStateException e) {
         BedrockNative.LOGGER.warn("Ore UI: couldn't read {}, no featured servers", file, e);
         return List.of();
      }
   }

   static List<FeaturedServers.Server> parse(String json, Path configFolder) {
      JsonElement root = JsonParser.parseString(json);
      JsonArray array = root instanceof JsonObject object && object.get("servers") instanceof JsonArray servers
         ? servers
         : (root instanceof JsonArray servers ? servers : null);
      if (array == null) {
         return List.of();
      }

      List<FeaturedServers.Server> servers = new ArrayList<>();

      for (JsonElement element : array) {
         if (servers.size() >= 32) {
            break;
         }

         if (element instanceof JsonObject entry) {
            String name = text(entry, "name");
            String address = text(entry, "address").trim();
            if (!name.isBlank() && !address.isEmpty() && address.indexOf(32) < 0) {
               int port = entry.get("port") instanceof JsonElement value && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                  ? value.getAsInt()
                  : 19132;
               if (port >= 1 && port <= 65535) {
                  servers.add(
                     new FeaturedServers.Server(
                        name.strip(),
                        address,
                        port,
                        text(entry, "message"),
                        text(entry, "description"),
                        picture(entry, "icon", configFolder),
                        picture(entry, "banner", configFolder)
                     )
                  );
               }
            }
         }
      }

      return List.copyOf(servers);
   }

   private static String text(JsonObject entry, String key) {
      JsonElement value = entry.get(key);
      if (value != null && value.isJsonPrimitive()) {
         String text = value.getAsString();
         return text.length() > 512 ? text.substring(0, 512) : text;
      } else {
         return "";
      }
   }

   private static @Nullable Path picture(JsonObject entry, String key, Path configFolder) {
      String name = text(entry, key).trim();
      if (!name.isEmpty() && !name.contains("://") && name.toLowerCase(Locale.ROOT).endsWith(".png")) {
         try {
            Path path = configFolder.resolve(name).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
         } catch (RuntimeException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public record Server(
      String name, String address, int port, String message, String description, @Nullable Path icon, @Nullable Path banner, boolean sponsored
   ) {
      public Server(String name, String address, int port, String message, String description, @Nullable Path icon, @Nullable Path banner) {
         this(name, address, port, message, description, icon, banner, false);
      }
   }
}
