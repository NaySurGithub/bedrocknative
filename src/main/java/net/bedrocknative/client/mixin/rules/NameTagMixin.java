package net.bedrocknative.client.mixin.rules;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.bedrocknative.client.rules.BedrockNameTags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
   @ModifyExpressionValue(
      method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z")
   )
   private boolean bedrocknative$showTags(boolean original) {
      return original && !BedrockNameTags.hides();
   }
}
