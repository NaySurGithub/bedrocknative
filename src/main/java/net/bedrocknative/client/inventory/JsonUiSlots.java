package net.bedrocknative.client.inventory;

import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

public interface JsonUiSlots {
   @Nullable Slot slotAt(double var1, double var3);
}
