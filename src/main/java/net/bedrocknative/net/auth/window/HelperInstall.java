package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class HelperInstall {
   public static final String RESOURCES = "/bedrocknative/webview-helper/";
   public static final String EXE = "AuthWebView.exe";
   static final int MAX_PATH = 240;
   private final Map<String, String> files;
   private final HelperInstall.Resources resources;
   private final List<Path> roots;

   HelperInstall(Map<String, String> files, HelperInstall.Resources resources, List<Path> roots) {
      this.files = new TreeMap<>(files);
      this.resources = resources;
      this.roots = List.copyOf(roots);
   }

   public static HelperInstall standard(Function<String, @Nullable String> environment, Path tempRoot) {
      List<Path> roots = new ArrayList<>();
      String local = environment.apply("LOCALAPPDATA");
      if (local != null && !local.isBlank()) {
         roots.add(Path.of(local, "BedrockNative", "wv"));
      }

      roots.add(tempRoot.resolve("bnwv"));
      return new HelperInstall(HelperBuild.FILES, name -> HelperInstall.class.getResourceAsStream("/bedrocknative/webview-helper/" + name), roots);
   }

   public boolean built() {
      return this.files.containsKey("AuthWebView.exe");
   }

   public synchronized HelperInstall.Result install() {
      if (!this.built()) {
         return new HelperInstall.Result(null, "this build has no sign-in window (./gradlew buildWebViewHelper)");
      }

      for (String name : this.files.keySet()) {
         if (!safeName(name)) {
            return new HelperInstall.Result(null, "the helper's file list names an unusable path");
         }
      }

      String version = this.version();
      List<String> problems = new ArrayList<>();

      for (Path root : this.roots) {
         Path folder = root.resolve(version);
         String problem = this.installInto(folder);
         if (problem == null) {
            removeOtherVersions(root, version);
            return new HelperInstall.Result(folder.resolve("AuthWebView.exe"), null);
         }

         problems.add(problem);
      }

      return new HelperInstall.Result(null, String.join("; ", problems));
   }

   private @Nullable String installInto(Path folder) {
      int longest = this.files.keySet().stream().mapToInt(String::length).max().orElse(0);
      if (folder.toAbsolutePath().toString().length() + 1 + longest > 240) {
         return "the path of " + folder + " is too long for Windows";
      }

      try {
         Files.createDirectories(folder);

         for (Entry<String, String> file : this.files.entrySet()) {
            Path target = folder.resolve(file.getKey());
            if (!file.getValue().equals(hash(target))) {
               byte[] bytes;
               try (InputStream in = this.resources.open(file.getKey())) {
                  if (in == null) {
                     return "the jar lacks " + file.getKey();
                  }

                  bytes = in.readAllBytes();
               }

               if (!file.getValue().equals(sha256(bytes))) {
                  return "the jar's " + file.getKey() + " is not the one built";
               }

               write(target, bytes);
               if (!file.getValue().equals(hash(target))) {
                  return "the copy of " + file.getKey() + " in " + folder + " is not the one built";
               }
            }
         }

         return null;
      } catch (IOException | RuntimeException e) {
         return "can't write " + folder + " (" + e.getClass().getSimpleName() + ")";
      }
   }

   private static void write(Path target, byte[] bytes) throws IOException {
      Files.createDirectories(target.getParent());
      Path temporary = Files.createTempFile(target.getParent(), ".wv-", ".tmp");

      try {
         Files.write(temporary, bytes);

         try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   private static void removeOtherVersions(Path root, String version) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
         for (Path other : stream) {
            if (!other.getFileName().toString().equals(version) && Files.isDirectory(other, LinkOption.NOFOLLOW_LINKS)) {
               SignInFolder.delete(other);
            }
         }
      } catch (IOException | RuntimeException var7) {
      }
   }

   String version() {
      StringBuilder all = new StringBuilder();
      this.files.forEach((name, hash) -> all.append(name).append('=').append(hash).append('\n'));
      return sha256(all.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
   }

   static boolean safeName(String name) {
      if (!name.isEmpty() && !name.startsWith("/") && !name.endsWith("/") && name.length() <= 120) {
         for (String part : name.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
               return false;
            }

            for (int i = 0; i < part.length(); i++) {
               char c = part.charAt(i);
               if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '.' && c != '-' && c != '_') {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static @Nullable String hash(Path file) {
      try {
         return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? sha256(Files.readAllBytes(file)) : null;
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   static String sha256(byte[] bytes) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   @FunctionalInterface
   interface Resources {
      @Nullable InputStream open(String var1) throws IOException;
   }

   public record Result(@Nullable Path exe, @Nullable String problem) {
   }
}
