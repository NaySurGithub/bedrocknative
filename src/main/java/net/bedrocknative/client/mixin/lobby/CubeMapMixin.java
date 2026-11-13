package net.bedrocknative.client.mixin.lobby;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.lobby.BedrockPanorama;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CubeMap.class)
public abstract class CubeMapMixin {
   @WrapOperation(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"
      ),
      require = 0
   )
   private AbstractTexture bedrocknative$bedrockPanorama(TextureManager manager, Identifier location, Operation<AbstractTexture> original) {
      return BedrockPanorama.texture((AbstractTexture)original.call(new Object[]{manager, location}));
   }
}
