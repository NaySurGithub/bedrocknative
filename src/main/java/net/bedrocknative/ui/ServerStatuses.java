package net.bedrocknative.ui;

import net.bedrocknative.net.BedrockPing;
import net.bedrocknative.net.transport.JavaStatus;
import net.bedrocknative.net.transport.ProbeResult;
import org.jspecify.annotations.Nullable;

public final class ServerStatuses {
   private ServerStatuses() {
   }

   public static PlayData.Status of(BedrockPing.@Nullable Pong pong, @Nullable ProbeResult netherNet, @Nullable JavaStatus java) {
      if (pong != null) {
         return PlayData.Status.online(pong.motd(), pong.players(), pong.maxPlayers(), pong.latencyMillis());
      } else if (netherNet != null && netherNet.supported()) {
         ProbeResult.Status status = netherNet.status() == null ? ProbeResult.Status.EMPTY : netherNet.status();
         return PlayData.Status.online(status.name(), status.players(), status.maxPlayers(), netherNet.rttMillis());
      } else {
         return java != null && java.answered()
            ? PlayData.Status.online(oneLine(java.motd()), java.online(), java.max(), java.latencyMillis())
            : PlayData.Status.OFFLINE;
      }
   }

   static String oneLine(String motd) {
      return String.join(" ", motd.lines().map(String::strip).filter(line -> !line.isEmpty()).toList());
   }
}
