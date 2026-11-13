package net.bedrocknative.client.model;

import java.util.HashMap;
import java.util.Map;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.model.geometry.TextureMesher;
import net.bedrocknative.model.skin.PlayerSkin;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class SkinTextures {
   private static final Map<String, SkinTextures.Images> SKINS = new HashMap<>();

   private SkinTextures() {
   }

   static void acquire(PlayerSkin skin) {
      if (!skin.isDefault()) {
         SKINS.computeIfAbsent(skin.key(), key -> new SkinTextures.Images(skin)).wearers++;
      }
   }

   static void release(PlayerSkin skin) {
      SkinTextures.Images images = SKINS.get(skin.key());
      if (images != null && --images.wearers <= 0) {
         SKINS.remove(skin.key());
         images.uploaded.values().forEach(BedrockTextures::releaseEntityTexture);
         ComposedTextures.releaseBase("bedrocknative:skin/" + skin.key() + "/");
      }
   }

   static void clear() {
      SKINS.values().forEach(images -> images.uploaded.values().forEach(BedrockTextures::release));
      SKINS.clear();
   }

   static boolean handles(@Nullable String path) {
      return path != null && path.startsWith("bedrocknative:skin/");
   }

   @Nullable
   static TextureMesher.Pixels pixels(String path) {
      int slash = path.indexOf(47, "bedrocknative:skin/".length());
      if (slash < 0) {
         return null;
      } else {
         SkinTextures.Images images = SKINS.get(path.substring("bedrocknative:skin/".length(), slash));
         return images != null ? part(images.skin, path.substring(slash + 1)) : null;
      }
   }

   @Nullable
   static Identifier texture(String path) {
      int slash = path.indexOf(47, "bedrocknative:skin/".length());
      if (slash < 0) {
         return null;
      } else {
         SkinTextures.Images images = SKINS.get(path.substring("bedrocknative:skin/".length(), slash));
         if (images == null) {
            return null;
         } else {
            String part = path.substring(slash + 1);
            Identifier id = images.uploaded.get(part);
            if (id == null) {
               TextureMesher.Pixels pixels = part(images.skin, part);
               if (pixels == null) {
                  return null;
               }

               id = BedrockTextures.registerArgb(
                  "skin/" + images.skin.key() + "_" + part, pixels.width(), pixels.height(), pixels.argb(), "skin " + images.skin.key() + " " + part
               );
               images.uploaded.put(part, id);
            }

            return id;
         }
      }
   }

   @Nullable
   private static TextureMesher.Pixels part(PlayerSkin skin, String part) {
      if (part.equals("skin")) {
         return skin.image();
      } else if (part.equals("cape")) {
         return skin.cape();
      } else {
         for (PlayerSkin.Animation animation : skin.animations().values()) {
            if (animation.type().part().equals(part)) {
               return animation.image();
            }
         }

         return null;
      }
   }

   public static int size() {
      return SKINS.size();
   }

   public static int uploaded() {
      return SKINS.values().stream().mapToInt(images -> images.uploaded.size()).sum();
   }

   private static final class Images {
      final PlayerSkin skin;
      final Map<String, Identifier> uploaded = new HashMap<>();
      int wearers;

      Images(PlayerSkin skin) {
         this.skin = skin;
      }
   }
}
