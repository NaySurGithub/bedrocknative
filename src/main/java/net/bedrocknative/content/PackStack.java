package net.bedrocknative.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.own.OwnUiPack;
import org.jspecify.annotations.Nullable;

public final class PackStack {
   private static final List<String> TEXTURE_EXTENSIONS = List.of(".png", ".tga", ".jpg", ".jpeg");
   private static final String ILLEGAL = "<>\"|?*";
   private static volatile @Nullable PackStack base;
   private static volatile @Nullable String baseKey;
   private static volatile @Nullable String baseSource;
   private static volatile List<PackSource> serverPacks = List.of();
   private static volatile List<String> skippedServerPacks = List.of();
   private static volatile @Nullable PackStack active;
   private final List<PackSource> topFirst = new CopyOnWriteArrayList<>();

   public static PackStack active() {
      PackStack stack = active;
      if (stack == null) {
         selectBase(null, List.of());
         stack = active;
      }

      return stack;
   }

   public static synchronized boolean selectBase(@Nullable String baseGameVersion, Collection<String> experiments) {
      String source = BaseContent.describe();
      String key = baseGameVersion + " " + experiments + " " + source + (BaseContent.ownUi() ? " own-menus" : "");
      if (base != null && key.equals(baseKey)) {
         return false;
      }

      PackStack created = base(baseGameVersion, experiments, base);
      if (!source.equals(baseSource)) {
         if (created.topFirst.isEmpty()) {
            BedrockNative.LOGGER.warn(BaseContent.line());
         } else {
            BedrockNative.LOGGER.info("{}: {} packs", BaseContent.line(), created.topFirst.size());
         }

         baseSource = source;
      }

      baseKey = key;
      base = created;
      active = over(serverPacks, created);
      return true;
   }

   public static List<String> skippedServerPacks() {
      return skippedServerPacks;
   }

   public static void recordSkippedServerPacks(List<String> skipped) {
      skippedServerPacks = List.copyOf(skipped);
   }

   public static synchronized void useServerPacks(List<? extends PackSource> topFirst) {
      List<PackSource> previous = serverPacks;
      serverPacks = List.copyOf(topFirst);
      previous.stream().filter(pack -> !serverPacks.contains(pack)).forEach(PackSource::close);
      PackStack current = base;
      active = current != null ? over(serverPacks, current) : null;
   }

   public static boolean hasServerPacks() {
      return !serverPacks.isEmpty();
   }

   public static int serverPackCount() {
      return serverPacks.size();
   }

   public static boolean isServerPack(PackSource pack) {
      return serverPacks.contains(pack);
   }

   public static PackStack base(@Nullable String baseGameVersion, Collection<String> experiments) {
      return base(baseGameVersion, experiments, null);
   }

   static PackStack base(@Nullable String baseGameVersion, Collection<String> experiments, @Nullable PackStack reuse) {
      Map<Path, FolderPack> existing = new HashMap<>();
      if (reuse != null) {
         for (PackSource pack : reuse.topFirst) {
            if (pack instanceof FolderPack folder) {
               existing.putIfAbsent(folder.root(), folder);
            }
         }
      }

      PackStack created = new PackStack();
      BaseContent.basePacks(baseGameVersion, experiments).forEach(path -> created.pushTop(existing.getOrDefault(path, new FolderPack(path))));
      if (BaseContent.kind() == BaseContent.Kind.EMBEDDED) {
         BaseContent.embeddedPack().ifPresent(created::pushTop);
         created.pushBottom(BaseGaps.INSTANCE);
         if (created.topFirst.stream().noneMatch(packx -> has(packx, "font/default8.png"))) {
            created.pushBottom(JavaLatinFont.INSTANCE);
         }

         if (created.topFirst.stream().noneMatch(packx -> has(packx, "font/font_metadata.json"))) {
            created.pushBottom(SmoothFontStandIn.INSTANCE);
            Thread.ofVirtual().name("Smooth font stand-in").start(SmoothFontStandIn.INSTANCE::available);
         }
      } else if (BaseContent.ownUi()) {
         created.pushBottom(OwnUiPack.INSTANCE);
         created.pushBottom(JavaLatinFont.INSTANCE);
         created.pushBottom(SmoothFontStandIn.INSTANCE);
         Thread.ofVirtual().name("Smooth font stand-in").start(SmoothFontStandIn.INSTANCE::available);
      }

      return created;
   }

   private static boolean has(PackSource pack, String path) {
      try {
         return pack.has(path);
      } catch (IOException e) {
         return false;
      }
   }

   public static PackStack of(List<? extends PackSource> topFirst) {
      PackStack created = new PackStack();
      created.topFirst.addAll(topFirst);
      return created;
   }

   public static PackStack over(List<? extends PackSource> topFirst, PackStack below) {
      PackStack created = new PackStack();
      created.topFirst.addAll(topFirst);
      created.topFirst.addAll(below.topFirst);
      return created;
   }

   public List<PackSource> packs() {
      return Collections.unmodifiableList(this.topFirst);
   }

   public void pushTop(PackSource pack) {
      this.topFirst.addFirst(pack);
   }

   public void pushBottom(PackSource pack) {
      this.topFirst.addLast(pack);
   }

   public void remove(PackSource pack) {
      this.topFirst.remove(pack);
   }

   public Optional<PackStack.Resource> read(String path) {
      String normalized = normalize(path);
      if (normalized == null) {
         return Optional.empty();
      }

      for (PackSource pack : this.topFirst) {
         try {
            Optional<byte[]> data = pack.read(normalized);
            if (data.isPresent()) {
               return Optional.of(new PackStack.Resource(normalized, data.get()));
            }
         } catch (IOException e) {
            BedrockNative.LOGGER.warn("Couldn't read {} from {}", new Object[]{normalized, pack, e});
         }
      }

      return Optional.empty();
   }

   public List<PackStack.PackFile> readAll(String path) {
      String normalized = normalize(path);
      if (normalized == null) {
         return List.of();
      }

      List<PackStack.PackFile> files = new ArrayList<>();

      for (PackSource pack : this.topFirst.reversed()) {
         try {
            pack.read(normalized).ifPresent(data -> files.add(new PackStack.PackFile(pack, data)));
         } catch (IOException e) {
            BedrockNative.LOGGER.warn("Couldn't read {} from {}", new Object[]{normalized, pack, e});
         }
      }

      return files;
   }

   public Optional<PackStack.Resource> texture(String path) {
      return this.locateTexture(path).map(PackStack.Located::resource);
   }

   public Optional<PackStack.Located> locateTexture(String path) {
      String lower = path.toLowerCase(Locale.ROOT);
      boolean hasExtension = false;

      for (int i = 0; i < TEXTURE_EXTENSIONS.size() && !hasExtension; i++) {
         hasExtension = lower.endsWith(TEXTURE_EXTENSIONS.get(i));
      }

      String[] candidates = new String[hasExtension ? 1 : TEXTURE_EXTENSIONS.size()];

      for (int i = 0; i < candidates.length; i++) {
         candidates[i] = normalize(hasExtension ? path : path + TEXTURE_EXTENSIONS.get(i));
      }

      for (PackSource pack : this.topFirst) {
         for (String normalized : candidates) {
            if (normalized == null) {
               return Optional.empty();
            }

            try {
               Optional<byte[]> data = pack.read(normalized);
               if (data.isPresent()) {
                  return Optional.of(new PackStack.Located(new PackStack.Resource(normalized, data.get()), pack));
               }
            } catch (IOException e) {
               BedrockNative.LOGGER.warn("Couldn't read {} from {}", new Object[]{normalized, pack, e});
            }
         }
      }

      return Optional.empty();
   }

   public static @Nullable String normalize(String path) {
      String trimmed = path.trim();
      char[] out = new char[trimmed.length()];
      int length = 0;
      boolean changed = false;

      for (int i = 0; i < trimmed.length(); i++) {
         char c = trimmed.charAt(i);
         if (c == '\\') {
            c = '/';
            changed = true;
         }

         if (c != '/' || length != 0 && out[length - 1] != '/') {
            if (c == ':' || c < ' ' || "<>\"|?*".indexOf(c) >= 0) {
               return null;
            }

            if (c == '/' && (out[length - 1] == '.' || out[length - 1] == ' ')) {
               return null;
            }

            out[length++] = c;
         } else {
            changed = true;
         }
      }

      if (length == 0) {
         return null;
      } else {
         char last = out[length - 1];
         if (last != '/' && last != '.' && last != ' ') {
            return changed ? new String(out, 0, length) : trimmed;
         } else {
            return null;
         }
      }
   }

   public record Located(PackStack.Resource resource, PackSource pack) {
   }

   public record PackFile(PackSource pack, byte[] data) {
   }

   public record Resource(String path, byte[] data) {
   }
}
