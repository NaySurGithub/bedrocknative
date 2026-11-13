package net.bedrocknative.content.scan;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.model.render.MaterialTable;
import net.bedrocknative.ui.UiJson;
import org.jspecify.annotations.Nullable;

public final class DependencyScan {
   private static final Set<PackIndex.Kind> OPTIONAL = Set.of(PackIndex.Kind.UI_FILE_PATCH, PackIndex.Kind.LANG_KEY, PackIndex.Kind.UI_VARIABLE_ELEMENT);
   private static final MaterialTable CORE_MATERIALS = new MaterialTable();

   private DependencyScan() {
   }

   public static List<DependencyScan.PackReport> scan(Map<String, List<PackIndex>> groups, PackLookup base) {
      List<DependencyScan.PackReport> reports = new ArrayList<>();
      groups.forEach((group, packs) -> {
         for (PackIndex pack : packs) {
            List<PackIndex> mates = packs.stream().filter(other -> other != pack).toList();
            List<DependencyScan.Resolved> resolved = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (PackIndex.Ref ref : pack.refs()) {
               if (seen.add(ref.kind() + " " + ref.key())) {
                  DependencyScan.Resolved found = resolve(ref, pack, mates, base);
                  if (found != null) {
                     resolved.add(found);
                  }
               }
            }

            reports.add(new DependencyScan.PackReport(pack, group, resolved));
         }
      });
      return reports;
   }

   public static DependencyScan.@Nullable Resolved resolve(PackIndex.Ref ref, PackIndex self, List<PackIndex> mates, PackLookup base) {
      if (builtin(ref.kind(), ref.key())) {
         return new DependencyScan.Resolved(ref, DependencyScan.Where.BUILTIN, null);
      }

      if (ref.kind() != PackIndex.Kind.UI_FILE_PATCH) {
         PackLookup.Hit own = self.find(ref.kind(), ref.key());
         if (own != null) {
            return new DependencyScan.Resolved(ref, DependencyScan.Where.SELF, own);
         }
      }

      for (PackIndex mate : mates) {
         PackLookup.Hit hit = mate.find(ref.kind(), ref.key());
         if (hit != null) {
            return new DependencyScan.Resolved(ref, DependencyScan.Where.STACK, hit);
         }
      }

      PackLookup.Hit hit = base.find(ref.kind(), ref.key());
      if (hit != null) {
         return new DependencyScan.Resolved(ref, DependencyScan.Where.BASE, hit);
      } else {
         return OPTIONAL.contains(ref.kind()) ? null : new DependencyScan.Resolved(ref, DependencyScan.Where.MISSING, null);
      }
   }

   public static boolean builtin(PackIndex.Kind kind, String key) {
      return switch (kind) {
         case GEOMETRY -> key.startsWith("minecraft:geometry.");
         case MATERIAL -> CORE_MATERIALS.get(key).known();
         case FONT -> key.equals("default") || key.equals("unicode") || key.equals("rune") || key.isEmpty();
         default -> false;
      };
   }

   public static List<DependencyScan.Root> baseRoots(Collection<DependencyScan.PackReport> reports) {
      List<DependencyScan.Root> roots = new ArrayList<>();

      for (DependencyScan.PackReport report : reports) {
         for (DependencyScan.Resolved resolved : report.resolved()) {
            if (resolved.where() == DependencyScan.Where.BASE) {
               roots.add(new DependencyScan.Root(resolved.ref().kind(), resolved.ref().key(), report.pack().name()));
            }
         }
      }

      return roots;
   }

   public static DependencyScan.Closure closure(Collection<DependencyScan.Root> roots, PackLookup base) {
      Map<String, DependencyScan.BaseFile> files = new TreeMap<>();
      Map<String, PackLookup.Hit> hits = new HashMap<>();
      Set<String> visited = new TreeSet<>();
      Set<String> missing = new TreeSet<>();
      Deque<DependencyScan.Root> queue = new ArrayDeque<>(roots);
      boolean uiFramework = false;

      while (!queue.isEmpty()) {
         DependencyScan.Root root = queue.poll();
         String id = root.kind() + " " + root.key();
         if (!builtin(root.kind(), root.key())) {
            if (!visited.add(id)) {
               PackLookup.Hit known = base.find(root.kind(), root.key());
               if (known != null && files.get(PackIndex.clean(known.path())) instanceof DependencyScan.BaseFile file) {
                  file.origins().add(root.origin());
               }
            } else {
               PackLookup.Hit hit = base.find(root.kind(), root.key());
               if (hit == null) {
                  if (!OPTIONAL.contains(root.kind())) {
                     missing.add(id);
                  }
               } else {
                  addFile(files, hit, root, id);
                  hits.putIfAbsent(PackIndex.clean(hit.path()), hit);
                  if (hit.def() != null) {
                     for (PackIndex.Ref ref : hit.pack().refsOf(hit.def())) {
                        queue.add(new DependencyScan.Root(ref.kind(), ref.key(), root.origin()));
                     }
                  }

                  switch (root.kind()) {
                     case UI_FILE:
                     case UI_FILE_PATCH:
                        for (PackIndex.Def element : hit.pack().uiElements(hit.path())) {
                           queue.add(new DependencyScan.Root(PackIndex.Kind.UI_ELEMENT, element.key(), root.origin()));
                        }
                        break;
                     case TEXTURE:
                        String path = PackIndex.clean(hit.path());
                        int dot = path.lastIndexOf(46);
                        queue.add(
                           new DependencyScan.Root(PackIndex.Kind.FILE, (dot > path.lastIndexOf(47) ? path.substring(0, dot) : path) + ".json", root.origin())
                        );
                  }

                  if (!uiFramework
                     && (
                        root.kind() == PackIndex.Kind.UI_ELEMENT
                           || root.kind() == PackIndex.Kind.UI_VARIABLE_ELEMENT
                           || root.kind() == PackIndex.Kind.UI_FILE
                           || root.kind() == PackIndex.Kind.UI_FILE_PATCH
                     )) {
                     uiFramework = true;
                     queue.add(new DependencyScan.Root(PackIndex.Kind.FILE, "ui/_ui_defs.json", root.origin()));
                     queue.add(new DependencyScan.Root(PackIndex.Kind.FILE, "ui/_global_variables.json", root.origin()));
                  }
               }
            }
         }
      }

      missing.removeIf(idx -> idx.startsWith(PackIndex.Kind.FILE + " textures/") && idx.endsWith(".json"));
      Map<String, PackLookup.Hit> sources = hits;
      return new DependencyScan.Closure(files, visited, missing, file -> {
         PackLookup.Hit hitx = sources.get(PackIndex.clean(file.path()));

         try {
            return hitx == null ? null : hitx.pack().pack().read(hitx.path()).orElse(null);
         } catch (IOException e) {
            return null;
         }
      });
   }

   private static void addFile(Map<String, DependencyScan.BaseFile> files, PackLookup.Hit hit, DependencyScan.Root root, String id) {
      String key = PackIndex.clean(hit.path());
      DependencyScan.BaseFile file = files.get(key);
      if (file == null) {
         byte[] data;
         try {
            data = hit.pack().pack().read(hit.path()).orElse(new byte[0]);
         } catch (IOException e) {
            data = new byte[0];
         }

         file = new DependencyScan.BaseFile(hit.path(), hit.pack().name(), data.length, sha1(data), new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
         files.put(key, file);
      }

      file.kinds().add(root.kind());
      file.origins().add(root.origin());
      file.provides().add(id);
   }

   public static List<DependencyScan.CoverageRow> coverage(DependencyScan.Closure closure, PackLookup alt) {
      List<DependencyScan.CoverageRow> rows = new ArrayList<>();

      for (DependencyScan.BaseFile file : closure.files().values()) {
         String path = PackIndex.clean(file.path());
         PackLookup.Hit same = alt.find(PackIndex.Kind.FILE, path);
         if (same != null) {
            byte[] altData;
            try {
               altData = same.pack().pack().read(same.path()).orElse(new byte[0]);
            } catch (IOException e) {
               altData = new byte[0];
            }

            DependencyScan.Coverage coverage = sha1(altData).equals(file.sha1())
               ? DependencyScan.Coverage.SAME
               : (equivalent(closure, file, altData) ? DependencyScan.Coverage.EQUIVALENT : DependencyScan.Coverage.DIFFERENT);
            rows.add(new DependencyScan.CoverageRow(file, coverage, same.path(), file.provides().size(), file.provides().size()));
         } else {
            int dot = path.lastIndexOf(46);
            if (file.kinds().contains(PackIndex.Kind.TEXTURE) && dot > 0) {
               PackLookup.Hit texture = alt.find(PackIndex.Kind.TEXTURE, path.substring(0, dot));
               if (texture != null) {
                  rows.add(new DependencyScan.CoverageRow(file, DependencyScan.Coverage.OTHER_FORMAT, texture.path(), 1, 1));
                  continue;
               }
            }

            int found = 0;
            String altPath = null;

            for (String provided : file.provides()) {
               int space = provided.indexOf(32);
               PackLookup.Hit hit = alt.find(PackIndex.Kind.valueOf(provided.substring(0, space)), provided.substring(space + 1));
               if (hit != null) {
                  found++;
                  altPath = altPath == null ? hit.path() : altPath;
               }
            }

            DependencyScan.Coverage coverage = found == 0
               ? DependencyScan.Coverage.MISSING
               : (found == file.provides().size() ? DependencyScan.Coverage.OTHER_PATH : DependencyScan.Coverage.PARTLY);
            rows.add(new DependencyScan.CoverageRow(file, coverage, altPath, found, file.provides().size()));
         }
      }

      return rows;
   }

   static boolean equivalent(DependencyScan.Closure closure, DependencyScan.BaseFile file, byte[] altData) {
      byte[] own = closure.data().apply(file);
      if (own != null && altData.length != 0) {
         String path = file.path().toLowerCase(Locale.ROOT);

         try {
            if (path.endsWith(".json") || path.endsWith(".material")) {
               JsonElement a = json(own);
               return a != null && a.equals(json(altData));
            } else {
               return !path.endsWith(".png") && !path.endsWith(".tga") && !path.endsWith(".jpg") && !path.endsWith(".jpeg")
                  ? false
                  : samePixels(BedrockImages.decode(own), BedrockImages.decode(altData));
            }
         } catch (IOException | RuntimeException e) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static @Nullable JsonElement json(byte[] data) {
      try {
         return UiJson.parse(data);
      } catch (UiJson.Error e) {
         return ModelJson.parse(data, "scan");
      }
   }

   static boolean samePixels(ArgbImage a, ArgbImage b) {
      if (a.width() == b.width() && a.height() == b.height()) {
         for (int i = 0; i < a.argb().length; i++) {
            int x = a.argb()[i];
            int y = b.argb()[i];
            if (x != y && (x >>> 24 != 0 || y >>> 24 != 0)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   static String sha1(byte[] data) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public static String folderOf(String path) {
      String[] parts = path.toLowerCase(Locale.ROOT).split("/");
      return parts.length > 2 ? parts[0] + "/" + parts[1] : (parts.length == 2 ? parts[0] : "(root)");
   }

   public record BaseFile(String path, String layer, long size, String sha1, Set<PackIndex.Kind> kinds, Set<String> origins, Set<String> provides) {
   }

   public record Closure(
      Map<String, DependencyScan.BaseFile> files, Set<String> visited, Set<String> missing, Function<DependencyScan.BaseFile, byte @Nullable []> data
   ) {
      public long size() {
         return this.files.values().stream().mapToLong(DependencyScan.BaseFile::size).sum();
      }
   }

   public enum Coverage {
      SAME,
      EQUIVALENT,
      DIFFERENT,
      OTHER_FORMAT,
      OTHER_PATH,
      PARTLY,
      MISSING;
   }

   public record CoverageRow(DependencyScan.BaseFile file, DependencyScan.Coverage coverage, @Nullable String altPath, int providedFound, int provided) {
   }

   public record PackReport(PackIndex pack, String group, List<DependencyScan.Resolved> resolved) {
      public Map<PackIndex.Kind, Map<DependencyScan.Where, Integer>> counts() {
         Map<PackIndex.Kind, Map<DependencyScan.Where, Integer>> counts = new EnumMap<>(PackIndex.Kind.class);

         for (DependencyScan.Resolved resolved : this.resolved) {
            counts.computeIfAbsent(resolved.ref().kind(), key -> new EnumMap<>(DependencyScan.Where.class)).merge(resolved.where(), 1, Integer::sum);
         }

         return counts;
      }
   }

   public record Resolved(PackIndex.Ref ref, DependencyScan.Where where, PackLookup.@Nullable Hit hit) {
   }

   public record Root(PackIndex.Kind kind, String key, String origin) {
   }

   public enum Where {
      SELF,
      STACK,
      BASE,
      BUILTIN,
      MISSING;
   }
}
