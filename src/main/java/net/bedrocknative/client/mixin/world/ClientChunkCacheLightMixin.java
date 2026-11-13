package net.bedrocknative.client.mixin.world;

import net.bedrocknative.client.world.light.ThreadedLight;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheLightMixin {
   @Redirect(
      method = "<init>",
      at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/chunk/LightChunkGetter;ZZ)Lnet/minecraft/world/level/lighting/LevelLightEngine;"),
      require = 0
   )
   private LevelLightEngine bedrocknative$lightEngine(LightChunkGetter source, boolean hasBlockLight, boolean hasSkyLight) {
      return ThreadedLight.create(source, hasBlockLight, hasSkyLight);
   }
}
