package net.bedrocknative.world.item;

import java.util.Map;

public final class LegacyItemIds {
   public static final int ANY_DATA = 32767;
   private static final Map<Integer, String> IDENTIFIERS = Map.of(
      58,
      "minecraft:crafting_table",
      264,
      "minecraft:diamond",
      265,
      "minecraft:iron_ingot",
      266,
      "minecraft:gold_ingot",
      388,
      "minecraft:emerald",
      742,
      "minecraft:netherite_ingot"
   );

   private LegacyItemIds() {
   }

   public static String identifier(int idAux) {
      return IDENTIFIERS.get(idAux >> 16);
   }

   public static int data(int idAux) {
      int data = idAux & 65535;
      return data == 32767 ? 0 : data;
   }
}
