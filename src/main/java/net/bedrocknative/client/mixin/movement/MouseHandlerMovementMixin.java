package net.bedrocknative.client.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({MouseHandler.class})
public abstract class MouseHandlerMovementMixin {
   @WrapOperation(
      method = {"onScroll"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Abilities;setFlyingSpeed(F)V"
      )}
   )
   private void bedrocknative$serverFlySpeed(Abilities abilities, float speed, Operation<Void> original) {
      if (!BedrockMovement.active()) {
         original.call(new Object[]{abilities, speed});
      }
   }
}
