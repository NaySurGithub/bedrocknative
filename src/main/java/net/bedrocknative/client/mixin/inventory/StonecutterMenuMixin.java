package net.bedrocknative.client.mixin.inventory;

import net.bedrocknative.client.net.ClientBedrockSession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.StonecutterMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
   @Inject(method = "clickMenuButton", at = @At("TAIL"))
   private void bedrocknative$recipeSelected(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
      if ((Boolean)cir.getReturnValue()) {
         ClientBedrockSession session = ClientBedrockSession.active();
         if (session != null) {
            session.stonecutterSelection();
         }
      }
   }
}
