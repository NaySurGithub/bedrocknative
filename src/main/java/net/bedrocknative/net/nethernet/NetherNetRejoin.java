package net.bedrocknative.net.nethernet;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.transport.ServerTarget;

public final class NetherNetRejoin {
   public static final long DEFAULT_LEAVE_MILLIS = 2000L;
   public static final long DEFAULT_ABANDON_MILLIS = 40000L;
   static final String LEAVE_PROPERTY = "bedrocknative.nethernetRejoinMs";
   static final String ABANDON_PROPERTY = "bedrocknative.nethernetAbandonMs";
   private static final long FORGET_AFTER_MILLIS = 60000L;
   private final Map<String, Long> leftAt = new ConcurrentHashMap<>();
   private final Map<String, Long> abandonedAt = new ConcurrentHashMap<>();
   private final long leaveMillis;
   private final long abandonMillis;
   private final LongSupplier clock;

   public NetherNetRejoin(long leaveMillis, long abandonMillis, LongSupplier clock) {
      this.leaveMillis = Math.max(0L, leaveMillis);
      this.abandonMillis = Math.max(0L, abandonMillis);
      this.clock = clock;
   }

   public static NetherNetRejoin live() {
      return new NetherNetRejoin(
         setting("bedrocknative.nethernetRejoinMs", 2000L), setting("bedrocknative.nethernetAbandonMs", 40000L), () -> System.nanoTime() / 1000000L
      );
   }

   private static long setting(String property, long fallback) {
      String set = System.getProperty(property, "").trim();
      if (set.isEmpty()) {
         return fallback;
      }

      try {
         long value = Long.parseLong(set);
         BedrockNative.LOGGER.info("NetherNet wait set to {} ms (-D{})", value, property);
         return value;
      } catch (NumberFormatException e) {
         BedrockNative.LOGGER.warn("NetherNet wait: -D{}={} is not a number of milliseconds, {} ms kept", new Object[]{property, set, fallback});
         return fallback;
      }
   }

   public long leaveMillis() {
      return this.leaveMillis;
   }

   public long abandonMillis() {
      return this.abandonMillis;
   }

   public void left(InetSocketAddress server) {
      this.note(this.leftAt, server);
   }

   public void abandoned(InetSocketAddress server) {
      this.note(this.abandonedAt, server);
   }

   private void note(Map<String, Long> times, InetSocketAddress server) {
      long now = this.clock.getAsLong();
      long keep = Math.max(this.leaveMillis, this.abandonMillis) + 60000L;
      this.leftAt.values().removeIf(at -> now - at > keep);
      this.abandonedAt.values().removeIf(at -> now - at > keep);
      times.put(key(server), now);
   }

   public long remainingMillis(InetSocketAddress server) {
      String key = key(server);
      long now = this.clock.getAsLong();
      return Math.max(remaining(this.leftAt.get(key), this.leaveMillis, now), remaining(this.abandonedAt.get(key), this.abandonMillis, now));
   }

   public String reason(InetSocketAddress server) {
      String key = key(server);
      long now = this.clock.getAsLong();
      Long left = this.leftAt.get(key);
      Long abandoned = this.abandonedAt.get(key);
      boolean byAbandon = remaining(abandoned, this.abandonMillis, now) >= remaining(left, this.leaveMillis, now);
      return byAbandon && abandoned != null
         ? "an attempt of it was abandoned " + (now - abandoned) + " ms ago"
         : (left != null ? "it was left " + (now - left) + " ms ago" : "nothing");
   }

   private static long remaining(Long at, long wait, long now) {
      return at == null ? 0L : Math.max(0L, wait - (now - at));
   }

   static String key(InetSocketAddress server) {
      String host = server.getAddress() != null ? server.getAddress().getHostAddress() : server.getHostString();
      return ServerTarget.hostPort(host, server.getPort());
   }
}
