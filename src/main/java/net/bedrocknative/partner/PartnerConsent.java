package net.bedrocknative.partner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;

public final class PartnerConsent {
   public static final String FILE = "partner-servers-consent.json";
   static final int MAX_FILE = 4096;

   private PartnerConsent() {
   }

   public static PartnerConsent.Answer read(Path configFolder, String host) {
      Path file = configFolder.resolve("partner-servers-consent.json");

      try {
         if (Files.isRegularFile(file) && Files.size(file) <= 4096L) {
            if (JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)) instanceof JsonObject object
               && object.get("answer") instanceof JsonPrimitive answer
               && answer.isString()) {
               return switch (answer.getAsString()) {
                  case "refused" -> PartnerConsent.Answer.REFUSED;
                  case "accepted" -> {
                     if (object.get("host") instanceof JsonPrimitive named
                        && named.isString()
                        && named.getAsString().toLowerCase(Locale.ROOT).equals(host.toLowerCase(Locale.ROOT))) {
                        yield PartnerConsent.Answer.ACCEPTED;
                     }

                     yield PartnerConsent.Answer.NONE;
                  }
                  default -> PartnerConsent.Answer.NONE;
               };
            } else {
               return PartnerConsent.Answer.NONE;
            }
         } else {
            return PartnerConsent.Answer.NONE;
         }
      } catch (IOException | JsonParseException | IllegalStateException e) {
         return PartnerConsent.Answer.NONE;
      }
   }

   public static void write(Path configFolder, boolean accepted, String host, Instant now) throws IOException {
      JsonObject object = new JsonObject();
      object.addProperty("answer", accepted ? "accepted" : "refused");
      object.addProperty("host", host.toLowerCase(Locale.ROOT));
      object.addProperty("date", now.toString());
      object.addProperty("note", "Delete this file to be asked again about the partner (sponsored) server list.");
      Files.createDirectories(configFolder);
      Path file = configFolder.resolve("partner-servers-consent.json");
      Path temp = configFolder.resolve("partner-servers-consent.json.tmp");
      Files.writeString(temp, object + "\n", StandardCharsets.UTF_8);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }

   public enum Answer {
      NONE,
      ACCEPTED,
      REFUSED;
   }
}
