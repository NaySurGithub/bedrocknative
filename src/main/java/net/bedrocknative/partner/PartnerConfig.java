package net.bedrocknative.partner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public record PartnerConfig(String url) {
   public static final String FILE = "partner-servers.json";
   public static final String DEFAULT_URL = "";
   static final int MAX_FILE = 16384;
   public static final PartnerConfig OFF = new PartnerConfig("");

   public static PartnerConfig load(Path configFolder) {
      Path file = configFolder.resolve("partner-servers.json");
      if (!Files.isRegularFile(file)) {
         return new PartnerConfig("");
      }

      try {
         if (Files.size(file) > 16384L) {
            throw new IOException("larger than 16384 bytes");
         } else {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (root instanceof JsonObject object && object.get("url") instanceof JsonPrimitive url && url.isString()) {
               return new PartnerConfig(url.getAsString().strip());
            } else if (root instanceof JsonObject object && !object.has("url")) {
               return new PartnerConfig("");
            } else {
               throw new IOException("\"url\" must be a string");
            }
         }
      } catch (IOException | JsonParseException | IllegalStateException e) {
         BedrockNative.LOGGER.warn("Partner servers: {} does not read ({}); partner servers off", file, e.getMessage());
         return OFF;
      }
   }

   public @Nullable URI uri(PartnerGuard guard) {
      if (!this.url.isEmpty() && this.url.length() <= 512) {
         try {
            URI uri = new URI(this.url);
            guard.checkForm(uri);
            return uri;
         } catch (URISyntaxException | IOException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public @Nullable String host(PartnerGuard guard) {
      URI uri = this.uri(guard);
      return uri == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
   }
}
