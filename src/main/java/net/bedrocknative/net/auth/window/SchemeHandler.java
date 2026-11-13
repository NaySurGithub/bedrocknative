package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.LongPredicate;
import org.jspecify.annotations.Nullable;

public final class SchemeHandler {
   public static final String KEY = "Software\\Classes\\ms-xal-0000000048183522";
   static final String MARKER = "BedrockNativeSignIn";
   static final String COMMAND_KEY = "Software\\Classes\\ms-xal-0000000048183522\\shell\\open\\command";
   private static final String[] KEYS = new String[]{
      "Software\\Classes\\ms-xal-0000000048183522\\shell\\open\\command",
      "Software\\Classes\\ms-xal-0000000048183522\\shell\\open",
      "Software\\Classes\\ms-xal-0000000048183522\\shell",
      "Software\\Classes\\ms-xal-0000000048183522"
   };
   private static final SecureRandom RANDOM = new SecureRandom();
   private final Registry registry;
   private final long processId;
   private final LongPredicate alive;

   public SchemeHandler(Registry registry, long processId, LongPredicate alive) {
      this.registry = registry;
      this.processId = processId;
      this.alive = alive;
   }

   public @Nullable String blocked() {
      try {
         if (!this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522")) {
            return null;
         } else {
            Long owner = this.owner();
            if (owner == null) {
               return "another program handles ms-xal-0000000048183522 (left alone)";
            } else {
               return owner != this.processId && this.alive.test(owner) ? "another game is signing in through the browser" : null;
            }
         }
      } catch (IOException | RuntimeException e) {
         return "the registry can't be read (" + e.getClass().getSimpleName() + ")";
      }
   }

   public SchemeHandler.Registration register(String command) throws IOException {
      String blocked = this.blocked();
      if (blocked != null) {
         throw new IOException(blocked);
      }

      byte[] token = new byte[8];
      RANDOM.nextBytes(token);
      String marker = this.processId + ":" + HexFormat.of().formatHex(token);

      try {
         this.registry.set("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn", marker);
         this.verify(marker);
         this.registry.set("Software\\Classes\\ms-xal-0000000048183522", "", "URL:BedrockNative sign-in");
         this.registry.set("Software\\Classes\\ms-xal-0000000048183522", "URL Protocol", "");
         this.registry.set("Software\\Classes\\ms-xal-0000000048183522\\shell\\open", "FriendlyAppName", "BedrockNative");
         this.registry.set("Software\\Classes\\ms-xal-0000000048183522\\shell\\open\\command", "", command);
         this.verify(marker);
      } catch (IOException | RuntimeException e) {
         this.remove(marker);
         throw e instanceof IOException io ? io : new IOException("registry: " + e.getClass().getSimpleName());
      }

      return new SchemeHandler.Registration(marker);
   }

   private void verify(String marker) throws IOException {
      if (!marker.equals(this.registry.get("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn"))) {
         throw new IOException("another game is signing in through the browser");
      }
   }

   public boolean removeLeftover() {
      try {
         Long owner = this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522") ? this.owner() : null;
         return owner != null && owner != this.processId && !this.alive.test(owner)
            ? this.remove(this.registry.get("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn"))
            : false;
      } catch (IOException | RuntimeException e) {
         return false;
      }
   }

   private boolean remove(@Nullable String marker) {
      try {
         if (!this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522")) {
            return true;
         }

         if (marker != null && marker.equals(this.registry.get("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn"))) {
            for (String key : KEYS) {
               if (this.registry.keyExists(key)) {
                  this.registry.deleteKey(key);
               }
            }

            return !this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522");
         } else {
            return false;
         }
      } catch (IOException | RuntimeException e) {
         return false;
      }
   }

   private @Nullable Long owner() throws IOException {
      String marker = this.registry.get("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn");
      if (marker == null) {
         return null;
      }

      try {
         int colon = marker.indexOf(58);
         return Long.parseLong((colon < 0 ? marker : marker.substring(0, colon)).strip());
      } catch (NumberFormatException e) {
         return -1L;
      }
   }

   boolean registeredByUs() {
      try {
         Long owner = this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522") ? this.owner() : null;
         return owner != null && owner == this.processId;
      } catch (IOException | RuntimeException e) {
         return false;
      }
   }

   public final class Registration implements AutoCloseable {
      private final String marker;
      private boolean closed;

      private Registration(String marker) {
         this.marker = marker;
      }

      @Override
      public synchronized void close() {
         if (!this.closed) {
            this.closed = true;
            SchemeHandler.this.remove(this.marker);
         }
      }
   }
}
