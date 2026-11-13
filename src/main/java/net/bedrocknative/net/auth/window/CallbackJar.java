package net.bedrocknative.net.auth.window;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;

public final class CallbackJar {
   static final String NAME = "signin-callback.jar";
   private static final String ENTRY = SignInCallback.class.getName().replace('.', '/') + ".class";
   private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

   private CallbackJar() {
   }

   static byte[] bytes() throws IOException {
      byte[] classFile;
      try (InputStream in = SignInCallback.class.getResourceAsStream(SignInCallback.class.getSimpleName() + ".class")) {
         if (in == null) {
            throw new IOException("the callback's class file is not readable");
         }

         classFile = in.readAllBytes();
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try (ZipOutputStream zip = new ZipOutputStream(out)) {
         ZipEntry entry = new ZipEntry(ENTRY);
         entry.setTimeLocal(FIXED_TIME);
         zip.putNextEntry(entry);
         zip.write(classFile);
         zip.closeEntry();
      }

      return out.toByteArray();
   }

   static Path install(List<Path> roots) throws IOException {
      byte[] bytes = bytes();
      String hash = HelperInstall.sha256(bytes);
      StringBuilder problems = new StringBuilder();

      for (Path root : roots) {
         Path jar = root.resolve(hash.substring(0, 16)).resolve("signin-callback.jar");

         try {
            if (!hash.equals(hash(jar))) {
               Files.createDirectories(jar.getParent());
               Path temporary = Files.createTempFile(jar.getParent(), ".cb-", ".tmp");

               try {
                  Files.write(temporary, bytes);

                  try {
                     Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                  } catch (AtomicMoveNotSupportedException var13) {
                     Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
                  }
               } finally {
                  Files.deleteIfExists(temporary);
               }
            }

            if (hash.equals(hash(jar))) {
               return jar;
            }

            problems.append(jar).append(" does not match; ");
         } catch (RuntimeException | IOException var15) {
            problems.append("can't write ").append(root).append(" (").append(var15.getClass().getSimpleName()).append("); ");
         }
      }

      throw new IOException("no place for the sign-in callback: " + problems);
   }

   @Nullable
   static String command(Path java, Path jar, Path folder) {
      for (Path path : List.of(java, jar, folder)) {
         String text = path.toString();
         if (text.indexOf(34) >= 0 || text.indexOf(37) >= 0 || !path.isAbsolute()) {
            return null;
         }
      }

      return "\"" + java + "\" -cp \"" + jar + "\" " + SignInCallback.class.getName() + " \"" + folder + "\" \"%1\"";
   }

   @Nullable
   static Path java() {
      Optional<String> command = ProcessHandle.current().info().command();
      if (command.isEmpty()) {
         return null;
      } else {
         Path java = Path.of(command.get());
         Path javaw = java.resolveSibling("javaw.exe");
         if (Files.isRegularFile(javaw)) {
            return javaw;
         } else {
            return Files.isRegularFile(java) ? java : null;
         }
      }
   }

   @Nullable
   private static String hash(Path file) {
      try {
         return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? HelperInstall.sha256(Files.readAllBytes(file)) : null;
      } catch (RuntimeException | IOException var2) {
         return null;
      }
   }
}
