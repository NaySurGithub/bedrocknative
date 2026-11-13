package net.bedrocknative.net.nethernet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class NetherNetTrustStore {
   public static final String FILE = "nethernet-trust.json";
   private static final String LOCK_FILE = "nethernet-trust.json.lock";
   private static final int VERSION = 1;
   private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
   private static final Map<Path, Object> FILE_MONITORS = new ConcurrentHashMap<>();
   private final @Nullable Path file;
   private final Map<String, NetherNetTrustStore.Entry> keys = new LinkedHashMap<>();
   private final Map<String, String> addresses = new LinkedHashMap<>();
   private final Set<String> unsaved = new HashSet<>();

   private NetherNetTrustStore(@Nullable Path file) {
      this.file = file;
   }

   public static NetherNetTrustStore open(Path folder) {
      NetherNetTrustStore store = new NetherNetTrustStore(folder.resolve("nethernet-trust.json"));
      synchronized (store) {
         store.readFile();
         return store;
      }
   }

   public static NetherNetTrustStore inMemory() {
      return new NetherNetTrustStore(null);
   }

   static String addressKey(String target) {
      return target.trim().toLowerCase(Locale.ROOT);
   }

   private void readFile() {
      if (this.file != null && Files.isRegularFile(this.file)) {
         Map<String, NetherNetTrustStore.Entry> diskKeys = new LinkedHashMap<>();
         Map<String, String> diskAddresses = new LinkedHashMap<>();

         try {
            JsonObject json = JsonParser.parseString(Files.readString(this.file)).getAsJsonObject();
            JsonObject entries = json.has("keys") ? json.getAsJsonObject("keys") : null;
            if (entries != null) {
               for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                  String digest = entry.getKey().toLowerCase(Locale.ROOT);
                  if (DIGEST.matcher(digest).matches() && entry.getValue().isJsonObject()) {
                     JsonObject value = entry.getValue().getAsJsonObject();
                     diskKeys.put(
                        digest,
                        new NetherNetTrustStore.Entry(
                           value.has("firstSeen") ? value.get("firstSeen").getAsLong() : 0L, text(value, "lastTarget"), text(value, "domain")
                        )
                     );
                  }
               }
            }

            JsonObject places = json.has("addresses") ? json.getAsJsonObject("addresses") : null;
            if (places != null) {
               for (Map.Entry<String, JsonElement> place : places.entrySet()) {
                  String digest = place.getValue().getAsString().toLowerCase(Locale.ROOT);
                  if (diskKeys.containsKey(digest)) {
                     diskAddresses.put(addressKey(place.getKey()), digest);
                  }
               }
            }

            for (Map.Entry<String, NetherNetTrustStore.Entry> entry : diskKeys.entrySet()) {
               if (!entry.getValue().lastTarget().isBlank()) {
                  diskAddresses.putIfAbsent(addressKey(entry.getValue().lastTarget()), entry.getKey());
               }
            }
         } catch (IOException | RuntimeException e) {
            BedrockNative.LOGGER.warn("NetherNet trust store {} is unreadable ({}); set aside, starting empty", this.file.getFileName(), e.getMessage());

            try {
               Files.move(this.file, this.file.resolveSibling("nethernet-trust.unreadable.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException var9) {
            }

            return;
         }

         Map<String, NetherNetTrustStore.Entry> pendingKeys = new LinkedHashMap<>();

         for (String digest : this.unsaved) {
            NetherNetTrustStore.Entry entry = this.keys.get(digest);
            if (entry != null) {
               pendingKeys.put(digest, entry);
            }
         }

         Map<String, String> pendingAddresses = new LinkedHashMap<>();
         this.addresses.forEach((address, digestx) -> {
            if (pendingKeys.containsKey(digestx)) {
               pendingAddresses.put(address, digestx);
            }
         });
         this.keys.clear();
         this.keys.putAll(diskKeys);
         this.keys.putAll(pendingKeys);
         this.addresses.clear();
         this.addresses.putAll(diskAddresses);
         this.addresses.putAll(pendingAddresses);
      }
   }

   private static String text(JsonObject json, String key) {
      JsonElement value = json.get(key);
      return value != null && !value.isJsonNull() ? value.getAsString() : "";
   }

   public synchronized boolean known(String digest) {
      return this.keys.containsKey(digest.toLowerCase(Locale.ROOT));
   }

   public synchronized NetherNetTrustStore.@Nullable Entry entry(String digest) {
      return this.keys.get(digest.toLowerCase(Locale.ROOT));
   }

   public synchronized @Nullable String trustedAt(String target) {
      return this.addresses.get(addressKey(target));
   }

   public synchronized void trust(String digest, String target, String domain) {
      String key = checked(digest);
      String shown = PromptText.plain(domain, 64);
      this.commit(() -> this.put(key, target, shown), key);
   }

   public synchronized void replace(String oldDigest, String newDigest, String target, String domain) {
      String old = checked(oldDigest);
      String key = checked(newDigest);
      String shown = PromptText.plain(domain, 64);
      this.commit(() -> {
         this.put(key, target, shown);
         if (!old.equals(key) && !this.addresses.containsValue(old)) {
            this.keys.remove(old);
         }
      }, key);
   }

   private static String checked(String digest) {
      String key = digest.toLowerCase(Locale.ROOT);
      if (!DIGEST.matcher(key).matches()) {
         throw new IllegalArgumentException("Not a SHA-256 key digest");
      } else {
         return key;
      }
   }

   private void put(String key, String target, String domain) {
      NetherNetTrustStore.Entry old = this.keys.get(key);
      this.keys.put(key, new NetherNetTrustStore.Entry(old != null ? old.firstSeen() : System.currentTimeMillis() / 1000L, target, domain));
      this.addresses.put(addressKey(target), key);
   }

   public synchronized void joined(String digest, String target) {
      String key = digest.toLowerCase(Locale.ROOT);
      NetherNetTrustStore.Entry old = this.keys.get(key);
      if (old != null && (!old.lastTarget().equals(target) || !key.equals(this.addresses.get(addressKey(target))))) {
         this.commit(() -> {
            NetherNetTrustStore.Entry now = this.keys.get(key);
            if (now != null) {
               this.keys.put(key, new NetherNetTrustStore.Entry(now.firstSeen(), target, now.domain()));
               this.addresses.put(addressKey(target), key);
            }
         }, null);
      }
   }

   public synchronized boolean forget(String digest) {
      String key = digest.toLowerCase(Locale.ROOT);
      boolean[] removed = new boolean[1];
      this.commit(() -> {
         removed[0] = this.keys.remove(key) != null;
         this.addresses.values().removeIf(key::equals);
      }, null);
      return removed[0];
   }

   public synchronized int size() {
      return this.keys.size();
   }

   private void commit(Runnable change, @Nullable String pendingKey) {
      if (this.file == null) {
         change.run();
      } else {
         synchronized (FILE_MONITORS.computeIfAbsent(this.file.toAbsolutePath().normalize(), path -> new Object())) {
            try {
               Files.createDirectories(this.file.getParent());
            } catch (IOException e) {
               BedrockNative.LOGGER.warn("Couldn't create the NetherNet trust folder ({})", e.getMessage());
            }

            FileChannel channel = null;
            FileLock lock = null;

            try {
               channel = FileChannel.open(this.file.resolveSibling("nethernet-trust.json.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
               lock = channel.lock();
            } catch (IOException | RuntimeException e) {
               BedrockNative.LOGGER.debug("NetherNet trust store lock unavailable ({}); saving without it", e.getMessage());
            }

            try {
               this.readFile();
               change.run();
               if (this.save()) {
                  this.unsaved.clear();
               } else if (pendingKey != null) {
                  this.unsaved.add(pendingKey);
               }
            } finally {
               try {
                  if (lock != null) {
                     lock.release();
                  }

                  if (channel != null) {
                     channel.close();
                  }
               } catch (IOException var17) {
               }
            }
         }
      }
   }

   private boolean save() {
      JsonObject json = new JsonObject();
      json.addProperty("version", 1);
      JsonObject entries = new JsonObject();
      this.keys.forEach((digest, entry) -> {
         JsonObject value = new JsonObject();
         value.addProperty("firstSeen", entry.firstSeen());
         value.addProperty("lastTarget", entry.lastTarget());
         value.addProperty("domain", entry.domain());
         entries.add(digest, value);
      });
      json.add("keys", entries);
      JsonObject places = new JsonObject();
      this.addresses.forEach(places::addProperty);
      json.add("addresses", places);
      Path temporary = null;

      try {
         Files.createDirectories(this.file.getParent());
         temporary = Files.createTempFile(this.file.getParent(), "nethernet-trust.json.", ".tmp");
         Files.writeString(temporary, json.toString());

         try {
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
         }

         return true;
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Couldn't save the NetherNet trust store ({}); kept in memory", e.getMessage());
         if (temporary != null) {
            try {
               Files.deleteIfExists(temporary);
            } catch (IOException var7) {
            }
         }

         return false;
      }
   }

   public record Entry(long firstSeen, String lastTarget, String domain) {
   }
}
