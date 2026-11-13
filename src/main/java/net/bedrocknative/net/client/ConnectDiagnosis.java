package net.bedrocknative.net.client;

import io.netty.channel.ConnectTimeoutException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Locale;
import net.bedrocknative.net.BedrockPing;
import net.bedrocknative.protocol.BedrockVersion;
import org.jspecify.annotations.Nullable;

public final class ConnectDiagnosis {
   private ConnectDiagnosis() {
   }

   public static boolean worthAnotherTry(@Nullable Throwable failure, @Nullable BedrockPing.Pong pong, RakHandshakeTrace trace) {
      return failure != null && (pong != null || trace.pongs() > 0) && timedOut(failure) && !trace.answered();
   }

   public static boolean bindFailed(@Nullable Throwable failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
         if (cause instanceof BindException) {
            return true;
         }
      }

      return false;
   }

   public static ConnectDiagnosis.Explanation explain(
      Throwable failure, String host, InetSocketAddress address, boolean pinged, @Nullable BedrockPing.Pong pong, RakHandshakeTrace trace
   ) {
      String where = host
         + " ("
         + (address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress())
         + ":"
         + address.getPort()
         + ")";
      StringBuilder text = new StringBuilder();
      if (pinged && pong == null && trace.pongs() > 0) {
         text.append(where)
            .append(" answered the server-list ping late")
            .append(timedOut(failure) ? " and did not accept the connection in time" : " and the connection failed");
      } else if (pinged && pong == null) {
         text.append("No Bedrock server answers at ")
            .append(where)
            .append(", not even the server-list ping: the address may be wrong (a website rather than the game server) or the server is offline");
      } else if (pong != null) {
         text.append(where)
            .append(" answers the server-list ping (")
            .append(pong.protocol() > 0 ? "protocol " + pong.protocol() : "no protocol")
            .append(pong.version().isBlank() ? "" : ", " + pong.version())
            .append(", ")
            .append(pong.players())
            .append('/')
            .append(pong.maxPlayers())
            .append(" players, ")
            .append(pong.latencyMillis())
            .append(" ms)")
            .append(timedOut(failure) ? " but did not accept the connection in time" : " but the connection failed");
         int ours = BedrockVersion.codec().getProtocolVersion();
         if (pong.protocol() > 200 && pong.protocol() != ours) {
            text.append(String.format(Locale.ROOT, "; it announces protocol %d, this client speaks %d", pong.protocol(), ours));
         }
      } else {
         text.append(timedOut(failure) ? "No answer in time from " : "Could not connect to ").append(where);
      }

      return new ConnectDiagnosis.Explanation(text.toString(), trace.summary());
   }

   static boolean timedOut(Throwable failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
         if (cause instanceof ConnectTimeoutException) {
            return true;
         }
      }

      return false;
   }

   public record Explanation(String headline, String handshake) {
      public String full() {
         return this.handshake.isEmpty() ? this.headline : this.headline + "; " + this.handshake;
      }
   }
}
