package net.bedrocknative.client.world;

import java.util.List;
import net.bedrocknative.world.item.BedrockItemTags;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity.WobbleStyle;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.jspecify.annotations.Nullable;

public final class BedrockDecoratedPots {
   private static volatile int wobbles;

   private BedrockDecoratedPots() {
   }

   public static int wobbleCount() {
      return wobbles;
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      if (level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot) {
         apply(pot, tag, level.getGameTime());
      }
   }

   static void apply(DecoratedPotBlockEntity pot, NbtMap tag, long gameTime) {
      ItemStack carrier = new ItemStack(Items.DECORATED_POT);
      BedrockItemTags.sherds(carrier, tag.getList("sherds", NbtType.STRING));
      NbtMap held = tag.getCompound("item", NbtMap.EMPTY);
      ItemStack inside = held.isEmpty() ? ItemStack.EMPTY : ItemMappings.fromNbt(held);
      carrier.set(DataComponents.CONTAINER, inside.isEmpty() ? ItemContainerContents.EMPTY : ItemContainerContents.fromItems(List.of(inside)));
      pot.applyComponentsFromItemStack(carrier);
      WobbleStyle style = wobble(tag);
      if (style != null) {
         pot.wobbleStartedAtTick = gameTime;
         pot.lastWobbleStyle = style;
         wobbles++;
      }
   }

   @Nullable
   public static WobbleStyle wobble(NbtMap tag) {
      Number animation;

      return switch (tag.get("animation") instanceof animation ? animation.intValue() : 0) {
         case 1 -> WobbleStyle.NEGATIVE;
         case 2 -> WobbleStyle.POSITIVE;
         default -> null;
      };
   }
}
