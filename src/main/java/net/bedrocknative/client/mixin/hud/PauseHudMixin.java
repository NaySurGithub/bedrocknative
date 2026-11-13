package net.bedrocknative.client.mixin.hud;

import net.bedrocknative.client.ui.JsonUiPauseScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Hud.class})
public abstract class PauseHudMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void bedrocknative$noHudUnderThePauseMenu(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (JsonUiPauseScreen.hidesHud()) {
         ci.cancel();
      }
   }
}
