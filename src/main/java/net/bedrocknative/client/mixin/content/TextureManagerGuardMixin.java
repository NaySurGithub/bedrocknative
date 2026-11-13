package net.bedrocknative.client.mixin.content;

import java.util.Map;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.content.BedrockTextures;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({TextureManager.class})
public abstract class TextureManagerGuardMixin {
   @Shadow
   @Final
   private Map<Identifier, AbstractTexture> byPath;

   @Inject(
      method = {"getTexture"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$noLoadOfReleasedBedrockTexture(Identifier location, CallbackInfoReturnable<AbstractTexture> cir) {
      if (LunarCompat.active() && !this.byPath.containsKey(location) && BedrockTextures.isMadeHere(location)) {
         AbstractTexture missing = this.byPath.get(MissingTextureAtlasSprite.getLocation());
         if (missing != null) {
            LunarCompat.diagnose("textures", 10, "{} was asked for after its release: drawn as the missing texture instead of loaded mid-frame", location);
            cir.setReturnValue(missing);
         }
      }
   }
}
