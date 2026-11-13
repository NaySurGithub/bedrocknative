package net.bedrocknative.client.content;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.content.BedrockTextureMap;
import net.bedrocknative.content.BedrockTextureSet;
import net.bedrocknative.content.LegacyTextureNames;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.jspecify.annotations.Nullable;

public record BedrockSpriteSource(BedrockTextureMap.Atlas atlas) implements SpriteSource {
   public static final Identifier TYPE = Identifier.fromNamespaceAndPath("bedrocknative", "bedrock_textures");
   private static final Codec<BedrockTextureMap.Atlas> ATLAS_CODEC = Codec.STRING
      .xmap(id -> id.equals(BedrockTextureMap.Atlas.ITEMS.id()) ? BedrockTextureMap.Atlas.ITEMS : BedrockTextureMap.Atlas.BLOCKS, BedrockTextureMap.Atlas::id);
   public static final MapCodec<BedrockSpriteSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(ATLAS_CODEC.fieldOf("atlas").forGetter(BedrockSpriteSource::atlas)).apply(instance, BedrockSpriteSource::new)
   );
   private static final Map<BedrockTextureMap.Atlas, BedrockSpriteSource.Stitch> LAST = new ConcurrentHashMap<>();
   private static final int LOGGED_NAMES = 8;

   public static void register() {
      SpriteSources.ID_MAPPER.put(TYPE, MAP_CODEC);
   }

   public static BedrockSpriteSource.@Nullable Stitch last(BedrockTextureMap.Atlas atlas) {
      return LAST.get(atlas);
   }

   public static boolean runLast(List<SpriteSource> sources) {
      List<SpriteSource> ours = new ArrayList<>();

      for (SpriteSource source : sources) {
         if (source instanceof BedrockSpriteSource) {
            ours.add(source);
         }
      }

      if (ours.isEmpty()) {
         return false;
      }

      if (!sources.subList(sources.size() - ours.size(), sources.size()).equals(ours)) {
         sources.removeIf(sourcex -> sourcex instanceof BedrockSpriteSource);
         sources.addAll(ours);
      }

      return true;
   }

   @Override
   public void run(ResourceManager resourceManager, Output output) {
      BedrockTextureSet set = BedrockVanillaTextures.armed(this.atlas);
      if (set == null) {
         LAST.remove(this.atlas);
      } else {
         BedrockSpriteSource.Stitch stitch = new BedrockSpriteSource.Stitch();
         LAST.put(this.atlas, stitch);
         SpriteProvenance provenance = output instanceof SpriteProvenance known ? known : null;
         Set<String> packs = new TreeSet<>();
         LegacyJavaPacks legacy = LegacyJavaPacks.of(resourceManager);
         stitch.legacyStrips = legacy.strips();
         Map<String, List<LegacyTextureNames.Candidate>> oldNames = legacy.isEmpty() ? Map.of() : LegacyTextureNames.bundled(this.atlas);

         for (BedrockTextureSet.Sprite sprite : set.sprites().values()) {
            Identifier id = Identifier.withDefaultNamespace(sprite.name());
            Identifier file = TEXTURE_ID_CONVERTER.idToFile(id);
            Resource top = resourceManager.getResource(file).orElse(null);
            Resource shown = provenance != null ? provenance.file(id) : top;
            stitch.offered.incrementAndGet();
            LegacyJavaPacks.Found old = legacy.isEmpty() ? null : legacy.find(oldNames.getOrDefault(sprite.name(), List.of()), shown);
            if (old != null || shown != null && JavaResourcePacks.isPlayers(shown)) {
               packs.add(old != null ? old.packId() : shown.sourcePackId());
               if (!sprite.serverDefined()) {
                  stitch.javaPacks.add(sprite.name());
                  if (old != null) {
                     stitch.legacyPacks.add(sprite.name());
                     Resource below = shown;
                     output.add(id, loader -> loadLegacy(loader, id, old, () -> below != null ? loader.loadSprite(id, below) : null));
                  }
                  continue;
               }

               stitch.serverOverJavaPacks.add(sprite.name());
            }

            Resource reference = top != null && JavaResourcePacks.isPlayers(top) ? JavaResourcePacks.below(resourceManager, file) : top;
            Resource fallback = shown != null ? shown : top;
            output.add(id, loader -> this.load(loader, id, sprite, reference, fallback, stitch));
         }

         if (!legacy.isEmpty()) {
            for (Entry<String, List<LegacyTextureNames.Candidate>> entry : oldNames.entrySet()) {
               if (!set.sprites().containsKey(entry.getKey())) {
                  Identifier id = Identifier.withDefaultNamespace(entry.getKey());
                  Resource shown = provenance != null ? provenance.file(id) : resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(id)).orElse(null);
                  LegacyJavaPacks.Found old = legacy.find(entry.getValue(), shown);
                  if (old != null) {
                     packs.add(old.packId());
                     stitch.javaPacks.add(entry.getKey());
                     stitch.legacyPacks.add(entry.getKey());
                     output.add(id, loader -> loadLegacy(loader, id, old, () -> shown != null ? loader.loadSprite(id, shown) : null));
                  }
               }
            }

            legacy.modelSprites(this.atlas).forEach((idx, oldx) -> {
               stitch.legacyModelSprites.incrementAndGet();
               output.add(idx, loader -> loadLegacy(loader, idx, oldx, () -> null));
            });
         }

         if (!packs.isEmpty()) {
            BedrockNative.LOGGER
               .info(
                  "Bedrock textures, {} atlas: {} sprites from the player's Java packs {}{}, {} of the server's own over them{}{}",
                  new Object[]{
                     this.atlas.id(),
                     stitch.javaPacks.size(),
                     packs,
                     names(stitch.javaPacks),
                     stitch.serverOverJavaPacks.size(),
                     names(stitch.serverOverJavaPacks),
                     stitch.legacyPacks.isEmpty() && stitch.legacyModelSprites.get() == 0
                        ? ""
                        : "; legacy packs "
                           + legacy.ids()
                           + ": "
                           + stitch.legacyPacks.size()
                           + " sprites under old names"
                           + names(stitch.legacyPacks)
                           + ", "
                           + stitch.legacyModelSprites.get()
                           + " named by their models"
                  }
               );
         }
      }
   }

   private static @Nullable SpriteContents loadLegacy(
      SpriteResourceLoader loader, Identifier id, LegacyJavaPacks.Found old, Supplier<@Nullable SpriteContents> fallback
   ) {
      SpriteContents contents = old.load(loader, id);
      return contents != null ? contents : fallback.get();
   }

   static SpriteContents still(Identifier id, ArgbImage pixels) {
      NativeImage image = new NativeImage(pixels.width(), pixels.height(), false);

      try {
         for (int y = 0; y < pixels.height(); y++) {
            for (int x = 0; x < pixels.width(); x++) {
               image.setPixel(x, y, pixels.pixel(x, y));
            }
         }

         return new SpriteContents(id, new FrameSize(pixels.width(), pixels.height()), image, Optional.empty(), List.of(), Optional.empty());
      } catch (RuntimeException e) {
         image.close();
         throw e;
      }
   }

   private static String names(Set<String> sprites) {
      if (sprites.isEmpty()) {
         return "";
      }

      List<String> sorted = new ArrayList<>(new TreeSet<>(sprites));
      return " " + sorted.subList(0, Math.min(8, sorted.size())) + (sorted.size() > 8 ? " and " + (sorted.size() - 8) + " more" : "");
   }

   private @Nullable SpriteContents load(
      SpriteResourceLoader loader,
      Identifier id,
      BedrockTextureSet.Sprite sprite,
      @Nullable Resource reference,
      @Nullable Resource fallback,
      BedrockSpriteSource.Stitch stitch
   ) {
      try {
         ArgbImage javaImage = null;
         FrameSize javaFrame = null;
         Optional<TextureMetadataSection> textureInfo = Optional.empty();
         if (reference != null) {
            ResourceMetadata metadata = reference.metadata();
            textureInfo = metadata.getSection(TextureMetadataSection.TYPE);

            try (InputStream input = reference.open()) {
               javaImage = BedrockImages.decode(input.readAllBytes());
            }

            ArgbImage image = javaImage;
            javaFrame = metadata.getSection(AnimationMetadataSection.TYPE)
               .map(animation -> animation.calculateFrameSize(image.width(), image.height()))
               .orElse(new FrameSize(image.width(), image.height()));
         }

         ArgbImage fitted = javaFrame == null
            ? sprite.image()
            : BedrockTextureSet.fitJava(sprite, javaImage, javaFrame.width(), javaFrame.height(), this.atlas == BedrockTextureMap.Atlas.BLOCKS);
         if (fitted == null) {
            stitch.fallback("aspect");
            BedrockNative.LOGGER
               .debug(
                  "Bedrock texture {} ({}x{}) doesn't fit Java's {} ({}x{}); Java's stays",
                  new Object[]{sprite.source(), sprite.image().width(), sprite.frameHeight(), id, javaFrame.width(), javaFrame.height()}
               );
            return fallback != null ? loader.loadSprite(id, fallback) : null;
         } else {
            SpriteContents contents = contents(id, sprite, fitted, textureInfo);
            stitch.bedrock.incrementAndGet();
            stitch.fromBedrock.add(sprite.name());
            return contents;
         }
      } catch (Exception e) {
         stitch.fallback("error");
         BedrockNative.LOGGER.warn("Couldn't use Bedrock texture {} for {}; Java's stays", new Object[]{sprite.source(), id, e});
         return fallback != null ? loader.loadSprite(id, fallback) : null;
      }
   }

   private static SpriteContents contents(Identifier id, BedrockTextureSet.Sprite sprite, ArgbImage pixels, Optional<TextureMetadataSection> textureInfo) {
      NativeImage image = new NativeImage(pixels.width(), pixels.height(), false);

      try {
         for (int y = 0; y < pixels.height(); y++) {
            for (int x = 0; x < pixels.width(); x++) {
               image.setPixel(x, y, pixels.pixel(x, y));
            }
         }

         BedrockTextureSet.Animation animation = sprite.animation();
         if (animation == null) {
            return new SpriteContents(id, new FrameSize(pixels.width(), pixels.height()), image, Optional.empty(), List.of(), textureInfo);
         }

         int frameCount = pixels.height() / pixels.width();
         boolean everyFrameInOrder = animation.frames().equals(IntStream.range(0, frameCount).boxed().toList());
         Optional<List<AnimationFrame>> frames = everyFrameInOrder
            ? Optional.empty()
            : Optional.of(animation.frames().stream().map(AnimationFrame::new).toList());
         AnimationMetadataSection metadata = new AnimationMetadataSection(
            frames, Optional.empty(), Optional.empty(), animation.ticksPerFrame(), animation.interpolate()
         );
         return new SpriteContents(id, new FrameSize(pixels.width(), pixels.width()), image, Optional.of(metadata), List.of(), textureInfo);
      } catch (RuntimeException e) {
         image.close();
         throw e;
      }
   }

   @Override
   public MapCodec<BedrockSpriteSource> codec() {
      return MAP_CODEC;
   }

   public static final class Stitch {
      private final AtomicInteger offered = new AtomicInteger();
      private final AtomicInteger bedrock = new AtomicInteger();
      private final Map<String, AtomicInteger> java = new ConcurrentHashMap<>();
      private final Set<String> fromBedrock = ConcurrentHashMap.newKeySet();
      private final Set<String> javaPacks = ConcurrentHashMap.newKeySet();
      private final Set<String> serverOverJavaPacks = ConcurrentHashMap.newKeySet();
      private final Set<String> legacyPacks = ConcurrentHashMap.newKeySet();
      private final AtomicInteger legacyModelSprites = new AtomicInteger();
      private volatile LegacyJavaPacks.@Nullable Strips legacyStrips;

      public int offered() {
         return this.offered.get();
      }

      public int bedrock() {
         return this.bedrock.get();
      }

      public Map<String, Integer> javaFallbacks() {
         Map<String, Integer> counts = new TreeMap<>();
         this.java.forEach((reason, count) -> counts.put(reason, count.get()));
         return counts;
      }

      public boolean isBedrock(String sprite) {
         return this.fromBedrock.contains(sprite);
      }

      public Set<String> javaPacks() {
         return new TreeSet<>(this.javaPacks);
      }

      public Set<String> serverOverJavaPacks() {
         return new TreeSet<>(this.serverOverJavaPacks);
      }

      public Set<String> legacyPacks() {
         return new TreeSet<>(this.legacyPacks);
      }

      public int legacyModelSprites() {
         return this.legacyModelSprites.get();
      }

      public int legacyStripDecodes() {
         LegacyJavaPacks.Strips strips = this.legacyStrips;
         return strips == null ? 0 : strips.decodes();
      }

      private void fallback(String reason) {
         this.java.computeIfAbsent(reason, key -> new AtomicInteger()).incrementAndGet();
      }

      @Override
      public String toString() {
         return this.bedrock()
            + " of "
            + this.offered()
            + " offered sprites from Bedrock"
            + (
               this.javaPacks.isEmpty()
                  ? ""
                  : ", "
                     + this.javaPacks.size()
                     + " from the player's Java packs"
                     + (this.legacyPacks.isEmpty() ? "" : " (" + this.legacyPacks.size() + " under old names)")
            )
            + (this.serverOverJavaPacks.isEmpty() ? "" : ", " + this.serverOverJavaPacks.size() + " of the server's over them")
            + (this.java.isEmpty() ? "" : ", Java's kept " + this.javaFallbacks());
      }
   }
}
