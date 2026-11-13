package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Item.class})
public abstract class ShieldMixin {
   @WrapOperation(
      method = {"use"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z"
      )}
   )
   private boolean bedrocknative$noUseBlocking(ItemStack stack, DataComponentType<?> type, Operation<Boolean> original, @Local(argsOnly = true) Level level) {
      return type == DataComponents.BLOCKS_ATTACKS && level.isClientSide() && BedrockMovement.active()
         ? false
         : (Boolean)original.call(new Object[]{stack, type});
   }
}
