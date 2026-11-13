package net.bedrocknative.net.transport;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum SignalingScheme {
   HTTPS,
   HTTP;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static @Nullable SignalingScheme byId(@Nullable String id) {
      if (id == null) {
         return null;
      }

      return switch (id.trim().toLowerCase(Locale.ROOT)) {
         case "https" -> HTTPS;
         case "http" -> HTTP;
         default -> null;
      };
   }
}
