package net.bedrocknative.client.mixin.rules;

import net.bedrocknative.client.rules.BedrockInputLocks;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardInput.class})
public abstract class KeyboardInputLockMixin extends ClientInput {
   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$locks(CallbackInfo ci) {
      if (BedrockInputLocks.any()) {
         Input keys = BedrockInputLocks.filter(this.keyPresses);
         this.keyPresses = keys;
         this.moveVector = new Vec2(impulse(keys.left(), keys.right()), impulse(keys.forward(), keys.backward())).normalized();
      }
   }

   private static float impulse(boolean positive, boolean negative) {
      return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
   }
}
