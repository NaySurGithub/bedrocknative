package net.bedrocknative.client.mixin.content;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.bedrocknative.client.content.ModelFileCache;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.BlockStateModelLoader.LoadedModels;
import net.minecraft.client.resources.model.ClientItemInfoLoader.LoadedClientInfos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelManager.class)
public abstract class ModelFileCacheMixin {
   @WrapOperation(
      method = "reload",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockModels(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
      ),
      require = 0
   )
   private CompletableFuture<Map<Identifier, UnbakedModel>> bedrocknative$keptBlockModels(
      ResourceManager manager, Executor executor, Operation<CompletableFuture<Map<Identifier, UnbakedModel>>> original
   ) {
      return ModelFileCache.load(
         ModelFileCache.Kind.BLOCK_MODELS,
         () -> (CompletableFuture<Map<Identifier, UnbakedModel>>)original.call(new Object[]{manager, executor}),
         Map::copyOf,
         executor
      );
   }

   @WrapOperation(
      method = "reload",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/resources/model/BlockStateModelLoader;loadBlockStates(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
      ),
      require = 0
   )
   private CompletableFuture<LoadedModels> bedrocknative$keptBlockStates(
      ResourceManager manager, Executor executor, Operation<CompletableFuture<LoadedModels>> original
   ) {
      return ModelFileCache.load(
         ModelFileCache.Kind.BLOCK_STATES,
         () -> (CompletableFuture<LoadedModels>)original.call(new Object[]{manager, executor}),
         loaded -> new LoadedModels(new IdentityHashMap<>(loaded.models())),
         executor
      );
   }

   @WrapOperation(
      method = "reload",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/resources/model/ClientItemInfoLoader;scheduleLoad(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
      ),
      require = 0
   )
   private CompletableFuture<LoadedClientInfos> bedrocknative$keptItems(
      ResourceManager manager, Executor executor, Operation<CompletableFuture<LoadedClientInfos>> original
   ) {
      return ModelFileCache.load(
         ModelFileCache.Kind.ITEMS,
         () -> (CompletableFuture<LoadedClientInfos>)original.call(new Object[]{manager, executor}),
         loaded -> new LoadedClientInfos(new HashMap<>(loaded.contents())),
         executor
      );
   }
}
