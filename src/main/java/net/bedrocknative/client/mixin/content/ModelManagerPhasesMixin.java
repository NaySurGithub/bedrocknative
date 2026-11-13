package net.bedrocknative.client.mixin.content;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.bedrocknative.client.content.AtlasReload;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelManager.class)
public abstract class ModelManagerPhasesMixin {
   @Inject(method = "loadBlockModels", at = @At("RETURN"), cancellable = true)
   private static void bedrocknative$modelFilesRead(ResourceManager manager, Executor executor, CallbackInfoReturnable<CompletableFuture<?>> cir) {
      if (AtlasReload.running()) {
         cir.setReturnValue(((CompletableFuture)cir.getReturnValue()).whenComplete((value, error) -> AtlasReload.phase("model files read")));
      }
   }

   @Inject(method = "loadModels", at = @At("RETURN"), cancellable = true)
   private static void bedrocknative$modelsBaked(CallbackInfoReturnable<CompletableFuture<?>> cir) {
      if (AtlasReload.running()) {
         cir.setReturnValue(((CompletableFuture)cir.getReturnValue()).whenComplete((value, error) -> AtlasReload.phase("models baked")));
      }
   }
}
