package net.bedrocknative.net;

import java.util.Locale;
import net.bedrocknative.net.transport.TransportKind;
import org.jspecify.annotations.Nullable;

public record BedrockAddress(String host, int port, @Nullable TransportKind forced) {
   public static final int DEFAULT_PORT = 19132;
   public static final int JAVA_DEFAULT_PORT = 25565;

   public BedrockAddress(String host, int port) {
      this(host, port, null);
   }

   public static int defaultPort(@Nullable TransportKind forced) {
      return forced == TransportKind.JAVA ? 25565 : 19132;
   }

   public static BedrockAddress parse(String stored) {
      String text = stored.trim();
      TransportKind forced = prefix(text);
      if (forced != null) {
         text = text.substring(forced.prefix().length()).trim();
      }

      int defaultPort = defaultPort(forced);
      if (text.startsWith("[")) {
         int close = text.indexOf(93);
         if (close > 0) {
            String host = text.substring(1, close);
            String rest = text.substring(close + 1);
            return new BedrockAddress(host, rest.startsWith(":") ? port(rest.substring(1), defaultPort) : defaultPort, forced);
         }
      }

      int colon = text.indexOf(58);
      return colon >= 0 && colon == text.lastIndexOf(58)
         ? new BedrockAddress(text.substring(0, colon), port(text.substring(colon + 1), defaultPort), forced)
         : new BedrockAddress(text, defaultPort, forced);
   }

   public static @Nullable TransportKind prefix(String text) {
      int separator = text.indexOf("://");
      return separator <= 0 ? null : TransportKind.byId(text.substring(0, separator).trim().toLowerCase(Locale.ROOT));
   }

   public static boolean portTyped(String stored) {
      String text = stored.trim();
      TransportKind forced = prefix(text);
      if (forced != null) {
         text = text.substring(forced.prefix().length()).trim();
      }

      if (text.startsWith("[")) {
         int close = text.indexOf(93);
         return close > 0 && text.startsWith(":", close + 1) && validPort(text.substring(close + 2));
      } else {
         int colon = text.indexOf(58);
         return colon >= 0 && colon == text.lastIndexOf(58) && validPort(text.substring(colon + 1));
      }
   }

   public static BedrockAddress fromForm(String hostBox, int port) {
      String host = hostBox.trim();
      TransportKind forced = prefix(host);
      if (forced != null) {
         host = host.substring(forced.prefix().length()).trim();
      }

      if (host.length() > 2 && host.startsWith("[") && host.endsWith("]")) {
         host = host.substring(1, host.length() - 1);
      }

      return new BedrockAddress(host, forced == TransportKind.JAVA && port == 19132 ? 25565 : port, forced);
   }

   public static String withoutPrefix(String hostBox) {
      String host = hostBox.trim();
      TransportKind forced = prefix(host);
      return forced == null ? host : host.substring(forced.prefix().length()).trim();
   }

   private static boolean validPort(String text) {
      return typedPort(text) > 0;
   }

   private static int port(String text, int fallback) {
      try {
         int port = Integer.parseInt(text.trim());
         return port >= 1 && port <= 65535 ? port : fallback;
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   public static int typedPort(String text) {
      String trimmed = text.trim();
      if (!trimmed.isEmpty() && trimmed.length() <= 5 && trimmed.chars().allMatch(c -> c >= 48 && c <= 57)) {
         int port = Integer.parseInt(trimmed);
         return port >= 1 && port <= 65535 ? port : -1;
      } else {
         return -1;
      }
   }

   private boolean ipv6() {
      return this.host.indexOf(58) >= 0;
   }

   private String prefixText() {
      return this.forced == null ? "" : this.forced.prefix();
   }

   public String stored() {
      String shown = this.ipv6() ? "[" + this.host + "]" : this.host;
      return this.prefixText() + (this.port == defaultPort(this.forced) ? shown : shown + ":" + this.port);
   }

   public String display() {
      return this.prefixText() + (this.ipv6() ? "[" + this.host + "]" : this.host) + ":" + this.port;
   }

   public String hostBox() {
      return this.prefixText() + this.host;
   }

   public boolean sameServer(BedrockAddress other) {
      return this.port == other.port && this.host.toLowerCase(Locale.ROOT).equals(other.host.toLowerCase(Locale.ROOT));
   }
}
