package net.bedrocknative.net.transport;

import net.bedrocknative.net.BedrockPing;
import org.jspecify.annotations.Nullable;

public enum PingOutcome {
   PONG,
   SILENT,
   NOT_PINGED;

   public static PingOutcome of(@Nullable BedrockPing.Located located) {
      if (located == null) {
         return NOT_PINGED;
      } else {
         return located.pong() == null ? SILENT : PONG;
      }
   }
}
