package net.bedrocknative.client.mixin.world;

import net.bedrocknative.client.net.BedrockBridging;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class})
public abstract class MinecraftBridgingMixin {
   @Shadow
   @Nullable
   public HitResult hitResult;
   @Unique
   @Nullable
   private HitResult bedrocknative$crosshair;
   @Unique
   private boolean bedrocknative$bridging;

   @Inject(
      method = {"startUseItem"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$bridge(CallbackInfo ci) {
      BlockHitResult target = BedrockBridging.target((Minecraft)this);
      if (target != null) {
         this.bedrocknative$crosshair = this.hitResult;
         this.bedrocknative$bridging = true;
         this.hitResult = target;
      }
   }

   @Inject(
      method = {"startUseItem"},
      at = {@At("RETURN")}
   )
   private void bedrocknative$restore(CallbackInfo ci) {
      if (this.bedrocknative$bridging) {
         this.bedrocknative$bridging = false;
         this.hitResult = this.bedrocknative$crosshair;
         this.bedrocknative$crosshair = null;
      }
   }
}
