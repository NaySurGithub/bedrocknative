package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

public record GrindstoneVariant(String name, GrindstoneVariant.Id id, GrindstoneVariant.Cost cost, boolean deprecatedResults, boolean consumeFirst) {
   public static final int REQUEST_ID = Integer.MIN_VALUE;
   public static final GrindstoneVariant DEFAULT = of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.RESULT, false, false);
   public static final List<GrindstoneVariant> SEARCH;
   public static final List<GrindstoneVariant> ALL;
   private static volatile GrindstoneVariant active;

   private static GrindstoneVariant of(GrindstoneVariant.Id id, GrindstoneVariant.Cost cost, boolean deprecated, boolean consumeFirst) {
      String name = id.name().toLowerCase().replace('_', '-')
         + "-id"
         + (cost == GrindstoneVariant.Cost.RESULT ? "" : "-cost-" + cost.name().toLowerCase())
         + (consumeFirst ? "-consume-first" : "")
         + (deprecated ? "-dep" : "");
      return new GrindstoneVariant(name, id, cost, deprecated, consumeFirst);
   }

   public static GrindstoneVariant active() {
      return active;
   }

   public static void override(GrindstoneVariant variant) {
      active = variant != null ? variant : fromProperty();
   }

   public static GrindstoneVariant named(String name) {
      return ALL.stream().filter(variant -> variant.name.equals(name)).findFirst().orElse(null);
   }

   private static GrindstoneVariant fromProperty() {
      String name = System.getProperty("bedrocknative.grindstoneVariant");
      if (name != null && !name.isEmpty()) {
         GrindstoneVariant variant = named(name);
         if (variant == null) {
            BedrockNative.LOGGER.warn("Unknown grindstone variant '{}', using {}", name, DEFAULT.name);
            return DEFAULT;
         } else {
            return variant;
         }
      } else {
         return DEFAULT;
      }
   }

   int idFor(ItemData input, ItemData additional, int repairItemRecipe) {
      return switch (this.id) {
         case INPUT -> !input.isNull() ? input.getNetId() : additional.getNetId();
         case ADDITIONAL -> !additional.isNull() ? additional.getNetId() : input.getNetId();
         case REQUEST -> Integer.MIN_VALUE;
         case ZERO -> 0;
         case ONE -> 1;
         case REPAIR_ITEM -> repairItemRecipe;
      };
   }

   int costFor(ItemData result, ItemData input, ItemData additional) {
      return switch (this.cost) {
         case RESULT -> WorkstationRules.repairCost(result);
         case INPUT -> WorkstationRules.repairCost(!input.isNull() ? input : additional);
         case ZERO -> 0;
         case ONE -> 1;
         case TWO -> 2;
         case FIVE -> 5;
      };
   }

   @Override
   public String toString() {
      return this.name;
   }

   static {
      List<GrindstoneVariant> search = new ArrayList<>();

      for (boolean deprecated : new boolean[]{false, true}) {
         search.add(of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.RESULT, deprecated, false));
         search.add(of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.ONE, deprecated, false));
         search.add(of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.TWO, deprecated, false));
         if (!deprecated) {
            search.add(of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.FIVE, false, false));
         }

         search.add(of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.RESULT, deprecated, true));
         search.add(of(GrindstoneVariant.Id.REQUEST, GrindstoneVariant.Cost.RESULT, deprecated, false));
         search.add(of(GrindstoneVariant.Id.REPAIR_ITEM, GrindstoneVariant.Cost.RESULT, deprecated, false));
         search.add(of(GrindstoneVariant.Id.ONE, GrindstoneVariant.Cost.RESULT, deprecated, false));
         search.add(of(GrindstoneVariant.Id.ZERO, GrindstoneVariant.Cost.RESULT, deprecated, false));
      }

      SEARCH = List.copyOf(search);
      List<GrindstoneVariant> all = new ArrayList<>(search);

      for (boolean deprecated : new boolean[]{false, true}) {
         all.add(of(GrindstoneVariant.Id.ADDITIONAL, GrindstoneVariant.Cost.RESULT, deprecated, false));
         all.add(of(GrindstoneVariant.Id.INPUT, GrindstoneVariant.Cost.INPUT, deprecated, false));
      }

      ALL = List.copyOf(all);
      active = fromProperty();
   }

   public enum Cost {
      RESULT,
      INPUT,
      ZERO,
      ONE,
      TWO,
      FIVE;
   }

   public enum Id {
      INPUT,
      ADDITIONAL,
      REQUEST,
      ZERO,
      ONE,
      REPAIR_ITEM;
   }
}
