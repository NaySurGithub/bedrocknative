package net.bedrocknative.client.mixin.particle;

import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
   @Inject(method = "tick", at = @At("TAIL"))
   private void bedrocknative$tick(CallbackInfo ci) {
      BedrockParticleEngine.tick();
   }

   @Inject(method = "extract", at = @At("TAIL"))
   private void bedrocknative$extract(ParticlesRenderState particles, Frustum frustum, Camera camera, float partialTickTime, CallbackInfo ci) {
      BedrockParticleEngine.extract(particles, frustum, camera, partialTickTime);
   }

   @Inject(method = "clearParticles", at = @At("HEAD"))
   private void bedrocknative$clear(CallbackInfo ci) {
      BedrockParticleEngine.clearParticles();
   }
}
