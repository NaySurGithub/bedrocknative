package net.bedrocknative.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class BedrockInstall {
   public static final String SWITCH = "bedrocknative.bedrockInstall";
   private static final Path DEFAULT_DATA = Path.of("C:/XboxGames/Minecraft for Windows/Content/data");
   private static final Path HIDDEN = Path.of(System.getProperty("java.io.tmpdir"), "bedrocknative-bedrock-install-hidden");
   private static volatile BedrockInstall.@Nullable InstalledIndex installed;

   private BedrockInstall() {
   }

   public static boolean enabled() {
      String value = System.getProperty("bedrocknative.bedrockInstall");
      return value == null || !value.trim().equalsIgnoreCase("off");
   }

   public static Path dataFolder() {
      if (!enabled()) {
         return HIDDEN;
      }

      String configured = System.getProperty("bedrocknative.bedrockData");
      return configured != null ? Path.of(configured) : DEFAULT_DATA;
   }

   public static Optional<Path> vanillaResourcePack() {
      if (!enabled()) {
         return Optional.empty();
      }

      Path vanilla = dataFolder().resolve("resource_packs").resolve("vanilla");
      return Files.isDirectory(vanilla) ? Optional.of(vanilla) : Optional.empty();
   }

   public static Optional<Path> musicPack() {
      if (!enabled()) {
         return Optional.empty();
      }

      Path music = dataFolder().resolve("resource_packs").resolve("vanilla_music");
      return Files.isDirectory(music) ? Optional.of(music) : Optional.empty();
   }

   public static List<Path> basePacks(@Nullable String baseGameVersion, Collection<String> experiments) {
      Path packs = dataFolder().resolve("resource_packs");
      List<Path> stack = new ArrayList<>();
      if (enabled() && Files.isDirectory(packs.resolve("vanilla"))) {
         int[] limit = version(baseGameVersion);
         List<Path> versioned = new ArrayList<>();

         try (Stream<Path> folders = Files.list(packs)) {
            folders.filter(x$0 -> Files.isDirectory(x$0)).forEach(folder -> {
               String name = folder.getFileName().toString();
               int[] version = name.startsWith("vanilla_") ? version(name.substring("vanilla_".length())) : null;
               if (version != null && (limit == null || compare(version, limit) <= 0)) {
                  versioned.add(folder);
               }
            });
         } catch (IOException e) {
            return List.of(packs.resolve("vanilla"));
         }

         versioned.sort(Comparator.comparing(folder -> version(folder.getFileName().toString().substring("vanilla_".length())), BedrockInstall::compare));
         if (Files.isDirectory(packs.resolve("vanilla_base"))) {
            stack.add(packs.resolve("vanilla_base"));
         }

         stack.add(packs.resolve("vanilla"));
         stack.addAll(versioned);

         for (String experiment : experiments) {
            Path pack = packs.resolve("experimental_" + experiment);
            if (experiment.matches("[a-z0-9_]+") && Files.isDirectory(pack)) {
               stack.add(pack);
            }
         }

         return stack;
      } else {
         return stack;
      }
   }

   public static Optional<Path> resourcePack(UUID uuid, String version) {
      return installedPacks()
         .stream()
         .filter(pack -> pack.manifest().uuid().equals(uuid) && PackManifest.sameVersion(pack.manifest().version(), version))
         .map(BedrockInstall.Installed::folder)
         .findFirst();
   }

   private static List<BedrockInstall.Installed> installedPacks() {
      if (!enabled()) {
         return List.of();
      }

      Path packs = dataFolder().resolve("resource_packs");
      BedrockInstall.InstalledIndex index = installed;
      if (index != null && index.root().equals(packs)) {
         return index.packs();
      }

      List<BedrockInstall.Installed> found = new ArrayList<>();

      try (Stream<Path> folders = Files.list(packs)) {
         for (Path folder : folders.filter(x$0 -> Files.isDirectory(x$0)).sorted().toList()) {
            try {
               found.add(new BedrockInstall.Installed(folder, PackManifest.parse(Files.readAllBytes(folder.resolve("manifest.json")))));
            } catch (IOException var8) {
            }
         }
      } catch (IOException e) {
         return List.of();
      }

      installed = new BedrockInstall.InstalledIndex(packs, List.copyOf(found));
      return found;
   }

   static int @Nullable [] version(@Nullable String text) {
      if (text != null && text.matches("\\d{1,9}(\\.\\d{1,9})*")) {
         String[] parts = text.split("\\.");
         int[] version = new int[parts.length];

         for (int i = 0; i < parts.length; i++) {
            version[i] = Integer.parseInt(parts[i]);
         }

         return version;
      } else {
         return null;
      }
   }

   static int compare(int[] a, int[] b) {
      for (int i = 0; i < Math.max(a.length, b.length); i++) {
         int difference = Integer.compare(i < a.length ? a[i] : 0, i < b.length ? b[i] : 0);
         if (difference != 0) {
            return difference;
         }
      }

      return 0;
   }

   private record Installed(Path folder, PackManifest manifest) {
   }

   private record InstalledIndex(Path root, List<BedrockInstall.Installed> packs) {
   }
}
