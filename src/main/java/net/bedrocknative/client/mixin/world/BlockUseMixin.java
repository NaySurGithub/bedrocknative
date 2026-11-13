package net.bedrocknative.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.net.BedrockBlockUse;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class BlockUseMixin {
   @Inject(method = "performUseItemOn", at = @At("HEAD"))
   private void bedrocknative$beginBlockUse(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir) {
      BedrockBlockUse.begin();
   }

   @WrapOperation(
      method = "performUseItemOn",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
      )
   )
   private InteractionResult bedrocknative$blockUseWithItem(
      BlockState state, ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original
   ) {
      return BedrockBlockUse.useItemOn((InteractionResult)original.call(new Object[]{state, stack, level, player, hand, hit}));
   }

   @WrapOperation(
      method = "performUseItemOn",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/state/BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
      )
   )
   private InteractionResult bedrocknative$blockUse(BlockState state, Level level, Player player, BlockHitResult hit, Operation<InteractionResult> original) {
      return BedrockBlockUse.useWithoutItem(state, () -> (InteractionResult)original.call(new Object[]{state, level, player, hit}));
   }
}
