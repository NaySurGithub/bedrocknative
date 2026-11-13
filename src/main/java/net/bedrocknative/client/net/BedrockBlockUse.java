package net.bedrocknative.client.net;

import java.util.function.Supplier;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.BlockUseRules;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

public final class BedrockBlockUse {
   private static boolean blockTook;

   private BedrockBlockUse() {
   }

   public static void begin() {
      blockTook = false;
   }

   public static InteractionResult useItemOn(InteractionResult javaResult) {
      if (javaResult.consumesAction()) {
         blockTook = true;
      }

      return javaResult;
   }

   public static InteractionResult useWithoutItem(BlockState state, Supplier<InteractionResult> java) {
      if (!BedrockMovement.active()) {
         return java.get();
      } else {
         BlockUseRules.Rule rule = rule(state);
         if (rule == BlockUseRules.Rule.ITEM) {
            return InteractionResult.PASS;
         } else {
            InteractionResult result = java.get();
            CustomBlockState custom = CarrierBlock.data(state);
            if (BlockUseRules.blockTakes(rule, result.consumesAction(), custom != null && custom.interactable())) {
               blockTook = true;
               return (InteractionResult)(result.consumesAction() ? result : InteractionResult.SUCCESS);
            } else {
               return result;
            }
         }
      }
   }

   static boolean blockTook() {
      return blockTook;
   }

   static BlockUseRules.Rule rule(BlockState state) {
      if (state.getBlock() instanceof CarrierBlock) {
         return BlockUseRules.Rule.JAVA;
      } else {
         BlockMappings mappings = BlockMappings.get();
         String name = mappings.bedrockName(mappings.toBedrock(state));
         return name == null ? BlockUseRules.Rule.JAVA : BlockUseRules.rule(name);
      }
   }
}
