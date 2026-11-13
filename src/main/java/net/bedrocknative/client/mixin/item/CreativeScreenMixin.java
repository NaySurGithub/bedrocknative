package net.bedrocknative.client.mixin.item;

import java.util.Collection;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Type;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
   @Shadow
   @Final
   private static SimpleContainer CONTAINER;
   @Shadow
   private static CreativeModeTab selectedTab;
   @Shadow
   private @Nullable Slot destroyItemSlot;

   @Shadow
   private void refreshCurrentTabContents(Collection<ItemStack> displayList) {
      throw new AssertionError();
   }

   @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$bedrockEntry(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput input, CallbackInfo ci) {
      if (slot != null
         && slot.container == CONTAINER
         && slot != this.destroyItemSlot
         && selectedTab.getType() != Type.INVENTORY
         && BedrockCreativeTabs.active()) {
         ItemStack clicked = slot.getItem();
         if (!BedrockCreativeTabs.isHeader(clicked)) {
            BedrockCreativeTabs.pick(clicked);
         } else {
            boolean carrying = !((CreativeModeInventoryScreen)(Object)this).getMenu().getCarried().isEmpty();
            if (!carrying || input != ContainerInput.PICKUP) {
               if (!carrying && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
                  BedrockCreativeTabs.toggle(clicked, selectedTab);
                  this.refreshCurrentTabContents(selectedTab.getDisplayItems());
               }

               ci.cancel();
            }
         }
      }
   }
}
