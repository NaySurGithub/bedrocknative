package net.bedrocknative.client.mixin.hud;

import net.bedrocknative.client.hud.BedrockHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.cloudburstmc.protocol.bedrock.data.HudElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ExperienceBar.class})
public abstract class ExperienceBarMixin {
   @Inject(
      method = {"extractBackground"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$progressBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (BedrockHud.hides(HudElement.PROGRESS_BAR)) {
         ci.cancel();
      }
   }
}
