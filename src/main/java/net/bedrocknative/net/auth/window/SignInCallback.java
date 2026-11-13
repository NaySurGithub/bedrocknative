package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

public final class SignInCallback {
   static final String SCHEME_PREFIX = "ms-xal-0000000048183522:";
   static final int MAX_LENGTH = 8192;
   static final int MAX_WAITING = 16;
   static final String WAITING = "waiting";
   static final String SUFFIX = ".url";

   private SignInCallback() {
   }

   public static void main(String[] args) {
      System.exit(run(args));
   }

   static int run(String[] args) {
      if (args != null && args.length == 2 && acceptable(args[1])) {
         Path folder;
         try {
            folder = Path.of(args[0]);
         } catch (InvalidPathException e) {
            return 1;
         }

         if (folder.isAbsolute()
            && Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
            && Files.isRegularFile(folder.resolve("waiting"), LinkOption.NOFOLLOW_LINKS)) {
            int waiting = 0;

            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.url")) {
               for (Path ignored : files) {
                  waiting++;
               }
            } catch (IOException | RuntimeException e) {
               return 1;
            }

            if (waiting >= 16) {
               return 1;
            }

            byte[] random = new byte[8];
            new SecureRandom().nextBytes(random);
            String name = "redirect-" + System.nanoTime() + "-" + HexFormat.of().formatHex(random);
            Path temporary = folder.resolve(name + ".tmp");

            try {
               Files.write(temporary, args[1].getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
               Files.move(temporary, folder.resolve(name + ".url"), StandardCopyOption.ATOMIC_MOVE);
               return 0;
            } catch (IOException | RuntimeException e) {
               try {
                  Files.deleteIfExists(temporary);
               } catch (IOException | RuntimeException var8) {
               }

               return 1;
            }
         } else {
            return 1;
         }
      } else {
         return 1;
      }
   }

   static boolean acceptable(String address) {
      if (address != null
         && address.length() > "ms-xal-0000000048183522:".length()
         && address.length() <= 8192
         && address.substring(0, "ms-xal-0000000048183522:".length()).toLowerCase(Locale.ROOT).equals("ms-xal-0000000048183522:")) {
         for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (c < '!' || c > '~') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }
}
