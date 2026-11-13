package net.bedrocknative.client.mixin.inventory;

import net.bedrocknative.client.inventory.JsonUiSlots;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractContainerScreen.class})
public abstract class ContainerScreenSlotMixin {
   @Inject(
      method = {"getHoveredSlot"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$jsonUiSlot(double x, double y, CallbackInfoReturnable<Slot> cir) {
      if (this instanceof JsonUiSlots screen) {
         cir.setReturnValue(screen.slotAt(x, y));
      }
   }
}
