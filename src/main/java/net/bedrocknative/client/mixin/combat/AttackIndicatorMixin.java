package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Hud.class})
public abstract class AttackIndicatorMixin {
   @WrapOperation(
      method = {"extractCrosshair"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"
      )}
   )
   private Object bedrocknative$noAttackIndicator(OptionInstance<?> option, Operation<Object> original) {
      return BedrockMovement.active() ? AttackIndicatorStatus.OFF : original.call(new Object[]{option});
   }
}
