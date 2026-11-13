package net.bedrocknative.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.world.BedrockFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({FogRenderer.class})
public abstract class FogRendererMixin {
   @ModifyReturnValue(
      method = {"setupFog"},
      at = {@At("RETURN")}
   )
   private FogData bedrocknative$bedrockFog(
      FogData fog, Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level
   ) {
      BedrockFog.modify(fog, camera, renderDistanceInChunks * 16.0F, level);
      return fog;
   }
}
