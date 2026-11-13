package net.bedrocknative.client.mixin.rules;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.rules.BedrockInputLocks;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({MouseHandler.class})
public abstract class MouseHandlerLockMixin {
   @WrapOperation(
      method = {"turnPlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
      )}
   )
   private void bedrocknative$cameraLock(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
      if (!BedrockInputLocks.cameraLocked()) {
         original.call(new Object[]{player, yaw, pitch});
      }
   }
}
