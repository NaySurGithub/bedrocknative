package net.bedrocknative.client.mixin.item;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({CreativeModeInventoryScreen.class})
public interface CreativeScreenAccessor {
   @Invoker("selectTab")
   void bedrocknative$selectTab(CreativeModeTab var1);

   @Invoker("refreshSearchResults")
   void bedrocknative$refreshSearchResults();

   @Accessor("searchBox")
   EditBox bedrocknative$searchBox();

   @Accessor("hasClickedOutside")
   void bedrocknative$hasClickedOutside(boolean var1);

   @Accessor("selectedTab")
   static CreativeModeTab bedrocknative$selectedTab() {
      throw new AssertionError();
   }

   @Accessor("CONTAINER")
   static SimpleContainer bedrocknative$container() {
      throw new AssertionError();
   }
}
