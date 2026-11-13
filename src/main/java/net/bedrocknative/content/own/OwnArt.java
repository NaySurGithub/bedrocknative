package net.bedrocknative.content.own;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import org.jspecify.annotations.Nullable;

public final class OwnArt {
   private static final Map<String, Supplier<OwnArt.@Nullable Texture>> PAINTERS = painters();
   private static final Map<String, Optional<OwnArt.Texture>> PAINTED = new ConcurrentHashMap<>();
   private static final Map<String, byte[]> PNGS = new ConcurrentHashMap<>();

   private OwnArt() {
   }

   private static Map<String, Supplier<OwnArt.Texture>> painters() {
      Map<String, Supplier<OwnArt.Texture>> all = new HashMap<>();
      OwnMenuArt.register(all);
      OwnHudArt.register(all);
      OwnContainerArt.register(all);
      JavaArt.register(all);
      Map<String, Supplier<OwnArt.Texture>> lower = new HashMap<>();
      all.forEach((path, painter) -> lower.put(path.toLowerCase(Locale.ROOT), (Supplier<OwnArt.Texture>)painter));
      return Map.copyOf(lower);
   }

   public static boolean has(String path) {
      return PAINTERS.containsKey(path.toLowerCase(Locale.ROOT));
   }

   public static Optional<OwnArt.Texture> texture(String path) {
      String key = path.toLowerCase(Locale.ROOT);
      Supplier<OwnArt.Texture> painter = PAINTERS.get(key);
      return painter == null ? Optional.empty() : PAINTED.computeIfAbsent(key, k -> {
         try {
            return Optional.ofNullable(painter.get());
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.warn("Own menus: the picture {} couldn't be drawn", k, e);
            return Optional.empty();
         }
      });
   }

   public static Optional<byte[]> read(String lowerPath) {
      if (lowerPath.endsWith(".png")) {
         String path = lowerPath.substring(0, lowerPath.length() - 4);
         Optional<OwnArt.Texture> texture = texture(path);
         return texture.isEmpty() ? Optional.empty() : Optional.of((byte[])PNGS.computeIfAbsent(path, k -> PixelArt.png(texture.get().image())).clone());
      } else {
         return lowerPath.endsWith(".json")
            ? texture(lowerPath.substring(0, lowerPath.length() - 5)).map(OwnArt.Texture::sidecar).map(text -> text.getBytes(StandardCharsets.UTF_8))
            : Optional.empty();
      }
   }

   public static boolean hasFile(String lowerPath) {
      if (lowerPath.endsWith(".png")) {
         return has(lowerPath.substring(0, lowerPath.length() - 4));
      } else {
         return lowerPath.endsWith(".json") ? texture(lowerPath.substring(0, lowerPath.length() - 5)).map(OwnArt.Texture::sidecar).isPresent() : false;
      }
   }

   public static Set<String> names() {
      return new TreeSet<>(PAINTERS.keySet());
   }

   public record Texture(ArgbImage image, @Nullable String sidecar) {
      public static OwnArt.Texture plain(ArgbImage image) {
         return new OwnArt.Texture(image, null);
      }

      public static OwnArt.Texture sliced(ArgbImage image, int slice) {
         return new OwnArt.Texture(image, PixelArt.nineSlice(slice, image.width(), image.height()));
      }
   }
}
