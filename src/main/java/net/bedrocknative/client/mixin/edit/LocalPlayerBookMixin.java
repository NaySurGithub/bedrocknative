package net.bedrocknative.client.mixin.edit;

import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LocalPlayer.class})
public abstract class LocalPlayerBookMixin {
   @Shadow
   @Final
   protected Minecraft minecraft;

   @Inject(
      method = {"openItemGui"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$readWrittenBook(ItemStack itemStack, InteractionHand hand, CallbackInfo ci) {
      if (BedrockMovement.active() && itemStack.is(Items.WRITTEN_BOOK)) {
         BookAccess access = BookAccess.fromItem(itemStack);
         if (access != null) {
            this.minecraft.gui.setScreen(new BookViewScreen(access));
            ci.cancel();
         }
      }
   }
}
