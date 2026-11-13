package net.bedrocknative.net.client;

import java.util.List;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.SwingSource;

public final class CombatPackets {
   public static final int ENTITY_INTERACT = 0;
   public static final int ENTITY_ATTACK = 1;
   public static final int USE_AS_ATTACK = 3;

   private CombatPackets() {
   }

   public static InventoryTransactionPacket onEntity(int actionType, long target, int hotbarSlot, ItemData held, Vector3f eye, Vector3f click) {
      return onEntity(actionType, target, hotbarSlot, held, eye, click, HandSlot.MAINHAND);
   }

   public static InventoryTransactionPacket onEntity(int actionType, long target, int hotbarSlot, ItemData held, Vector3f eye, Vector3f click, HandSlot hand) {
      InventoryTransactionPacket use = new InventoryTransactionPacket();
      use.setTransactionType(InventoryTransactionType.ITEM_USE_ON_ENTITY);
      use.setActionType(actionType);
      use.setRuntimeEntityId(target);
      use.setHotbarSlot(hotbarSlot);
      use.setHand(hand);
      use.setItemInHand(held);
      use.setPlayerPosition(eye);
      use.setClickPosition(click);
      return use;
   }

   public static AnimatePacket swing(long runtimeId, SwingSource source) {
      return swing(runtimeId, source, HandSlot.MAINHAND);
   }

   public static AnimatePacket swing(long runtimeId, SwingSource source, HandSlot hand) {
      AnimatePacket animate = new AnimatePacket();
      animate.setAction(Action.SWING_ARM);
      animate.setRuntimeEntityId(runtimeId);
      animate.setSwingSource(source);
      animate.setHand(hand);
      return animate;
   }

   public static List<BedrockPacket> attack(long self, long target, int hotbarSlot, ItemData held, Vector3f eye, Vector3f click) {
      return List.of(swing(self, SwingSource.ATTACK), onEntity(1, target, hotbarSlot, held, eye, click), swing(self, SwingSource.ATTACK));
   }
}
