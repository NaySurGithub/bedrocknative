package net.bedrocknative.net.transport;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum TransportKind {
   RAKNET("raknet", "RakNet"),
   NETHERNET("nethernet", "NetherNet"),
   JAVA("java", "Java");

   private final String id;
   private final String shown;

   private TransportKind(String id, String shown) {
      this.id = id;
      this.shown = shown;
   }

   public String id() {
      return this.id;
   }

   public String shown() {
      return this.shown;
   }

   public String prefix() {
      return this.id + "://";
   }

   public boolean bedrock() {
      return this != JAVA;
   }

   @Nullable
   public static TransportKind byId(@Nullable String id) {
      if (id == null) {
         return null;
      } else {
         String lower = id.trim().toLowerCase(Locale.ROOT);

         for (TransportKind kind : values()) {
            if (kind.id.equals(lower)) {
               return kind;
            }
         }

         return null;
      }
   }
}
