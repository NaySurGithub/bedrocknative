package net.bedrocknative.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class FogDefinitions {
   private static final String FOLDER = "fogs";
   private static final int MAX_FILE_BYTES = 1048576;
   private final Map<String, FogDefinition> byIdentifier;

   private FogDefinitions(Map<String, FogDefinition> byIdentifier) {
      this.byIdentifier = byIdentifier;
   }

   public static FogDefinitions read(PackStack stack) {
      Map<String, FogDefinition> found = new HashMap<>();

      for (PackSource pack : stack.packs()) {
         try {
            for (String name : pack.listNames("fogs")) {
               if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
                  Optional<byte[]> data = pack.read("fogs/" + name);
                  if (data.isPresent() && data.get().length <= 1048576) {
                     FogDefinition fog = FogDefinition.parse(new String(data.get(), StandardCharsets.UTF_8));
                     if (fog != null) {
                        found.putIfAbsent(fog.identifier(), fog);
                     }
                  }
               }
            }
         } catch (IOException e) {
            BedrockNative.LOGGER.warn("Couldn't read the fogs of {}", pack, e);
         }
      }

      return new FogDefinitions(found);
   }

   public static FogDefinitions of(Map<String, FogDefinition> byIdentifier) {
      return new FogDefinitions(Map.copyOf(byIdentifier));
   }

   public @Nullable FogDefinition get(String identifier) {
      return this.byIdentifier.get(identifier.toLowerCase(Locale.ROOT));
   }

   public int size() {
      return this.byIdentifier.size();
   }
}
