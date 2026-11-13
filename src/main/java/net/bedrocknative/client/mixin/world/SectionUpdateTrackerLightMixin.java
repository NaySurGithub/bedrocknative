package net.bedrocknative.client.mixin.world;

import net.bedrocknative.client.world.light.ThreadedLightEngine;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionUpdateTracker.class)
public abstract class SectionUpdateTrackerLightMixin {
   @Inject(method = "hasAllNeighbors", at = @At("HEAD"), cancellable = true, require = 0)
   private void bedrocknative$ownColumnLit(ClientLevel level, long sectionNode, CallbackInfoReturnable<Boolean> cir) {
      if (level.getLightEngine() instanceof ThreadedLightEngine engine && !engine.lightOnInColumn(SectionPos.getZeroNode(sectionNode))) {
         cir.setReturnValue(false);
      }
   }
}
