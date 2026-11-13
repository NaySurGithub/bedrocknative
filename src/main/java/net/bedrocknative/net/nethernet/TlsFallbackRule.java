package net.bedrocknative.net.nethernet;

import net.bedrocknative.net.transport.SignalingScheme;
import org.jspecify.annotations.Nullable;

public final class TlsFallbackRule {
   static final int ALERT = 21;
   static final int HANDSHAKE = 22;
   static final int HANDSHAKE_FAILURE = 40;
   static final int PROTOCOL_VERSION = 70;

   private TlsFallbackRule() {
   }

   public static TlsFallbackRule.Decision afterFailedHandshake(boolean certificatePresented, TlsFallbackRule.PeerReply reply, @Nullable SignalingScheme pinned) {
      if (certificatePresented) {
         return TlsFallbackRule.Decision.CERTIFICATE_REFUSED;
      } else if (reply == TlsFallbackRule.PeerReply.OTHER_TLS) {
         return TlsFallbackRule.Decision.TLS_FAILED;
      } else {
         return pinned == SignalingScheme.HTTPS ? TlsFallbackRule.Decision.DOWNGRADE_REFUSED : TlsFallbackRule.Decision.PLAIN_HTTP;
      }
   }

   public static TlsFallbackRule.PeerReply classify(byte[] first, int count) {
      if (count <= 0) {
         return TlsFallbackRule.PeerReply.NOTHING;
      } else {
         int type = first[0] & 255;
         if (type >= 20 && type <= 24 && (count <= 1 || (first[1] & 255) == 3)) {
            if (type == 21 && count >= 7) {
               int level = first[5] & 255;
               int description = first[6] & 255;
               if (level == 2 && (description == 40 || description == 70)) {
                  return TlsFallbackRule.PeerReply.REFUSAL_ALERT;
               }
            }

            return TlsFallbackRule.PeerReply.OTHER_TLS;
         } else {
            return TlsFallbackRule.PeerReply.NOT_TLS;
         }
      }
   }

   public static enum Decision {
      PLAIN_HTTP,
      CERTIFICATE_REFUSED,
      DOWNGRADE_REFUSED,
      TLS_FAILED;
   }

   public static enum PeerReply {
      NOTHING,
      NOT_TLS,
      REFUSAL_ALERT,
      OTHER_TLS;
   }
}
