package net.bedrocknative.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;

public final class FolderPack implements PackSource {
   private final Path root;
   private final Map<String, Optional<BrArchive>> archives = new ConcurrentHashMap<>();
   private final Map<String, Boolean> looseFolders = new ConcurrentHashMap<>();

   public FolderPack(Path root) {
      this.root = root;
   }

   public Path root() {
      return this.root;
   }

   public void forgetFolders() {
      this.looseFolders.clear();
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      if (PackPaths.escapes(path)) {
         return Optional.empty();
      }

      int slash = path.lastIndexOf(47);
      String folder = slash > 0 ? path.substring(0, slash) : "";

      try {
         if (this.looseFolders.computeIfAbsent(folder, key -> Files.isDirectory(this.root.resolve(key)))) {
            Path loose = this.root.resolve(path);
            if (Files.isRegularFile(loose)) {
               return Optional.of(Files.readAllBytes(loose));
            }
         }
      } catch (InvalidPathException e) {
         return Optional.empty();
      }

      if (folder.isEmpty()) {
         return Optional.empty();
      }

      Optional<BrArchive> archive = this.archives.computeIfAbsent(folder, this::openArchive);
      return archive.isPresent() ? archive.get().read(path.substring(slash + 1)) : Optional.empty();
   }

   @Override
   public boolean has(String path) {
      if (PackPaths.escapes(path)) {
         return false;
      }

      int slash = path.lastIndexOf(47);
      String folder = slash > 0 ? path.substring(0, slash) : "";

      try {
         if (this.looseFolders.computeIfAbsent(folder, key -> Files.isDirectory(this.root.resolve(key))) && Files.isRegularFile(this.root.resolve(path))) {
            return true;
         }
      } catch (InvalidPathException e) {
         return false;
      }

      if (folder.isEmpty()) {
         return false;
      }

      Optional<BrArchive> archive = this.archives.computeIfAbsent(folder, this::openArchive);
      return archive.isPresent() && archive.get().has(path.substring(slash + 1));
   }

   @Override
   public List<String> list(String folder) {
      String prefix = ZipPack.folderPrefix(folder);
      String clean = prefix.isEmpty() ? "" : prefix.substring(0, prefix.length() - 1);
      Set<String> paths = new TreeSet<>();

      try {
         Path loose = clean.isEmpty() ? this.root : this.root.resolve(clean);
         if (Files.isDirectory(loose)) {
            try (Stream<Path> files = Files.walk(loose)) {
               files.filter(x$0 -> Files.isRegularFile(x$0))
                  .map(filex -> this.root.relativize(filex).toString().replace('\\', '/'))
                  .filter(path -> !path.startsWith("__brarchive/"))
                  .forEach(paths::add);
            }
         }

         Path archives = this.root.resolve("__brarchive");
         List<Path> files = new ArrayList<>();
         if (!clean.isEmpty() && Files.isRegularFile(archives.resolve(clean + ".brarchive"))) {
            files.add(archives.resolve(clean + ".brarchive"));
         }

         Path nested = clean.isEmpty() ? archives : archives.resolve(clean);
         if (Files.isDirectory(nested)) {
            try (Stream<Path> found = Files.walk(nested)) {
               found.filter(filex -> Files.isRegularFile(filex) && filex.getFileName().toString().endsWith(".brarchive")).forEach(files::add);
            }
         }

         for (Path file : files) {
            String archived = archives.relativize(file).toString().replace('\\', '/');
            String archiveFolder = archived.substring(0, archived.length() - ".brarchive".length());
            Optional<BrArchive> archive = this.archives(archiveFolder);
            archive.ifPresent(foundx -> foundx.names().forEach(name -> paths.add(archiveFolder + "/" + name)));
         }
      } catch (IOException | InvalidPathException | UncheckedIOException e) {
         BedrockNative.LOGGER.warn("Couldn't list {} in {}: {}", new Object[]{folder, this.root, e.getMessage()});
      }

      return List.copyOf(paths);
   }

   private Optional<BrArchive> archives(String folder) {
      return this.archives.computeIfAbsent(folder, this::openArchive);
   }

   @Override
   public List<String> listNames(String folder) throws IOException {
      Set<String> names = new LinkedHashSet<>();

      try {
         if (this.looseFolders.computeIfAbsent(folder, key -> Files.isDirectory(this.root.resolve(key)))) {
            try (Stream<Path> files = Files.list(this.root.resolve(folder))) {
               files.filter(x$0 -> Files.isRegularFile(x$0)).forEach(file -> names.add(file.getFileName().toString()));
            }
         }
      } catch (InvalidPathException e) {
         return List.of();
      }

      this.archives.computeIfAbsent(folder, this::openArchive).ifPresent(archive -> names.addAll(archive.names()));
      return List.copyOf(names);
   }

   private Optional<BrArchive> openArchive(String folder) {
      Path file;
      try {
         file = this.root.resolve("__brarchive").resolve(folder + ".brarchive");
      } catch (InvalidPathException e) {
         return Optional.empty();
      }

      if (!Files.isRegularFile(file)) {
         return Optional.empty();
      }

      try {
         return Optional.of(BrArchive.open(file));
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Couldn't read Bedrock archive {}", file, e);
         return Optional.empty();
      }
   }

   @Override
   public String toString() {
      return this.root.toString();
   }
}
