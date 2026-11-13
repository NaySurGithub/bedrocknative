package net.bedrocknative.content.base;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class BaseConsent {
   public static final String PROPERTY = "bedrocknative.baseDownload";
   static final String FILE = "base-download.json";

   private BaseConsent() {
   }

   public static BaseConsent.Policy policy() {
      String value = System.getProperty("bedrocknative.baseDownload");
      if (value != null && !value.isBlank()) {
         try {
            return BaseConsent.Policy.valueOf(value.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
            return BaseConsent.Policy.ASK;
         }
      } else {
         return BaseConsent.Policy.ASK;
      }
   }

   @Nullable
   public static BaseConsent.Answer stored(Path configFolder) {
      try {
         JsonObject json = JsonParser.parseString(Files.readString(configFolder.resolve("base-download.json"), StandardCharsets.UTF_8)).getAsJsonObject();
         return BaseConsent.Answer.valueOf(json.get("answer").getAsString().trim().toUpperCase(Locale.ROOT));
      } catch (RuntimeException | IOException var2) {
         return null;
      }
   }

   public static void store(Path configFolder, BaseConsent.Answer answer) throws IOException {
      JsonObject json = new JsonObject();
      json.addProperty("answer", answer.name().toLowerCase(Locale.ROOT));
      json.addProperty("at", Instant.now().toString());
      Files.createDirectories(configFolder);
      Path file = configFolder.resolve("base-download.json");
      Path part = file.resolveSibling("base-download.json." + ProcessHandle.current().pid() + ".part");
      Files.writeString(part, json.toString(), StandardCharsets.UTF_8);
      Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }

   public static boolean allowed(Path configFolder) {
      return switch (policy()) {
         case AUTO -> true;
         case ASK -> stored(configFolder) == BaseConsent.Answer.YES;
         case OFF -> false;
      };
   }

   public static boolean shouldAsk(Path configFolder) {
      if (policy() != BaseConsent.Policy.ASK) {
         return false;
      } else {
         BaseConsent.Answer answer = stored(configFolder);
         return answer == null || answer == BaseConsent.Answer.LATER;
      }
   }

   public static enum Answer {
      YES,
      NO,
      LATER;
   }

   public static enum Policy {
      AUTO,
      ASK,
      OFF;
   }
}
