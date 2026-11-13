package net.bedrocknative.client.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.ComposedTextures;
import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.compat.DeferredReleases;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.content.ImageLimits;
import net.bedrocknative.content.ImageSignature;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.SafeHttp;
import net.bedrocknative.content.UrlGuard;
import net.bedrocknative.content.UrlImageBudget;
import net.bedrocknative.model.render.RenderKind;
import net.bedrocknative.ui.BitmapFontMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.PngInfo;

public final class BedrockTextures {
   private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(20L);
   private static final Duration DOWNLOAD_DEADLINE = Duration.ofSeconds(30L);
   private static final HttpClient HTTP = SafeHttp.client(DOWNLOAD_TIMEOUT);
   private static final UrlImageBudget URL_BUDGET = new UrlImageBudget();
   private static volatile UrlGuard urlGuard = UrlGuard.PUBLIC_ONLY;
   private static final Map<String, Optional<BedrockTextures.Texture>> PACK = new ConcurrentHashMap<>();
   private static final Map<String, Optional<BedrockTextures.NineSlice>> NINE_SLICES = new ConcurrentHashMap<>();
   private static final Map<String, BedrockTextures.UrlImage> URLS = new ConcurrentHashMap<>();
   private static final List<Identifier> REGISTERED = new ArrayList<>();
   static final long MAX_TEXTURE_PIXELS = 134217728L;
   private static final Map<Identifier, Long> PIXELS = new ConcurrentHashMap<>();
   private static final Map<String, Float> ALLOWED_FILES = new ConcurrentHashMap<>();
   private static final Map<String, Optional<BedrockTextures.Texture>> FILES = new ConcurrentHashMap<>();
   private static final DeferredReleases<Identifier> RELEASE_AFTER_TICK = new DeferredReleases<>();
   static final int LUNAR_HOLD_TICKS = 20;
   private static final Set<Identifier> MADE_HERE = ConcurrentHashMap.newKeySet();
   private static final Set<Identifier> BLENDED = ConcurrentHashMap.newKeySet();
   private static volatile int blendVersion;

   private BedrockTextures() {
   }

   public static Optional<BedrockTextures.Texture> pack(String path) {
      return PACK.computeIfAbsent(path, BedrockTextures::loadPack);
   }

   public static void selectBaseGame(String baseGameVersion, Collection<String> experiments) {
      if (PackStack.selectBase(baseGameVersion, experiments)) {
         packsChanged();
      }
   }

   public static void packsChanged() {
      PACK.clear();
      NINE_SLICES.clear();
      ComposedTextures.clear();
      BedrockItemIcons.packsChanged();
      BedrockBlockModels.packsChanged();
      BedrockModels.packsChanged();
      JsonUi.packsChanged();
      BedrockVanillaTextures.packsChanged();
      BedrockGlyphs.packsChanged();
      BedrockSoundEngine.packsChanged();
      BedrockParticleEngine.packsChanged();
   }

   public static Optional<BitmapFontMetrics.Pixels> pixels(String path) {
      try {
         return PackStack.active().texture(path).flatMap(resource -> {
            try (NativeImage image = decode(resource.data())) {
               return Optional.of(new BitmapFontMetrics.Pixels(image.getWidth(), image.getHeight(), image.getPixels()));
            } catch (IOException e) {
               return Optional.empty();
            }
         });
      } catch (RuntimeException e) {
         return Optional.empty();
      }
   }

   public static Optional<BedrockTextures.NineSlice> nineSlice(String path) {
      return NINE_SLICES.computeIfAbsent(path, key -> pack(key).map(texture -> {
         int[] sizes = new int[]{0, 0, 0, 0};
         PackStack.active().read(key + ".json").ifPresent(json -> {
            try {
               JsonObject definition = JsonParser.parseString(new String(json.data(), StandardCharsets.UTF_8)).getAsJsonObject();
               JsonElement size = definition.get("nineslice_size");
               if (size != null && size.isJsonArray()) {
                  for (int i = 0; i < 4 && i < size.getAsJsonArray().size(); i++) {
                     sizes[i] = size.getAsJsonArray().get(i).getAsInt();
                  }
               } else if (size != null) {
                  Arrays.fill(sizes, size.getAsInt());
               }
            } catch (RuntimeException e) {
               BedrockNative.LOGGER.debug("Bad nineslice definition for {}", key, e);
            }
         });
         return new BedrockTextures.NineSlice(texture, sizes[0], sizes[1], sizes[2], sizes[3]);
      }));
   }

   public static BedrockTextures.UrlImage url(String url) {
      BedrockTextures.UrlImage known = URLS.get(url);
      if (known != null) {
         return known;
      } else if (!URL_BUDGET.tryStartUrl()) {
         BedrockNative.LOGGER.info("Form image {} not loaded: more than {} image URLs this session", url, 128);
         BedrockTextures.UrlImage refused = new BedrockTextures.UrlImage();
         refused.done = true;
         return refused;
      } else {
         return URLS.computeIfAbsent(url, key -> {
            BedrockTextures.UrlImage image = new BedrockTextures.UrlImage();
            image.download = download(key).whenComplete((decoded, error) -> Minecraft.getInstance().execute(() -> {
               if (image.released) {
                  if (decoded != null) {
                     decoded.close();
                  }
               } else if (error != null) {
                  BedrockNative.LOGGER.info("Form image {} not loaded: {}", key, error.getMessage());
               } else {
                  try {
                     image.texture = register("url/" + sha1(key), decoded, key);
                  } catch (IOException e) {
                     BedrockNative.LOGGER.info("Form image {} not shown: {}", key, e.getMessage());
                  }
               }

               image.done = true;
            }));
            return image;
         });
      }
   }

   public static void allowFile(Path file, float aspect) {
      ALLOWED_FILES.put(file.toAbsolutePath().normalize().toString(), aspect);
   }

   public static Optional<BedrockTextures.Texture> file(String path) {
      Float aspect = ALLOWED_FILES.get(path);
      return aspect == null ? Optional.empty() : FILES.computeIfAbsent(path, key -> {
         try {
            NativeImage image = crop(decode(Files.readAllBytes(Path.of(key))), aspect);
            return Optional.of(register("file/" + sha1(key), image, key));
         } catch (IOException | RuntimeException e) {
            BedrockNative.LOGGER.debug("Local picture {} not shown: {}", key, e.getMessage());
            return Optional.empty();
         }
      });
   }

   public static void releaseFiles() {
      Minecraft minecraft = Minecraft.getInstance();
      FILES.values().forEach(texture -> texture.ifPresent(found -> {
         PIXELS.remove(found.id());
         BLENDED.remove(found.id());
         if (REGISTERED.remove(found.id())) {
            minecraft.getTextureManager().release(found.id());
         }
      }));
      FILES.clear();
   }

   private static NativeImage crop(NativeImage image, float aspect) {
      if (aspect <= 0.0F) {
         return image;
      }

      int width = image.getWidth();
      int height = image.getHeight();
      int keepWidth = Math.min(width, Math.max(1, Math.round(height * aspect)));
      int keepHeight = Math.min(height, Math.max(1, Math.round(width / aspect)));
      if (keepWidth == width && keepHeight == height) {
         return image;
      }

      NativeImage cropped = new NativeImage(keepWidth, keepHeight, false);
      int left = (width - keepWidth) / 2;
      int top = (height - keepHeight) / 2;

      for (int y = 0; y < keepHeight; y++) {
         for (int x = 0; x < keepWidth; x++) {
            cropped.setPixel(x, y, image.getPixel(left + x, top + y));
         }
      }

      image.close();
      return cropped;
   }

   public static void releaseAll() {
      Minecraft minecraft = Minecraft.getInstance();
      ComposedTextures.clear();
      REGISTERED.forEach(minecraft.getTextureManager()::release);
      REGISTERED.clear();
      PIXELS.clear();
      BLENDED.clear();
      blendVersion++;
      PACK.clear();
      NINE_SLICES.clear();
      URLS.values().forEach(image -> {
         image.released = true;
         image.download.cancel(true);
      });
      URLS.clear();
      URL_BUDGET.reset();
      urlGuard = UrlGuard.PUBLIC_ONLY;
      FILES.clear();
   }

   public static void trustServer(InetAddress server) {
      urlGuard = server == null ? UrlGuard.PUBLIC_ONLY : UrlGuard.forServer(server);
   }

   private static Optional<BedrockTextures.Texture> loadPack(String path) {
      try {
         return PackStack.active().texture(path).flatMap(resource -> {
            try {
               return Optional.of(register("pack/" + sanitize(resource.path()), decode(resource.data()), resource.path()));
            } catch (IOException e) {
               BedrockNative.LOGGER.warn("Couldn't decode Bedrock texture {}", resource.path(), e);
               return Optional.empty();
            }
         });
      } catch (RuntimeException e) {
         BedrockNative.LOGGER.warn("Couldn't load Bedrock texture {}", path, e);
         return Optional.empty();
      }
   }

   private static CompletableFuture<NativeImage> download(String url) {
      try {
         URI uri = URI.create(url.trim());
         CompletableFuture<HttpResponse<byte[]>> response = SafeHttp.getBytes(
            HTTP, uri, urlGuard, DOWNLOAD_TIMEOUT, "image/png,image/jpeg", info -> bodyCap(url, info)
         );
         CompletableFuture.delayedExecutor(DOWNLOAD_DEADLINE.toMillis(), TimeUnit.MILLISECONDS).execute(() -> response.cancel(true));
         return response.thenApply(
            received -> {
               try {
                  if (received.statusCode() / 100 != 2) {
                     throw new IOException("HTTP " + received.statusCode());
                  }

                  byte[] data = received.body();
                  if (!isPng(data) && !isJpeg(data)) {
                     throw new IOException(
                        "not a PNG or JPEG image (" + ImageSignature.describe(received.headers().firstValue("content-type").orElse(null), data) + ")"
                     );
                  }

                  long pixels = BedrockImages.pixelCount(data);
                  if (!URL_BUDGET.tryAddImage(pixels)) {
                     throw new IOException("image of " + pixels + " pixels is over the limits for downloaded images");
                  }

                  try {
                     return decode(data);
                  } catch (IOException exx) {
                     throw new IOException(
                        exx.getMessage()
                           + " ("
                           + data.length
                           + " bytes received, content-length "
                           + received.headers().firstValue("content-length").orElse("not declared")
                           + ")",
                        exx
                     );
                  }
               } catch (IOException ex) {
                  throw new UncheckedIOException(ex);
               }
            }
         );
      } catch (RuntimeException e) {
         return CompletableFuture.failedFuture(e);
      }
   }

   private static long bodyCap(String url, ResponseInfo info) {
      long declared;
      try {
         declared = info.headers().firstValueAsLong("content-length").orElse(-1L);
      } catch (NumberFormatException e) {
         declared = -1L;
      }

      long cap = URL_BUDGET.bodyCap(info.statusCode(), declared);
      if (cap > 2097152L) {
         BedrockNative.LOGGER
            .info(
               "Form image {}: {} bytes, past the {} MB of a usual picture: taken ({} MB of the session's {} MB for large pictures spent)",
               new Object[]{url, declared, 2L, URL_BUDGET.largeExtraBytes() >> 20, 64L}
            );
      } else if (info.statusCode() / 100 == 2 && declared > cap) {
         BedrockNative.LOGGER
            .info(
               "Form image {}: {} bytes declared, more than a picture may take ({} MB each, {} MB past {} MB per session): refused",
               new Object[]{url, declared, 16L, 64L, 2L}
            );
      }

      return cap;
   }

   static NativeImage decode(byte[] data) throws IOException {
      if (isPng(data)) {
         PngInfo info = PngInfo.fromBytes(data);
         if (ImageLimits.step(info.width(), info.height()) == 1) {
            return NativeImage.read(data);
         }
      }

      ArgbImage decoded = BedrockImages.decode(data);
      NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);

      for (int y = 0; y < decoded.height(); y++) {
         for (int x = 0; x < decoded.width(); x++) {
            image.setPixel(x, y, decoded.pixel(x, y));
         }
      }

      return image;
   }

   public static Identifier registerArgb(String name, int width, int height, int[] argb, String label) {
      NativeImage image = new NativeImage(width, height, false);
      NativeImageFill.fillArgb(image, argb);
      return track(Identifier.fromNamespaceAndPath("bedrocknative", "bedrock/" + name), image, label).id();
   }

   public static void releaseAfterTick(Identifier id) {
      RELEASE_AFTER_TICK.add(id, LunarCompat.active() ? 20 : 1);
   }

   public static void releaseEntityTexture(Identifier id) {
      if (LunarCompat.active()) {
         releaseAfterTick(id);
      } else {
         release(id);
      }
   }

   public static void tick() {
      if (!RELEASE_AFTER_TICK.isEmpty()) {
         RELEASE_AFTER_TICK.tick().forEach(BedrockTextures::release);
      }
   }

   public static void madeHere(Identifier id) {
      MADE_HERE.add(id);
   }

   public static boolean isMadeHere(Identifier id) {
      return "bedrocknative".equals(id.getNamespace()) && (id.getPath().startsWith("bedrock/") || MADE_HERE.contains(id));
   }

   public static void release(Identifier id) {
      if (PIXELS.remove(id) != null && !BLENDED.remove(id)) {
         blendVersion++;
      }

      if (REGISTERED.remove(id)) {
         Minecraft.getInstance().getTextureManager().release(id);
      }
   }

   private static BedrockTextures.Texture register(String name, NativeImage image, String label) throws IOException {
      Identifier id = Identifier.fromNamespaceAndPath("bedrocknative", "bedrock/" + name);
      long pixels = (long)image.getWidth() * image.getHeight();
      long others = PIXELS.values().stream().mapToLong(Long::longValue).sum() - PIXELS.getOrDefault(id, 0L);
      if (others + pixels > 134217728L) {
         image.close();
         throw new IOException("texture budget of 128 M pixels reached");
      } else {
         return track(id, image, label);
      }
   }

   public static boolean needsBlending(Identifier id) {
      return !PIXELS.containsKey(id) || BLENDED.contains(id);
   }

   public static int blendVersion() {
      return blendVersion;
   }

   private static boolean partlyTransparent(NativeImage image) {
      for (int y = 0; y < image.getHeight(); y++) {
         for (int x = 0; x < image.getWidth(); x++) {
            if (RenderKind.partlyTransparent(image.getPixel(x, y) >>> 24)) {
               return true;
            }
         }
      }

      return false;
   }

   private static BedrockTextures.Texture track(Identifier id, NativeImage image, String label) {
      BedrockTextures.Texture texture = new BedrockTextures.Texture(id, image.getWidth(), image.getHeight());
      boolean replaced = PIXELS.containsKey(id);
      if ((partlyTransparent(image) ? BLENDED.add(id) : BLENDED.remove(id)) && replaced) {
         blendVersion++;
      }

      RELEASE_AFTER_TICK.cancel(id);
      PIXELS.put(id, (long)image.getWidth() * image.getHeight());
      Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Bedrock " + label, image));
      REGISTERED.add(id);
      return texture;
   }

   private static boolean isPng(byte[] data) {
      return data.length > 8 && (data[0] & 255) == 137 && data[1] == 80 && data[2] == 78 && data[3] == 71;
   }

   private static boolean isJpeg(byte[] data) {
      return data.length > 3 && (data[0] & 255) == 255 && (data[1] & 255) == 216;
   }

   private static String sanitize(String path) {
      StringBuilder out = new StringBuilder();

      for (char c : path.toLowerCase(Locale.ROOT).toCharArray()) {
         out.append(Identifier.isAllowedInIdentifier(c) && c != ':' ? c : '_');
      }

      return out.toString();
   }

   private static String sha1(String text) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public record NineSlice(BedrockTextures.Texture texture, int left, int top, int right, int bottom) {
   }

   public record Texture(Identifier id, int width, int height) {
   }

   public static final class UrlImage {
      private volatile BedrockTextures.Texture texture;
      private volatile boolean done;
      private volatile CompletableFuture<?> download = CompletableFuture.completedFuture(null);
      private volatile boolean released;

      public Optional<BedrockTextures.Texture> texture() {
         return Optional.ofNullable(this.texture);
      }

      public boolean loading() {
         return !this.done;
      }
   }
}
