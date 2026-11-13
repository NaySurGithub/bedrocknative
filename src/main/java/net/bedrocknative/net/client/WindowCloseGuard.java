package net.bedrocknative.net.client;

import org.cloudburstmc.protocol.bedrock.data.PacketViolationSeverity;
import org.cloudburstmc.protocol.bedrock.data.PacketViolationType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;

public final class WindowCloseGuard {
   public static final int CAUSE_ID = 156;
   public static final String CONTEXT = "Server sent a ContainerClosePacket response packet when it should have sent the server initiated packet";
   private static final int NONE = Integer.MIN_VALUE;
   private int deferred = Integer.MIN_VALUE;
   private int answersDue;
   private int violations;
   private int answers;
   private int kept;

   public static boolean unaskedResponse(boolean serverInitiated, boolean windowOpen) {
      return !serverInitiated && windowOpen;
   }

   public PacketViolationWarningPacket defer(int windowId) {
      this.deferred = windowId;
      this.answersDue++;
      this.violations++;
      return violation();
   }

   public boolean reopened(int windowId) {
      if (this.deferred != windowId) {
         return false;
      } else {
         this.deferred = Integer.MIN_VALUE;
         return true;
      }
   }

   public void keep() {
      this.kept++;
   }

   public WindowCloseGuard.Settled endOfTick() {
      if (this.answersDue == 0 && this.deferred == Integer.MIN_VALUE) {
         return WindowCloseGuard.Settled.NOTHING;
      } else {
         WindowCloseGuard.Settled settled = new WindowCloseGuard.Settled(this.answersDue, this.deferred == Integer.MIN_VALUE ? null : this.deferred);
         this.answers = this.answers + this.answersDue;
         this.answersDue = 0;
         this.deferred = Integer.MIN_VALUE;
         return settled;
      }
   }

   public void reset() {
      this.deferred = Integer.MIN_VALUE;
      this.answersDue = 0;
   }

   public int violations() {
      return this.violations;
   }

   public int answers() {
      return this.answers;
   }

   public int kept() {
      return this.kept;
   }

   public static PacketViolationWarningPacket violation() {
      PacketViolationWarningPacket packet = new PacketViolationWarningPacket();
      packet.setType(PacketViolationType.UNKNOWN);
      packet.setSeverity(PacketViolationSeverity.WARNING);
      packet.setPacketCauseId(156);
      packet.setContext("Server sent a ContainerClosePacket response packet when it should have sent the server initiated packet");
      return packet;
   }

   public static ContainerClosePacket answer() {
      ContainerClosePacket packet = new ContainerClosePacket();
      packet.setId((byte)-1);
      packet.setType(ContainerType.NONE);
      packet.setServerInitiated(false);
      return packet;
   }

   public record Settled(int answers, Integer closeWindow) {
      static final WindowCloseGuard.Settled NOTHING = new WindowCloseGuard.Settled(0, null);
   }
}
