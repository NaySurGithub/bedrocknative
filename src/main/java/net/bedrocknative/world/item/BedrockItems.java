package net.bedrocknative.world.item;

import net.bedrocknative.BedrockNative;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class BedrockItems {
   public static final ResourceKey<Item> CUSTOM_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("bedrocknative", "bedrock_item"));
   public static final Item CUSTOM = Registry.register(BuiltInRegistries.ITEM, CUSTOM_KEY, new BedrockItems.Carrier(new Properties().setId(CUSTOM_KEY)));

   private BedrockItems() {
   }

   public static void register() {
      BedrockNative.LOGGER.debug("Registered {}", CUSTOM_KEY.identifier());
   }

   private static final class Carrier extends Item {
      Carrier(Properties properties) {
         super(properties);
      }

      private static @Nullable CustomItem held(ItemStack stack) {
         CustomItem custom = stack.has(DataComponents.CONSUMABLE) ? null : ItemMappings.custom(stack);
         return custom != null && custom.holdTicks() > 0 ? custom : null;
      }

      @Override
      public InteractionResult use(Level level, Player player, InteractionHand hand) {
         if (held(player.getItemInHand(hand)) == null) {
            return super.use(level, player, hand);
         }

         player.startUsingItem(hand);
         return InteractionResult.CONSUME;
      }

      @Override
      public int getUseDuration(ItemStack stack, LivingEntity user) {
         CustomItem custom = held(stack);
         return custom != null ? custom.holdTicks() : super.getUseDuration(stack, user);
      }

      @Override
      public ItemUseAnimation getUseAnimation(ItemStack stack) {
         CustomItem custom = held(stack);
         return custom != null ? custom.holdAnimation() : super.getUseAnimation(stack);
      }
   }
}
