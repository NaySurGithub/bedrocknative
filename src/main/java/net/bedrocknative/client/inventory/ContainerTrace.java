package net.bedrocknative.client.inventory;

import net.bedrocknative.BedrockNative;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

public final class ContainerTrace {
   private static volatile boolean on;
   private static final long START = System.currentTimeMillis();

   private ContainerTrace() {
   }

   public static void enable() {
      on = true;
   }

   public static boolean on() {
      return on;
   }

   public static void in(BedrockPacket packet) {
      if (on) {
         switch (packet) {
            case UpdateBlockPacket block:
               line("S->C", "UpdateBlock " + block.getBlockPosition() + " layer " + block.getDataLayer());
               break;
            case BlockEntityDataPacket data:
               line("S->C", "BlockEntityData " + data.getBlockPosition() + " " + data.getData());
               break;
            case ContainerOpenPacket open:
               line(
                  "S->C", "ContainerOpen id " + open.getId() + " " + open.getType() + " at " + open.getBlockPosition() + " entity " + open.getUniqueEntityId()
               );
               break;
            case ContainerClosePacket close:
               line("S->C", "ContainerClose id " + close.getId() + " " + close.getType() + " serverInitiated " + close.isServerInitiated());
               break;
            case InventoryContentPacket content:
               line("S->C", "InventoryContent container " + content.getContainerId() + " " + content.getContents().size() + " slots");
               break;
            case NetworkStackLatencyPacket latency:
               line("S->C", "NetworkStackLatency " + latency.getTimestamp());
               break;
            case ModalFormRequestPacket form:
               line("S->C", "ModalFormRequest id " + form.getFormId());
               break;
            default:
         }
      }
   }

   public static void out(BedrockPacket packet) {
      if (on) {
         switch (packet) {
            case ContainerClosePacket close:
               line("C->S", "ContainerClose id " + close.getId() + " " + close.getType() + " serverInitiated " + close.isServerInitiated());
               break;
            case PacketViolationWarningPacket warning:
               line("C->S", "PacketViolationWarning " + warning.getType() + " " + warning.getSeverity() + " cause " + warning.getPacketCauseId());
               break;
            case NetworkStackLatencyPacket latency:
               line("C->S", "NetworkStackLatency " + latency.getTimestamp());
               break;
            default:
         }
      }
   }

   public static void client(String what) {
      if (on) {
         line("client", what);
      }
   }

   private static void line(String direction, String text) {
      BedrockNative.LOGGER.info("Dev chests trace +{} ms {} {}", new Object[]{System.currentTimeMillis() - START, direction, text});
   }
}
