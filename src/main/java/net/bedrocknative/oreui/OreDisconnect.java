package net.bedrocknative.oreui;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bedrocknative.net.client.SessionEnd;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;

public record OreDisconnect(
   OreDisconnect.Variant variant,
   int reason,
   String reasonName,
   String title,
   String message,
   String serverName,
   String serverMessage,
   String codeword,
   String errorDetails,
   boolean reconnect
) {
   public static final String ROUTE = "hbui.ConnectionErrorRoute";
   public static final int CLIENT_MISMATCH_REASON = 35;
   private static final Pattern WHOLE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]*(?:\\.[A-Za-z0-9_\\-]+)+");
   private static final Pattern KEY_TOKEN = Pattern.compile("%([A-Za-z][A-Za-z0-9_\\-]*(?:\\.[A-Za-z0-9_\\-]+)+)");
   private static final OreDisconnect.Entry DEFAULT = new OreDisconnect.Entry("disconnectionScreen.title.disconnected", "disconnectionScreen.noReason", true);
   private static final Map<DisconnectFailReason, OreDisconnect.Entry> ENTRIES = new EnumMap<>(DisconnectFailReason.class);

   private static void put(OreDisconnect.Entry entry, DisconnectFailReason... reasons) {
      for (DisconnectFailReason reason : reasons) {
         ENTRIES.put(reason, entry);
      }
   }

   public static OreDisconnect.Entry entry(DisconnectFailReason reason) {
      return ENTRIES.getOrDefault(reason, DEFAULT);
   }

   public static OreDisconnect of(SessionEnd end, String serverName, String address, OreLang lang) {
      DisconnectFailReason reason = end.reason();
      String name = serverName == null ? "" : serverName.strip();
      String shown = end.source() == SessionEnd.Source.SERVER ? serverMessage(end.message(), lang) : "";
      String details = details(end, name, address, lang);
      if (!shown.isBlank()) {
         String title = name.isEmpty()
            ? lang.get("hbui.ConnectionErrorRoute.server.disconnectedFromServer")
            : lang.get("hbui.ConnectionErrorRoute.server.disconnectedFrom", name);
         return new OreDisconnect(OreDisconnect.Variant.SERVER, end.reasonCode(), reason.name(), title, "", name, shown, "", details, false);
      } else if (end.reasonCode() == 35) {
         return new OreDisconnect(
            OreDisconnect.Variant.CLIENT_MISMATCH,
            end.reasonCode(),
            reason.name(),
            lang.get("hbui.ConnectionErrorRoute.client.gameUpdateRequired"),
            lang.get("hbui.ConnectionErrorRoute.client.updateGameMessage"),
            name,
            "",
            "",
            details,
            false
         );
      } else {
         OreDisconnect.Entry entry = entry(reason);
         return new OreDisconnect(
            OreDisconnect.Variant.STANDARD,
            end.reasonCode(),
            reason.name(),
            lang.get(entry.title()),
            lang.get(entry.body()),
            name,
            "",
            "",
            details,
            entry.reconnect()
         );
      }
   }

   public static String serverMessage(String raw, OreLang lang) {
      if (raw != null && !raw.isEmpty()) {
         String text = raw.replace("\r", "");
         String trimmed = text.strip();
         String key = trimmed.startsWith("%") ? trimmed.substring(1) : trimmed;
         if (WHOLE_KEY.matcher(key).matches() && lang.has(key)) {
            text = lang.get(key);
         } else if (text.indexOf(37) >= 0) {
            Matcher tokens = KEY_TOKEN.matcher(text);
            StringBuilder out = new StringBuilder();

            while (tokens.find()) {
               String found = tokens.group(1);
               tokens.appendReplacement(out, Matcher.quoteReplacement(lang.has(found) ? lang.get(found) : tokens.group()));
            }

            tokens.appendTail(out);
            text = out.toString();
         }

         return stripFormatting(text);
      } else {
         return "";
      }
   }

   public static String stripFormatting(String text) {
      if (text.indexOf(167) < 0) {
         return text;
      } else {
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167) {
               i++;
            } else {
               out.append(c);
            }
         }

         return out.toString();
      }
   }

   static String details(SessionEnd end, String serverName, String address, OreLang lang) {
      StringBuilder out = new StringBuilder();
      String code = lang.has("disconnectionScreen.errorCode") ? lang.get("disconnectionScreen.errorCode") : "Error Code:";
      out.append(code).append(' ').append(end.reasonCode()).append(" (").append(end.reason().name()).append(')');
      if (end.source() != SessionEnd.Source.SERVER && !end.detail().isBlank()) {
         String detail = end.detail().strip();
         out.append('\n').append(WHOLE_KEY.matcher(detail).matches() && lang.has(detail) ? lang.get(detail) : detail);
      }

      String where = address == null ? "" : address.strip();
      if (!serverName.isEmpty() && !where.isEmpty()) {
         out.append('\n').append(serverName).append(" (").append(where).append(')');
      } else if (!serverName.isEmpty() || !where.isEmpty()) {
         out.append('\n').append(serverName.isEmpty() ? where : serverName);
      }

      return out.toString();
   }

   public boolean hasDetails() {
      return !this.errorDetails.isEmpty();
   }

   public boolean hasCodeword() {
      return !this.codeword.isEmpty();
   }

   static {
      String t = "disconnectionScreen.title.";
      String b = "disconnectionScreen.body.";
      String s = "disconnectionScreen.";
      put(
         new OreDisconnect.Entry(t + "connectionTimeout", b + "checkConnectionAndTry", true),
         DisconnectFailReason.TIMEOUT,
         DisconnectFailReason.CONN_INACTIVITY_TIMEOUT,
         DisconnectFailReason.CONN_NEGOTIATION_TIMEOUT
      );
      put(new OreDisconnect.Entry(t + "connectionTimeout", s + "timeout", true), DisconnectFailReason.LOADING_STATE_TIMEOUT);
      put(
         new OreDisconnect.Entry(t + "disconnectedFromHost", s + "connectionLost", true),
         DisconnectFailReason.CONNECTION_LOST,
         DisconnectFailReason.HOST_DISCONNECTED,
         DisconnectFailReason.ZOMBIE_CONNECTION,
         DisconnectFailReason.STALE_CONNECTION_BEING_REPLACED
      );
      put(
         new OreDisconnect.Entry(t + "unableToConnect", b + "cantConnectServer", true),
         DisconnectFailReason.CANT_CONNECT,
         DisconnectFailReason.UNSPECIFIED_CLIENT_INSTANCE_DISCONNECTION
      );
      put(
         new OreDisconnect.Entry(t + "serverNotFound", b + "serverNoneOrLocked", true),
         DisconnectFailReason.SERVER_NOT_FOUND,
         DisconnectFailReason.THIRD_PARTY_NO_SERVER_OR_SERVER_LOCKED
      );
      put(new OreDisconnect.Entry(t + "serverNotFound", s + "invalidIP", false), DisconnectFailReason.THIRD_PARTY_BAD_IP);
      put(
         new OreDisconnect.Entry(t + "unableToConnect", s + "noInternet", true),
         DisconnectFailReason.CANT_CONNECT_NO_INTERNET,
         DisconnectFailReason.THIRD_PARTY_NO_INTERNET
      );
      put(new OreDisconnect.Entry(t + "noWifi", s + "nowifi", true), DisconnectFailReason.NO_WIFI);
      put(new OreDisconnect.Entry(t + "errorOccurred", s + "badPacket", true), DisconnectFailReason.BAD_PACKET);
      put(
         new OreDisconnect.Entry(t + "errorOccurred", s + "unexpectedPacket", true),
         DisconnectFailReason.UNEXPECTED_PACKET,
         DisconnectFailReason.UNKNOWN_PACKET,
         DisconnectFailReason.INVALID_COMMAND_REQUEST_PACKET
      );
      put(new OreDisconnect.Entry(t + "errorOccurred", s + "internalError.cantConnect", true), DisconnectFailReason.UNRECOVERABLE_ERROR);
      put(
         new OreDisconnect.Entry(t + "disconnected", s + "noReason", true),
         DisconnectFailReason.UNKNOWN,
         DisconnectFailReason.NO_REASON,
         DisconnectFailReason.DISCONNECTED,
         DisconnectFailReason.REASON_NOT_SET,
         DisconnectFailReason.LEGACY_DISCONNECT,
         DisconnectFailReason.DISCONNECT_PACKET_DEPRECATED
      );
      put(new OreDisconnect.Entry(t + "kickedFromWorld", s + "noReason", true), DisconnectFailReason.KICKED, DisconnectFailReason.KICKED_FOR_EXPLOIT);
      put(new OreDisconnect.Entry(t + "kickedFromWorld", b + "idleTooLong", true), DisconnectFailReason.KICKED_FOR_IDLE);
      put(new OreDisconnect.Entry(s + "serverFull.title", s + "serverFull", true), DisconnectFailReason.SERVER_FULL);
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "serverIdConflict", true), DisconnectFailReason.SERVER_ID_CONFLICT);
      put(new OreDisconnect.Entry(t + "disconnected", b + "loggedInElsewhere", true), DisconnectFailReason.LOGGED_IN_OTHER_LOCATION);
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "notAllowed", false), DisconnectFailReason.NOT_ALLOWED);
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "invalidPlayer", false), DisconnectFailReason.INVALID_PLAYER);
      put(
         new OreDisconnect.Entry(t + "unableToConnect", s + "notAuthenticated", false),
         DisconnectFailReason.NOT_AUTHENTICATED,
         DisconnectFailReason.NOT_AUTHENTICATED_FAST_FAIL
      );
      put(
         new OreDisconnect.Entry(t + "unableToConnect", b + "permissionsDoNotAllow", false),
         DisconnectFailReason.NO_PERMISSIONS,
         DisconnectFailReason.SESSION_ACCESS_DENIED
      );
      put(new OreDisconnect.Entry(t + "bannedByHost", b + "permissionsDoNotAllow", false), DisconnectFailReason.DENY_LISTED);
      put(new OreDisconnect.Entry(t + "unableToConnect", b + "needsHostUnblock", false), DisconnectFailReason.THIRD_PARTY_BLOCKED);
      put(
         new OreDisconnect.Entry(t + "disconnectedFromHost", s + "hostSuspended", true),
         DisconnectFailReason.HOST_SUSPENDED,
         DisconnectFailReason.SHUTDOWN,
         DisconnectFailReason.SERVER_SHUTDOWN
      );
      put(new OreDisconnect.Entry(t + "disconnectedFromHost", b + "hostSignedOut", true), DisconnectFailReason.HOST_SIGNED_OUT);
      put(new OreDisconnect.Entry(t + "unableToConnect", b + "hostWithoutKeys", true), DisconnectFailReason.HOST_WITHOUT_KEYS);
      put(
         new OreDisconnect.Entry(t + "unableToConnect", s + "sessionNotFound", true),
         DisconnectFailReason.SESSION_NOT_FOUND,
         DisconnectFailReason.INVITE_SESSION_NOT_FOUND,
         DisconnectFailReason.CONN_SESSION_NOT_FOUND
      );
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "failedAsyncCheck", true), DisconnectFailReason.ASYNC_JOIN_TASK_DENIED);
      put(new OreDisconnect.Entry(t + "multiplayerDisabled", s + "multiplayerDisabled", false), DisconnectFailReason.MULTIPLAYER_DISABLED);
      put(new OreDisconnect.Entry(s + "crossPlatformDisabled.header", s + "crossPlatformDisabled", false), DisconnectFailReason.CROSS_PLATFORM_DISABLED);
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "invalidName", false), DisconnectFailReason.INVALID_NAME);
      put(
         new OreDisconnect.Entry(t + "unableToConnect", b + "expiredToken", true),
         DisconnectFailReason.EXPIRED_TOKEN,
         DisconnectFailReason.EXPIRED_AUTH_FROM_DISCOVERY
      );
      put(new OreDisconnect.Entry(t + "unableToConnect", b + "discoveryMismatch", false), DisconnectFailReason.DISCOVERY_ENVIRONMENT_MISMATCH);
      put(new OreDisconnect.Entry(t + "disconnectedFromHost", "disconnect.scriptWatchdog", true), DisconnectFailReason.SCRIPT_WATCHDOG_EXCEPTION);
      put(new OreDisconnect.Entry(t + "disconnectedFromHost", "disconnect.scriptWatchdogOutOfMemory", true), DisconnectFailReason.SCRIPT_MEMORY_LIMIT_EXCEEDED);
      put(
         new OreDisconnect.Entry(t + "versionMismatch", b + "versionMismatchHost", false),
         DisconnectFailReason.VERSION_MISMATCH,
         DisconnectFailReason.CLIENT_SETTINGS_INCOMPATIBLE_WITH_SERVER,
         DisconnectFailReason.CONN_PROTOCOL_VERSION
      );
      put(new OreDisconnect.Entry(t + "versionMismatchHost", b + "hostVersionOlder", false), DisconnectFailReason.OUTDATED_SERVER);
      put(new OreDisconnect.Entry(t + "versionOutOfDate", b + "gameOutOfDate", false), DisconnectFailReason.OUTDATED_CLIENT);
      put(new OreDisconnect.Entry(t + "invalidTenantId", s + "invalidTenant", false), DisconnectFailReason.INVALID_TENANT);
      put(new OreDisconnect.Entry(t + "incompatibleWorld", s + "editionMismatchEduToVanilla", false), DisconnectFailReason.EDITION_MISMATCH_EDU_TO_VANILLA);
      put(new OreDisconnect.Entry(t + "incompatibleWorld", s + "editionMismatchVanillaToEdu", false), DisconnectFailReason.EDITION_MISMATCH_VANILLA_TO_EDU);
      put(
         new OreDisconnect.Entry(t + "incompatibleWorld", s + "editionMismatch", false),
         DisconnectFailReason.EDITION_MISMATCH,
         DisconnectFailReason.EDITION_VERSION_MISMATCH
      );
      put(new OreDisconnect.Entry(t + "incompatibleWorld", s + "editor.mismatchEditorToVanilla", false), DisconnectFailReason.EDITOR_MISMATCH_EDITOR_TO_VANILLA);
      put(new OreDisconnect.Entry(t + "incompatibleWorld", s + "editor.mismatchVanillaToEditor", false), DisconnectFailReason.EDITOR_MISMATCH_VANILLA_TO_EDITOR);
      put(new OreDisconnect.Entry(t + "incompatibleWorld", s + "editor.mismatchEditorWorld", false), DisconnectFailReason.EDITOR_MISMATCH_EDITOR_WORLD);
      put(new OreDisconnect.Entry(t + "incompatibleWorld", s + "editor.mismatchVanillaWorld", false), DisconnectFailReason.EDITOR_MISMATCH_VANILLA_WORLD);
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "editor.joinIntentPolicyFailure", false), DisconnectFailReason.EDITOR_JOIN_INTENT_POLICY_FAILURE);
      put(new OreDisconnect.Entry(t + "unableToConnect", s + "editor.notAllowed", false), DisconnectFailReason.EDITOR_NOT_ALLOWED);
      put(new OreDisconnect.Entry(t + "incompatibleWorld", b + "worldNewerVersion", false), DisconnectFailReason.LEVEL_NEWER_THAN_EXE_VERSION);
      put(new OreDisconnect.Entry(t + "skinIssue", b + "skinLockedPlatform", false), DisconnectFailReason.SKIN_ISSUE);
      put(new OreDisconnect.Entry(s + "lockedSkin.title", s + "lockedSkin", false), DisconnectFailReason.PLATFORM_LOCKED_SKINS_ERROR);
      put(new OreDisconnect.Entry(t + "invalidSkin", s + "invalidSkin", false), DisconnectFailReason.INVALID_PLATFORM_SKIN);
      put(new OreDisconnect.Entry(t + "skinError", b + "skinBanned", false), DisconnectFailReason.BANNED_SKIN);
      put(
         new OreDisconnect.Entry(t + "resourcePackLoad", s + "resourcePack", true),
         DisconnectFailReason.RESOURCE_PACK_PROBLEM,
         DisconnectFailReason.RESOURCE_PACK_LOADING_FAILED
      );
      put(new OreDisconnect.Entry(t + "incompatiblePack", s + "incompatiblePack", false), DisconnectFailReason.INCOMPATIBLE_PACK);
      put(new OreDisconnect.Entry(t + "errorOccurred", s + "blockMismatch", true), DisconnectFailReason.BLOCK_MISMATCH);
      put(new OreDisconnect.Entry(t + "errorOccurred", s + "invalidOverworldDimensionHeights", true), DisconnectFailReason.INVALID_HEIGHTS);
      put(new OreDisconnect.Entry(t + "errorOccurred", s + "missingStructureData", true), DisconnectFailReason.MISSING_STRUCTURE_DATA);
      put(
         new OreDisconnect.Entry(t + "worldFailedLoad", s + "worldCorruption", false),
         DisconnectFailReason.WORLD_CORRUPTION,
         DisconnectFailReason.LEVEL_STORAGE_CORRUPTION,
         DisconnectFailReason.INVALID_LEVEL
      );
      put(
         new OreDisconnect.Entry(t + "outOfStorage", b + "storageOut", false),
         DisconnectFailReason.OUT_OF_STORAGE,
         DisconnectFailReason.STORAGE_LOW_DURING_GAMEPLAY,
         DisconnectFailReason.STORAGE_FULL_DURING_GAMEPLAY
      );
      put(ENTRIES.get(DisconnectFailReason.CONNECTION_LOST), DisconnectFailReason.NETHER_NET_DATA_CHANNEL_CLOSED);
      put(
         ENTRIES.get(DisconnectFailReason.CANT_CONNECT),
         DisconnectFailReason.CONN_ICE,
         DisconnectFailReason.CONN_CLIENT_SIGNALING_ERROR,
         DisconnectFailReason.UNSUPPORTED_TRANSPORT
      );
      put(ENTRIES.get(DisconnectFailReason.TIMEOUT), DisconnectFailReason.CONN_NEGOTIATION_TIMEOUT_WAITING_FOR_RESPONSE);
      put(ENTRIES.get(DisconnectFailReason.NOT_AUTHENTICATED), DisconnectFailReason.NETHER_NET_IDENTITY_NOT_ALLOWED);
   }

   public record Entry(String title, String body, boolean reconnect) {
   }

   public static enum Variant {
      STANDARD,
      SERVER,
      CLIENT_MISMATCH;
   }
}
