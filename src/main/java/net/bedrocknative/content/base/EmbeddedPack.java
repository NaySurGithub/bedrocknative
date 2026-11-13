package net.bedrocknative.content.base;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.bedrocknative.content.PackPaths;
import net.bedrocknative.content.PackSource;

public final class EmbeddedPack implements PackSource {
   private final Path root;
   private final String name;
   private final Map<String, String> byLower;
   private final List<String> sorted;

   EmbeddedPack(Path root, Collection<String> paths, String name) {
      this.root = root;
      this.name = name;
      Map<String, String> index = new HashMap<>(paths.size() * 2);

      for (String path : paths) {
         index.putIfAbsent(path.toLowerCase(Locale.ROOT), path);
      }

      this.byLower = Collections.unmodifiableMap(index);
      this.sorted = List.copyOf(new TreeSet<>(paths));
   }

   public Path root() {
      return this.root;
   }

   public int size() {
      return this.byLower.size();
   }

   private String spelled(String path) {
      return PackPaths.escapes(path) ? null : this.byLower.get(path.toLowerCase(Locale.ROOT));
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      String file = this.spelled(path);
      return file == null ? Optional.empty() : Optional.of(Files.readAllBytes(this.root.resolve(file)));
   }

   @Override
   public boolean has(String path) {
      return this.spelled(path) != null;
   }

   @Override
   public List<String> list(String folder) {
      String prefix = prefix(folder);
      List<String> found = new ArrayList<>();

      for (String path : this.sorted) {
         if (path.regionMatches(true, 0, prefix, 0, prefix.length())) {
            found.add(path);
         }
      }

      return found;
   }

   @Override
   public List<String> listNames(String folder) {
      String prefix = prefix(folder);
      Set<String> names = new LinkedHashSet<>();

      for (String path : this.sorted) {
         if (path.length() > prefix.length() && path.regionMatches(true, 0, prefix, 0, prefix.length()) && path.indexOf(47, prefix.length()) < 0) {
            names.add(path.substring(prefix.length()));
         }
      }

      return List.copyOf(names);
   }

   private static String prefix(String folder) {
      String clean = folder.replace('\\', '/');

      while (clean.endsWith("/")) {
         clean = clean.substring(0, clean.length() - 1);
      }

      while (clean.startsWith("/")) {
         clean = clean.substring(1);
      }

      return clean.isEmpty() ? "" : clean + "/";
   }

   @Override
   public String toString() {
      return this.name;
   }
}
