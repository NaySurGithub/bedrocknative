package net.bedrocknative.client.mixin.content;

import java.util.Map;
import net.bedrocknative.client.content.AtlasReload;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   targets = {"net.minecraft.client.resources.model.sprite.AtlasManager$PendingStitch"}
)
public abstract class PendingStitchMixin {
   @Redirect(
      method = {"joinAndUpload"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V"
      )}
   )
   private void bedrocknative$uploadUnlessKept(TextureAtlas atlas, Preparations preparations) {
      if (!AtlasReload.kept(atlas, preparations)) {
         atlas.upload(preparations);
      }
   }

   @Redirect(
      method = {"joinAndUpload"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;regions()Ljava/util/Map;"
      )}
   )
   private Map<Identifier, TextureAtlasSprite> bedrocknative$currentSpritesWhenKept(Preparations preparations) {
      return AtlasReload.regions(preparations);
   }
}
