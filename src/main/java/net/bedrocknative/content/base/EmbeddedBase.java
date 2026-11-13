package net.bedrocknative.content.base;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.perf.StartupClock;
import org.jspecify.annotations.Nullable;

public final class EmbeddedBase {
   public static final String SWITCH = "bedrocknative.embeddedBase";
   public static final String ARCHIVE_PROPERTY = "bedrocknative.embeddedBaseArchive";
   public static final Set<BaseTable.Part> PARTS = EnumSet.of(BaseTable.Part.CORE, BaseTable.Part.LANGUAGE);
   static final String FOLDER = "/bedrocknative/base-pack/";
   static final int FORMAT = 2;
   static final long WAIT_SECONDS = 10L;
   private static final long WAIT_NOTE_MILLIS = 5L;
   private static final List<Runnable> LATE = new CopyOnWriteArrayList<>();
   private static final AtomicBoolean LATE_TOLD = new AtomicBoolean();
   private static final AtomicBoolean WAIT_TOLD = new AtomicBoolean();
   @Nullable
   private static volatile EmbeddedBase.Opening opening;
   private static volatile boolean timedOut;

   private EmbeddedBase() {
   }

   public static boolean enabled() {
      String value = System.getProperty("bedrocknative.embeddedBase");
      return value == null || !value.trim().equalsIgnoreCase("off");
   }

   public static void prepare() {
      if (enabled() && BedrockInstall.vanillaResourcePack().isEmpty()) {
         start(true);
      }
   }

   private static String sourceKey() {
      String archive = System.getProperty("bedrocknative.embeddedBaseArchive");
      return archive == null ? "" : archive.trim();
   }

   private static synchronized CompletableFuture<EmbeddedBase.Outcome> start(boolean background) {
      String key = sourceKey();
      EmbeddedBase.Opening current = opening;
      if (current != null && current.key().equals(key)) {
         return current.future();
      } else {
         CompletableFuture<EmbeddedBase.Outcome> created = new CompletableFuture<>();
         opening = new EmbeddedBase.Opening(key, created);
         timedOut = false;
         LATE_TOLD.set(false);
         Runnable work = () -> {
            EmbeddedBase.Outcome outcome;
            try {
               outcome = openSource(key);
            } catch (RuntimeException var4x) {
               outcome = EmbeddedBase.Outcome.missing("it couldn't be opened: " + var4x);
               BedrockNative.LOGGER.warn("Embedded base: not used: {}", outcome.problem());
            }

            created.complete(outcome);
            if (outcome.opened() != null) {
               StartupClock.background("embedded base", outcome.opened().millis());
            }

            tellLate();
         };
         if (background) {
            Thread.ofPlatform().daemon().name("BedrockNative embedded base").start(work);
         } else {
            work.run();
         }

         return created;
      }
   }

   public static void whenLate(Runnable listener) {
      LATE.add(listener);
   }

   private static void tellLate() {
      EmbeddedBase.Opening current = opening;
      EmbeddedBase.Outcome outcome = current != null ? current.future().getNow(null) : null;
      if (timedOut && outcome != null && outcome.opened() != null && LATE_TOLD.compareAndSet(false, true)) {
         LATE.forEach(Runnable::run);
      }
   }

   public static Optional<EmbeddedBase.Opened> ready() {
      if (!enabled()) {
         return Optional.empty();
      } else {
         CompletableFuture<EmbeddedBase.Outcome> future = start(false);
         long started = System.nanoTime();
         boolean done = future.isDone();

         try {
            Optional<EmbeddedBase.Opened> opened = Optional.ofNullable(future.get(10L, TimeUnit.SECONDS).opened());
            long waited = (System.nanoTime() - started) / 1000000L;
            if (!done && waited >= 5L && WAIT_TOLD.compareAndSet(false, true)) {
               BedrockNative.LOGGER.info("Embedded base: {} waited {} ms for the opening", Thread.currentThread().getName(), waited);
            }

            return opened;
         } catch (TimeoutException var7) {
            if (!timedOut) {
               timedOut = true;
               BedrockNative.LOGGER.warn("Embedded base: still opening after {} s; Java's look until it is open", 10L);
               tellLate();
            }

            return Optional.empty();
         } catch (InterruptedException var8) {
            Thread.currentThread().interrupt();
            return Optional.empty();
         } catch (ExecutionException var9) {
            return Optional.empty();
         }
      }
   }

   @Nullable
   public static String problem() {
      if (!enabled()) {
         return "turned off by -Dbedrocknative.embeddedBase=off";
      } else {
         EmbeddedBase.Opening current = opening;
         EmbeddedBase.Outcome outcome = current != null ? current.future().getNow(null) : null;
         return outcome != null ? outcome.problem() : null;
      }
   }

   private static EmbeddedBase.Outcome openSource(String key) {
      BaseTable table = BaseTable.bundled();
      if (table == null) {
         return EmbeddedBase.Outcome.missing("the jar carries no base table");
      } else {
         URL archive;
         byte[] sidecar;
         if (key.isEmpty()) {
            String name = table.folderName();
            archive = EmbeddedBase.class.getResource("/bedrocknative/base-pack/" + name + ".zip");

            try (InputStream in = EmbeddedBase.class.getResourceAsStream("/bedrocknative/base-pack/" + name + ".json")) {
               sidecar = in == null ? null : in.readAllBytes();
            } catch (IOException var12) {
               sidecar = null;
            }

            if (archive == null || sidecar == null) {
               EmbeddedBase.Outcome outcome = EmbeddedBase.Outcome.missing("the jar carries no base archive (built with -PbaseBundle=off)");
               BedrockNative.LOGGER.info("Embedded base: {}", outcome.problem());
               return outcome;
            }
         } else {
            Path file = Path.of(key);
            String fileName = file.getFileName().toString();
            Path sidecarFile = file.resolveSibling((fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName) + ".json");

            try {
               archive = file.toUri().toURL();
               sidecar = Files.readAllBytes(sidecarFile);
            } catch (IOException var9) {
               EmbeddedBase.Outcome outcome = EmbeddedBase.Outcome.missing("the archive -Dbedrocknative.embeddedBaseArchive names can't be read (" + var9 + ")");
               BedrockNative.LOGGER.warn("Embedded base: not used: {}", outcome.problem());
               return outcome;
            }
         }

         EmbeddedBase.Outcome outcome = open(archive, sidecar, table, DownloadedBase.dataRoot().resolve("base-pack"));
         EmbeddedBase.Opened opened = outcome.opened();
         if (opened != null) {
            BedrockNative.LOGGER
               .info(
                  "Embedded base: {} files ({} MB) of {} read from {} in {} ms",
                  new Object[]{
                     opened.files(), String.format(Locale.ROOT, "%.1f", opened.fileBytes() / 1000000.0), table.pin(), opened.source(), opened.millis()
                  }
               );
         } else {
            BedrockNative.LOGGER.warn("Embedded base: not used: {}", outcome.problem());
         }

         return outcome;
      }
   }

   static EmbeddedBase.Outcome open(URL archive, byte[] sidecarBytes, BaseTable table, Path cacheFolder) {
      long started = System.nanoTime();

      EmbeddedBase.Sidecar sidecar;
      try {
         sidecar = EmbeddedBase.Sidecar.parse(sidecarBytes);
      } catch (RuntimeException var20) {
         return EmbeddedBase.Outcome.missing("its sidecar can't be read (" + var20.getMessage() + ")");
      }

      if (sidecar.format() != 2) {
         return EmbeddedBase.Outcome.missing("its sidecar has format " + sidecar.format() + ", this mod reads 2");
      } else if (!sidecar.table().equals(table.digest())) {
         return EmbeddedBase.Outcome.missing(
            "it was made from another base table (" + shortHash(sidecar.table()) + "; this jar's is " + shortHash(table.digest()) + ")"
         );
      } else {
         EmbeddedBase.Located located;
         try {
            located = locate(archive, sidecar, table.folderName() + ".zip", cacheFolder);
         } catch (IOException var19) {
            return EmbeddedBase.Outcome.missing("it can't be read: " + var19.getMessage());
         }

         FileSystem system = null;

         try {
            if (!located.checked()) {
               String problem = check(located.path(), sidecar);
               if (problem != null) {
                  return EmbeddedBase.Outcome.missing(problem);
               }
            }

            system = FileSystems.newFileSystem(located.path());
            Path root = system.getRootDirectories().iterator().next();
            List<String> paths = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(root)) {
               walk.filter(x$0 -> Files.isRegularFile(x$0)).forEach(file -> paths.add(root.relativize(file).toString()));
            }

            String problem = compare(root, paths, table);
            if (problem != null) {
               system.close();
               return EmbeddedBase.Outcome.missing(problem);
            } else {
               EmbeddedPack pack = new EmbeddedPack(root, paths, "embedded " + table.folderName());
               long bytes = 0L;

               for (BaseTable.Part part : PARTS) {
                  bytes += table.bytes(part);
               }

               return new EmbeddedBase.Outcome(
                  new EmbeddedBase.Opened(pack, root, located.source(), paths.size(), bytes, (System.nanoTime() - started) / 1000000L), null
               );
            }
         } catch (RuntimeException | IOException var22) {
            if (system != null) {
               try {
                  system.close();
               } catch (IOException var17) {
               }
            }

            return EmbeddedBase.Outcome.missing("it is damaged (" + var22 + ")");
         }
      }
   }

   private static EmbeddedBase.Located locate(URL archive, EmbeddedBase.Sidecar sidecar, String name, Path cacheFolder) throws IOException {
      URI uri;
      try {
         uri = archive.toURI();
      } catch (URISyntaxException var6) {
         uri = null;
      }

      if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
         Path file = Path.of(uri);
         return new EmbeddedBase.Located(file, "the archive " + file, false);
      } else {
         if (uri != null && "jar".equalsIgnoreCase(uri.getScheme())) {
            Path entry = jarEntry(uri);
            if (entry != null) {
               return new EmbeddedBase.Located(entry, "the mod jar " + entry.getFileSystem(), false);
            }
         }

         return new EmbeddedBase.Located(
            copy(archive, sidecar, name, cacheFolder), "one copy of the archive (the class loader gave a " + archive.getProtocol() + ": address)", true
         );
      }
   }

   @Nullable
   private static Path jarEntry(URI uri) {
      try {
         return Path.of(uri);
      } catch (FileSystemNotFoundException var6) {
         String spec = uri.getRawSchemeSpecificPart();
         int separator = spec.indexOf("!/");
         if (separator < 0) {
            return null;
         } else {
            try {
               FileSystem jar = FileSystems.newFileSystem(Path.of(URI.create(spec.substring(0, separator))));
               return jar.getPath(URI.create("entry:" + spec.substring(separator + 1)).getSchemeSpecificPart());
            } catch (RuntimeException | IOException var5) {
               return null;
            }
         }
      } catch (RuntimeException var7) {
         return null;
      }
   }

   static Path copy(URL archive, EmbeddedBase.Sidecar sidecar, String name, Path folder) throws IOException {
      Path target = folder.resolve(name);
      if (Files.isRegularFile(target) && check(target, sidecar) == null) {
         return target;
      } else {
         Path part;
         try {
            Files.createDirectories(folder);
            part = Files.createTempFile(folder, name + ".", ".part");
         } catch (IOException var8) {
            Path temporary = Files.createTempFile("bedrocknative-" + name + ".", ".part");
            temporary.toFile().deleteOnExit();
            copyChecked(archive, sidecar, temporary);
            BedrockNative.LOGGER.info("Embedded base: the data folder {} can't be written ({}); a temporary copy is used", folder, var8.toString());
            return temporary;
         }

         try {
            copyChecked(archive, sidecar, part);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
         } catch (IOException var9) {
            if (Files.isRegularFile(target) && check(target, sidecar) == null) {
               Files.deleteIfExists(part);
               return target;
            } else if (Files.isRegularFile(part) && check(part, sidecar) == null) {
               part.toFile().deleteOnExit();
               return part;
            } else {
               throw var9;
            }
         }
      }
   }

   private static void copyChecked(URL archive, EmbeddedBase.Sidecar sidecar, Path file) throws IOException {
      MessageDigest digest = sha256();
      InputStream in = new DigestInputStream(archive.openStream(), digest);

      long bytes;
      try (OutputStream out = Files.newOutputStream(file)) {
         bytes = in.transferTo(out);
      } catch (Throwable var13) {
         try {
            in.close();
         } catch (Throwable var10) {
            var13.addSuppressed(var10);
         }

         throw var13;
      }

      in.close();
      String var14 = HexFormat.of().formatHex(digest.digest());
      if (bytes != sidecar.bytes() || !var14.equals(sidecar.sha256())) {
         Files.deleteIfExists(file);
         throw new IOException(
            "the archive in the jar is damaged or truncated (SHA-256 "
               + shortHash(var14)
               + ", "
               + bytes
               + " bytes; the build wrote "
               + shortHash(sidecar.sha256())
               + ", "
               + sidecar.bytes()
               + " bytes)"
         );
      }
   }

   @Nullable
   static String check(Path archive, EmbeddedBase.Sidecar sidecar) throws IOException {
      MessageDigest digest = sha256();

      long bytes;
      try (InputStream in = new DigestInputStream(Files.newInputStream(archive), digest)) {
         bytes = in.transferTo(OutputStream.nullOutputStream());
      }

      String var10 = HexFormat.of().formatHex(digest.digest());
      return bytes == sidecar.bytes() && var10.equals(sidecar.sha256())
         ? null
         : "the archive is damaged or truncated (SHA-256 "
            + shortHash(var10)
            + ", "
            + bytes
            + " bytes; the build wrote "
            + shortHash(sidecar.sha256())
            + ", "
            + sidecar.bytes()
            + " bytes)";
   }

   @Nullable
   private static String compare(Path root, List<String> paths, BaseTable table) throws IOException {
      Map<String, BaseTable.Entry> expected = new HashMap<>();

      for (BaseTable.Part part : PARTS) {
         table.entries(part).forEach(entryx -> expected.put(entryx.path(), entryx));
      }

      for (String path : paths) {
         BaseTable.Entry entry = expected.remove(path);
         if (entry == null) {
            return "the archive holds " + path + ", which the base table does not list";
         }

         long size = Files.size(root.resolve(path));
         if (size != entry.size()) {
            return "the archive's " + path + " has " + size + " bytes, the table says " + entry.size();
         }
      }

      return !expected.isEmpty()
         ? "the archive misses " + expected.size() + " of the table's files (" + expected.keySet().stream().sorted().findFirst().orElse("") + "...)"
         : null;
   }

   private static MessageDigest sha256() {
      try {
         return MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException var1) {
         throw new IllegalStateException(var1);
      }
   }

   private static String shortHash(String hash) {
      return hash.length() > 12 ? hash.substring(0, 12) : hash;
   }

   private record Located(Path path, String source, boolean checked) {
   }

   public record Opened(EmbeddedPack pack, Path root, String source, int files, long fileBytes, long millis) {
   }

   private record Opening(String key, CompletableFuture<EmbeddedBase.Outcome> future) {
   }

   public record Outcome(@Nullable EmbeddedBase.Opened opened, @Nullable String problem) {
      static EmbeddedBase.Outcome missing(String why) {
         return new EmbeddedBase.Outcome(null, why);
      }
   }

   record Sidecar(int format, String archive, long bytes, String sha256, String table) {
      static EmbeddedBase.Sidecar parse(byte[] json) {
         JsonObject object = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
         String sha256 = object.get("sha256").getAsString();
         String table = object.get("table").getAsString();
         if (sha256.matches("[0-9a-f]{64}") && table.matches("[0-9a-f]{64}")) {
            return new EmbeddedBase.Sidecar(
               object.get("format").getAsInt(), object.get("archive").getAsString(), object.get("bytes").getAsLong(), sha256, table
            );
         } else {
            throw new IllegalArgumentException("bad hashes");
         }
      }
   }
}
