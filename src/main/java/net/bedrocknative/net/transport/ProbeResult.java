package net.bedrocknative.net.transport;

import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public record ProbeResult(
   boolean supported,
   @Nullable SignalingScheme scheme,
   int httpStatus,
   ProbeResult.@Nullable Status status,
   List<SignalingScheme> tried,
   ProbeResult.Reason reason,
   long rttMillis
) {
   public ProbeResult {
      tried = List.copyOf(tried);
   }

   public static ProbeResult unavailable() {
      return new ProbeResult(false, null, 0, null, List.of(), ProbeResult.Reason.UNAVAILABLE, 0L);
   }

   public static ProbeResult failed(ProbeResult.Reason reason, List<SignalingScheme> tried, long millis) {
      return new ProbeResult(false, null, 0, null, tried, reason, millis);
   }

   public static ProbeResult answered(SignalingScheme scheme, int httpStatus, ProbeResult.@Nullable Status status, List<SignalingScheme> tried, long millis) {
      boolean ok = httpStatus >= 200 && httpStatus <= 299;
      return new ProbeResult(
         ok,
         ok ? scheme : null,
         httpStatus,
         ok ? (status == null ? ProbeResult.Status.EMPTY : status) : null,
         tried,
         ok ? ProbeResult.Reason.OK : ProbeResult.Reason.HTTP_STATUS,
         millis
      );
   }

   public boolean nothingListens() {
      return this.reason == ProbeResult.Reason.REFUSED || this.reason == ProbeResult.Reason.TIMEOUT;
   }

   @Override
   public String toString() {
      String schemes = this.tried.stream().map(SignalingScheme::id).collect(Collectors.joining(", ", "[", "]"));
      if (!this.supported) {
         return "NetherNet probe: no ("
            + this.reason
            + (this.httpStatus > 0 ? " " + this.httpStatus : "")
            + ") after "
            + this.rttMillis
            + " ms, tried "
            + schemes;
      }

      String body = this.status != null && !this.status.empty()
         ? "\""
            + this.status.name()
            + "\" protocol "
            + this.status.protocol()
            + (this.status.countsKnown() ? ", " + this.status.players() + "/" + this.status.maxPlayers() + " players" : "")
         : "empty status";
      return "NetherNet probe: "
         + (this.scheme == null ? "?" : this.scheme.id())
         + " "
         + this.httpStatus
         + " ("
         + body
         + ") in "
         + this.rttMillis
         + " ms, tried "
         + schemes;
   }

   public enum Reason {
      OK,
      REFUSED,
      TIMEOUT,
      HTTP_STATUS,
      TLS_CERTIFICATE,
      TLS_DOWNGRADE,
      TOO_LARGE,
      UNAVAILABLE,
      ERROR;
   }

   public record Status(String name, int protocol, String version, String level, int players, int maxPlayers, String gameType) {
      public static final ProbeResult.Status EMPTY = new ProbeResult.Status("", 0, "", "", -1, -1, "");

      public boolean empty() {
         return this.equals(EMPTY);
      }

      public boolean countsKnown() {
         return this.players >= 0 && this.maxPlayers >= 0;
      }
   }
}
