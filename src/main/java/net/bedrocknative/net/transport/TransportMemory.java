package net.bedrocknative.net.transport;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class TransportMemory {
   public static final String FILE = "transports.json";
   static final int MAX_ENTRIES = 256;
   static final long MAX_FILE = 262144L;
   private final Path file;
   private final Supplier<Instant> clock;
   private final LinkedHashMap<String, TransportMemory.Entry> entries = new LinkedHashMap<>(16, 0.75F, true);

   private TransportMemory(Path file, Supplier<Instant> clock) {
      this.file = file;
      this.clock = clock;
   }

   public static TransportMemory load(Path file) {
      return load(file, Instant::now);
   }

   static TransportMemory load(Path file, Supplier<Instant> clock) {
      TransportMemory memory = new TransportMemory(file, clock);
      memory.read();
      return memory;
   }

   private void read() {
      try {
         if (!Files.isRegularFile(this.file)) {
            return;
         }

         if (Files.size(this.file) > 262144L) {
            BedrockNative.LOGGER.warn("{} is larger than {} bytes, ignored", "transports.json", 262144L);
            return;
         }

         if (!(JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8)) instanceof JsonObject object)) {
            BedrockNative.LOGGER.warn("{} is not a JSON object, ignored", "transports.json");
            return;
         }

         for (Map.Entry<String, JsonElement> server : object.entrySet()) {
            TransportMemory.Entry entry = entry(server.getValue());
            if (entry != null) {
               this.entries.put(server.getKey(), entry);
            }
         }

         this.trim();
      } catch (IOException | JsonParseException | IllegalStateException e) {
         BedrockNative.LOGGER.warn("Couldn't read {}, starting empty: {}", "transports.json", e.toString());
         this.entries.clear();
      }
   }

   private static TransportMemory.@Nullable Entry entry(JsonElement value) {
      if (value instanceof JsonObject object && object.get("kind") instanceof JsonPrimitive kindName && kindName.isString()) {
         TransportKind kind = TransportKind.byId(kindName.getAsString());
         if (kind == null) {
            return null;
         }

         SignalingScheme scheme = object.get("scheme") instanceof JsonPrimitive text && text.isString() ? SignalingScheme.byId(text.getAsString()) : null;
         int port = object.get("port") instanceof JsonPrimitive number && number.isNumber() ? number.getAsInt() : 0;
         long at = object.get("at") instanceof JsonPrimitive time && time.isNumber() ? time.getAsLong() : 0L;
         return new TransportMemory.Entry(kind, kind == TransportKind.NETHERNET ? scheme : null, port > 0 && port <= 65535 ? port : 0, at);
      } else {
         return null;
      }
   }

   public static String key(String host, int port) {
      return ServerTarget.hostPort(host, port);
   }

   public synchronized TransportMemory.@Nullable Entry get(String host, int port) {
      return this.entries.get(key(host, port));
   }

   public synchronized void put(String host, int port, TransportKind kind, @Nullable SignalingScheme scheme, int foundOnPort) {
      String key = key(host, port);
      TransportMemory.Entry before = this.entries.get(key);
      TransportMemory.Entry entry = new TransportMemory.Entry(
         kind, kind == TransportKind.NETHERNET ? scheme : null, foundOnPort, this.clock.get().getEpochSecond()
      );
      this.entries.put(key, entry);
      this.trim();
      if (before == null || before.kind() != entry.kind() || before.scheme() != entry.scheme() || before.port() != entry.port()) {
         BedrockNative.LOGGER
            .info("Server {} remembered as {}{}", new Object[]{key, kind.shown(), foundOnPort != port ? " (on port " + foundOnPort + ")" : ""});
      }

      this.write();
   }

   public synchronized void forget(String host, int port) {
      if (this.entries.remove(key(host, port)) != null) {
         BedrockNative.LOGGER.info("Server {} forgotten: its remembered way did not work", key(host, port));
         this.write();
      }
   }

   public synchronized boolean anyBedrock() {
      for (TransportMemory.Entry entry : this.entries.values()) {
         if (entry.kind().bedrock()) {
            return true;
         }
      }

      return false;
   }

   synchronized int size() {
      return this.entries.size();
   }

   private void trim() {
      while (this.entries.size() > 256) {
         this.entries.remove(this.entries.keySet().iterator().next());
      }
   }

   private void write() {
      JsonObject root = new JsonObject();

      for (Map.Entry<String, TransportMemory.Entry> server : this.entries.entrySet()) {
         TransportMemory.Entry entry = server.getValue();
         JsonObject object = new JsonObject();
         object.addProperty("kind", entry.kind().name());
         if (entry.scheme() != null) {
            object.addProperty("scheme", entry.scheme().id());
         }

         if (entry.port() > 0) {
            object.addProperty("port", entry.port());
         }

         object.addProperty("at", entry.at());
         root.add(server.getKey(), object);
      }

      try {
         Files.createDirectories(this.file.getParent());
         Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n", StandardCharsets.UTF_8);
         Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Couldn't write {}: {}", "transports.json", e.toString());
      }
   }

   public record Entry(TransportKind kind, @Nullable SignalingScheme scheme, int port, long at) {
   }
}
