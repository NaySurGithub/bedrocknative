package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.world.item.ItemStack;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.jspecify.annotations.Nullable;

public final class CreativeItems {
   private final Map<Object, List<CreativeItems.Entry>> byItem = new HashMap<>();
   private List<CreativeItemData> contents = List.of();
   private List<CreativeItemGroup> groups = List.of();
   private @Nullable CreativeItemData lastCreated;

   public void apply(CreativeContentPacket packet) {
      this.byItem.clear();
      this.contents = List.copyOf(packet.getContents());
      this.groups = List.copyOf(packet.getGroups());
      Map<CreativeItemData, ItemStack> shown = new IdentityHashMap<>();

      for (CreativeItemData data : this.contents) {
         ItemStack java = ItemMappings.toJava(data.getItem());
         if (!java.isEmpty()) {
            this.byItem.computeIfAbsent(key(java), key -> new ArrayList<>()).add(new CreativeItems.Entry(data, java));
            shown.put(data, java);
         }
      }

      BedrockCreativeTabs.apply(this.contents, this.groups, shown);
      BedrockNative.LOGGER
         .info(
            "Creative inventory: {} entries in {} groups ({} without a Java item); {}",
            new Object[]{this.contents.size(), this.groups.size(), this.contents.size() - shown.size(), BedrockCreativeTabs.describe()}
         );
   }

   public List<CreativeItemData> contents() {
      return this.contents;
   }

   public List<CreativeItemGroup> groups() {
      return this.groups;
   }

   public @Nullable CreativeItemData entryFor(ItemStack stack) {
      CreativeItemData picked = BedrockCreativeTabs.picked(stack);
      return picked != null ? picked : this.find(stack);
   }

   void created(CreativeItemData entry) {
      this.lastCreated = entry;
   }

   public @Nullable CreativeItemData lastCreated() {
      return this.lastCreated;
   }

   public @Nullable CreativeItemData find(ItemStack stack) {
      List<CreativeItems.Entry> entries = this.byItem.get(key(stack));
      if (entries == null) {
         return null;
      }

      for (CreativeItems.Entry entry : entries) {
         if (ItemStack.isSameItemSameComponents(entry.java(), stack)) {
            return entry.data();
         }
      }

      return entries.size() == 1 ? entries.getFirst().data() : null;
   }

   private static Object key(ItemStack stack) {
      CustomItem custom = ItemMappings.custom(stack);
      return custom != null ? custom.identifier() : stack.getItem();
   }

   private record Entry(CreativeItemData data, ItemStack java) {
   }
}
