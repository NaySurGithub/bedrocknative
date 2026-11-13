package net.bedrocknative.client.inventory;

import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.item.BedrockItemTags;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class BedrockItemTooltips {
   private static final int LOCK_IN_SLOT = 1;
   private static final int LOCK_IN_INVENTORY = 2;

   private BedrockItemTooltips() {
   }

   public static void register() {
      ItemTooltipCallback.EVENT.register((ItemTooltipCallback)(stack, context, flag, lines) -> {
         CompoundTag data = BedrockItemTags.bedrockData(stack);
         if (!data.isEmpty()) {
            switch (data.getByteOr("item_lock", (byte)0)) {
               case 1:
                  lines.add(text("item.itemLock.popupNotice.cantMove", ChatFormatting.GOLD));
                  break;
               case 2:
                  lines.add(text("item.itemLock.popupNotice.cantDrop", ChatFormatting.GOLD));
            }

            if (data.getBooleanOr("keep_on_death", false)) {
               lines.add(text("item.itemLock.keepOnDeath", ChatFormatting.GRAY));
            }
         }
      });
   }

   private static Component text(String key, ChatFormatting color) {
      String text = BedrockText.lookupKey(key);
      return Component.literal(text == null ? key : text.replaceAll(":[a-z_]+: ?", "")).withStyle(color);
   }
}
