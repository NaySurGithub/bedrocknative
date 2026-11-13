package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab.Type;
import net.minecraft.world.item.component.ItemLore;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.jspecify.annotations.Nullable;

public final class BedrockCreativeTabs {
   private static final List<BedrockCreativeTabs.Tab> TABS = List.of(
      new BedrockCreativeTabs.Tab("building_blocks", CreativeItemCategory.CONSTRUCTION, "construction", Items.BRICKS),
      new BedrockCreativeTabs.Tab("colored_blocks", CreativeItemCategory.EQUIPMENT, "equipment", Items.IRON_SWORD),
      new BedrockCreativeTabs.Tab("natural_blocks", CreativeItemCategory.ITEMS, "items", (Item)Items.BED.pick(DyeColor.RED)),
      new BedrockCreativeTabs.Tab("functional_blocks", CreativeItemCategory.NATURE, "nature", Items.GRASS_BLOCK)
   );
   private static final Map<BedrockCreativeTabs.Tab, ItemStack> ICONS = new IdentityHashMap<>();
   private static final Set<Integer> OPEN = new HashSet<>();
   @Nullable
   private static volatile BedrockCreativeTabs.Layout layout;
   @Nullable
   private static CreativeItemData picked;
   private static ItemStack pickedStack = ItemStack.EMPTY;

   private BedrockCreativeTabs() {
   }

   static void apply(List<CreativeItemData> contents, List<CreativeItemGroup> groups, Map<CreativeItemData, ItemStack> java) {
      Map<CreativeItemCategory, List<Object>> rows = new EnumMap<>(CreativeItemCategory.class);
      Map<ItemStack, CreativeItemData> entries = new IdentityHashMap<>();
      Map<ItemStack, BedrockCreativeTabs.Group> headers = new IdentityHashMap<>();
      List<List<ItemStack>> byGroup = new ArrayList<>();

      for (int i = 0; i < groups.size(); i++) {
         byGroup.add(new ArrayList<>());
      }

      List<ItemStack> ungrouped = new ArrayList<>();

      for (CreativeItemData data : contents) {
         ItemStack stack = java.get(data);
         if (stack != null) {
            ItemStack shown = stack.copyWithCount(1);
            entries.put(shown, data);
            (data.getGroupId() >= 0 && data.getGroupId() < groups.size() ? byGroup.get(data.getGroupId()) : ungrouped).add(shown);
         }
      }

      for (int i = 0; i < groups.size(); i++) {
         CreativeItemGroup group = groups.get(i);
         List<ItemStack> members = byGroup.get(i);
         if (!members.isEmpty() && shownCategory(group.getCategory())) {
            List<Object> row = rows.computeIfAbsent(group.getCategory(), key -> new ArrayList<>());
            if (group.getName().isEmpty()) {
               row.addAll(members);
            } else {
               String name = BedrockText.translate(group.getName(), List.of());
               ItemStack header = header(group, members, name);
               BedrockCreativeTabs.Group shown = new BedrockCreativeTabs.Group(i, group.getCategory(), name, header, List.copyOf(members));
               headers.put(header, shown);
               row.add(shown);
            }
         }
      }

      rows.computeIfAbsent(CreativeItemCategory.ITEMS, key -> new ArrayList<>()).addAll(ungrouped);
      List<ItemStack> all = new ArrayList<>();

      for (BedrockCreativeTabs.Tab tab : TABS) {
         all.addAll(flatten(rows.getOrDefault(tab.category(), List.of())));
      }

      OPEN.clear();
      picked = null;
      pickedStack = ItemStack.EMPTY;
      layout = new BedrockCreativeTabs.Layout(rows, entries, headers, List.copyOf(all));
      invalidate();
   }

   public static void clear() {
      if (layout != null) {
         layout = null;
         OPEN.clear();
         picked = null;
         pickedStack = ItemStack.EMPTY;
         invalidate();
      }
   }

   public static boolean active() {
      return layout != null;
   }

   @Nullable
   public static List<List<ItemStack>> contents(CreativeModeTab javaTab) {
      BedrockCreativeTabs.Layout current = layout;
      if (current == null) {
         return null;
      } else if (javaTab.getType() == Type.SEARCH) {
         return List.of(current.all(), current.all());
      } else if (javaTab.getType() != Type.CATEGORY) {
         return null;
      } else {
         BedrockCreativeTabs.Tab tab = tab(javaTab);
         if (tab == null) {
            return List.of(List.of(), List.of());
         } else {
            List<Object> row = current.rows().getOrDefault(tab.category(), List.of());
            return List.of(view(row), flatten(row));
         }
      }
   }

   @Nullable
   public static Component name(CreativeModeTab javaTab) {
      BedrockCreativeTabs.Tab tab = layout == null ? null : tab(javaTab);
      return tab == null ? null : Component.literal(BedrockText.translate("craftingScreen.tab." + tab.langKey(), List.of()));
   }

   public static Component searchName() {
      return Component.literal(BedrockText.translate("craftingScreen.tab.allItems", List.of()));
   }

   @Nullable
   public static ItemStack icon(CreativeModeTab javaTab) {
      BedrockCreativeTabs.Tab tab = layout == null ? null : tab(javaTab);
      return tab == null ? null : ICONS.computeIfAbsent(tab, key -> new ItemStack(key.icon()));
   }

   public static boolean isHeader(ItemStack stack) {
      BedrockCreativeTabs.Layout current = layout;
      return current != null && current.headers().containsKey(stack);
   }

   public static void toggle(ItemStack header, CreativeModeTab javaTab) {
      BedrockCreativeTabs.Layout current = layout;
      BedrockCreativeTabs.Group group = current == null ? null : current.headers().get(header);
      BedrockCreativeTabs.Tab tab = tab(javaTab);
      if (group != null && tab != null) {
         if (!OPEN.remove(group.index())) {
            OPEN.add(group.index());
         }

         Collection<ItemStack> shown = javaTab.getDisplayItems();
         shown.clear();
         shown.addAll(view(current.rows().getOrDefault(tab.category(), List.of())));
      }
   }

   public static boolean isOpen(BedrockCreativeTabs.Group group) {
      return OPEN.contains(group.index());
   }

   public static boolean inOpenGroup(ItemStack shown) {
      BedrockCreativeTabs.Layout current = layout;
      if (current != null && !OPEN.isEmpty()) {
         for (BedrockCreativeTabs.Group group : current.headers().values()) {
            if (OPEN.contains(group.index())) {
               for (ItemStack entry : group.entries()) {
                  if (entry == shown) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void pick(ItemStack shown) {
      BedrockCreativeTabs.Layout current = layout;
      CreativeItemData entry = current == null ? null : current.entries().get(shown);
      if (entry != null) {
         picked = entry;
         pickedStack = shown;
      }
   }

   @Nullable
   public static CreativeItemData picked(ItemStack stack) {
      return picked != null && ItemStack.isSameItemSameComponents(pickedStack, stack) ? picked : null;
   }

   @Nullable
   public static CreativeItemData entry(ItemStack shown) {
      BedrockCreativeTabs.Layout current = layout;
      return current == null ? null : current.entries().get(shown);
   }

   @Nullable
   public static BedrockCreativeTabs.Group group(ItemStack header) {
      BedrockCreativeTabs.Layout current = layout;
      return current == null ? null : current.headers().get(header);
   }

   public static List<ItemStack> entries(CreativeItemCategory category) {
      BedrockCreativeTabs.Layout current = layout;
      return current == null ? List.of() : flatten(current.rows().getOrDefault(category, List.of()));
   }

   public static List<CreativeItemCategory> categories() {
      return TABS.stream().map(BedrockCreativeTabs.Tab::category).toList();
   }

   @Nullable
   public static CreativeModeTab javaTab(CreativeItemCategory category) {
      for (BedrockCreativeTabs.Tab tab : TABS) {
         if (tab.category() == category) {
            return (CreativeModeTab)BuiltInRegistries.CREATIVE_MODE_TAB.getValue(key(tab));
         }
      }

      return null;
   }

   public static String describe() {
      BedrockCreativeTabs.Layout current = layout;
      if (current == null) {
         return "inactive";
      } else {
         List<String> parts = new ArrayList<>();

         for (BedrockCreativeTabs.Tab tab : TABS) {
            List<Object> row = current.rows().getOrDefault(tab.category(), List.of());
            parts.add(
               tab.langKey()
                  + " "
                  + flatten(row).size()
                  + " entries in "
                  + row.stream().filter(BedrockCreativeTabs.Group.class::isInstance).count()
                  + " groups + "
                  + row.stream().filter(ItemStack.class::isInstance).count()
                  + " loose"
            );
         }

         return String.join(", ", parts);
      }
   }

   private static boolean shownCategory(CreativeItemCategory category) {
      return TABS.stream().anyMatch(tab -> tab.category() == category);
   }

   private static ItemStack header(CreativeItemGroup group, List<ItemStack> members, String name) {
      ItemStack icon = group.getIcon() != null && !group.getIcon().isNull() ? ItemMappings.toJava(group.getIcon()) : ItemStack.EMPTY;
      ItemStack header = (icon.isEmpty() ? members.getFirst() : icon).copyWithCount(1);
      header.set(DataComponents.ITEM_NAME, BedrockText.format(name));
      header.remove(DataComponents.CUSTOM_NAME);
      header.set(
         DataComponents.LORE,
         new ItemLore(List.of(Component.literal("+ " + members.size()).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))))
      );
      return header;
   }

   private static List<ItemStack> view(List<Object> row) {
      List<ItemStack> slots = new ArrayList<>();

      for (Object item : row) {
         if (item instanceof BedrockCreativeTabs.Group group) {
            slots.add(group.header());
            if (OPEN.contains(group.index())) {
               slots.addAll(group.entries());
            }
         } else {
            slots.add((ItemStack)item);
         }
      }

      return slots;
   }

   private static List<ItemStack> flatten(List<Object> row) {
      List<ItemStack> stacks = new ArrayList<>();

      for (Object item : row) {
         if (item instanceof BedrockCreativeTabs.Group group) {
            stacks.addAll(group.entries());
         } else {
            stacks.add((ItemStack)item);
         }
      }

      return stacks;
   }

   @Nullable
   private static BedrockCreativeTabs.Tab tab(CreativeModeTab javaTab) {
      ResourceKey<CreativeModeTab> key = (ResourceKey<CreativeModeTab>)BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(javaTab).orElse(null);
      if (key == null) {
         return null;
      } else {
         for (BedrockCreativeTabs.Tab tab : TABS) {
            if (key.equals(key(tab))) {
               return tab;
            }
         }

         return null;
      }
   }

   private static ResourceKey<CreativeModeTab> key(BedrockCreativeTabs.Tab tab) {
      return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(tab.javaTab()));
   }

   private static void invalidate() {
      CreativeModeTabs.CACHED_PARAMETERS = null;
   }

   public record Group(int index, CreativeItemCategory category, String name, ItemStack header, List<ItemStack> entries) {
   }

   private record Layout(
      Map<CreativeItemCategory, List<Object>> rows,
      Map<ItemStack, CreativeItemData> entries,
      Map<ItemStack, BedrockCreativeTabs.Group> headers,
      List<ItemStack> all
   ) {
   }

   private record Tab(String javaTab, CreativeItemCategory category, String langKey, Item icon) {
   }
}
