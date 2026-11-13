package net.bedrocknative.client.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bedrocknative.client.net.LoadingScreenHold;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LevelLoadTracker.class})
public abstract class LevelLoadTrackerMixin {
   @ModifyReturnValue(
      method = {"isLevelReady"},
      at = {@At("RETURN")}
   )
   private boolean bedrocknative$holdForBedrock(boolean ready) {
      return LoadingScreenHold.filter(ready);
   }
}
