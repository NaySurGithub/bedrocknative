package net.bedrocknative.client.mixin.rules;

import net.bedrocknative.client.rules.BedrockAdventureSettings;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackRulesMixin {
   @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$immutableWorld(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
      if (BedrockAdventureSettings.blocksBuilding(context.getPlayer())
         && !((ItemStack)(Object)this).canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), context.getClickedPos(), false))) {
         cir.setReturnValue(InteractionResult.PASS);
      }
   }
}
