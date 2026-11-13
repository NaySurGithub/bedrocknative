package net.bedrocknative.molang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class QueryRegistry {
   private static final String RESOURCE = "/assets/bedrocknative/mappings/molang_queries.txt";
   private static final Map<String, String> METADATA = new LinkedHashMap<>();
   private static final Map<String, QueryRegistry.Entry> ENTRIES = load();

   private QueryRegistry() {
   }

   @Nullable
   public static QueryRegistry.Entry get(String name) {
      return ENTRIES.get(name);
   }

   public static Collection<QueryRegistry.Entry> entries() {
      return Collections.unmodifiableCollection(ENTRIES.values());
   }

   public static Map<String, String> metadataNames() {
      return Collections.unmodifiableMap(METADATA);
   }

   private static Map<String, QueryRegistry.Entry> load() {
      Map<String, QueryRegistry.Entry> entries = new LinkedHashMap<>();
      QueryFunction zero = (env, args) -> 0.0F;

      try (InputStream in = QueryRegistry.class.getResourceAsStream("/assets/bedrocknative/mappings/molang_queries.txt")) {
         if (in == null) {
            throw new IOException("missing /assets/bedrocknative/mappings/molang_queries.txt");
         }

         BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

         String line;
         while ((line = reader.readLine()) != null) {
            if (!line.isBlank() && !line.startsWith("#")) {
               String[] parts = line.trim().split("\\s+");
               METADATA.put(parts[0], parts.length > 1 ? parts[1] : "0+");
               entries.put(parts[0], new QueryRegistry.Entry(parts[0], 0, -1, zero, true));
            }
         }
      } catch (IOException var8) {
         BedrockNative.LOGGER.warn("Couldn't read the Molang query list: {}", var8.getMessage());
      }

      Queries.register((name, min, max, function) -> entries.put(name, new QueryRegistry.Entry(name, min, max, function, false)));
      return entries;
   }

   public record Entry(String name, int minArgs, int maxArgs, QueryFunction function, boolean stub) {
      boolean accepts(int count) {
         return count >= this.minArgs && (this.maxArgs < 0 || count <= this.maxArgs);
      }
   }

   @FunctionalInterface
   interface Registrar {
      void register(String var1, int var2, int var3, QueryFunction var4);
   }
}
