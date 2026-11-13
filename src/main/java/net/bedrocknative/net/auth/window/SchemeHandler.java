package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
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

   @Nullable
   public String blocked() {
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
      } catch (RuntimeException | IOException var2) {
         return "the registry can't be read (" + var2.getClass().getSimpleName() + ")";
      }
   }

   public SchemeHandler.Registration register(String command) throws IOException {
      String blocked = this.blocked();
      if (blocked != null) {
         throw new IOException(blocked);
      } else {
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
         } catch (RuntimeException | IOException var7) {
            this.remove(marker);
            throw var7 instanceof IOException io ? io : new IOException("registry: " + var7.getClass().getSimpleName());
         }

         return new SchemeHandler.Registration(marker);
      }
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
      } catch (RuntimeException | IOException var2) {
         return false;
      }
   }

   private boolean remove(@Nullable String marker) {
      try {
         if (!this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522")) {
            return true;
         } else if (marker != null && marker.equals(this.registry.get("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn"))) {
            for (String key : KEYS) {
               if (this.registry.keyExists(key)) {
                  this.registry.deleteKey(key);
               }
            }

            return !this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522");
         } else {
            return false;
         }
      } catch (RuntimeException | IOException var6) {
         return false;
      }
   }

   @Nullable
   private Long owner() throws IOException {
      String marker = this.registry.get("Software\\Classes\\ms-xal-0000000048183522", "BedrockNativeSignIn");
      if (marker == null) {
         return null;
      } else {
         try {
            int colon = marker.indexOf(58);
            return Long.parseLong((colon < 0 ? marker : marker.substring(0, colon)).strip());
         } catch (NumberFormatException var3) {
            return -1L;
         }
      }
   }

   boolean registeredByUs() {
      try {
         Long owner = this.registry.keyExists("Software\\Classes\\ms-xal-0000000048183522") ? this.owner() : null;
         return owner != null && owner == this.processId;
      } catch (RuntimeException | IOException var2) {
         return false;
      }
   }

   public final class Registration implements AutoCloseable {
      private final String marker;
      private boolean closed;

      private Registration(String marker) {
         Objects.requireNonNull(SchemeHandler.this);
         super();
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
