package net.bedrocknative.client.mixin.inventory;

import net.bedrocknative.client.net.ClientBedrockSession;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractContainerScreen.class})
public abstract class ContainerScreenDragMixin {
   @Unique
   private boolean bedrocknative$cursorDrag;

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$dragStart(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
      if (!this.bedrocknative$cursorDrag && event.button() == 0 && !((AbstractContainerScreen)this).getMenu().getCarried().isEmpty()) {
         ClientBedrockSession session = ClientBedrockSession.active();
         if (session != null) {
            session.cursorDrag(true);
            this.bedrocknative$cursorDrag = true;
         }
      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$dragStop(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
      this.bedrocknative$stopDrag();
   }

   @Inject(
      method = {"removed"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$removed(CallbackInfo ci) {
      this.bedrocknative$stopDrag();
   }

   @Unique
   private void bedrocknative$stopDrag() {
      if (this.bedrocknative$cursorDrag) {
         this.bedrocknative$cursorDrag = false;
         ClientBedrockSession session = ClientBedrockSession.active();
         if (session != null) {
            session.cursorDrag(false);
         }
      }
   }
}
