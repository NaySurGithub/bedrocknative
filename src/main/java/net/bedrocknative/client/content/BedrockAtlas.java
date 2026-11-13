package net.bedrocknative.client.content;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class BedrockAtlas {
   private final Identifier location;
   @Nullable
   private TextureAtlas atlas;

   public BedrockAtlas(Identifier location) {
      this.location = location;
   }

   public Identifier location() {
      return this.location;
   }

   public Map<String, TextureAtlasSprite> stitch(Map<String, NativeImage> images) {
      Minecraft minecraft = Minecraft.getInstance();
      if (this.atlas == null) {
         this.atlas = new TextureAtlas(this.location);
         minecraft.getTextureManager().register(this.location, this.atlas);
         BedrockTextures.madeHere(this.location);
      }

      fit(images, this.atlas.maxSupportedTextureSize());
      List<SpriteContents> contents = new ArrayList<>();
      Map<Identifier, String> paths = new HashMap<>();
      contents.add(MissingTextureAtlasSprite.create());
      images.forEach((path, image) -> {
         Identifier name = this.spriteName(path);
         paths.put(name, path);
         contents.add(new SpriteContents(name, new FrameSize(image.getWidth(), image.getHeight()), image));
      });
      Preparations preparations = SpriteLoader.create(this.atlas).stitch(contents, 0, Runnable::run);
      preparations.readyForUpload().join();
      this.atlas.upload(preparations);
      Map<String, TextureAtlasSprite> sprites = new HashMap<>();
      preparations.regions().forEach((name, sprite) -> {
         String path = paths.get(name);
         if (path != null) {
            sprites.put(path, sprite);
         }
      });
      BedrockNative.LOGGER
         .info("Bedrock atlas {}: {} sprites, {}x{}", new Object[]{this.location, sprites.size(), preparations.width(), preparations.height()});
      return sprites;
   }

   static void fit(Map<String, NativeImage> images, int maxSide) {
      long budget = (long)maxSide * maxSide / 2L;

      while (area(images) > budget) {
         int largest = 0;

         for (NativeImage image : images.values()) {
            largest = Math.max(largest, Math.max(image.getWidth(), image.getHeight()));
         }

         if (largest <= 1) {
            return;
         }

         for (Entry<String, NativeImage> entry : images.entrySet()) {
            NativeImage image = entry.getValue();
            if (Math.max(image.getWidth(), image.getHeight()) == largest) {
               NativeImage half = new NativeImage(Math.max(1, image.getWidth() / 2), Math.max(1, image.getHeight() / 2), false);
               image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), half);
               image.close();
               entry.setValue(half);
            }
         }

         BedrockNative.LOGGER.info("Bedrock atlas: images of {} px a side halved to fit one {} px texture", largest, maxSide);
      }
   }

   private static long area(Map<String, NativeImage> images) {
      long area = 0L;

      for (NativeImage image : images.values()) {
         area += (long)image.getWidth() * image.getHeight();
      }

      return area;
   }

   public void release() {
      if (this.atlas != null) {
         Minecraft.getInstance().getTextureManager().release(this.location);
         this.atlas = null;
      }
   }

   private Identifier spriteName(String path) {
      String clean = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
      return Identifier.fromNamespaceAndPath("bedrocknative", "bedrock/" + Integer.toHexString(path.hashCode()) + "/" + clean);
   }
}
