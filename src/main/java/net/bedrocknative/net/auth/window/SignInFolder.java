package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class SignInFolder implements AutoCloseable {
   static final String PREFIX = "bnsi-";
   public static final String WAITING = "waiting";
   private final Path path;

   private SignInFolder(Path path) {
      this.path = path;
   }

   public static SignInFolder create(Path tempRoot, long processId) throws IOException {
      Files.createDirectories(tempRoot);
      return new SignInFolder(Files.createTempDirectory(tempRoot, "bnsi-" + processId + "-"));
   }

   public Path path() {
      return this.path;
   }

   @Override
   public void close() {
      delete(this.path);
   }

   static boolean delete(Path folder) {
      boolean interrupted = Thread.interrupted();

      boolean var17;
      try {
         for (int attempt = 0; attempt < 15; attempt++) {
            if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
               return true;
            }

            try (Stream<Path> walk = Files.walk(folder)) {
               for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                  Files.deleteIfExists(path);
               }
            } catch (RuntimeException | IOException var15) {
               if (!interrupted) {
                  try {
                     Thread.sleep(100L);
                  } catch (InterruptedException var12) {
                     interrupted = true;
                  }
               }
            }
         }

         var17 = !Files.exists(folder, LinkOption.NOFOLLOW_LINKS);
      } finally {
         if (interrupted) {
            Thread.currentThread().interrupt();
         }
      }

      return var17;
   }

   public static int removeLeftovers(Path tempRoot, LongPredicate alive) {
      int removed = 0;
      if (!Files.isDirectory(tempRoot)) {
         return 0;
      } else {
         List<Path> folders = new ArrayList<>();

         try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempRoot, "bnsi-*")) {
            stream.forEach(folders::add);
         } catch (RuntimeException | IOException var9) {
            return 0;
         }

         for (Path folder : folders) {
            Long owner = owner(folder.getFileName().toString());
            if (owner != null && !alive.test(owner) && Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) && delete(folder)) {
               removed++;
            }
         }

         return removed;
      }
   }

   @Nullable
   static Long owner(String name) {
      if (!name.startsWith("bnsi-")) {
         return null;
      } else {
         int dash = name.indexOf(45, "bnsi-".length());
         if (dash <= "bnsi-".length()) {
            return null;
         } else {
            try {
               return Long.parseLong(name.substring("bnsi-".length(), dash));
            } catch (NumberFormatException var3) {
               return null;
            }
         }
      }
   }
}
