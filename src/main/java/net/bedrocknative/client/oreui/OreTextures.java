package net.bedrocknative.client.oreui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.oreui.OreInstall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class OreTextures {
   private static final long MAX_BYTES = 16777216L;
   private final @Nullable OreInstall install;
   private final Map<Path, Optional<OreTextures.Texture>> textures = new HashMap<>();
   private final Map<Path, Optional<OreTextures.Texture>> masks = new HashMap<>();
   private static final AtomicInteger IDS = new AtomicInteger();

   OreTextures(@Nullable OreInstall install) {
      this.install = install;
   }

   Optional<OreTextures.Texture> get(Path file) {
      return this.textures.computeIfAbsent(file.toAbsolutePath().normalize(), key -> this.load(key, false));
   }

   Optional<OreTextures.Texture> mask(Path file) {
      return this.masks.computeIfAbsent(file.toAbsolutePath().normalize(), key -> this.load(key, true));
   }

   private Optional<OreTextures.Texture> load(Path file, boolean mask) {
      try {
         Optional<byte[]> painted = this.install == null ? Optional.empty() : this.install.generated(file);
         if (!painted.isEmpty() || Files.isRegularFile(file) && Files.size(file) <= 16777216L) {
            ArgbImage decoded = BedrockImages.decode(painted.isPresent() ? painted.get() : Files.readAllBytes(file));
            NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);

            for (int y = 0; y < decoded.height(); y++) {
               for (int x = 0; x < decoded.width(); x++) {
                  int pixel = decoded.pixel(x, y);
                  image.setPixel(x, y, mask ? pixel & 0xFF000000 | 16777215 : pixel);
               }
            }

            String name = (mask ? "mask_" : "") + file.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            Identifier id = Identifier.fromNamespaceAndPath("bedrocknative", "oreui/" + IDS.incrementAndGet() + "_" + name);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Ore UI " + file.getFileName(), image));
            return Optional.of(new OreTextures.Texture(id, decoded.width(), decoded.height()));
         } else {
            return Optional.empty();
         }
      } catch (IOException | RuntimeException e) {
         BedrockNative.LOGGER.debug("Ore UI: picture {} not shown: {}", file, e.getMessage());
         return Optional.empty();
      }
   }

   void release() {
      Minecraft minecraft = Minecraft.getInstance();
      this.textures.values().forEach(texture -> texture.ifPresent(found -> minecraft.getTextureManager().release(found.id())));
      this.textures.clear();
      this.masks.values().forEach(texture -> texture.ifPresent(found -> minecraft.getTextureManager().release(found.id())));
      this.masks.clear();
   }

   void forget(@Nullable Path file) {
      if (file != null) {
         Optional<OreTextures.Texture> texture = this.textures.remove(file.toAbsolutePath().normalize());
         if (texture != null) {
            texture.ifPresent(found -> Minecraft.getInstance().getTextureManager().release(found.id()));
         }
      }
   }

   record Texture(Identifier id, int width, int height) {
   }
}
