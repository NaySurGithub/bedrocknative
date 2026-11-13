package net.bedrocknative.content.base;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.content.PackPaths;
import org.jspecify.annotations.Nullable;

public final class BaseTable {
   public static final String RESOURCE = "/bedrocknative/base/bedrock-samples.txt";
   public static final String RAW_HOST = "raw.githubusercontent.com";
   public static final long MAX_FILE_BYTES = 67108864L;
   private static volatile @Nullable BaseTable bundled;
   private final String repository;
   private final String tag;
   private final String commit;
   private final String root;
   private final String release;
   private final long releaseSize;
   private final String digest;
   private final Map<String, BaseTable.Entry> byPath;
   private final Map<String, BaseTable.Entry> byLowerPath;

   private BaseTable(
      String repository, String tag, String commit, String root, String release, long releaseSize, String digest, Map<String, BaseTable.Entry> byPath
   ) {
      this.repository = repository;
      this.tag = tag;
      this.commit = commit;
      this.root = root;
      this.release = release;
      this.releaseSize = releaseSize;
      this.digest = digest;
      this.byPath = Collections.unmodifiableMap(byPath);
      Map<String, BaseTable.Entry> lower = new LinkedHashMap<>();
      byPath.values().forEach(entry -> lower.putIfAbsent(entry.path().toLowerCase(Locale.ROOT), entry));
      this.byLowerPath = Collections.unmodifiableMap(lower);
   }

   public static @Nullable BaseTable bundled() {
      BaseTable table = bundled;
      if (table == null) {
         try (InputStream in = BaseTable.class.getResourceAsStream("/bedrocknative/base/bedrock-samples.txt")) {
            if (in == null) {
               return null;
            }

            table = parse(in.readAllBytes());
            bundled = table;
         } catch (IOException | IllegalArgumentException e) {
            return null;
         }
      }

      return table;
   }

   public static BaseTable parse(byte[] text) {
      Map<String, String> header = new LinkedHashMap<>();
      Map<String, BaseTable.Entry> entries = new LinkedHashMap<>();

      for (String line : new String(text, StandardCharsets.UTF_8).split("\n")) {
         String clean = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
         if (!clean.isEmpty() && !clean.startsWith("#")) {
            String[] fields = clean.split("\t", -1);
            if (fields.length == 2) {
               header.put(fields[0], fields[1]);
            } else {
               if (fields.length != 4) {
                  throw new IllegalArgumentException("Bad base table line: " + clean);
               }

               BaseTable.Part part = BaseTable.Part.of(fields[0]);
               long size = Long.parseLong(fields[1]);
               String sha1 = fields[2].toLowerCase(Locale.ROOT);
               String path = fields[3];
               if (part == null || size < 0L || size > 67108864L || !sha1.matches("[0-9a-f]{40}") || !safe(path)) {
                  throw new IllegalArgumentException("Bad base table entry: " + clean);
               }

               if (entries.put(path, new BaseTable.Entry(path, size, sha1, part)) != null) {
                  throw new IllegalArgumentException("Duplicate base table entry: " + path);
               }
            }
         }
      }

      String repository = required(header, "repository");
      String commit = required(header, "commit");
      if (repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") && commit.matches("[0-9a-f]{40}")) {
         String root = header.getOrDefault("root", "");
         if (root.isEmpty() || root.endsWith("/") && safe(root.substring(0, root.length() - 1))) {
            String release = header.getOrDefault("release", "");
            long releaseSize = Long.parseLong(header.getOrDefault("releaseSize", "0"));
            return new BaseTable(repository, required(header, "tag"), commit, root, release, releaseSize, sha256(text), entries);
         } else {
            throw new IllegalArgumentException("Bad base table root: " + root);
         }
      } else {
         throw new IllegalArgumentException("Bad base table source: " + repository + " " + commit);
      }
   }

   private static String required(Map<String, String> header, String key) {
      String value = header.get(key);
      if (value != null && !value.isBlank()) {
         return value;
      } else {
         throw new IllegalArgumentException("Base table without " + key);
      }
   }

   static boolean safe(String path) {
      if (!path.isEmpty() && !PackPaths.escapes(path) && path.indexOf(92) < 0) {
         for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) < ' ') {
               return false;
            }
         }

         for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.endsWith(".") || segment.endsWith(" ")) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   static String sha256(byte[] data) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public static String blobSha1(byte[] data) {
      return blobSha1(data, data.length);
   }

   public static String blobSha1(byte[] data, int length) {
      try {
         MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
         sha1.update(("blob " + length + "\u0000").getBytes(StandardCharsets.US_ASCII));
         sha1.update(data, 0, length);
         return HexFormat.of().formatHex(sha1.digest());
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public String repository() {
      return this.repository;
   }

   public String tag() {
      return this.tag;
   }

   public String commit() {
      return this.commit;
   }

   public String digest() {
      return this.digest;
   }

   public String pin() {
      return this.repository.substring(this.repository.indexOf(47) + 1) + " " + this.tag + " (" + this.commit.substring(0, 8) + ")";
   }

   public String folderName() {
      return "samples-" + this.commit.substring(0, 8);
   }

   public Optional<BaseTable.Entry> entry(String path) {
      BaseTable.Entry entry = this.byPath.get(path);
      return entry != null ? Optional.of(entry) : Optional.ofNullable(this.byLowerPath.get(path.toLowerCase(Locale.ROOT)));
   }

   public List<BaseTable.Entry> entries() {
      return List.copyOf(this.byPath.values());
   }

   public List<BaseTable.Entry> entries(BaseTable.Part part) {
      List<BaseTable.Entry> found = new ArrayList<>();

      for (BaseTable.Entry entry : this.byPath.values()) {
         if (entry.part() == part) {
            found.add(entry);
         }
      }

      return found;
   }

   public URI rawUrl(BaseTable.Entry entry) {
      try {
         return new URI("https", "raw.githubusercontent.com", "/" + this.repository + "/" + this.commit + "/" + this.root + entry.path(), null);
      } catch (URISyntaxException e) {
         throw new IllegalArgumentException("Bad base file path " + entry.path(), e);
      }
   }

   public Optional<URI> releaseUrl() {
      if (this.release.isEmpty()) {
         return Optional.empty();
      }

      try {
         return Optional.of(new URI("https", "github.com", "/" + this.repository + "/releases/download/" + this.tag + "/" + this.release, null));
      } catch (URISyntaxException e) {
         return Optional.empty();
      }
   }

   public long releaseSize() {
      return this.releaseSize;
   }

   public String root() {
      return this.root;
   }

   public long bytes(BaseTable.Part part) {
      long total = 0L;

      for (BaseTable.Entry entry : this.byPath.values()) {
         if (entry.part() == part) {
            total += entry.size();
         }
      }

      return total;
   }

   public record Entry(String path, long size, String sha1, BaseTable.Part part) {
   }

   public enum Part {
      CORE("c"),
      SOUND("s"),
      LANGUAGE("l");

      private final String code;

      Part(String code) {
         this.code = code;
      }

      public String code() {
         return this.code;
      }

      static BaseTable.@Nullable Part of(String code) {
         for (BaseTable.Part part : values()) {
            if (part.code.equals(code)) {
               return part;
            }
         }

         return null;
      }
   }
}
