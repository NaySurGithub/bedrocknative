package net.bedrocknative.net.transport;

import java.net.InetSocketAddress;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public record ServerTarget(String host, InetSocketAddress address, int port, @Nullable TransportKind forced) {
   public ServerTarget(String host, InetSocketAddress address, @Nullable TransportKind forced) {
      this(host, address, address.getPort(), forced);
   }

   public String hostPort() {
      return hostPort(this.host, this.port);
   }

   public static String hostPort(String host, int port) {
      String lower = host.trim().toLowerCase(Locale.ROOT);
      if (lower.startsWith("[") && lower.endsWith("]")) {
         lower = lower.substring(1, lower.length() - 1);
      }

      return (lower.indexOf(58) >= 0 ? "[" + lower + "]" : lower) + ":" + port;
   }
}
