package net.bedrocknative.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;

public final class ZipPack implements PackSource {
   public static final long MAX_ENTRY_BYTES = 67108864L;
   public static final long MAX_TEXT_BYTES = 8388608L;
   static final int MAX_JSON_SEPARATORS = 500000;
   static final int MAX_UI_JSON_SEPARATORS = 2000000;
   public static final long MAX_TOTAL_BYTES = 1073741824L;
   static final long MAX_DIRECTORY_BYTES = 16777216L;
   static final int MAX_NAME_CHARS = 1024;
   static final int MAX_ENTRIES = 100000;
   public static final long MAX_TOTAL_JSON_BYTES = 67108864L;
   private static final Set<String> BINARY_EXTENSIONS = Set.of(
      "png", "tga", "jpg", "jpeg", "ogg", "wav", "mp3", "fsb", "ttf", "otf", "bin", "mcstructure", "brarchive", "zip", "dat", "ktx", "psd"
   );
   private static final String SUBPACKS = "subpacks/";
   private static final int END_RECORD = 22;
   private final ZipFile zip;
   private final String name;
   private final PackManifest manifest;
   private final Map<String, ZipEntry> entries;
   private final Map<String, String> keys;
   @Nullable
   private final String subpack;
   private final Map<String, String> subpackKeys;

   private ZipPack(
      ZipFile zip,
      String name,
      PackManifest manifest,
      Map<String, ZipEntry> entries,
      Map<String, String> keys,
      @Nullable String subpack,
      Map<String, String> subpackKeys
   ) {
      this.zip = zip;
      this.name = name;
      this.manifest = manifest;
      this.entries = entries;
      this.keys = keys;
      this.subpack = subpack;
      this.subpackKeys = subpackKeys;
   }

   public static PackManifest readManifest(Path file) throws IOException {
      PackManifest var2;
      try (ZipPack pack = open(file, null, "")) {
         var2 = pack.manifest();
      }

      return var2;
   }

   public static ZipPack open(Path file, @Nullable String contentKey, String subpackName) throws IOException {
      checkDirectory(file);

      ZipFile zip;
      try {
         zip = new ZipFile(file.toFile());
      } catch (RuntimeException var6) {
         throw new IOException("Not a zip: " + var6.getMessage(), var6);
      }

      try {
         return open(zip, file.getFileName().toString(), contentKey, subpackName);
      } catch (RuntimeException | IOException var7) {
         zip.close();
         throw var7 instanceof IOException io ? io : new IOException("Invalid pack: " + var7.getMessage(), var7);
      }
   }

   private static ZipPack open(ZipFile zip, String name, @Nullable String contentKey, String subpackName) throws IOException {
      if (zip.size() > 100000) {
         throw new IOException("Too many zip entries: " + zip.size());
      } else {
         Map<String, ZipEntry> all = new HashMap<>();
         Enumeration<? extends ZipEntry> enumeration = zip.entries();
         long declared = 0L;
         long totalJson = 0L;

         while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (!entry.isDirectory() && entry.getName().length() <= 1024) {
               declared += Math.max(entry.getSize(), 0L);
               if (declared > 1073741824L) {
                  throw new IOException("Pack inflates to more than 1073741824 bytes");
               }

               String normalized = PackStack.normalize(entry.getName());
               if (normalized != null) {
                  String key = normalized.toLowerCase(Locale.ROOT);
                  if (all.putIfAbsent(key, entry) == null && isJson(key)) {
                     totalJson += Math.max(0L, entry.getSize());
                     if (totalJson > 67108864L) {
                        throw new IOException("Pack inflates to more than 67108864 bytes of JSON");
                     }
                  }
               }
            }
         }

         String root = root(all);
         if (root == null) {
            throw new IOException("No manifest.json");
         } else {
            Map<String, ZipEntry> entries = new HashMap<>();
            all.forEach((path, entry) -> {
               if (path.startsWith(root)) {
                  entries.put(path.substring(root.length()), entry);
               }
            });
            PackManifest manifest = PackManifest.parse(read(zip, entries.get("manifest.json")));
            Map<String, String> keys = keys(zip, entries.get("contents.json"), contentKey);
            String subpack = null;
            Map<String, String> subpackKeys = Map.of();
            if (!subpackName.isBlank()) {
               String folder = manifest.subpackFolder(subpackName);
               String prefix = ("subpacks/" + (folder != null ? folder : subpackName) + "/").toLowerCase(Locale.ROOT);
               if (PackStack.normalize(prefix + "x") != null && entries.keySet().stream().anyMatch(path -> path.startsWith(prefix))) {
                  subpack = prefix;
                  subpackKeys = keys(zip, entries.get(prefix + "contents.json"), contentKey);
               }
            }

            return new ZipPack(zip, name, manifest, entries, keys, subpack, subpackKeys);
         }
      }
   }

   static void checkDirectory(Path file) throws IOException {
      try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
         long length = channel.size();
         int tail = (int)Math.min(length, 65557L);
         if (tail < 22) {
            return;
         }

         ByteBuffer buffer = ByteBuffer.allocate(tail).order(ByteOrder.LITTLE_ENDIAN);
         readFully(channel, buffer, length - tail);

         for (int at = tail - 22; at >= 0; at--) {
            if (buffer.getInt(at) == 101010256 && at + 22 + (buffer.getShort(at + 20) & '\uffff') == tail) {
               long entries = buffer.getShort(at + 10) & '\uffff';
               long directory = buffer.getInt(at + 12) & 4294967295L;
               if ((entries == 65535L || directory == 4294967295L) && at >= 20 && buffer.getInt(at - 20) == 117853008) {
                  long zip64 = buffer.getLong(at - 20 + 8);
                  ByteBuffer record = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
                  if (zip64 < 0L || zip64 + 56L > length) {
                     throw new IOException("Bad zip64 directory");
                  }

                  readFully(channel, record, zip64);
                  if (record.getInt(0) == 101075792) {
                     entries = record.getLong(32);
                     directory = record.getLong(40);
                  }
               }

               if (entries >= 0L && entries <= 100000L) {
                  if (directory < 0L || directory > 16777216L) {
                     throw new IOException("Zip directory too large: " + directory + " bytes");
                  }

                  return;
               }

               throw new IOException("Too many zip entries: " + entries);
            }
         }
      }
   }

   private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
      while (buffer.hasRemaining()) {
         int read = channel.read(buffer, position + buffer.position());
         if (read < 0) {
            throw new IOException("Truncated zip");
         }
      }
   }

   @Nullable
   private static String root(Map<String, ZipEntry> entries) {
      return entries.containsKey("manifest.json")
         ? ""
         : entries.keySet()
            .stream()
            .filter(path -> path.endsWith("/manifest.json") && path.indexOf(47) == path.length() - "/manifest.json".length())
            .sorted()
            .map(path -> path.substring(0, path.length() - "manifest.json".length()))
            .findFirst()
            .orElse(null);
   }

   private static Map<String, String> keys(ZipFile zip, @Nullable ZipEntry contents, @Nullable String contentKey) throws IOException {
      if (contents != null && contentKey != null) {
         byte[] data = read(zip, contents);
         if (!PackCrypto.isEncryptedContents(data)) {
            return Map.of();
         } else if (contentKey.isEmpty()) {
            throw new IOException("Encrypted pack without a content key");
         } else {
            Map<String, String> keys = new HashMap<>();
            PackCrypto.readContents(data, contentKey).forEach((path, key) -> {
               String normalized = PackStack.normalize(path);
               if (normalized != null) {
                  keys.put(normalized.toLowerCase(Locale.ROOT), key);
               }
            });
            return keys;
         }
      } else {
         return Map.of();
      }
   }

   public PackManifest manifest() {
      return this.manifest;
   }

   public boolean encrypted() {
      return !this.keys.isEmpty() || !this.subpackKeys.isEmpty();
   }

   @Nullable
   public String subpack() {
      return this.subpack;
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      String lower = path.toLowerCase(Locale.ROOT);
      if (this.subpack != null) {
         ZipEntry entry = this.entries.get(this.subpack + lower);
         if (entry != null) {
            String key = this.subpackKeys.getOrDefault(lower, this.keys.get(this.subpack + lower));
            return Optional.of(checked(path, decrypt(read(this.zip, entry), key)));
         }
      }

      ZipEntry entry = this.entries.get(lower);
      return entry == null ? Optional.empty() : Optional.of(checked(path, decrypt(read(this.zip, entry), this.keys.get(lower))));
   }

   @Override
   public boolean has(String path) {
      String lower = path.toLowerCase(Locale.ROOT);
      return this.subpack != null && this.entries.containsKey(this.subpack + lower) || this.entries.containsKey(lower);
   }

   @Override
   public List<String> list(String folder) {
      String prefix = folderPrefix(folder);
      Set<String> paths = new TreeSet<>();

      for (String path : this.entries.keySet()) {
         if (this.subpack != null && path.startsWith(this.subpack)) {
            String inner = path.substring(this.subpack.length());
            if (inner.startsWith(prefix)) {
               paths.add(inner);
            }
         } else if (path.startsWith(prefix) && !path.startsWith("subpacks/")) {
            paths.add(path);
         }
      }

      return List.copyOf(paths);
   }

   @Override
   public List<String> listNames(String folder) {
      String prefix = folder.toLowerCase(Locale.ROOT) + "/";
      Set<String> names = new LinkedHashSet<>();
      if (this.subpack != null) {
         this.collect(this.subpack + prefix, names);
      }

      this.collect(prefix, names);
      return List.copyOf(names);
   }

   private void collect(String prefix, Set<String> names) {
      for (String path : this.entries.keySet()) {
         if (path.startsWith(prefix) && path.indexOf(47, prefix.length()) < 0 && path.length() > prefix.length()) {
            names.add(path.substring(prefix.length()));
         }
      }
   }

   static String folderPrefix(String folder) {
      String clean = folder.replace('\\', '/').toLowerCase(Locale.ROOT);

      while (clean.endsWith("/")) {
         clean = clean.substring(0, clean.length() - 1);
      }

      while (clean.startsWith("/")) {
         clean = clean.substring(1);
      }

      return clean.isEmpty() ? "" : clean + "/";
   }

   private static byte[] decrypt(byte[] data, @Nullable String key) throws IOException {
      return key == null ? data : PackCrypto.decrypt(data, key);
   }

   private static boolean isJson(String lowerPath) {
      return lowerPath.endsWith(".json");
   }

   private static byte[] checked(String name, byte[] data) throws IOException {
      checkJson(name, data);
      return data;
   }

   static void checkJson(String name, byte[] data) throws IOException {
      if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
         int separators = 0;
         int limit = name.startsWith("ui/") ? 2000000 : 500000;

         for (byte b : data) {
            if (b == 44 || b == 58) {
               if (++separators > limit) {
                  throw new IOException("JSON entry too complex: " + name);
               }
            }
         }
      }
   }

   static long limit(String name) {
      int dot = name.lastIndexOf(46);
      return dot >= 0 && BINARY_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT)) ? 67108864L : 8388608L;
   }

   private static byte[] read(ZipFile zip, @Nullable ZipEntry entry) throws IOException {
      if (entry == null) {
         throw new IOException("Missing zip entry");
      } else {
         long limit = limit(entry.getName());
         if (entry.getSize() > limit) {
            throw new IOException("Zip entry too large: " + entry.getName());
         } else {
            long allowed = entry.getSize() >= 0L ? entry.getSize() : limit;

            try {
               byte[] var8;
               try (InputStream in = zip.getInputStream(entry)) {
                  byte[] data = in.readNBytes((int)allowed + 1);
                  if (data.length > allowed) {
                     throw new IOException("Zip entry too large: " + entry.getName());
                  }

                  checkJson(entry.getName(), data);
                  var8 = data;
               }

               return var8;
            } catch (RuntimeException var11) {
               throw new IOException("Corrupt zip entry " + entry.getName(), var11);
            }
         }
      }
   }

   @Override
   public void close() {
      try {
         this.zip.close();
      } catch (IOException var2) {
      }
   }

   @Override
   public String toString() {
      return this.name;
   }
}
