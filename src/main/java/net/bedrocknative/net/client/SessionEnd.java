package net.bedrocknative.net.client;

import java.net.UnknownHostException;
import java.util.Locale;
import net.bedrocknative.net.nethernet.NetherNetDiagnosis;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.Status;
import org.jspecify.annotations.Nullable;

public record SessionEnd(SessionEnd.Source source, DisconnectFailReason reason, String message, boolean skipScreen, String detail) {
   public SessionEnd(SessionEnd.Source source, DisconnectFailReason reason, String message, boolean skipScreen, String detail) {
      message = message == null ? "" : message;
      detail = detail == null ? "" : detail;
      this.source = source;
      this.reason = reason;
      this.message = message;
      this.skipScreen = skipScreen;
      this.detail = detail;
   }

   public static SessionEnd disconnectPacket(@Nullable DisconnectFailReason reason, boolean skipped, @Nullable CharSequence message) {
      return disconnectPacket(reason, skipped, message, null);
   }

   public static SessionEnd disconnectPacket(
      @Nullable DisconnectFailReason reason, boolean skipped, @Nullable CharSequence message, @Nullable CharSequence filtered
   ) {
      DisconnectFailReason known = reason == null ? DisconnectFailReason.UNKNOWN : reason;
      String text = skipped ? "" : (filtered != null && !filtered.isEmpty() ? filtered.toString() : (message == null ? "" : message.toString()));
      return new SessionEnd(SessionEnd.Source.SERVER, known, text, skipped, "Disconnect packet " + known);
   }

   public static SessionEnd loginFailed(Status status) {
      DisconnectFailReason reason = switch (status) {
         case LOGIN_FAILED_CLIENT_OLD -> DisconnectFailReason.OUTDATED_CLIENT;
         case LOGIN_FAILED_SERVER_OLD -> DisconnectFailReason.OUTDATED_SERVER;
         case LOGIN_FAILED_INVALID_TENANT -> DisconnectFailReason.INVALID_TENANT;
         case LOGIN_FAILED_EDITION_MISMATCH_EDU_TO_VANILLA -> DisconnectFailReason.EDITION_MISMATCH_EDU_TO_VANILLA;
         case LOGIN_FAILED_EDITION_MISMATCH_VANILLA_TO_EDU -> DisconnectFailReason.EDITION_MISMATCH_VANILLA_TO_EDU;
         case FAILED_SERVER_FULL_SUB_CLIENT -> DisconnectFailReason.SERVER_FULL;
         case EDITOR_TO_VANILLA_MISMATCH -> DisconnectFailReason.EDITOR_MISMATCH_EDITOR_TO_VANILLA;
         case VANILLA_TO_EDITOR_MISMATCH -> DisconnectFailReason.EDITOR_MISMATCH_VANILLA_TO_EDITOR;
         default -> DisconnectFailReason.UNKNOWN;
      };
      return new SessionEnd(SessionEnd.Source.LOGIN, reason, "", false, "Login rejected: " + status);
   }

   public static SessionEnd connection(String reason) {
      String key = reason.trim().toLowerCase(Locale.ROOT);

      DisconnectFailReason mapped = switch (key) {
         case "disconnect.timeout", "timed_out", "timed out" -> DisconnectFailReason.TIMEOUT;
         case "disconnect.removed", "bad_packet" -> DisconnectFailReason.BAD_PACKET;
         case "disconnect.disconnected", "disconnected" -> DisconnectFailReason.DISCONNECTED;
         case "nethernet.data_channel_closed" -> DisconnectFailReason.NETHER_NET_DATA_CHANNEL_CLOSED;
         case "nethernet.link_failed" -> DisconnectFailReason.CONN_ICE;
         default -> DisconnectFailReason.CONNECTION_LOST;
      };
      return new SessionEnd(SessionEnd.Source.CONNECTION, mapped, "", false, reason);
   }

   public static SessionEnd connectFailed(Throwable cause, @Nullable String explanation) {
      SessionEnd end = connectFailed(cause);
      return explanation != null && !explanation.isBlank()
         ? new SessionEnd(end.source(), end.reason(), end.message(), end.skipScreen(), end.detail() + " - " + explanation)
         : end;
   }

   public static SessionEnd connectFailed(Throwable cause) {
      Throwable root = cause;

      while (root.getCause() != null && root.getCause() != root && !(root instanceof UnknownHostException)) {
         root = root.getCause();
      }

      boolean unknownHost = root instanceof UnknownHostException || cause instanceof UnknownHostException;
      String text = root.getMessage() == null ? root.getClass().getSimpleName() : root.getClass().getSimpleName() + ": " + root.getMessage();
      DisconnectFailReason netherNet = unknownHost ? null : NetherNetDiagnosis.reason(cause);
      DisconnectFailReason reason = unknownHost ? DisconnectFailReason.SERVER_NOT_FOUND : (netherNet != null ? netherNet : DisconnectFailReason.CANT_CONNECT);
      return new SessionEnd(SessionEnd.Source.CONNECT_FAILED, reason, "", false, text);
   }

   public static SessionEnd client(DisconnectFailReason reason, String detail) {
      return new SessionEnd(SessionEnd.Source.CLIENT, reason, "", false, detail);
   }

   public static SessionEnd left(String detail) {
      return new SessionEnd(SessionEnd.Source.LEFT, DisconnectFailReason.USER_LEAVE_GAME_ATTEMPTED, "", true, detail);
   }

   public boolean showsScreen() {
      return !this.skipScreen && this.source != SessionEnd.Source.LEFT;
   }

   public int reasonCode() {
      return this.reason.ordinal();
   }

   public String logText() {
      StringBuilder text = new StringBuilder(this.reason.name());
      if (!this.message.isEmpty()) {
         text.append(": ").append(this.message);
      }

      if (!this.detail.isEmpty()) {
         text.append(" (").append(this.detail).append(')');
      }

      return text.toString();
   }

   public static enum Source {
      SERVER,
      LOGIN,
      CONNECTION,
      CONNECT_FAILED,
      CLIENT,
      LEFT;
   }
}
