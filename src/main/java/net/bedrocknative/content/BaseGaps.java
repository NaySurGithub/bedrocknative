package net.bedrocknative.content;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BaseGaps implements PackSource {
   public static final BaseGaps INSTANCE = new BaseGaps();
   private static final Map<String, byte[]> FILES = Map.of(
      "textures/ui/white.json", "{\"nineslice_size\":1,\"base_size\":[2,2]}".getBytes(StandardCharsets.UTF_8)
   );

   private BaseGaps() {
   }

   @Override
   public Optional<byte[]> read(String path) {
      String normalized = PackStack.normalize(path);
      byte[] data = normalized == null ? null : FILES.get(normalized.toLowerCase(Locale.ROOT));
      return data == null ? Optional.empty() : Optional.of((byte[])data.clone());
   }

   @Override
   public String toString() {
      return "base-gaps";
   }
}
