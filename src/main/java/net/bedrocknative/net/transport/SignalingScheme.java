package net.bedrocknative.net.transport;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum SignalingScheme {
   HTTPS,
   HTTP;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static SignalingScheme byId(@Nullable String id) {
      if (id == null) {
         return null;
      } else {
         String var1 = id.trim().toLowerCase(Locale.ROOT);

         return switch (var1) {
            case "https" -> HTTPS;
            case "http" -> HTTP;
            default -> null;
         };
      }
   }
}
