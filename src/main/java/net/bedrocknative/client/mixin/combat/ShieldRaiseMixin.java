package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IsUsingItem.class)
public abstract class ShieldRaiseMixin {
   @ModifyReturnValue(method = "get", at = @At("RETURN"))
   private boolean bedrocknative$sneakBlocking(boolean original, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) LivingEntity owner) {
      if (!original
         && BedrockMovement.active()
         && owner instanceof LocalPlayer player
         && BedrockLocalPlayer.serverBlocking()
         && stack.has(DataComponents.BLOCKS_ATTACKS)) {
         ItemStack offhand = player.getOffhandItem();
         return stack == offhand || stack == player.getMainHandItem() && !offhand.has(DataComponents.BLOCKS_ATTACKS);
      } else {
         return original;
      }
   }
}
