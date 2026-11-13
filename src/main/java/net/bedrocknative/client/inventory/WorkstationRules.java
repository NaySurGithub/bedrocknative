package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.Builder;
import org.jspecify.annotations.Nullable;

public final class WorkstationRules {
   public static final int TOO_EXPENSIVE = 40;
   private static final String ENCHANTED_BOOK = "minecraft:enchanted_book";
   private static final String BOOK = "minecraft:book";
   private static final int MAX_BANNER_PATTERNS = 6;

   private WorkstationRules() {
   }

   @Nullable
   public static WorkstationRules.Anvil anvil(ItemData left, ItemData right, @Nullable String name, boolean creative, WorkstationRules.Facts facts) {
      if (left != null && !left.isNull()) {
         NbtMapBuilder tag = builder(left.getTag());
         int baseCost = repairCost(left) + (right != null && !right.isNull() ? repairCost(right) : 0);
         int price = 0;
         int materialUsed = 0;
         int max = facts.maxDamage(left);
         if (right != null && !right.isNull()) {
            boolean book = right.getDefinition().getIdentifier().equals("minecraft:enchanted_book");
            if (max > 0 && facts.repairs(left, right)) {
               int damage = damage(left);
               int unit = Math.min(damage, max / 4);
               if (unit <= 0) {
                  return null;
               }

               while (unit > 0 && materialUsed < right.getCount()) {
                  damage -= unit;
                  price++;
                  materialUsed++;
                  unit = Math.min(damage, max / 4);
               }

               tag.putInt("Damage", damage);
            } else {
               if (!book && (!sameItem(left, right) || max <= 0)) {
                  return null;
               }

               if (max > 0 && !book) {
                  int remaining = max - damage(left) + max - damage(right) + max * 12 / 100;
                  int damage = Math.max(0, max - remaining);
                  if (damage < damage(left)) {
                     tag.putInt("Damage", damage);
                     price += 2;
                  }
               }

               Map<Integer, Integer> enchantments = enchantments(left);
               boolean anyCompatible = false;
               boolean anyIncompatible = false;
               boolean targetIsBook = left.getDefinition().getIdentifier().equals("minecraft:enchanted_book");

               for (Entry<Integer, Integer> added : enchantments(right).entrySet()) {
                  int id = added.getKey();
                  int current = enchantments.getOrDefault(id, 0);
                  int level = current == added.getValue() ? current + 1 : Math.max(current, added.getValue());
                  boolean compatible = targetIsBook || facts.canEnchant(id, left);

                  for (int other : enchantments.keySet()) {
                     if (other != id && !facts.compatible(id, other)) {
                        compatible = false;
                        price++;
                     }
                  }

                  if (!compatible) {
                     anyIncompatible = true;
                  } else {
                     anyCompatible = true;
                     level = Math.min(level, facts.maxLevel(id));
                     enchantments.put(id, level);
                     int fee = facts.anvilCost(id);
                     if (book) {
                        fee = Math.max(1, fee / 2);
                     }

                     price += fee * Math.max(0, level - current);
                  }
               }

               if (anyIncompatible && !anyCompatible) {
                  return null;
               }

               putEnchantments(tag, enchantments);
               materialUsed = 1;
            }
         }

         int renameCost = 0;
         String custom = customName(left);
         if (name != null && (name.isBlank() ? custom != null : !name.equals(custom))) {
            renameCost = 1;
            NbtMapBuilder display = builder(left.getTag() == null ? null : left.getTag().getCompound("display", null));
            if (name.isBlank()) {
               display.remove("Name");
            } else {
               display.putString("Name", name);
            }

            if (display.isEmpty()) {
               tag.remove("display");
            } else {
               tag.putCompound("display", display.build());
            }

            price += renameCost;
         }

         if (price <= 0) {
            return null;
         } else {
            boolean onlyRenaming = renameCost == price;
            int cost = baseCost + price;
            if (onlyRenaming && cost >= 40) {
               cost = 39;
            }

            if (cost >= 40 && !creative) {
               return new WorkstationRules.Anvil(null, cost, 0);
            } else {
               int repairCost = Math.max(repairCost(left), right != null && !right.isNull() ? repairCost(right) : 0);
               if (!onlyRenaming) {
                  repairCost = repairCost * 2 + 1;
               }

               tag.putInt("RepairCost", repairCost);
               return new WorkstationRules.Anvil(left.toBuilder().tag(tag.build()).usingNetId(false).netId(0).build(), cost, materialUsed);
            }
         }
      } else {
         return null;
      }
   }

   public static ItemData enchant(ItemData input, EnchantOptionData option, WorkstationRules.Facts facts) {
      Map<Integer, Integer> enchantments = enchantments(input);

      for (List<EnchantData> list : List.of(option.getEnchants0(), option.getEnchants1(), option.getEnchants2())) {
         for (EnchantData enchant : list) {
            enchantments.put(enchant.getType(), enchant.getLevel());
         }
      }

      NbtMapBuilder tag = builder(withDamage(input, facts).getTag());
      putEnchantments(tag, enchantments);
      Builder result = input.toBuilder().count(1).tag(tag.build()).usingNetId(false).netId(0);
      if (input.getDefinition().getIdentifier().equals("minecraft:book")) {
         ItemDefinition book = facts.definition("minecraft:enchanted_book");
         if (book != null) {
            result.definition(book).damage(0);
         }
      }

      return result.build();
   }

   @Nullable
   public static ItemData grindstone(ItemData input, ItemData additional, WorkstationRules.Facts facts) {
      boolean hasInput = input != null && !input.isNull();
      boolean hasAdditional = additional != null && !additional.isNull();
      if ((hasInput || hasAdditional) && (!hasInput || input.getCount() <= 1) && (!hasAdditional || additional.getCount() <= 1)) {
         if (!hasInput || !hasAdditional) {
            ItemData item = hasInput ? input : additional;
            return enchantments(item).isEmpty() ? null : removeNonCurses(item, enchantments(item), builder(item.getTag()), 1, facts);
         } else if (!sameItem(input, additional)) {
            return null;
         } else {
            int max = Math.max(facts.maxDamage(input), facts.maxDamage(additional));
            NbtMapBuilder tag = builder(input.getTag());
            int count = 1;
            if (max > 0) {
               int remaining = facts.maxDamage(input) - damage(input) + facts.maxDamage(additional) - damage(additional) + max * 5 / 100;
               tag.putInt("Damage", Math.max(max - remaining, 0));
            } else {
               if (!input.equals(additional)) {
                  return null;
               }

               count = 2;
            }

            Map<Integer, Integer> enchantments = enchantments(input);
            enchantments(additional).forEach((id, level) -> {
               if (!facts.isCurse(id) || !enchantments.containsKey(id)) {
                  enchantments.merge(id, level, Math::max);
               }
            });
            return removeNonCurses(input, enchantments, tag, count, facts);
         }
      } else {
         return null;
      }
   }

   private static ItemData removeNonCurses(ItemData item, Map<Integer, Integer> enchantments, NbtMapBuilder tag, int count, WorkstationRules.Facts facts) {
      enchantments.keySet().removeIf(id -> !facts.isCurse(id));
      putEnchantments(tag, enchantments);
      int repairCost = 0;

      for (int i = 0; i < enchantments.size(); i++) {
         repairCost = repairCost * 2 + 1;
      }

      tag.putInt("RepairCost", repairCost);
      Builder result = item.toBuilder().count(count).tag(tag.build()).usingNetId(false).netId(0);
      if (enchantments.isEmpty() && item.getDefinition().getIdentifier().equals("minecraft:enchanted_book")) {
         ItemDefinition book = facts.definition("minecraft:book");
         if (book != null) {
            NbtMapBuilder plain = builder(item.getTag());
            plain.remove("ench");
            plain.remove("RepairCost");
            result.definition(book).tag(plain.isEmpty() ? null : plain.build());
         }
      }

      return result.build();
   }

   @Nullable
   public static ItemData loom(ItemData banner, String pattern, int color) {
      if (banner != null && !banner.isNull() && pattern != null) {
         NbtMapBuilder tag = builder(banner.getTag());
         List<NbtMap> patterns = new ArrayList<>(banner.getTag() == null ? List.of() : banner.getTag().getList("Patterns", NbtType.COMPOUND));
         if (patterns.size() >= 6) {
            return null;
         } else {
            patterns.add(NbtMap.builder().putInt("Color", color).putString("Pattern", pattern).build());
            tag.putList("Patterns", NbtType.COMPOUND, patterns);
            return banner.toBuilder().count(1).tag(tag.build()).usingNetId(false).netId(0).build();
         }
      } else {
         return null;
      }
   }

   public static ItemData smithingTransform(ItemData base, ItemData result) {
      return result.toBuilder().count(1).damage(0).tag(base.getTag()).usingNetId(false).netId(0).build();
   }

   public static ItemData smithingTrim(ItemData base, String pattern, String material) {
      NbtMapBuilder tag = builder(base.getTag());
      tag.putCompound("Trim", NbtMap.builder().putString("Material", material).putString("Pattern", pattern).build());
      return base.toBuilder().count(1).tag(tag.build()).usingNetId(false).netId(0).build();
   }

   public static ItemData withDamage(ItemData item, WorkstationRules.Facts facts) {
      if (item != null && !item.isNull() && facts.maxDamage(item) > 0 && (item.getTag() == null || !item.getTag().containsKey("Damage"))) {
         NbtMapBuilder tag = NbtMap.builder().putInt("Damage", 0);
         if (item.getTag() != null) {
            tag.putAll(item.getTag());
         }

         return item.toBuilder().tag(tag.build()).build();
      } else {
         return item;
      }
   }

   public static Map<Integer, Integer> enchantments(ItemData item) {
      Map<Integer, Integer> enchantments = new LinkedHashMap<>();
      if (item != null && item.getTag() != null) {
         for (NbtMap entry : item.getTag().getList("ench", NbtType.COMPOUND)) {
            enchantments.put(Integer.valueOf(entry.getShort("id", (short)-1)), Integer.valueOf(entry.getShort("lvl", (short)1)));
         }
      }

      return enchantments;
   }

   public static int damage(ItemData item) {
      return item.getTag() == null ? 0 : item.getTag().getInt("Damage", 0);
   }

   public static int repairCost(ItemData item) {
      return item.getTag() == null ? 0 : item.getTag().getInt("RepairCost", 0);
   }

   @Nullable
   public static String customName(ItemData item) {
      NbtMap display = item.getTag() == null ? null : item.getTag().getCompound("display", null);
      String name = display == null ? null : display.getString("Name", null);
      return name != null && !name.isEmpty() ? name : null;
   }

   private static boolean sameItem(ItemData a, ItemData b) {
      return a.getDefinition().getRuntimeId() == b.getDefinition().getRuntimeId();
   }

   private static void putEnchantments(NbtMapBuilder tag, Map<Integer, Integer> enchantments) {
      if (enchantments.isEmpty()) {
         tag.remove("ench");
      } else {
         List<NbtMap> list = new ArrayList<>();
         enchantments.forEach((id, level) -> list.add(NbtMap.builder().putShort("id", (short)id.intValue()).putShort("lvl", (short)level.intValue()).build()));
         tag.putList("ench", NbtType.COMPOUND, list);
      }
   }

   private static NbtMapBuilder builder(@Nullable NbtMap tag) {
      return tag == null ? NbtMap.builder() : tag.toBuilder();
   }

   public record Anvil(@Nullable ItemData result, int cost, int materialUsed) {
   }

   public interface Facts {
      int maxDamage(ItemData var1);

      boolean repairs(ItemData var1, ItemData var2);

      int maxLevel(int var1);

      int anvilCost(int var1);

      boolean canEnchant(int var1, ItemData var2);

      boolean compatible(int var1, int var2);

      boolean isCurse(int var1);

      @Nullable
      ItemDefinition definition(String var1);
   }
}
