package net.bedrocknative.client.mixin.content;

import java.util.concurrent.Executor;
import net.bedrocknative.client.content.ReloadTasks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({Minecraft.class})
public abstract class MinecraftReloadMixin {
   @ModifyArg(
      method = {"reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"
      )},
      index = 1
   )
   private Executor bedrocknative$keepReloadSteps(Executor main) {
      return ReloadTasks.wrap(main);
   }
}
