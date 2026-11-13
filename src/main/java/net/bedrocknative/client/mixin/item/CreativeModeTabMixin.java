package net.bedrocknative.client.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CreativeModeTab.class})
public abstract class CreativeModeTabMixin {
   @Shadow
   private Collection<ItemStack> displayItems;
   @Shadow
   private Set<ItemStack> displayItemsSearchTab;

   @Inject(
      method = {"buildContents"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$bedrockContents(ItemDisplayParameters parameters, CallbackInfo ci) {
      List<List<ItemStack>> bedrock = BedrockCreativeTabs.contents((CreativeModeTab)this);
      if (bedrock != null) {
         this.displayItems = new ArrayList<>(bedrock.get(0));
         this.displayItemsSearchTab = ItemStackLinkedSet.createTypeAndComponentsSet();
         this.displayItemsSearchTab.addAll(bedrock.get(1));
      }
   }

   @ModifyReturnValue(
      method = {"getDisplayName"},
      at = {@At("RETURN")}
   )
   private Component bedrocknative$bedrockName(Component original) {
      Component name = BedrockCreativeTabs.name((CreativeModeTab)this);
      return name != null ? name : original;
   }

   @ModifyReturnValue(
      method = {"getIconItem"},
      at = {@At("RETURN")}
   )
   private ItemStack bedrocknative$bedrockIcon(ItemStack original) {
      ItemStack icon = BedrockCreativeTabs.icon((CreativeModeTab)this);
      return icon != null ? icon : original;
   }
}
