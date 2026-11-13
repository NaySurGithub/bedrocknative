package net.bedrocknative.net.nethernet;

import java.net.SocketTimeoutException;
import net.bedrocknative.net.transport.ProbeResult;
import net.bedrocknative.net.transport.TransportChooser;
import net.bedrocknative.net.transport.TransportUnavailableException;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.jspecify.annotations.Nullable;

public final class NetherNetDiagnosis {
   private NetherNetDiagnosis() {
   }

   public static NetherNetDiagnosis.Explanation probe(ProbeResult probe, int port) {
      String headline = switch (probe.reason()) {
         case REFUSED, TIMEOUT -> "Nothing answers on TCP port " + port + " (not a NetherNet server)";
         case HTTP_STATUS -> "The server does not speak NetherNet (HTTP " + probe.httpStatus() + ")";
         case TLS_CERTIFICATE -> "The server's HTTPS certificate is not valid (no plain HTTP is tried after a certificate)";
         case TLS_DOWNGRADE -> "This server used HTTPS before and now offers only plain HTTP";
         case TOO_LARGE -> "The server's NetherNet status is larger than a status can be";
         case UNAVAILABLE -> "NetherNet is not available in this build";
         case ERROR -> "The server's NetherNet signaling could not be read";
         case OK -> "The server speaks NetherNet";
      };
      return new NetherNetDiagnosis.Explanation(headline, probe.toString());
   }

   public static NetherNetDiagnosis.Explanation explain(Throwable failure, @Nullable ProbeResult probe) {
      String detail = (probe == null ? "" : probe + "; ") + describeChain(failure);
      UntrustedServerException untrusted = UntrustedServerException.in(failure);
      if (untrusted != null) {
         return new NetherNetDiagnosis.Explanation(
            untrusted.keyChanged()
               ? "The server's key changed (sha256 " + untrusted.shortDigest() + ", was " + untrusted.shortPreviousDigest() + ")"
               : "The server's key (sha256 " + untrusted.shortDigest() + ") is not trusted yet",
            detail
         );
      }

      SignalingRefusedException refused = find(failure, SignalingRefusedException.class);
      if (refused != null) {
         return refused.code() == 37
            ? new NetherNetDiagnosis.Explanation("Signaling error 37 (IdentityNotAllowed): the server refused our Xbox identity", detail)
            : new NetherNetDiagnosis.Explanation(refused.getMessage(), detail);
      }

      if (find(failure, ServerIdentityException.class) != null) {
         return new NetherNetDiagnosis.Explanation("The server's identity could not be verified", detail);
      }

      NetherNetConnectException connect = NetherNetConnectException.in(failure);
      if (connect == null) {
         return new NetherNetDiagnosis.Explanation("The NetherNet connection failed", detail);
      }

      return switch (connect.stage()) {
         case UNAVAILABLE -> new NetherNetDiagnosis.Explanation("NetherNet is not available on this system (native library)", detail);
         case OFFER -> new NetherNetDiagnosis.Explanation("NetherNet could not start on this computer (WebRTC made no offer)", detail);
         case SIGNALING -> find(failure, SocketTimeoutException.class) == null && connect.getCause() != null
            ? new NetherNetDiagnosis.Explanation("The server's NetherNet signaling failed", detail)
            : new NetherNetDiagnosis.Explanation("The server's NetherNet signaling did not answer in time", detail);
         case LINK -> new NetherNetDiagnosis.Explanation(
            "Reached the server, but no UDP path (firewall or NAT): candidates offered "
               + Math.max(0, connect.offeredCandidates())
               + ", answered "
               + Math.max(0, connect.answerCandidates()),
            detail
         );
      };
   }

   public static NetherNetDiagnosis.Explanation forJoin(@Nullable ProbeResult probe, @Nullable Throwable failure, int port) {
      if (failure != null) {
         TransportUnavailableException missing = find(failure, TransportUnavailableException.class);
         return missing != null ? new NetherNetDiagnosis.Explanation(missing.getMessage(), probe == null ? "" : probe.toString()) : explain(failure, probe);
      } else if (probe != null && !probe.supported()) {
         return probe(probe, port);
      } else {
         return probe != null
            ? new NetherNetDiagnosis.Explanation("The NetherNet connection failed", probe.toString())
            : probe(ProbeResult.unavailable(), port);
      }
   }

   public static boolean serverAnswered(Throwable failure) {
      if (UntrustedServerException.in(failure) == null && find(failure, ServerIdentityException.class) == null) {
         NetherNetConnectException connect = NetherNetConnectException.in(failure);
         return connect != null && connect.stage() == NetherNetConnectException.Stage.LINK;
      } else {
         return true;
      }
   }

   public static TransportChooser.JoinFailure kind(Throwable failure) {
      if (UntrustedServerException.in(failure) != null) {
         return TransportChooser.JoinFailure.NETHERNET_UNTRUSTED;
      }

      NetherNetConnectException connect = NetherNetConnectException.in(failure);
      return (connect == null || connect.stage() != NetherNetConnectException.Stage.UNAVAILABLE) && find(failure, TransportUnavailableException.class) == null
         ? TransportChooser.JoinFailure.NETHERNET_LINK
         : TransportChooser.JoinFailure.UNAVAILABLE;
   }

   public static @Nullable DisconnectFailReason reason(Throwable failure) {
      SignalingRefusedException refused = find(failure, SignalingRefusedException.class);
      if (refused != null) {
         return refused.code() == 37 ? DisconnectFailReason.NETHER_NET_IDENTITY_NOT_ALLOWED : DisconnectFailReason.CONN_CLIENT_SIGNALING_ERROR;
      }

      NetherNetConnectException connect = NetherNetConnectException.in(failure);
      if (connect == null) {
         return null;
      }

      return switch (connect.stage()) {
         case UNAVAILABLE -> DisconnectFailReason.UNSUPPORTED_TRANSPORT;
         case OFFER -> DisconnectFailReason.CANT_CONNECT;
         case SIGNALING -> connect.getCause() != null && find(failure, SocketTimeoutException.class) == null
            ? DisconnectFailReason.CANT_CONNECT
            : DisconnectFailReason.CONN_NEGOTIATION_TIMEOUT_WAITING_FOR_RESPONSE;
         case LINK -> DisconnectFailReason.CONN_ICE;
      };
   }

   private static <T extends Throwable> @Nullable T find(@Nullable Throwable failure, Class<T> type) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
         if (type.isInstance(cause)) {
            return type.cast(cause);
         }
      }

      return null;
   }

   private static String describeChain(Throwable failure) {
      StringBuilder text = new StringBuilder();

      for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
         if (!text.isEmpty()) {
            text.append(" <- ");
         }

         text.append(SignalingHttp.describe(cause));
         if (text.length() > 600) {
            break;
         }
      }

      return text.toString();
   }

   public record Explanation(String headline, String detail) {
      public String full() {
         return this.detail.isEmpty() ? this.headline : this.headline + "; " + this.detail;
      }
   }
}
