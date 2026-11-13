package net.bedrocknative.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;

public final class PackCache {
   private static final String PART = ".part";
   static final Duration STALE_PART = Duration.ofMinutes(5L);
   public static final long MAX_CACHE_BYTES = 2147483648L;
   private final Path folder;
   private final long maxBytes;

   public PackCache(Path folder) {
      this(folder, 2147483648L);
   }

   PackCache(Path folder, long maxBytes) {
      this.folder = folder;
      this.maxBytes = maxBytes;
   }

   public Path folder() {
      return this.folder;
   }

   public Optional<Path> find(UUID uuid, String version, long advertisedSize) {
      if (!cacheable(version)) {
         return Optional.empty();
      }

      Path zip = this.file(uuid, version, ".zip");
      Path info = this.file(uuid, version, ".json");
      if (Files.isRegularFile(zip) && Files.isRegularFile(info)) {
         try {
            JsonObject stored = JsonParser.parseString(Files.readString(info, StandardCharsets.UTF_8)).getAsJsonObject();
            if (stored.get("size").getAsLong() == Files.size(zip)
               && stored.get("advertisedSize").getAsLong() == advertisedSize
               && stored.get("sha256").getAsString().equals(HexFormat.of().formatHex(sha256(zip)))) {
               touch(zip);
               return Optional.of(zip);
            }

            BedrockNative.LOGGER.info("Cached pack {}_{} no longer matches its download; downloading it again", uuid, version);
         } catch (IOException | RuntimeException e) {
            BedrockNative.LOGGER.info("Cached pack {}_{} is unreadable; downloading it again", uuid, version);
         }

         this.delete(uuid, version);
         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   public Path store(UUID uuid, String version, long advertisedSize, Path downloaded, byte[] sha256) throws IOException {
      if (!cacheable(version)) {
         throw new IOException("Pack version can't be cached: " + version);
      }

      Path zip = this.file(uuid, version, ".zip");
      Files.createDirectories(this.folder);
      JsonObject stored = new JsonObject();
      stored.addProperty("uuid", uuid.toString());
      stored.addProperty("version", version);
      stored.addProperty("advertisedSize", advertisedSize);
      stored.addProperty("size", Files.size(downloaded));
      stored.addProperty("sha256", HexFormat.of().formatHex(sha256));
      Files.move(downloaded, zip, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(this.file(uuid, version, ".json"), stored.toString(), StandardCharsets.UTF_8);
      this.evict(zip);
      return zip;
   }

   void evict(Path keep) {
      this.trim(this.maxBytes, List.of(keep));
   }

   public void trim(long limit, Collection<Path> keep) {
      if (Files.isDirectory(this.folder)) {
         List<Path> zips;
         try (Stream<Path> files = Files.list(this.folder)) {
            zips = files.filter(file -> isCachedPack(file.getFileName().toString())).filter(x$0 -> Files.isRegularFile(x$0)).toList();
         } catch (IOException e) {
            BedrockNative.LOGGER.debug("Couldn't list {}", this.folder, e);
            return;
         }

         Set<Path> kept = new HashSet<>();
         keep.forEach(path -> kept.add(path.toAbsolutePath().normalize()));
         long total = 0L;
         List<Path> candidates = new ArrayList<>();

         for (Path zip : zips) {
            total += size(zip);
            if (!kept.contains(zip.toAbsolutePath().normalize())) {
               candidates.add(zip);
            }
         }

         candidates.sort(Comparator.comparingLong(PackCache::modified));

         for (Path zip : candidates) {
            if (total <= limit) {
               return;
            }

            long bytes = size(zip);

            try {
               Files.deleteIfExists(zip);
               String name = zip.getFileName().toString();
               Files.deleteIfExists(zip.resolveSibling(name.substring(0, name.length() - ".zip".length()) + ".json"));
               total -= bytes;
               BedrockNative.LOGGER.info("Deleted cached pack {} (cache over {} MiB)", name, limit >> 20);
            } catch (IOException e) {
               BedrockNative.LOGGER.debug("Couldn't delete {}", zip, e);
            }
         }
      }
   }

   private static long size(Path file) {
      try {
         return Files.size(file);
      } catch (IOException e) {
         return 0L;
      }
   }

   private static long modified(Path file) {
      try {
         return Files.getLastModifiedTime(file).toMillis();
      } catch (IOException e) {
         return 0L;
      }
   }

   private static void touch(Path zip) {
      try {
         Files.setLastModifiedTime(zip, FileTime.fromMillis(System.currentTimeMillis()));
      } catch (IOException e) {
         BedrockNative.LOGGER.debug("Couldn't mark {} as used", zip, e);
      }
   }

   public Path temporary(UUID uuid) throws IOException {
      Files.createDirectories(this.folder);
      return Files.createTempFile(this.folder, uuid.toString(), ".part");
   }

   public void removeStaleDownloads() {
      if (Files.isDirectory(this.folder)) {
         long cutoff = System.currentTimeMillis() - STALE_PART.toMillis();

         try (Stream<Path> files = Files.list(this.folder)) {
            for (Path file : files.filter(filex -> filex.getFileName().toString().endsWith(".part")).toList()) {
               try {
                  if (Files.getLastModifiedTime(file).toMillis() < cutoff && Files.deleteIfExists(file)) {
                     BedrockNative.LOGGER.info("Deleted unfinished pack download {}", file.getFileName());
                  }
               } catch (IOException e) {
                  BedrockNative.LOGGER.debug("Couldn't delete {}", file, e);
               }
            }
         } catch (IOException e) {
            BedrockNative.LOGGER.debug("Couldn't list {}", this.folder, e);
         }
      }
   }

   public void delete(UUID uuid, String version) {
      if (cacheable(version)) {
         try {
            Files.deleteIfExists(this.file(uuid, version, ".zip"));
            Files.deleteIfExists(this.file(uuid, version, ".json"));
         } catch (IOException e) {
            BedrockNative.LOGGER.warn("Couldn't delete cached pack {}_{}", new Object[]{uuid, version, e});
         }
      }
   }

   private static boolean isCachedPack(String name) {
      return name.endsWith(".zip") && name.length() > 37 && name.charAt(36) == '_';
   }

   private static boolean cacheable(String version) {
      return version.matches("[0-9A-Za-z+-]{1,32}(\\.[0-9A-Za-z+-]{1,32}){0,7}");
   }

   private Path file(UUID uuid, String version, String extension) {
      return this.folder.resolve(uuid + "_" + version + extension);
   }

   public static byte[] sha256(Path file) throws IOException {
      MessageDigest digest = sha256();

      try (InputStream in = Files.newInputStream(file)) {
         byte[] buffer = new byte[65536];

         int read;
         while ((read = in.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
         }
      }

      return digest.digest();
   }

   public static MessageDigest sha256() {
      try {
         return MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }
}
