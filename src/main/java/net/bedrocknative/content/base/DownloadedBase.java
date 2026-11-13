package net.bedrocknative.content.base;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class DownloadedBase {
   public static final String FOLDER_PROPERTY = "bedrocknative.baseFolder";
   private static volatile DownloadedBase.@Nullable Readiness cached;

   private DownloadedBase() {
   }

   public static @Nullable Path folder() {
      String configured = System.getProperty("bedrocknative.baseFolder");
      if (configured != null && !configured.isBlank()) {
         return Path.of(configured.trim());
      }

      BaseTable table = BaseTable.bundled();
      return table == null ? null : dataRoot().resolve("base-pack").resolve(table.folderName());
   }

   static Path dataRoot() {
      String local = System.getenv("LOCALAPPDATA");
      return local != null && !local.isBlank() ? Path.of(local, "BedrockNative") : Path.of(System.getProperty("user.home"), ".local", "share", "BedrockNative");
   }

   static Path stateFile(Path folder) {
      return folder.resolveSibling(folder.getFileName() + ".json");
   }

   public static Optional<Path> ready() {
      Path folder = folder();
      if (folder == null) {
         return Optional.empty();
      }

      DownloadedBase.Readiness known = cached;
      if (known == null || !known.folder().equals(folder)) {
         known = new DownloadedBase.Readiness(folder, check(folder, BaseTable.bundled()));
         cached = known;
      }

      return known.ready() ? Optional.of(folder) : Optional.empty();
   }

   public static Optional<Path> override() {
      String configured = System.getProperty("bedrocknative.baseFolder");
      return configured != null && !configured.isBlank() ? ready() : Optional.empty();
   }

   static boolean check(Path folder, @Nullable BaseTable table) {
      if (table != null && Files.isDirectory(folder)) {
         try {
            JsonObject state = JsonParser.parseString(Files.readString(stateFile(folder), StandardCharsets.UTF_8)).getAsJsonObject();
            return "complete".equals(string(state, "core")) && table.digest().equals(string(state, "table"));
         } catch (IOException | RuntimeException e) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static @Nullable String string(JsonObject object, String key) {
      return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
   }

   public static void invalidate() {
      cached = null;
   }

   public static void markComplete(Path folder, BaseTable table, long files, long bytes) throws IOException {
      JsonObject state = new JsonObject();
      state.addProperty("core", "complete");
      state.addProperty("table", table.digest());
      state.addProperty("source", table.repository() + " " + table.tag() + " " + table.commit());
      state.addProperty("files", files);
      state.addProperty("bytes", bytes);
      state.addProperty("completed", Instant.now().toString());
      Path file = stateFile(folder);
      Path part = file.resolveSibling(file.getFileName() + "." + ProcessHandle.current().pid() + ".part");
      Files.writeString(part, state.toString(), StandardCharsets.UTF_8);
      Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      invalidate();
   }

   static void markIncomplete(Path folder) throws IOException {
      Files.deleteIfExists(stateFile(folder));
      invalidate();
   }

   private record Readiness(Path folder, boolean ready) {
   }
}
