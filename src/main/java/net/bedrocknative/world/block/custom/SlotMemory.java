package net.bedrocknative.world.block.custom;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class SlotMemory {
   public static final String FILE = "block-slots.json";
   static final int MAX_ENTRIES = 64;
   static final long MAX_FILE = 65536L;
   private static final int MAX_COUNT = 1048576;
   private final Path file;
   private final Supplier<Instant> clock;
   private final LinkedHashMap<String, SlotMemory.Entry> entries = new LinkedHashMap<>(16, 0.75F, true);

   private SlotMemory(Path file, Supplier<Instant> clock) {
      this.file = file;
      this.clock = clock;
   }

   public static SlotMemory load(Path file) {
      return load(file, Instant::now);
   }

   static SlotMemory load(Path file, Supplier<Instant> clock) {
      SlotMemory memory = new SlotMemory(file, clock);
      memory.read();
      return memory;
   }

   public static String key(String host, int port) {
      return host.toLowerCase(Locale.ROOT) + ":" + port;
   }

   private void read() {
      try {
         if (!Files.isRegularFile(this.file)) {
            return;
         }

         if (Files.size(this.file) > 65536L) {
            BedrockNative.LOGGER.warn("{} is larger than {} bytes, ignored", "block-slots.json", 65536L);
            return;
         }

         if (!(JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8)) instanceof JsonObject object)) {
            BedrockNative.LOGGER.warn("{} is not a JSON object, ignored", "block-slots.json");
            return;
         }

         for (Map.Entry<String, JsonElement> server : object.entrySet()) {
            SlotMemory.Entry entry = entry(server.getValue());
            if (entry != null) {
               this.entries.put(server.getKey(), entry);
            }
         }

         this.trim();
      } catch (IOException | JsonParseException | IllegalStateException e) {
         BedrockNative.LOGGER.warn("Couldn't read {}, starting empty: {}", "block-slots.json", e.toString());
         this.entries.clear();
      }
   }

   private static SlotMemory.@Nullable Entry entry(JsonElement value) {
      if (value instanceof JsonObject object && object.get("slots") instanceof JsonArray array && array.size() == SlotPlan.SIZES.length) {
         int[] counts = new int[SlotPlan.SIZES.length];
         int i = 0;

         while (i < counts.length) {
            if (array.get(i) instanceof JsonPrimitive number && number.isNumber()) {
               int count = number.getAsInt();
               if (count >= 0 && count <= 1048576) {
                  counts[i] = count;
                  i++;
                  continue;
               }

               return null;
            }

            return null;
         }

         long at = object.get("at") instanceof JsonPrimitive time && time.isNumber() ? time.getAsLong() : 0L;
         return new SlotMemory.Entry(counts, at);
      } else {
         return null;
      }
   }

   public synchronized int @Nullable [] get(String key) {
      SlotMemory.Entry entry = this.entries.get(key);
      return entry == null ? null : (int[])entry.counts().clone();
   }

   public synchronized boolean put(String key, int[] counts) {
      if (!SlotPlan.exceeds(counts, SlotPlan.BASE)) {
         if (this.entries.remove(key) != null) {
            this.write();
            return true;
         } else {
            return false;
         }
      } else {
         int[] kept = Arrays.copyOf(counts, SlotPlan.SIZES.length);
         SlotMemory.Entry before = this.entries.put(key, new SlotMemory.Entry(kept, this.clock.get().getEpochSecond()));
         this.trim();
         if (before != null && Arrays.equals(before.counts(), kept)) {
            return false;
         }

         this.write();
         return true;
      }
   }

   synchronized int size() {
      return this.entries.size();
   }

   private void trim() {
      while (this.entries.size() > 64) {
         this.entries.remove(this.entries.keySet().iterator().next());
      }
   }

   private void write() {
      JsonObject root = new JsonObject();

      for (Map.Entry<String, SlotMemory.Entry> server : this.entries.entrySet()) {
         JsonObject object = new JsonObject();
         JsonArray slots = new JsonArray();

         for (int count : server.getValue().counts()) {
            slots.add(count);
         }

         object.add("slots", slots);
         object.addProperty("at", server.getValue().at());
         root.add(server.getKey(), object);
      }

      try {
         Files.createDirectories(this.file.getParent());
         Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n", StandardCharsets.UTF_8);
         Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Couldn't write {}: {}", "block-slots.json", e.toString());
      }
   }

   private record Entry(int[] counts, long at) {
   }
}
