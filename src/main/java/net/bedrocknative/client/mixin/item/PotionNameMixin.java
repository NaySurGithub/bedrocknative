package net.bedrocknative.client.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TippedArrowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({PotionItem.class, TippedArrowItem.class})
public abstract class PotionNameMixin {
   @ModifyReturnValue(method = "getName(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"))
   private Component bedrocknative$itemName(Component original, ItemStack stack) {
      if (!BedrockMovement.active()) {
         return original;
      }

      Component name = stack.hasNonDefault(DataComponents.ITEM_NAME) ? stack.get(DataComponents.ITEM_NAME) : null;
      return name != null ? name : original;
   }
}
