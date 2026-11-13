package net.bedrocknative.client.lobby;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

public final class BedrockPanorama {
   private static final Identifier ID = Identifier.fromNamespaceAndPath("bedrocknative", "lobby/panorama");
   private static final int[] FACES = new int[]{1, 3, 5, 4, 0, 2};
   private static final String[] SETS = new String[]{"textures/ui/panorama_alternate_", "textures/ui/panorama_"};
   private static boolean tried;
   private static @Nullable AbstractTexture texture;
   private static String set = SETS[0];

   private BedrockPanorama() {
   }

   public static void prepare(Minecraft minecraft) {
      if (!tried) {
         tried = true;
         String found = set(PackStack.active());
         if (found != null) {
            set = found;

            try {
               BedrockPanorama.BedrockCubeMap cube = new BedrockPanorama.BedrockCubeMap();
               cube.apply(cube.loadContents(minecraft.getResourceManager()));
               minecraft.getTextureManager().register(ID, cube);
               texture = cube;
            } catch (IOException | RuntimeException e) {
               BedrockNative.LOGGER.warn("Bedrock lobby: Bedrock's panorama didn't load; Java's turns instead", e);
            }
         }
      }
   }

   static @Nullable String set(PackStack stack) {
      for (String prefix : SETS) {
         boolean complete = true;

         for (int i = 0; i < 6 && complete; i++) {
            complete = stack.texture(prefix + i).isPresent();
         }

         if (complete) {
            return prefix;
         }
      }

      return null;
   }

   public static String faces() {
      return set;
   }

   public static AbstractTexture texture(AbstractTexture java) {
      AbstractTexture bedrock = texture;
      return bedrock != null && Minecraft.getInstance().gui.screen() instanceof LobbyScreen ? bedrock : java;
   }

   private static final class BedrockCubeMap extends CubeMapTexture {
      BedrockCubeMap() {
         super(BedrockPanorama.ID);
      }

      @Override
      public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
         NativeImage first = face(BedrockPanorama.FACES[0]);
         int width = first.getWidth();
         int height = first.getHeight();
         NativeImage stacked = new NativeImage(width, height * 6, false);

         try {
            first.copyRect(stacked, 0, 0, 0, 0, width, height, false, true);

            for (int i = 1; i < 6; i++) {
               try (NativeImage part = face(BedrockPanorama.FACES[i])) {
                  if (part.getWidth() != width || part.getHeight() != height) {
                     throw new IOException("Bedrock panorama faces differ in size");
                  }

                  part.copyRect(stacked, 0, 0, 0, i * height, width, height, false, true);
               }
            }
         } catch (IOException | RuntimeException e) {
            stacked.close();
            throw e;
         } finally {
            first.close();
         }

         return new TextureContents(stacked, new TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0.0F));
      }

      private static NativeImage face(int index) throws IOException {
         Optional<PackStack.Resource> resource = PackStack.active().texture(BedrockPanorama.set + index);
         if (resource.isEmpty()) {
            throw new IOException("no " + BedrockPanorama.set + index);
         } else {
            return NativeImage.read(resource.get().data());
         }
      }
   }
}
