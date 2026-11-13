package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

public record MountPlan(List<MountPlan.Entry> slots, int columns) {
   public static final int CHEST_ROWS = 3;
   public static final int MAX_COLUMNS = 9;

   public static boolean opensWindow(int windowType, int windowId, boolean alreadyOpen) {
      return windowType == ContainerType.HORSE.getId() && windowId > 0 && !alreadyOpen;
   }

   public static MountPlan of(boolean chested, boolean carpet, int contentSize) {
      List<MountPlan.Entry> slots = new ArrayList<>();
      int chest = chested ? Math.max(0, contentSize - 1) : 0;
      int columns = Math.min(chest / 3, 9);
      if (!chested && contentSize >= 2) {
         slots.add(new MountPlan.Entry(MountPlan.Kind.EQUIP, 0));
         slots.add(new MountPlan.Entry(MountPlan.Kind.EQUIP, 1));
      } else {
         slots.add(carpet ? new MountPlan.Entry(MountPlan.Kind.INACTIVE, 0) : new MountPlan.Entry(MountPlan.Kind.EQUIP, 0));
         slots.add(carpet ? new MountPlan.Entry(MountPlan.Kind.EQUIP, 0) : new MountPlan.Entry(MountPlan.Kind.INACTIVE, 0));
      }

      for (int index = 0; index < columns * 3; index++) {
         slots.add(new MountPlan.Entry(MountPlan.Kind.CHEST, index + 1));
      }

      return new MountPlan(List.copyOf(slots), columns);
   }

   public record Entry(MountPlan.Kind kind, int slot) {
   }

   public enum Kind {
      EQUIP,
      CHEST,
      INACTIVE;
   }
}
