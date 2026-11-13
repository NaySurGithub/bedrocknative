package net.bedrocknative.client.lobby;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.bedrocknative.client.net.BedrockConnector;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.net.BedrockPing;
import net.bedrocknative.net.transport.JavaStatus;
import net.bedrocknative.net.transport.JavaStatusProbe;
import net.bedrocknative.net.transport.NetherNetProbe;
import net.bedrocknative.net.transport.ProbeHistory;
import net.bedrocknative.net.transport.ProbeResult;
import net.bedrocknative.net.transport.TransportKind;
import net.bedrocknative.net.transport.TransportMemory;
import net.bedrocknative.net.transport.TransportRegistry;
import net.bedrocknative.ui.PlayData;
import net.bedrocknative.ui.ServerStatuses;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.jspecify.annotations.Nullable;

final class LobbyStatusProbe {
   private static final Duration JAVA_TIMEOUT = Duration.ofMillis(1500L);
   private static final Duration NETHERNET_TIMEOUT = Duration.ofMillis(1500L);

   private LobbyStatusProbe() {
   }

   static PlayData.Status savedServer(String stored, int pingTimeoutMillis) {
      return savedServer(stored, pingTimeoutMillis, LobbyStatusProbe.Context.live());
   }

   static PlayData.Status savedServer(String stored, int pingTimeoutMillis, LobbyStatusProbe.Context context) {
      BedrockAddress typed = BedrockAddress.parse(stored);
      boolean portTyped = BedrockAddress.portTyped(stored);
      int javaPort = !portTyped && typed.forced() != TransportKind.JAVA ? 25565 : typed.port();
      if (typed.forced() != null) {
         return switch (typed.forced()) {
            case JAVA -> ServerStatuses.of(null, null, java(typed.host(), javaPort, context));
            case RAKNET -> ServerStatuses.of(ping(typed.host(), typed.port(), pingTimeoutMillis), null, null);
            case NETHERNET -> ServerStatuses.of(null, netherNet(typed.host(), typed.port(), context), null);
         };
      } else {
         String key = TransportMemory.key(typed.host(), typed.port());
         ProbeHistory history = context.history();
         TransportMemory.Entry remembered = context.memory().get(typed.host(), typed.port());
         TransportKind first = remembered == null ? null : remembered.kind();
         boolean knownBedrock = first != null && first.bedrock();
         boolean probed = false;
         if (first == TransportKind.NETHERNET && context.netherNet() != null) {
            probed = true;
            ProbeResult probe = netherNet(typed.host(), typed.port(), context);
            if (probe != null && probe.supported()) {
               history.answered(key);
               return ServerStatuses.of(null, probe, null);
            }
         }

         JavaStatus javaStatus = null;
         BedrockPing.Pong pong;
         if (first == TransportKind.JAVA) {
            CompletableFuture<JavaStatus> asked = JavaStatusProbe.probeAsync(
               typed.host(), javaPort, context.lookup(), context.protocol(), JAVA_TIMEOUT, JAVA_TIMEOUT
            );
            pong = ping(typed.host(), typed.port(), pingTimeoutMillis);
            if (pong != null) {
               asked.cancel(true);
            } else {
               javaStatus = asked.join();
            }
         } else {
            pong = ping(typed.host(), typed.port(), pingTimeoutMillis);
            if (pong == null) {
               int misses = history.bedrockMissed(key);
               if (knownBedrock && misses < 2) {
                  return PlayData.Status.OFFLINE;
               }

               if (!history.tcpAllowed(key)) {
                  return PlayData.Status.OFFLINE;
               }

               javaStatus = java(typed.host(), javaPort, context);
            }
         }

         if (pong != null) {
            history.answered(key);
            remember(context.memory(), typed, TransportKind.RAKNET, typed.port());
            return ServerStatuses.of(pong, null, null);
         }

         if (javaStatus != null && javaStatus.answered() && javaStatus.address() != null) {
            history.answered(key);
            remember(context.memory(), typed, TransportKind.JAVA, javaStatus.address().getPort());
            return ServerStatuses.of(null, null, javaStatus);
         }

         if (probed) {
            history.tcpFailed(key);
            return PlayData.Status.OFFLINE;
         }

         ProbeResult netherNet = netherNet(typed.host(), typed.port(), context);
         if (netherNet != null && netherNet.supported()) {
            history.answered(key);
         } else {
            history.tcpFailed(key);
         }

         return ServerStatuses.of(null, netherNet, null);
      }
   }

   private static void remember(TransportMemory memory, BedrockAddress typed, TransportKind kind, int foundOn) {
      TransportMemory.Entry known = memory.get(typed.host(), typed.port());
      if (known == null || known.kind() != kind || known.port() != foundOn) {
         memory.put(typed.host(), typed.port(), kind, null, foundOn);
      }
   }

   static PlayData.Status bedrockServer(String host, int port, int pingTimeoutMillis) {
      LobbyStatusProbe.Context context = LobbyStatusProbe.Context.live();
      String key = TransportMemory.key(host, port);
      BedrockPing.Pong pong = ping(host, port, pingTimeoutMillis);
      if (pong != null) {
         context.history().answered(key);
         return ServerStatuses.of(pong, null, null);
      }

      if (!context.history().tcpAllowed(key)) {
         return PlayData.Status.OFFLINE;
      }

      ProbeResult probe = netherNet(host, port, context);
      if (probe != null && probe.supported()) {
         context.history().answered(key);
      } else {
         context.history().tcpFailed(key);
      }

      return ServerStatuses.of(null, probe, null);
   }

   private static BedrockPing.@Nullable Pong ping(String host, int port, int timeoutMillis) {
      try {
         return BedrockPing.ping(new InetSocketAddress(host, port), timeoutMillis);
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   private static JavaStatus java(String host, int port, LobbyStatusProbe.Context context) {
      return JavaStatusProbe.probe(host, port, context.lookup(), context.protocol(), JAVA_TIMEOUT, JAVA_TIMEOUT);
   }

   private static @Nullable ProbeResult netherNet(String host, int port, LobbyStatusProbe.Context context) {
      NetherNetProbe probe = context.netherNet();
      if (probe == null) {
         return null;
      }

      InetSocketAddress address = new InetSocketAddress(host, port);
      if (address.isUnresolved()) {
         return null;
      }

      long bound = NETHERNET_TIMEOUT.toMillis() * 4L + 1000L;
      CompletableFuture<ProbeResult> asked = probe.probe(host, address, null, NETHERNET_TIMEOUT);
      ProbeResult result = asked.copy()
         .completeOnTimeout(ProbeResult.failed(ProbeResult.Reason.TIMEOUT, List.of(), bound), bound, TimeUnit.MILLISECONDS)
         .join();
      if (!asked.isDone()) {
         asked.cancel(true);
      }

      return result;
   }

   private static @Nullable InetSocketAddress vanillaLookup(String host, int port) {
      return ServerNameResolver.DEFAULT.resolveAddress(new ServerAddress(host, port)).map(ResolvedServerAddress::asInetSocketAddress).orElse(null);
   }

   record Context(TransportMemory memory, JavaStatusProbe.Resolver lookup, @Nullable NetherNetProbe netherNet, int protocol, ProbeHistory history) {
      private static final ProbeHistory LIVE_HISTORY = new ProbeHistory();

      Context(TransportMemory memory, JavaStatusProbe.Resolver lookup, @Nullable NetherNetProbe netherNet, int protocol) {
         this(memory, lookup, netherNet, protocol, new ProbeHistory());
      }

      static LobbyStatusProbe.Context live() {
         return new LobbyStatusProbe.Context(
            BedrockConnector.memory(),
            LobbyStatusProbe::vanillaLookup,
            TransportRegistry.netherNetAvailable() ? TransportRegistry.netherNetProbe() : null,
            SharedConstants.getProtocolVersion(),
            LIVE_HISTORY
         );
      }
   }
}
