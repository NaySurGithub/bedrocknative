package net.bedrocknative.net.nethernet;

import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class SignalingRefusedException extends IOException {
   static final Map<Integer, String> NAMES = Map.of(
      1,
      "DestinationNotLoggedIn",
      2,
      "NegotiationTimeout",
      3,
      "WrongTransportVersion",
      4,
      "FailedToCreatePeerConnection",
      5,
      "ICE",
      32,
      "DataChannelClosed",
      35,
      "GenericFailure",
      36,
      "FailedToCreateIdentityAssertion",
      37,
      "IdentityNotAllowed"
   );
   public static final int IDENTITY_NOT_ALLOWED = 37;
   private final int httpStatus;
   private final int code;

   public SignalingRefusedException(int httpStatus, int code, @Nullable String body) {
      super(describe(httpStatus, code, body));
      this.httpStatus = httpStatus;
      this.code = code;
   }

   private static String describe(int httpStatus, int code, @Nullable String body) {
      return code >= 0
         ? "Signaling error " + code + " (" + name(code) + ")"
         : "Signaling refused: HTTP " + httpStatus + (body != null && !body.isBlank() ? ": " + body : "");
   }

   public static String name(int code) {
      return NAMES.getOrDefault(code, "unknown");
   }

   public int httpStatus() {
      return this.httpStatus;
   }

   public int code() {
      return this.code;
   }

   static int code(String body) {
      String trimmed = body.trim();
      if (!trimmed.isEmpty() && trimmed.length() <= 10) {
         for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
               return -1;
            }
         }

         long value = Long.parseLong(trimmed);
         return value > 2147483647L ? -1 : (int)value;
      } else {
         return -1;
      }
   }
}
