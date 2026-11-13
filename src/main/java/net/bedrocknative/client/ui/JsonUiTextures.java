package net.bedrocknative.client.ui;

import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.ui.UiPackTextures;
import net.bedrocknative.ui.UiTextureInfo;
import net.bedrocknative.ui.UiTextures;
import org.jspecify.annotations.Nullable;

final class JsonUiTextures implements UiTextures {
   static final String URL = "url:";
   static final String FILE = "file:";
   private final UiPackTextures pack;

   JsonUiTextures(UiPackTextures pack) {
      this.pack = pack;
   }

   @Nullable
   @Override
   public UiTextureInfo info(String texture, String fileSystem) {
      if (texture.isEmpty()) {
         return null;
      } else if ("Internet".equalsIgnoreCase(fileSystem)) {
         Optional<BedrockTextures.Texture> image = BedrockTextures.url(texture).texture();
         return image.<UiTextureInfo>map(found -> new UiTextureInfo("url:" + texture, found.width(), found.height(), found.width(), found.height(), null))
            .orElse(null);
      } else if ("RawPath".equalsIgnoreCase(fileSystem)) {
         Optional<BedrockTextures.Texture> image = BedrockTextures.file(texture);
         return image.<UiTextureInfo>map(found -> new UiTextureInfo("file:" + texture, found.width(), found.height(), found.width(), found.height(), null))
            .orElse(null);
      } else {
         try {
            return this.pack.info(texture, fileSystem);
         } catch (RuntimeException var4) {
            BedrockNative.LOGGER.debug("JSON UI texture {} not readable", texture, var4);
            return null;
         }
      }
   }
}
