package net.bedrocknative.client.mixin.rules;

import net.bedrocknative.client.rules.BedrockInputLocks;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientInput.class})
public abstract class ClientInputLockMixin {
   @Inject(
      method = {"makeJump"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$noJump(CallbackInfo ci) {
      if (BedrockInputLocks.jumpLocked()) {
         ci.cancel();
      }
   }
}
