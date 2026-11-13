package net.bedrocknative.client.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.net.client.SessionEnd;
import net.bedrocknative.net.transport.PingOutcome;
import net.bedrocknative.net.transport.TransportKind;
import net.bedrocknative.protocol.BedrockVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.jspecify.annotations.Nullable;

public final class DevConnectTest {
   private static final String BDS = "127.0.0.1";
   private static final String JAVA_REFUSAL = "BN stand-in Java server: login refused";
   private static final boolean LOCATE_OFF = "off".equalsIgnoreCase(System.getProperty("bedrocknative.locatePing", "").trim());
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   private DevConnectTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start() {
      Thread.ofVirtual().name("Dev connect test").start(() -> {
         Minecraft instance;
         while ((instance = Minecraft.getInstance()) == null || instance.getWindow() == null) {
            sleep(200L);
         }

         DevConnectTest test = new DevConnectTest(instance);

         try {
            test.run();
         } catch (Throwable var6) {
            BedrockNative.LOGGER.error("Dev connect test: FAIL crashed", var6);
            test.failed++;
         } finally {
            BedrockNative.LOGGER.info("Dev connect test: SUMMARY {} passed, {} failed", test.passed, test.failed);
            BedrockNative.LOGGER.info("Dev connect test: done");
            sleep(500L);
            test.onClient(() -> {
               test.minecraft.stop();
               return null;
            });
         }
      });
   }

   private void run() {
      this.waitFor(
         "the title screen",
         () -> this.onClient(() -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() != null),
         180000L
      );
      sleep(2000L);
      if (!LOCATE_OFF) {
         this.javaServer();
         this.bds();
      }

      this.nobodyThere();
   }

   private void javaServer() {
      try (ServerSocket socket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
         int port = socket.getLocalPort();
         DevConnectTest.StandInJavaServer stand = new DevConnectTest.StandInJavaServer(socket);
         Thread.ofVirtual().name("Dev connect test Java server").start(stand::serve);
         String address = "127.0.0.1:" + port;
         ServerData server = new ServerData("BN stand-in Java", address, Type.OTHER);
         this.join(server);
         boolean ended = this.waitFor(
            "Java's disconnect screen from " + address,
            () -> this.onClient(() -> this.minecraft.gui.screen() != null && this.minecraft.gui.screen().getClass() == DisconnectedScreen.class),
            30000L
         );
         BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
         log(
            "0 Java at {}: status pings {}, login from {}, choice {}", address, stand.statusPings.get(), stand.loginName, join == null ? "none" : join.choice()
         );
         this.check(
            "found Java: its status answered (" + (join == null ? null : join.javaStatus()) + ")",
            join != null && join.transport() == TransportKind.JAVA && join.javaStatus() != null && join.javaStatus().answered()
         );
         this.check("no Bedrock join was tried for the Java server", join != null && join.attempts() == 0);
         this.check("vanilla's own login reached the Java server", stand.loginName != null);
         this.check("the Java server's refusal shows on Java's own disconnect screen (not Bedrock's)", ended);
         this.check("the session kind is Java now (a Transfer would try Java first)", BedrockConnector.lastTransport() == TransportKind.JAVA);
         this.check("one status probe for the join (" + stand.statusPings.get() + ")", stand.statusPings.get() == 1);
         this.javaPrefix(stand, port);
      } catch (IOException var10) {
         this.check("a stand-in Java server on this computer (" + var10 + ")", false);
      }

      this.onClient(() -> {
         this.minecraft.gui.setScreen(new TitleScreen());
         return null;
      });
      sleep(1000L);
   }

   private void javaPrefix(DevConnectTest.StandInJavaServer stand, int port) {
      this.onClient(() -> {
         this.minecraft.gui.setScreen(new TitleScreen());
         return null;
      });
      sleep(1000L);
      int pingsBefore = stand.statusPings.get();
      stand.loginName = null;
      String address = "java://127.0.0.1:" + port;
      this.join(new ServerData("BN java:// stand-in", address, Type.OTHER));
      boolean ended = this.waitFor(
         "Java's disconnect screen from " + address,
         () -> this.onClient(() -> this.minecraft.gui.screen() != null && this.minecraft.gui.screen().getClass() == DisconnectedScreen.class),
         30000L
      );
      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      log(
         "0 {}: status pings {} (was {}), login from {}, choice {}",
         address,
         stand.statusPings.get(),
         pingsBefore,
         stand.loginName,
         join == null ? "none" : join.choice()
      );
      this.check(
         "java:// joined as Java with no probe at all (" + (join == null ? null : join.choice()) + ")",
         join != null
            && join.transport() == TransportKind.JAVA
            && !join.javaProbeSent()
            && join.javaStatus() == null
            && join.ping() == null
            && join.attempts() == 0
      );
      this.check("java:// sent the stand-in no status ping", stand.statusPings.get() == pingsBefore);
      this.check("vanilla's own login reached the Java server through java://", stand.loginName != null && ended);
   }

   private void bds() {
      ServerData server = new ServerData("BN connect test", "127.0.0.1", Type.OTHER);
      server.setResourcePackStatus(ServerPackStatus.ENABLED);
      this.join(server);
      boolean joined = this.waitFor(
            "the world after joining 127.0.0.1",
            () -> this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null)
               || BedrockDisconnects.isDisconnectScreen(this.onClient(() -> this.minecraft.gui.screen())),
            120000L
         )
         && this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null);
      this.check("joins 127.0.0.1", joined);
      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      if (join == null) {
         this.check("the connector reported the join", false);
      } else {
         log(
            "1 BDS: {} tries, ping {} from port {}, handshake from port {}: {}",
            join.attempts(),
            join.pong() == null ? "unanswered" : "protocol " + join.pong().protocol(),
            join.pingPort(),
            join.handshakePort(),
            join.handshake()
         );
         this.check(
            "the server-list ping was answered with protocol " + BedrockVersion.codec().getProtocolVersion(),
            join.pong() != null && join.pong().protocol() == BedrockVersion.codec().getProtocolVersion()
         );
         this.check(
            "the RakNet handshake came from the ping's port (" + join.pingPort() + ", handshake " + join.handshakePort() + ")",
            join.pingPort() > 0 && join.pingPort() == join.handshakePort()
         );
         boolean vanillaProbes = "vanilla".equalsIgnoreCase(System.getProperty("bedrocknative.rakHandshake", "").trim());
         int firstMtu = vanillaProbes ? 1492 : 1400;
         this.check(
            "the first probe is MTU " + firstMtu + (vanillaProbes ? " (vanilla RakNet, Don't Fragment set)" : " (the library's probes, the default)"),
            join.handshake().contains("(MTU " + firstMtu + " x")
         );
         this.check("connected in one try", join.attempts() == 1 && join.connected());
         log("1 BDS: choice: {}", join.choice());
         this.check(
            "joined over RakNet with no NetherNet probe (" + join.transport() + ", " + join.netherNetProbe() + ")",
            join.transport() == TransportKind.RAKNET && join.ping() == PingOutcome.PONG && join.netherNetProbe() == null
         );
         this.check(
            "a pong means no Java status probe went out at all (sent " + join.javaProbeSent() + ", status " + join.javaStatus() + ")",
            !join.javaProbeSent() && join.javaStatus() == null
         );
         sleep(3000L);
         this.onClient(() -> {
            this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            return null;
         });
         sleep(3000L);
      }
   }

   private void nobodyThere() {
      int port;
      try (DatagramSocket probe = new DatagramSocket(0)) {
         port = probe.getLocalPort();
      } catch (SocketException var14) {
         this.check("a free port for the empty address", false);
         return;
      }

      String address = "127.0.0.1:" + port;
      ServerData server = new ServerData("BN nobody there", address, Type.OTHER);
      long started = System.currentTimeMillis();
      this.join(server);
      boolean ended = this.waitFor(
         "the disconnect screen of " + address, () -> BedrockDisconnects.isDisconnectScreen(this.onClient(() -> this.minecraft.gui.screen())), 40000L
      );
      long seconds = (System.currentTimeMillis() - started) / 1000L;
      BedrockDisconnects.Ended last = BedrockDisconnects.last();
      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      SessionEnd end = last == null ? null : last.end();
      log("2 nobody at {}: ended after {} s: {}; join {}", address, seconds, end == null ? "nothing" : end.logText(), join);
      this.check("an address with no server ends on Bedrock's disconnect screen", ended && end != null);
      if (end != null) {
         this.check("as CANT_CONNECT (" + end.reason() + ")", end.reason() == DisconnectFailReason.CANT_CONNECT);
         if (LOCATE_OFF) {
            this.check(
               "no ping went out (" + (join == null ? null : join.ping()) + ")", join != null && join.ping() == PingOutcome.NOT_PINGED && !join.pinged()
            );
            this.check("the details say nothing answered in time", end.detail().contains("No answer in time from 127.0.0.1 (127.0.0.1:" + port + ")"));
         } else {
            this.check(
               "the details say no Bedrock server answers there, not even the ping",
               end.detail().contains("No Bedrock server answers at 127.0.0.1 (127.0.0.1:" + port + "), not even the server-list ping")
            );
         }

         this.check(
            "the details carry the handshake trace with no reply",
            end.detail().contains("RakNet handshake with ") && end.detail().contains("no OpenConnectionReply1")
         );
         this.check("no second try for an address that answers no ping", join != null && join.attempts() == 1 && !join.connected());
         this.check("the details say nothing answers on TCP port " + port + " either", end.detail().contains("nothing answers on TCP port " + port + " either"));
         this.check("Java's status was asked and nothing answered", join != null && join.javaStatus() != null && !join.javaStatus().answered());
         this.check(
            "the details name the Java possibility and java://" + address,
            end.detail().contains("Java Edition server") && end.detail().contains("java://" + address)
         );
         this.onClient(() -> {
            this.minecraft.gui.setScreen(new TitleScreen());
            return null;
         });
         sleep(1000L);
      }
   }

   private void join(ServerData server) {
      this.onClient(() -> {
         Screen parent = new TitleScreen();
         ConnectScreen.startConnecting(parent, this.minecraft, ServerAddress.parseString(server.ip), server, false, null);
         return null;
      });
   }

   private boolean waitFor(String what, Supplier<Boolean> done, long millis) {
      long started = System.currentTimeMillis();

      while (!done.get()) {
         if (System.currentTimeMillis() - started > millis) {
            this.check("waited too long for " + what, false);
            return false;
         }

         sleep(100L);
      }

      return true;
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
         BedrockNative.LOGGER.info("Dev connect test: PASS {}", what);
      } else {
         this.failed++;
         BedrockNative.LOGGER.warn("Dev connect test: FAIL {}", what);
      }
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev connect test: " + message, args);
   }

   private <T> T onClient(Supplier<T> task) {
      return this.minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private static final class StandInJavaServer {
      private final ServerSocket socket;
      private final AtomicInteger statusPings = new AtomicInteger();
      @Nullable
      private volatile String loginName;

      StandInJavaServer(ServerSocket socket) {
         this.socket = socket;
      }

      void serve() {
         while (!this.socket.isClosed()) {
            try (Socket client = this.socket.accept()) {
               client.setSoTimeout(5000);
               InputStream in = client.getInputStream();
               OutputStream out = client.getOutputStream();
               ByteBuffer handshake = ByteBuffer.wrap(frame(in));
               readVarInt(handshake);
               readVarInt(handshake);
               int hostLength = readVarInt(handshake);
               handshake.position(handshake.position() + hostLength + 2);
               int intent = readVarInt(handshake);
               ByteBuffer next = ByteBuffer.wrap(frame(in));
               if (intent == 1) {
                  this.statusPings.incrementAndGet();
                  out.write(
                     packet(
                        0,
                        string(
                           "{\"version\":{\"name\":\"stand-in\",\"protocol\":0},\"players\":{\"max\":1,\"online\":0},\"description\":\"BN stand-in Java server\"}"
                        )
                     )
                  );
                  out.flush();
                  byte[] ping = frame(in);
                  out.write(packet(1, Arrays.copyOfRange(ping, 1, ping.length)));
                  out.flush();
               } else {
                  readVarInt(next);
                  byte[] name = new byte[readVarInt(next)];
                  next.get(name);
                  this.loginName = new String(name, StandardCharsets.UTF_8);
                  out.write(packet(0, string("{\"text\":\"BN stand-in Java server: login refused\"}")));
                  out.flush();
                  DevConnectTest.sleep(500L);
               }
            } catch (RuntimeException | IOException var11) {
            }
         }
      }

      private static byte[] frame(InputStream in) throws IOException {
         int length = readVarInt(in);
         byte[] frame = in.readNBytes(length);
         if (frame.length < length) {
            throw new IOException("closed");
         } else {
            return frame;
         }
      }

      private static byte[] string(String text) {
         byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         writeVarInt(out, bytes.length);
         out.writeBytes(bytes);
         return out.toByteArray();
      }

      private static byte[] packet(int id, byte[] body) {
         ByteArrayOutputStream inner = new ByteArrayOutputStream();
         writeVarInt(inner, id);
         inner.writeBytes(body);
         ByteArrayOutputStream framed = new ByteArrayOutputStream();
         writeVarInt(framed, inner.size());
         framed.writeBytes(inner.toByteArray());
         return framed.toByteArray();
      }

      private static void writeVarInt(ByteArrayOutputStream out, int value) {
         int rest;
         for (rest = value; (rest & -128) != 0; rest >>>= 7) {
            out.write(rest & 127 | 128);
         }

         out.write(rest);
      }

      private static int readVarInt(InputStream in) throws IOException {
         int value = 0;

         for (int i = 0; i < 5; i++) {
            int read = in.read();
            if (read < 0) {
               throw new IOException("closed");
            }

            value |= (read & 127) << 7 * i;
            if ((read & 128) == 0) {
               return value;
            }
         }

         throw new IOException("VarInt too long");
      }

      private static int readVarInt(ByteBuffer in) {
         int value = 0;

         for (int i = 0; i < 5; i++) {
            int read = in.get() & 255;
            value |= (read & 127) << 7 * i;
            if ((read & 128) == 0) {
               return value;
            }
         }

         throw new IllegalStateException("VarInt too long");
      }
   }
}
