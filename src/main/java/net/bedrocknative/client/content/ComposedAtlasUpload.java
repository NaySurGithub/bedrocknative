package net.bedrocknative.client.content;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.content.SpriteContentsAccessor;
import net.bedrocknative.client.mixin.content.TextureAtlasSpriteAccessor;
import net.bedrocknative.content.AtlasComposer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class ComposedAtlasUpload {
   static final boolean ENABLED = !"java".equalsIgnoreCase(System.getProperty("bedrocknative.atlasUpload", "").trim());
   private static final long MAX_BYTES = 536870912L;
   private static final int PARTS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
   private static final Map<Map<Identifier, TextureAtlasSprite>, ComposedAtlasUpload.Composition> READY = Collections.synchronizedMap(new IdentityHashMap<>());
   private static int discards;
   private static @Nullable Preparations uploading;
   private static long uploadStarted;
   private static long staticStarted;
   private static long staticNanos;
   private static boolean composedNow;
   private static ComposedAtlasUpload.@Nullable Composition lastComposition;

   private ComposedAtlasUpload() {
   }

   public static CompletableFuture<Preparations> composing(Identifier atlas, CompletableFuture<Preparations> stitch, Executor executor) {
      String name = atlas.equals(TextureAtlas.LOCATION_BLOCKS) ? "block atlas" : "item atlas";
      return stitch.thenApply(
         preparations -> {
            ReloadPhases.mark(name + " stitched");
            CompletableFuture<Void> ready = ENABLED
               ? preparations.readyForUpload().thenComposeAsync(ignored -> compose(atlas, preparations, executor), executor)
               : preparations.readyForUpload();
            return new Preparations(
               preparations.width(),
               preparations.height(),
               preparations.mipLevel(),
               preparations.missing(),
               preparations.regions(),
               ready.thenRun(() -> ReloadPhases.mark(name + " ready"))
            );
         }
      );
   }

   private static CompletableFuture<Void> compose(Identifier atlas, Preparations preparations, Executor executor) {
      long started = System.nanoTime();
      int width = preparations.width();
      int height = preparations.height();
      int levelCount = preparations.mipLevel() + 1;
      long bytes = AtlasComposer.pixels(width, height, levelCount) * 4L;
      if (bytes > 536870912L) {
         BedrockNative.LOGGER
            .info(
               "Atlas upload ({}): {}x{} with {} levels would take {} MB composed; Java's own upload",
               new Object[]{atlas, width, height, levelCount, bytes >> 20}
            );
         return CompletableFuture.completedFuture(null);
      }

      ByteBuffer[] levels = new ByteBuffer[levelCount];

      try {
         for (int level = 0; level < levelCount; level++) {
            levels[level] = MemoryUtil.memCalloc(Math.max(1, width >> level) * Math.max(1, height >> level) * 4);
         }
      } catch (RuntimeException | OutOfMemoryError e) {
         BedrockNative.LOGGER.warn("Atlas upload ({}): no memory for the composed atlas ({}); Java's own upload", atlas, e.toString());
         free(levels);
         return CompletableFuture.completedFuture(null);
      }

      List<TextureAtlasSprite> statics = new ArrayList<>();
      int animated = 0;

      for (TextureAtlasSprite sprite : preparations.regions().values()) {
         if (sprite.isAnimated()) {
            animated++;
         } else {
            statics.add(sprite);
         }
      }

      int parts = Math.max(1, Math.min(PARTS, statics.size() / 64));
      CompletableFuture<?>[] work = new CompletableFuture[parts];

      for (int part = 0; part < parts; part++) {
         List<TextureAtlasSprite> share = statics.subList(statics.size() * part / parts, statics.size() * (part + 1) / parts);
         work[part] = CompletableFuture.runAsync(() -> share.forEach(sprite -> place(sprite, levels, width, height)), executor);
      }

      int animatedCount = animated;
      int generation;
      synchronized (READY) {
         generation = discards;
      }

      return CompletableFuture.allOf(work)
         .handle(
            (ignored, error) -> {
               if (error != null) {
                  BedrockNative.LOGGER
                     .warn("Atlas upload ({}): couldn't compose the atlas off the client thread ({}); Java's own upload", atlas, error.toString());
                  free(levels);
               } else {
                  ComposedAtlasUpload.Composition composition = new ComposedAtlasUpload.Composition(
                     atlas, width, height, levels, statics.size(), animatedCount, System.nanoTime() - started
                  );
                  boolean late;
                  synchronized (READY) {
                     late = generation != discards || !AtlasReload.running();
                     if (!late) {
                        READY.put(preparations.regions(), composition);
                     }
                  }

                  if (late) {
                     composition.free();
                  }
               }

               return null;
            }
         );
   }

   private static void free(ByteBuffer[] levels) {
      for (ByteBuffer level : levels) {
         if (level != null) {
            MemoryUtil.memFree(level);
         }
      }
   }

   private static void place(TextureAtlasSprite sprite, ByteBuffer[] levels, int width, int height) {
      NativeImage[] images = ((SpriteContentsAccessor)sprite.contents()).bedrocknative$byMipLevel();
      int padding = ((TextureAtlasSpriteAccessor)sprite).bedrocknative$padding();
      int spriteWidth = sprite.contents().width();
      int spriteHeight = sprite.contents().height();
      if (images.length < levels.length) {
         throw new IllegalStateException(sprite.contents().name() + " has " + images.length + " mip levels, the atlas " + levels.length);
      }

      for (int level = 0; level < levels.length; level++) {
         NativeImage image = images[level];
         int levelWidth = spriteWidth >> level;
         int levelHeight = spriteHeight >> level;
         if (image.format() != Format.RGBA || image.getWidth() != levelWidth || image.getHeight() != levelHeight) {
            throw new IllegalStateException(
               sprite.contents().name()
                  + " level "
                  + level
                  + " is "
                  + image.getWidth()
                  + "x"
                  + image.getHeight()
                  + " "
                  + image.format()
                  + ", expected "
                  + levelWidth
                  + "x"
                  + levelHeight
                  + " RGBA"
            );
         }

         int atlasWidth = Math.max(1, width >> level);
         int atlasHeight = Math.max(1, height >> level);
         IntBuffer target = MemoryUtil.memIntBuffer(MemoryUtil.memAddress(levels[level]), atlasWidth * atlasHeight);
         IntBuffer source = MemoryUtil.memIntBuffer(image.getPointer(), levelWidth * levelHeight);
         AtlasComposer.place(target, atlasWidth, atlasHeight, source, levelWidth, levelHeight, sprite.getX() >> level, sprite.getY() >> level, padding >> level);
      }
   }

   public static void uploadStarts(Preparations preparations) {
      uploading = preparations;
      uploadStarted = System.nanoTime();
      staticNanos = 0L;
      composedNow = false;
      lastComposition = null;
   }

   public static boolean writeComposed(TextureAtlas atlas, int maxMipLevel) {
      staticStarted = System.nanoTime();
      Preparations preparations = uploading;
      ComposedAtlasUpload.Composition composition = preparations == null ? null : READY.remove(preparations.regions());
      if (composition == null) {
         return false;
      }

      try {
         GpuTexture texture = atlas.getTexture();
         if (texture.getWidth(0) == composition.width
            && texture.getHeight(0) == composition.height
            && texture.getMipLevels() == composition.levels.length
            && maxMipLevel + 1 == composition.levels.length) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            for (int level = 0; level < composition.levels.length; level++) {
               encoder.writeToTexture(
                  texture, composition.levels[level], level, 0, 0, 0, Math.max(1, composition.width >> level), Math.max(1, composition.height >> level)
               );
            }

            composedNow = true;
            lastComposition = composition;
            return true;
         } else {
            BedrockNative.LOGGER.warn("Atlas upload ({}): the composed atlas no longer matches the texture; Java's own upload", composition.atlas);
            return false;
         }
      } finally {
         composition.free();
      }
   }

   public static void initialContentsDone() {
      staticNanos = System.nanoTime() - staticStarted;
   }

   public static void uploadDone(TextureAtlas atlas) {
      Preparations preparations = uploading;
      uploading = null;
      boolean restitched = atlas.location().equals(TextureAtlas.LOCATION_BLOCKS) || atlas.location().equals(TextureAtlas.LOCATION_ITEMS);
      if (preparations != null && AtlasReload.running() && restitched) {
         long total = System.nanoTime() - uploadStarted;
         int sprites = preparations.regions().size();
         long animated = preparations.regions().values().stream().filter(TextureAtlasSprite::isAnimated).count();
         ComposedAtlasUpload.Composition composition = lastComposition;
         lastComposition = null;
         String how = composedNow && composition != null
            ? String.format(
               Locale.ROOT,
               "static sprites composed on a worker in %.1f ms (%d MB), written with the animations' first frames in %.1f ms",
               composition.composeNanos / 1000000.0,
               composition.bytes() >> 20,
               staticNanos / 1000000.0
            )
            : String.format(
               Locale.ROOT, "static sprites Java's way (a texture and a draw each) with the animations' first frames in %.1f ms", staticNanos / 1000000.0
            );
         BedrockNative.LOGGER
            .info(
               "Atlas upload ({}): {} sprites ({} animated) {}x{} with {} levels: {}; the texture and the animations' setup {} ms; {} ms on the client thread in all",
               new Object[]{
                  atlas.location(),
                  sprites,
                  animated,
                  preparations.width(),
                  preparations.height(),
                  preparations.mipLevel() + 1,
                  how,
                  String.format(Locale.ROOT, "%.1f", (total - staticNanos) / 1000000.0),
                  String.format(Locale.ROOT, "%.1f", total / 1000000.0)
               }
            );
      }
   }

   public static void discardUnused() {
      List<ComposedAtlasUpload.Composition> left;
      synchronized (READY) {
         discards++;
         left = new ArrayList<>(READY.values());
         READY.clear();
      }

      left.forEach(ComposedAtlasUpload.Composition::free);
   }

   private static final class Composition {
      private final Identifier atlas;
      private final int width;
      private final int height;
      private final ByteBuffer[] levels;
      private final int statics;
      private final int animated;
      private final long composeNanos;
      private boolean freed;

      Composition(Identifier atlas, int width, int height, ByteBuffer[] levels, int statics, int animated, long composeNanos) {
         this.atlas = atlas;
         this.width = width;
         this.height = height;
         this.levels = levels;
         this.statics = statics;
         this.animated = animated;
         this.composeNanos = composeNanos;
      }

      synchronized void free() {
         if (!this.freed) {
            this.freed = true;

            for (ByteBuffer level : this.levels) {
               if (level != null) {
                  MemoryUtil.memFree(level);
               }
            }
         }
      }

      long bytes() {
         long total = 0L;

         for (ByteBuffer level : this.levels) {
            total += level == null ? 0L : level.capacity();
         }

         return total;
      }
   }
}
