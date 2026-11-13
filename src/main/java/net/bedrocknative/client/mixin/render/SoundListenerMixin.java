package net.bedrocknative.client.mixin.render;

import com.mojang.blaze3d.audio.ListenerTransform;
import net.bedrocknative.client.camera.BedrockCamera;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({SoundEngine.class})
public abstract class SoundListenerMixin {
   @ModifyVariable(
      method = {"updateSource"},
      at = {@At("STORE")}
   )
   private ListenerTransform bedrocknative$listener(ListenerTransform transform) {
      return BedrockCamera.listener(transform);
   }
}
