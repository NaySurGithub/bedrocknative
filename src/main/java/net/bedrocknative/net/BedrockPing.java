package net.bedrocknative.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class BedrockPing {
   public static final int UNCONNECTED_PING = 1;
   public static final int UNCONNECTED_PONG = 28;
   static final byte[] MAGIC = new byte[]{0, -1, -1, 0, -2, -2, -2, -2, -3, -3, -3, -3, 18, 52, 86, 120};
   private static final int MAX_PACKET = 1500;
   static final double RESEND_FRACTION = 0.4;
   private static final long CLIENT_GUID = ThreadLocalRandom.current().nextLong();

   private BedrockPing() {
   }

   static byte[] ping(long time) {
      ByteBuffer buffer = ByteBuffer.allocate(9 + MAGIC.length + 8);
      buffer.put((byte)1).putLong(time).put(MAGIC).putLong(CLIENT_GUID);
      return buffer.array();
   }

   @Nullable
   static BedrockPing.Pong parse(byte[] data, int length, InetSocketAddress from, long now) {
      if (length >= 17 + MAGIC.length + 2 && data[0] == 28) {
         ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
         buffer.get();
         long time = buffer.getLong();
         long guid = buffer.getLong();
         byte[] magic = new byte[MAGIC.length];
         buffer.get(magic);
         if (!Arrays.equals(magic, MAGIC)) {
            return null;
         } else {
            int size = Short.toUnsignedInt(buffer.getShort());
            if (size > buffer.remaining()) {
               return null;
            } else {
               String id = new String(data, buffer.position(), size, StandardCharsets.UTF_8);
               return fields(id, from, Math.max(0L, now - time), guid);
            }
         }
      } else {
         return null;
      }
   }

   static BedrockPing.Pong fields(String id, InetSocketAddress from, long latency) {
      return fields(id, from, latency, 0L);
   }

   static BedrockPing.Pong fields(String id, InetSocketAddress from, long latency, long guid) {
      List<String> parts = split(id);
      return new BedrockPing.Pong(
         from,
         part(parts, 0),
         part(parts, 1),
         number(part(parts, 2)),
         part(parts, 3),
         number(part(parts, 4)),
         number(part(parts, 5)),
         part(parts, 6),
         part(parts, 7),
         part(parts, 8),
         number(part(parts, 10)),
         number(part(parts, 11)),
         latency,
         guid
      );
   }

   private static List<String> split(String id) {
      List<String> parts = new ArrayList<>();
      int start = 0;

      for (int i = 0; i < id.length(); i++) {
         if (id.charAt(i) == ';') {
            parts.add(id.substring(start, i));
            start = i + 1;
         }
      }

      if (start < id.length()) {
         parts.add(id.substring(start));
      }

      return parts;
   }

   private static String part(List<String> parts, int index) {
      return index < parts.size() ? parts.get(index) : "";
   }

   private static int number(String text) {
      try {
         return Integer.parseInt(text.trim());
      } catch (NumberFormatException var2) {
         return 0;
      }
   }

   @Nullable
   public static BedrockPing.Pong ping(InetSocketAddress server, int timeoutMillis) throws IOException {
      BedrockPing.Pong var3;
      try (DatagramSocket socket = new DatagramSocket()) {
         var3 = ping(socket, server, timeoutMillis);
      }

      return var3;
   }

   public static BedrockPing.Located locate(InetSocketAddress server, int timeoutMillis) throws IOException {
      BedrockPing.Located var3;
      try (DatagramSocket socket = new DatagramSocket()) {
         var3 = new BedrockPing.Located(ping(socket, server, timeoutMillis), socket.getLocalPort());
      }

      return var3;
   }

   @Nullable
   private static BedrockPing.Pong ping(DatagramSocket socket, InetSocketAddress server, int timeoutMillis) throws IOException {
      long sent = System.currentTimeMillis();
      byte[] ping = ping(sent);
      socket.send(new DatagramPacket(ping, ping.length, server));
      byte[] buffer = new byte[1500];
      long deadline = sent + timeoutMillis;
      long resendAt = sent + Math.max(1L, (long)(timeoutMillis * 0.4));
      boolean resent = false;

      while (true) {
         long now = System.currentTimeMillis();
         long left = deadline - now;
         if (left <= 0L) {
            return null;
         }

         if (!resent && now >= resendAt) {
            resent = true;
            byte[] again = ping(now);
            socket.send(new DatagramPacket(again, again.length, server));
         }

         long untilResend = resent ? left : Math.min(left, Math.max(1L, resendAt - now));
         socket.setSoTimeout((int)untilResend);
         DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

         try {
            socket.receive(packet);
         } catch (SocketTimeoutException var20) {
            continue;
         }

         BedrockPing.Pong pong = parse(buffer, packet.getLength(), (InetSocketAddress)packet.getSocketAddress(), System.currentTimeMillis());
         if (pong != null) {
            return pong;
         }
      }
   }

   public static void discover(List<InetSocketAddress> targets, int listenMillis, Consumer<BedrockPing.Pong> found) throws IOException {
      try (DatagramSocket socket = new DatagramSocket()) {
         socket.setBroadcast(true);
         long sent = System.currentTimeMillis();
         byte[] ping = ping(sent);

         for (InetSocketAddress target : targets) {
            try {
               socket.send(new DatagramPacket(ping, ping.length, target));
            } catch (IOException var15) {
            }
         }

         byte[] buffer = new byte[1500];
         long deadline = sent + listenMillis;

         while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0L) {
               return;
            }

            socket.setSoTimeout((int)left);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
               socket.receive(packet);
            } catch (SocketTimeoutException var16) {
               return;
            }

            BedrockPing.Pong pong = parse(buffer, packet.getLength(), (InetSocketAddress)packet.getSocketAddress(), System.currentTimeMillis());
            if (pong != null) {
               found.accept(pong);
            }
         }
      }
   }

   public static List<InetSocketAddress> lanTargets(int port) {
      List<InetSocketAddress> targets = new ArrayList<>();

      try {
         targets.add(new InetSocketAddress(InetAddress.getByAddress(new byte[]{-1, -1, -1, -1}), port));
         targets.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
      } catch (IOException var3) {
      }

      return targets;
   }

   public record Located(@Nullable BedrockPing.Pong pong, int localPort) {
   }

   public record Pong(
      InetSocketAddress from,
      String edition,
      String motd,
      int protocol,
      String version,
      int players,
      int maxPlayers,
      String serverId,
      String levelName,
      String gameMode,
      int port,
      int portV6,
      long latencyMillis,
      long guid
   ) {
      public int joinPort() {
         return this.port > 0 && this.port <= 65535 ? this.port : this.from.getPort();
      }
   }
}
