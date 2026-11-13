package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public record TradeOffer(
   int netId,
   NbtMap buyA,
   int buyCountA,
   NbtMap buyB,
   int buyCountB,
   NbtMap sell,
   int uses,
   int maxUses,
   int tier,
   int traderExp,
   float priceMultiplierA,
   int demand
) {
   private static final int ANY_DATA = 32767;

   public static List<TradeOffer> parse(NbtMap offers) {
      List<TradeOffer> parsed = new ArrayList<>();
      if (offers == null) {
         return parsed;
      }

      for (NbtMap recipe : offers.getList("Recipes", NbtType.COMPOUND)) {
         parsed.add(
            new TradeOffer(
               recipe.getInt("netId", 0),
               recipe.getCompound("buyA"),
               recipe.getInt("buyCountA", 0),
               recipe.getCompound("buyB"),
               recipe.getInt("buyCountB", 0),
               recipe.getCompound("sell"),
               recipe.getInt("uses", 0),
               recipe.getInt("maxUses", 0),
               recipe.getInt("tier", 0),
               recipe.getInt("traderExp", 0),
               recipe.getFloat("priceMultiplierA", 0.0F),
               recipe.getInt("demand", 0)
            )
         );
      }

      return parsed;
   }

   public static List<Integer> tierExp(NbtMap offers) {
      List<Integer> exp = new ArrayList<>();
      if (offers != null) {
         for (NbtMap tier : offers.getList("TierExpRequirements", NbtType.COMPOUND)) {
            exp.add(tier.getInt(String.valueOf(exp.size()), 0));
         }
      }

      return exp;
   }

   public int priceA() {
      return count(this.buyA);
   }

   public int priceB() {
      return count(this.buyB);
   }

   public boolean hasB() {
      return isItem(this.buyB);
   }

   public boolean outOfStock() {
      return this.uses >= this.maxUses;
   }

   public int sellCount() {
      return count(this.sell);
   }

   public static @Nullable ItemData item(NbtMap item, Function<String, ItemDefinition> definitions) {
      if (!isItem(item)) {
         return null;
      }

      ItemDefinition definition = definitions.apply(item.getString("Name"));
      if (definition == null) {
         return null;
      }

      int data = item.getShort("Damage", (short)0);
      NbtMap tag = item.getCompound("tag", null);
      return ItemData.builder()
         .definition(definition)
         .damage(data == 32767 ? 0 : data)
         .count(count(item))
         .tag(tag != null && !tag.isEmpty() ? tag : null)
         .build();
   }

   public static boolean isItem(NbtMap item) {
      String name = item == null ? "" : item.getString("Name", "");
      return !name.isEmpty() && !name.equals("minecraft:air") && count(item) > 0;
   }

   private static int count(NbtMap item) {
      return item == null ? 0 : item.getByte("Count", (byte)0) & 0xFF;
   }
}
