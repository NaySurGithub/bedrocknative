package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.world.state.MountPlan;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;

final class MountLayout {
   private static final int INACTIVE = -1;

   private MountLayout() {
   }

   static MountLayout.Mount of(AbstractHorse mount, int containerId, int size) {
      MountPlan plan = MountPlan.of(mount instanceof AbstractChestedHorse, mount instanceof Llama, size);
      List<BedrockSlot> slots = new ArrayList<>();

      for (MountPlan.Entry entry : plan.slots()) {
         slots.add(switch (entry.kind()) {
            case EQUIP -> new BedrockSlot(containerId, entry.slot(), ContainerSlotType.HORSE_EQUIP);
            case CHEST -> new BedrockSlot(containerId, entry.slot(), ContainerSlotType.LEVEL_ENTITY);
            case INACTIVE -> new BedrockSlot(containerId, -1, ContainerSlotType.HORSE_EQUIP);
         });
      }

      return new MountLayout.Mount(new ContainerLayout(null, List.copyOf(slots)), plan.columns());
   }

   record Mount(ContainerLayout layout, int columns) {
   }
}
