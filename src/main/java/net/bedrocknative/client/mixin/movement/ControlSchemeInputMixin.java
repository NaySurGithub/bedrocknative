package net.bedrocknative.client.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.camera.BedrockCamera;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientInput.class)
public abstract class ControlSchemeInputMixin {
   @Shadow
   protected Vec2 moveVector;

   @ModifyReturnValue(method = "getMoveVector", at = @At("RETURN"))
   private Vec2 bedrocknative$controlScheme(Vec2 move) {
      return BedrockCamera.controlMove(move);
   }

   @ModifyReturnValue(method = "hasForwardImpulse", at = @At("RETURN"))
   private boolean bedrocknative$anyMoveIsForward(boolean forward) {
      return forward || this.moveVector.lengthSquared() > 0.0F && BedrockCamera.cameraRelativeControls();
   }
}
