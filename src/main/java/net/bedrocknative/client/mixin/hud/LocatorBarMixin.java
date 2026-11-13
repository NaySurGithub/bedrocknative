package net.bedrocknative.client.mixin.hud;

import net.bedrocknative.client.ui.JsonUiHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LocatorBar.class})
public abstract class LocatorBarMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$locatorDots(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (JsonUiHud.owns() && !JsonUiHud.drawingLocator()) {
         ci.cancel();
      }
   }
}
