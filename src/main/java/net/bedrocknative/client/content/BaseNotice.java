package net.bedrocknative.client.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import net.bedrocknative.content.base.BaseConsent;
import net.bedrocknative.content.base.BaseTable;

final class BaseNotice {
   static final String FILE = "base-notice.json";

   private BaseNotice() {
   }

   private static String archive() {
      BaseTable table = BaseTable.bundled();
      return table == null ? "" : table.folderName();
   }

   static boolean shouldShow(Path configFolder) {
      if ("on".equals(System.getProperty("bedrocknative.baseNotice")) && BaseConsent.policy() == BaseConsent.Policy.ASK) {
         try {
            JsonObject json = JsonParser.parseString(Files.readString(configFolder.resolve("base-notice.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            return !archive().equals(json.get("seen").getAsString());
         } catch (RuntimeException | IOException var2) {
            return true;
         }
      } else {
         return false;
      }
   }

   static void markSeen(Path configFolder) throws IOException {
      JsonObject json = new JsonObject();
      json.addProperty("seen", archive());
      json.addProperty("at", Instant.now().toString());
      Files.createDirectories(configFolder);
      Path file = configFolder.resolve("base-notice.json");
      Path part = file.resolveSibling("base-notice.json." + ProcessHandle.current().pid() + ".part");
      Files.writeString(part, json.toString(), StandardCharsets.UTF_8);
      Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }
}
