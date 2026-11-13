package net.bedrocknative.client.ui;

import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public interface ItemSource {
   ItemStack stack(UiControl var1);

   default int seed(UiControl renderer) {
      return 0;
   }

   default boolean draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      return false;
   }
}
