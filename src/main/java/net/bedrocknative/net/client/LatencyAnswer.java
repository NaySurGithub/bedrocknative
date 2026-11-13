package net.bedrocknative.net.client;

import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;

public final class LatencyAnswer {
   private LatencyAnswer() {
   }

   public static NetworkStackLatencyPacket to(NetworkStackLatencyPacket ping) {
      NetworkStackLatencyPacket answer = new NetworkStackLatencyPacket();
      answer.setTimestamp(ping.getTimestamp() * 1000000L);
      answer.setFromServer(true);
      return answer;
   }
}
