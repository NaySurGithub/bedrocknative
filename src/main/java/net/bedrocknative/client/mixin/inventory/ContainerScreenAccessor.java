package net.bedrocknative.client.mixin.inventory;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({AbstractContainerScreen.class})
public interface ContainerScreenAccessor {
   @Accessor("quickCraftingType")
   int bedrocknative$quickCraftingType();

   @Accessor("quickCraftingRemainder")
   int bedrocknative$quickCraftingRemainder();

   @Invoker("onStopHovering")
   void bedrocknative$onStopHovering(Slot var1);
}
