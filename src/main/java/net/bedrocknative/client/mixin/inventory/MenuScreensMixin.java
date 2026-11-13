package net.bedrocknative.client.mixin.inventory;

import net.bedrocknative.client.inventory.JsonUiContainers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MenuScreens.class})
public abstract class MenuScreensMixin {
   @Inject(
      method = {"create"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void bedrocknative$jsonUi(MenuType<?> type, Minecraft minecraft, int containerId, Component title, CallbackInfo ci) {
      if (JsonUiContainers.open(type, minecraft, containerId, title)) {
         ci.cancel();
      }
   }
}
