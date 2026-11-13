package net.bedrocknative.client.mixin.model;

import net.bedrocknative.client.model.BedrockPlayers;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({InventoryScreen.class})
public abstract class InventoryPaperdollMixin {
   @Inject(
      method = {"extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"},
      at = {@At("HEAD")}
   )
   private static void bedrocknative$paperdollStart(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> info) {
      BedrockPlayers.paperdoll(true);
   }

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"},
      at = {@At("RETURN")}
   )
   private static void bedrocknative$paperdollEnd(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> info) {
      BedrockPlayers.paperdoll(false);
   }
}
