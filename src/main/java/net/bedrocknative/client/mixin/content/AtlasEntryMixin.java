package net.bedrocknative.client.mixin.content;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.bedrocknative.client.content.AtlasReload;
import net.bedrocknative.client.content.ComposedAtlasUpload;
import net.minecraft.client.renderer.texture.SpriteLoader.Preparations;
import net.minecraft.client.resources.model.sprite.AtlasManager.AtlasConfig;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"net.minecraft.client.resources.model.sprite.AtlasManager$AtlasEntry"}
)
public abstract class AtlasEntryMixin {
   @Shadow
   @Final
   private AtlasConfig config;

   @Inject(
      method = {"scheduleLoad"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$keepUntouchedAtlases(
      ResourceManager resourceManager, Executor executor, int maxMipmapLevels, CallbackInfoReturnable<CompletableFuture<Preparations>> cir
   ) {
      if (AtlasReload.keeps(this.config.definitionLocation())) {
         cir.setReturnValue(CompletableFuture.completedFuture(AtlasReload.KEPT));
      }
   }

   @Inject(
      method = {"scheduleLoad"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bedrocknative$composeRestitchedAtlases(
      ResourceManager resourceManager, Executor executor, int maxMipmapLevels, CallbackInfoReturnable<CompletableFuture<Preparations>> cir
   ) {
      if (AtlasReload.restitches(this.config.definitionLocation())) {
         cir.setReturnValue(ComposedAtlasUpload.composing(this.config.textureId(), (CompletableFuture<Preparations>)cir.getReturnValue(), executor));
      }
   }
}
