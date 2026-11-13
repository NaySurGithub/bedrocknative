package net.bedrocknative.client.mixin.inventory;

import net.bedrocknative.client.ui.JsonUiScreens;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiInventoryScreenMixin {
   @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
   private @Nullable Screen bedrocknative$jsonUiInventory(@Nullable Screen screen) {
      return screen == null ? null : JsonUiScreens.swap(screen);
   }
}
