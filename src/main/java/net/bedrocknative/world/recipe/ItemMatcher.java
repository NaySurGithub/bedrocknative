package net.bedrocknative.world.recipe;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;

@FunctionalInterface
public interface ItemMatcher {
   boolean matches(ItemDescriptor var1, ItemData var2);
}
