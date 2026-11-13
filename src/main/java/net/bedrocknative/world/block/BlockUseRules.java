package net.bedrocknative.world.block;

import java.util.List;
import java.util.Set;

public final class BlockUseRules {
   static final Set<String> BLOCK_TAKES = Set.of(
      "minecraft:crafting_table",
      "minecraft:anvil",
      "minecraft:chipped_anvil",
      "minecraft:damaged_anvil",
      "minecraft:enchanting_table",
      "minecraft:furnace",
      "minecraft:lit_furnace",
      "minecraft:blast_furnace",
      "minecraft:lit_blast_furnace",
      "minecraft:smoker",
      "minecraft:lit_smoker",
      "minecraft:smithing_table",
      "minecraft:loom",
      "minecraft:stonecutter_block",
      "minecraft:cartography_table",
      "minecraft:grindstone",
      "minecraft:brewing_stand",
      "minecraft:beacon",
      "minecraft:crafter",
      "minecraft:chest",
      "minecraft:trapped_chest",
      "minecraft:ender_chest",
      "minecraft:barrel",
      "minecraft:hopper",
      "minecraft:dispenser",
      "minecraft:dropper",
      "minecraft:bed",
      "minecraft:lever",
      "minecraft:noteblock",
      "minecraft:unpowered_repeater",
      "minecraft:powered_repeater",
      "minecraft:unpowered_comparator",
      "minecraft:powered_comparator",
      "minecraft:daylight_detector",
      "minecraft:daylight_detector_inverted",
      "minecraft:dragon_egg",
      "minecraft:wooden_door",
      "minecraft:trapdoor",
      "minecraft:fence_gate"
   );
   static final List<String> BLOCK_TAKES_SUFFIXES = List.of("_shulker_box", "copper_chest", "_door", "_trapdoor", "_fence_gate", "_button");
   static final Set<String> NOT_BY_HAND = Set.of("minecraft:iron_door", "minecraft:iron_trapdoor");
   static final Set<String> ITEM_TAKES = Set.of("minecraft:redstone_wire");

   private BlockUseRules() {
   }

   public static BlockUseRules.Rule rule(String bedrockName) {
      if (ITEM_TAKES.contains(bedrockName)) {
         return BlockUseRules.Rule.ITEM;
      } else if (BLOCK_TAKES.contains(bedrockName)) {
         return BlockUseRules.Rule.BLOCK;
      } else {
         if (bedrockName.startsWith("minecraft:") && !NOT_BY_HAND.contains(bedrockName)) {
            for (String suffix : BLOCK_TAKES_SUFFIXES) {
               if (bedrockName.endsWith(suffix)) {
                  return BlockUseRules.Rule.BLOCK;
               }
            }
         }

         return BlockUseRules.Rule.JAVA;
      }
   }

   public static boolean blockTakes(BlockUseRules.Rule rule, boolean javaTook, boolean customInteract) {
      return switch (rule) {
         case JAVA -> javaTook || customInteract;
         case BLOCK -> true;
         case ITEM -> false;
      };
   }

   public static boolean clickInTheAirFollows(boolean swung, boolean repeat, boolean blockTook, boolean holdsSomething) {
      return swung && !repeat && !blockTook && holdsSomething;
   }

   public static enum Rule {
      JAVA,
      BLOCK,
      ITEM;
   }
}
