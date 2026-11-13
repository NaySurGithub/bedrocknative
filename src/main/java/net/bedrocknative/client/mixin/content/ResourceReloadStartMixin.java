package net.bedrocknative.client.mixin.content;

import net.bedrocknative.client.content.ModelFileCache;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ReloadableResourceManager.class})
public abstract class ResourceReloadStartMixin {
   @Inject(
      method = {"createReload"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$forgetModelFiles(CallbackInfoReturnable<?> cir) {
      ModelFileCache.javaReloadStarts();
   }
}
