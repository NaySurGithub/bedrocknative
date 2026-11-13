package net.bedrocknative.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class BedrockTextureSet {
   public static final long MAX_DECODED_PIXELS = 67108864L;
   private final BedrockTextureMap.Atlas atlas;
   private final Map<String, BedrockTextureSet.Sprite> sprites;
   private final BedrockTextureSet.Stats stats;
   private final String key;

   private BedrockTextureSet(BedrockTextureMap.Atlas atlas, Map<String, BedrockTextureSet.Sprite> sprites, BedrockTextureSet.Stats stats, String key) {
      this.atlas = atlas;
      this.sprites = sprites;
      this.stats = stats;
      this.key = key;
   }

   public BedrockTextureMap.Atlas atlas() {
      return this.atlas;
   }

   public Map<String, BedrockTextureSet.Sprite> sprites() {
      return this.sprites;
   }

   public BedrockTextureSet.Stats stats() {
      return this.stats;
   }

   public String key() {
      return this.key;
   }

   public static long cost(int width, int frameHeight, int padding, int granularity) {
      return (long)roundUp(width + 2 * padding, granularity) * roundUp(frameHeight + 2 * padding, granularity);
   }

   public long area(int padding, int granularity) {
      long area = 0L;

      for (BedrockTextureSet.Sprite sprite : this.sprites.values()) {
         area += cost(sprite.image().width(), sprite.frameHeight(), padding, granularity);
      }

      return area;
   }

   public BedrockTextureSet fitTo(long maxPixels, int padding, int granularity) {
      if (this.area(padding, granularity) <= maxPixels) {
         return this;
      }

      int largest = 16;

      for (BedrockTextureSet.Sprite sprite : this.sprites.values()) {
         largest = Math.max(largest, sprite.image().width());
      }

      int cap = Integer.highestOneBit(largest);

      while (cap > 16 && this.cappedArea(cap, padding, granularity) > maxPixels) {
         cap >>= 1;
      }

      Map<String, BedrockTextureSet.Sprite> fitted = new LinkedHashMap<>();
      int downscaled = 0;

      for (BedrockTextureSet.Sprite sprite : this.sprites.values()) {
         int width = sprite.image().width();
         if (width > cap) {
            int factor = (width + cap - 1) / cap;
            fitted.put(
               sprite.name(),
               new BedrockTextureSet.Sprite(
                  sprite.name(),
                  TextureOps.downscale(sprite.image(), factor),
                  sprite.animation(),
                  sprite.keepJavaAlpha(),
                  sprite.fromServerPack(),
                  sprite.serverDefined(),
                  sprite.source()
               )
            );
            downscaled++;
         } else {
            fitted.put(sprite.name(), sprite);
         }
      }

      BedrockTextureSet.Stats fittedStats = new BedrockTextureSet.Stats(
         this.stats.atlas(),
         this.stats.javaSprites(),
         this.stats.bedrock(),
         this.stats.fromServer(),
         this.stats.serverDefined(),
         this.stats.animated(),
         downscaled,
         this.stats.kept(),
         this.stats.missing()
      );
      BedrockNative.LOGGER
         .warn("Bedrock textures ({}): {} images shrunk to at most {} px wide to fit the atlas", new Object[]{this.atlas.id(), downscaled, cap});
      return new BedrockTextureSet(this.atlas, Collections.unmodifiableMap(fitted), fittedStats, this.key + "@" + cap);
   }

   private long cappedArea(int cap, int padding, int granularity) {
      long area = 0L;

      for (BedrockTextureSet.Sprite sprite : this.sprites.values()) {
         int width = sprite.image().width();
         int factor = width > cap ? (width + cap - 1) / cap : 1;
         area += cost(Math.max(1, width / factor), Math.max(1, sprite.frameHeight() / factor), padding, granularity);
      }

      return area;
   }

   private static int roundUp(int value, int granularity) {
      return granularity <= 1 ? value : (value + granularity - 1) / granularity * granularity;
   }

   public static @Nullable ArgbImage fitJava(
      BedrockTextureSet.Sprite sprite, @Nullable ArgbImage java, int javaFrameWidth, int javaFrameHeight, boolean classes
   ) {
      ArgbImage image = sprite.image();
      if ((long)image.width() * javaFrameHeight != (long)sprite.frameHeight() * javaFrameWidth) {
         return null;
      }

      if (java == null) {
         return image;
      }

      if (sprite.keepJavaAlpha()) {
         image = TextureOps.copyAlpha(image, sprite.frameHeight(), java, javaFrameWidth, javaFrameHeight);
      }

      return !classes ? image : TextureOps.applyClass(image, TextureOps.AlphaClass.of(java));
   }

   public static BedrockTextureSet.Resolved resolve(BedrockTextureMap map, PackStack stack, Predicate<PackSource> fromServer) {
      BedrockTextureMap.Atlas atlas = map.atlas();
      boolean serverPacks = stack.packs().stream().anyMatch(fromServer);
      Map<String, Optional<PackStack.Located>> files = new HashMap<>();
      Function<String, Optional<PackStack.Located>> locate = path -> files.computeIfAbsent(path, stack::locateTexture);
      FlipbookData flipbooks = null;
      BedrockTextureSet.BaseView base = null;
      Map<String, List<String>> names;
      if (!serverPacks) {
         names = TextureData.read(stack, atlas.table());
         flipbooks = atlas == BedrockTextureMap.Atlas.BLOCKS ? FlipbookData.read(stack) : null;
      } else {
         TextureData.Views tables = TextureData.readViews(stack, atlas.table(), fromServer);
         FlipbookData.Views animations = atlas == BedrockTextureMap.Atlas.BLOCKS ? FlipbookData.readViews(stack, fromServer) : null;
         names = tables.all();
         flipbooks = animations != null ? animations.all() : null;
         PackStack baseStack = new PackStack();
         stack.packs().stream().filter(fromServer.negate()).forEach(baseStack::pushBottom);
         base = new BedrockTextureSet.BaseView(baseStack, fromServer, tables.base(), animations != null ? animations.base() : null, locate);
      }

      Map<String, Integer> kept = new TreeMap<>();
      List<String> missing = new ArrayList<>();
      List<BedrockTextureSet.Pending> pending = new ArrayList<>();
      MessageDigest digest = sha256();

      for (BedrockTextureMap.Entry entry : map.entries().values()) {
         if (entry.keepJava() != null) {
            kept.merge(entry.keepJava(), 1, Integer::sum);
         } else {
            BedrockTextureSet.Choice choice = choose(entry, names, flipbooks, locate);
            List<PackStack.Located> inputs = new ArrayList<>();

            for (String path : choice.paths()) {
               locate.apply(path).ifPresent(inputs::add);
            }

            if (inputs.size() == choice.paths().size() && (!choice.paths().isEmpty() || "clear".equals(entry.op()))) {
               Map<String, PackStack.Located> compare = new HashMap<>();
               BedrockTextureSet.Origin origin = base == null ? BedrockTextureSet.Origin.BASE : base.origin(entry, choice, inputs, compare);
               digest.update(entry.sprite().getBytes(StandardCharsets.UTF_8));

               for (PackStack.Located input : inputs) {
                  digest.update(input.resource().path().getBytes(StandardCharsets.UTF_8));
                  digest.update(input.resource().data());
               }

               if (choice.flipbook() != null) {
                  digest.update(choice.flipbook().toString().getBytes(StandardCharsets.UTF_8));
               }

               if (base != null) {
                  digest.update((byte)origin.ordinal());

                  for (PackStack.Located copy : compare.values()) {
                     digest.update(copy.resource().data());
                  }
               }

               pending.add(new BedrockTextureSet.Pending(entry, List.copyOf(inputs), choice.flipbook(), origin, Map.copyOf(compare)));
            } else {
               kept.merge("missing", 1, Integer::sum);
               missing.add(entry.sprite() + " -> " + String.join("+", choice.paths()));
            }
         }
      }

      return new BedrockTextureSet.Resolved(
         atlas, map.entries().size(), List.copyOf(pending), kept, List.copyOf(missing), fromServer, HexFormat.of().formatHex(digest.digest())
      );
   }

   private static BedrockTextureSet.Choice choose(
      BedrockTextureMap.Entry entry, Map<String, List<String>> names, @Nullable FlipbookData flipbooks, Function<String, Optional<PackStack.Located>> locate
   ) {
      FlipbookData.Flipbook flipbook = null;
      List<String> wanted = new ArrayList<>();
      if (!entry.paths().isEmpty()) {
         for (int i = 0; i < entry.paths().size(); i++) {
            List<String> named = i < entry.shortNames().size() ? names.get(entry.shortNames().get(i)) : null;
            String path = named != null && !named.isEmpty() ? named.getFirst() : null;
            wanted.add(path != null && locate.apply(path).isPresent() ? path : entry.paths().get(i));
         }
      } else if (!"clear".equals(entry.op())) {
         String path = null;
         if (entry.shortName() != null) {
            flipbook = flipbooks != null ? flipbooks.get(entry.shortName(), entry.index()) : null;
            List<String> named = names.get(entry.shortName());
            path = flipbook != null ? flipbook.texture() : (named != null && entry.index() < named.size() ? named.get(entry.index()) : null);
         }

         if (path == null || locate.apply(path).isEmpty()) {
            flipbook = null;
            path = entry.path();
         }

         if (path != null) {
            wanted.add(path);
         }
      }

      return new BedrockTextureSet.Choice(List.copyOf(wanted), flipbook);
   }

   static long headerSize(byte[] data) {
      if (data.length >= 24 && (data[0] & 255) == 137 && data[1] == 80 && data[2] == 78 && data[3] == 71) {
         return data[12] == 73 && data[13] == 72 && data[14] == 68 && data[15] == 82
            ? (long)bigEndian(data, 16) << 32 | bigEndian(data, 20) & 4294967295L
            : -1L;
      } else if ((data.length < 2 || (data[0] & 255) != 255 || (data[1] & 255) != 216) && data.length >= 18) {
         int width = data[12] & 255 | (data[13] & 255) << 8;
         int height = data[14] & 255 | (data[15] & 255) << 8;
         return (long)width << 32 | height;
      } else {
         return -1L;
      }
   }

   private static int bigEndian(byte[] data, int at) {
      return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
   }

   private static Map<String, Optional<ArgbImage>> decodeAll(Map<String, PackStack.Resource> files, long maxDecodedPixels) {
      if (files.isEmpty()) {
         return new HashMap<>();
      }

      PackStack.Resource[] resources = files.values().toArray(new PackStack.Resource[0]);
      Optional<ArgbImage>[] images = new Optional[resources.length];
      AtomicLong decodedPixels = new AtomicLong();
      IntStream.range(0, resources.length).parallel().forEach(ix -> {
         if (decodedPixels.get() < maxDecodedPixels) {
            Optional<ArgbImage> image = decode(resources[ix]);
            image.ifPresent(found -> decodedPixels.addAndGet((long)found.width() * found.height()));
            images[ix] = image;
         }
      });
      Map<String, Optional<ArgbImage>> decoded = new HashMap<>(resources.length * 2);

      for (int i = 0; i < resources.length; i++) {
         if (images[i] != null) {
            decoded.put(resources[i].path(), images[i]);
         }
      }

      return decoded;
   }

   private static Optional<ArgbImage> decode(PackStack.Resource resource) {
      try {
         return Optional.of(BedrockImages.decode(resource.data()));
      } catch (IOException | RuntimeException e) {
         BedrockNative.LOGGER.warn("Couldn't decode Bedrock texture {}: {}", resource.path(), e.getMessage());
         return Optional.empty();
      }
   }

   private static BedrockTextureSet.Built apply(BedrockTextureSet.Pending sprite, List<ArgbImage> images) {
      BedrockTextureMap.Entry entry = sprite.entry();
      ArgbImage image = images.isEmpty() ? null : images.getFirst();
      String op = entry.op();
      if (op != null) {
         switch (op) {
            case "clear":
               image = ArgbImage.blank(16, 16);
               break;
            case "opaque_base":
               image = TextureOps.forceOpaque(image);
               break;
            case "overlay_mask":
               image = TextureOps.fringe(image);
               break;
            case "water_overlay":
               image = TextureOps.firstFrame(image);
            case "crack":
               break;
            case "chain":
               if (images.size() != 2) {
                  return BedrockTextureSet.Built.kept("bad_chain");
               }

               image = TextureOps.chain(images.get(0), images.get(1));
               break;
            case "mask_overlay":
               image = TextureOps.maskOverlay(image);
               if (image == null) {
                  return BedrockTextureSet.Built.kept("no_overlay_mask");
               }
               break;
            case "mask_base":
               image = TextureOps.maskBase(image);
               break;
            default:
               return BedrockTextureSet.Built.kept("unknown_op");
         }
      }

      if (entry.frames() > 0) {
         image = TextureOps.frame(image, entry.frame(), entry.frames());
      }

      FlipbookData.Flipbook flipbook = sprite.flipbook();
      int frameCount = image.height() / image.width();
      if (flipbook != null && image.height() % image.width() == 0 && frameCount >= 2) {
         List<Integer> frames = flipbook.order(frameCount);
         if (frames.size() >= 2) {
            return new BedrockTextureSet.Built(
               image, new BedrockTextureSet.Animation(List.copyOf(frames), flipbook.ticksPerFrame(), flipbook.blendFrames()), null
            );
         }

         image = image.crop(0, frames.isEmpty() ? 0 : frames.getFirst() * image.width(), image.width(), image.width());
      } else if ((flipbook != null || entry.flipbook()) && image.height() > image.width()) {
         image = TextureOps.firstFrame(image);
      }

      if (entry.transform() != null) {
         image = TextureOps.transform(image, entry.transform());
         if (image == null) {
            return BedrockTextureSet.Built.kept("unknown_transform");
         }
      }

      return new BedrockTextureSet.Built(image, null, null);
   }

   private static MessageDigest sha256() {
      try {
         return MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public record Animation(List<Integer> frames, int ticksPerFrame, boolean interpolate) {
   }

   private static final class BaseView {
      private final PackStack stack;
      private final Predicate<PackSource> fromServer;
      private final Map<String, List<String>> names;
      private final @Nullable FlipbookData flipbooks;
      private final Function<String, Optional<PackStack.Located>> full;
      private final Map<String, Optional<PackStack.Located>> files = new HashMap<>();

      private BaseView(
         PackStack stack,
         Predicate<PackSource> fromServer,
         Map<String, List<String>> names,
         @Nullable FlipbookData flipbooks,
         Function<String, Optional<PackStack.Located>> full
      ) {
         this.stack = stack;
         this.fromServer = fromServer;
         this.names = names;
         this.flipbooks = flipbooks;
         this.full = full;
      }

      Optional<PackStack.Located> locate(String path) {
         return this.files.computeIfAbsent(path, key -> {
            Optional<PackStack.Located> top = this.full.apply(key);
            return !top.isEmpty() && this.fromServer.test(top.get().pack()) ? this.stack.locateTexture(key) : top;
         });
      }

      BedrockTextureSet.Origin origin(
         BedrockTextureMap.Entry entry, BedrockTextureSet.Choice choice, List<PackStack.Located> inputs, Map<String, PackStack.Located> compare
      ) {
         if (!BedrockTextureSet.choose(entry, this.names, this.flipbooks, this::locate).equals(choice)) {
            return BedrockTextureSet.Origin.SERVER;
         }

         for (int i = 0; i < inputs.size(); i++) {
            PackStack.Located input = inputs.get(i);
            if (this.fromServer.test(input.pack())) {
               Optional<PackStack.Located> copy = this.locate(choice.paths().get(i));
               if (copy.isEmpty()) {
                  return BedrockTextureSet.Origin.SERVER;
               }

               byte[] server = input.resource().data();
               byte[] own = copy.get().resource().data();
               if (!Arrays.equals(server, own)) {
                  long serverSize = BedrockTextureSet.headerSize(server);
                  long ownSize = BedrockTextureSet.headerSize(own);
                  if (serverSize >= 0L && ownSize >= 0L && serverSize != ownSize) {
                     return BedrockTextureSet.Origin.SERVER;
                  }

                  compare.put(input.resource().path(), copy.get());
               }
            }
         }

         return compare.isEmpty() ? BedrockTextureSet.Origin.BASE : BedrockTextureSet.Origin.COMPARE;
      }
   }

   private record Built(@Nullable ArgbImage image, BedrockTextureSet.@Nullable Animation animation, @Nullable String reason) {
      static BedrockTextureSet.Built kept(String reason) {
         return new BedrockTextureSet.Built(null, null, reason);
      }
   }

   private record Choice(List<String> paths, FlipbookData.@Nullable Flipbook flipbook) {
   }

   private enum Origin {
      BASE,
      SERVER,
      COMPARE;
   }

   private record Pending(
      BedrockTextureMap.Entry entry,
      List<PackStack.Located> inputs,
      FlipbookData.@Nullable Flipbook flipbook,
      BedrockTextureSet.Origin origin,
      Map<String, PackStack.Located> compare
   ) {
      String source() {
         if (this.inputs.size() == 1) {
            return this.inputs.getFirst().resource().path();
         }

         StringBuilder joined = new StringBuilder();

         for (PackStack.Located input : this.inputs) {
            joined.append(joined.isEmpty() ? "" : "+").append(input.resource().path());
         }

         return joined.toString();
      }
   }

   public static final class Resolved {
      private final BedrockTextureMap.Atlas atlas;
      private final int javaSprites;
      private final List<BedrockTextureSet.Pending> pending;
      private final Map<String, Integer> kept;
      private final List<String> missing;
      private final Predicate<PackSource> fromServer;
      private final String key;

      private Resolved(
         BedrockTextureMap.Atlas atlas,
         int javaSprites,
         List<BedrockTextureSet.Pending> pending,
         Map<String, Integer> kept,
         List<String> missing,
         Predicate<PackSource> fromServer,
         String key
      ) {
         this.atlas = atlas;
         this.javaSprites = javaSprites;
         this.pending = pending;
         this.kept = kept;
         this.missing = missing;
         this.fromServer = fromServer;
         this.key = key;
      }

      public String key() {
         return this.key;
      }

      public int size() {
         return this.pending.size();
      }

      public BedrockTextureSet build() {
         return this.build(67108864L);
      }

      BedrockTextureSet build(long maxDecodedPixels) {
         Map<String, Integer> kept = new TreeMap<>(this.kept);
         Map<String, PackStack.Resource> files = new LinkedHashMap<>();
         Map<String, PackStack.Resource> baseCopies = new LinkedHashMap<>();

         for (BedrockTextureSet.Pending sprite : this.pending) {
            sprite.inputs().forEach(input -> files.putIfAbsent(input.resource().path(), input.resource()));
            sprite.compare().values().forEach(copy -> baseCopies.putIfAbsent(copy.resource().path(), copy.resource()));
         }

         Map<String, Optional<ArgbImage>> decoded = BedrockTextureSet.decodeAll(files, maxDecodedPixels);
         Map<String, Optional<ArgbImage>> baseDecoded = BedrockTextureSet.decodeAll(baseCopies, Long.MAX_VALUE);
         Set<String> accounted = new HashSet<>();
         Map<String, BedrockTextureSet.Sprite> sprites = new LinkedHashMap<>();
         int serverSprites = 0;
         int serverDefinedSprites = 0;
         int animated = 0;
         long[] pixels = new long[]{0L};

         for (BedrockTextureSet.Pending sprite : this.pending) {
            List<ArgbImage> images = new ArrayList<>();
            boolean overBudget = false;

            for (PackStack.Located input : sprite.inputs()) {
               String path = input.resource().path();
               Optional<ArgbImage> image;
               if (accounted.contains(path)) {
                  image = decoded.get(path);
               } else {
                  if (pixels[0] >= maxDecodedPixels) {
                     overBudget = true;
                     break;
                  }

                  image = decoded.get(path);
                  if (image == null) {
                     image = BedrockTextureSet.decode(input.resource());
                     decoded.put(path, image);
                  }

                  accounted.add(path);
                  image.ifPresent(found -> pixels[0] += (long)found.width() * found.height());
               }

               image.ifPresent(images::add);
            }

            if (!overBudget && images.size() == sprite.inputs().size()) {
               BedrockTextureSet.Built built = BedrockTextureSet.apply(sprite, images);
               if (built.reason() != null) {
                  kept.merge(built.reason(), 1, Integer::sum);
               } else {
                  boolean server = sprite.inputs().stream().anyMatch(input -> this.fromServer.test(input.pack()));
                  boolean serverDefined = sprite.origin() == BedrockTextureSet.Origin.SERVER
                     || sprite.origin() == BedrockTextureSet.Origin.COMPARE && differs(sprite, images, baseDecoded);
                  serverSprites += server ? 1 : 0;
                  serverDefinedSprites += serverDefined ? 1 : 0;
                  animated += built.animation() != null ? 1 : 0;
                  String name = sprite.entry().sprite();
                  sprites.put(
                     name,
                     new BedrockTextureSet.Sprite(
                        name, built.image(), built.animation(), sprite.entry().keepJavaAlpha(), server, serverDefined, sprite.source()
                     )
                  );
               }
            } else {
               kept.merge(overBudget ? "pixel budget" : "undecodable", 1, Integer::sum);
            }
         }

         BedrockTextureSet.Stats stats = new BedrockTextureSet.Stats(
            this.atlas.id(),
            this.javaSprites,
            sprites.size(),
            serverSprites,
            serverDefinedSprites,
            animated,
            0,
            Collections.unmodifiableMap(kept),
            this.missing
         );
         return new BedrockTextureSet(this.atlas, Collections.unmodifiableMap(sprites), stats, this.key);
      }

      private static boolean differs(BedrockTextureSet.Pending sprite, List<ArgbImage> images, Map<String, Optional<ArgbImage>> baseDecoded) {
         for (int i = 0; i < sprite.inputs().size(); i++) {
            PackStack.Located copy = sprite.compare().get(sprite.inputs().get(i).resource().path());
            if (copy != null) {
               Optional<ArgbImage> own = baseDecoded.get(copy.resource().path());
               ArgbImage server = images.get(i);
               if (own == null || own.isEmpty() || !own.get().samePixels(server)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public record Sprite(
      String name,
      ArgbImage image,
      BedrockTextureSet.@Nullable Animation animation,
      boolean keepJavaAlpha,
      boolean fromServerPack,
      boolean serverDefined,
      String source
   ) {
      public int frameHeight() {
         return this.animation != null ? this.image.width() : this.image.height();
      }
   }

   public record Stats(
      String atlas,
      int javaSprites,
      int bedrock,
      int fromServer,
      int serverDefined,
      int animated,
      int downscaled,
      Map<String, Integer> kept,
      List<String> missing
   ) {
      @Override
      public String toString() {
         return String.format(
            "%s: %d of %d Java sprites from Bedrock (%d from server packs, %d changed by them, %d animated, %d downscaled), Java kept %s%s",
            this.atlas,
            this.bedrock,
            this.javaSprites,
            this.fromServer,
            this.serverDefined,
            this.animated,
            this.downscaled,
            this.kept,
            this.missing.isEmpty()
               ? ""
               : ", missing "
                  + this.missing.subList(0, Math.min(8, this.missing.size()))
                  + (this.missing.size() > 8 ? " and " + (this.missing.size() - 8) + " more" : "")
         );
      }
   }
}
