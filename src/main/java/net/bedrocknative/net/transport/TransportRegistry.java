package net.bedrocknative.net.transport;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class TransportRegistry {
   public static final String NETHERNET_UNAVAILABLE = "NetherNet is not available in this build";
   static final NetherNetProbe NO_PROBE = new NetherNetProbe() {
      @Override
      public CompletableFuture<ProbeResult> probe(String host, InetSocketAddress address, @Nullable SignalingScheme pinned, Duration timeout) {
         return CompletableFuture.completedFuture(ProbeResult.unavailable());
      }

      @Override
      public boolean available() {
         return false;
      }
   };
   private static volatile @Nullable BedrockTransport netherNet;
   private static volatile NetherNetProbe probe = NO_PROBE;

   private TransportRegistry() {
   }

   public static synchronized void useNetherNet(BedrockTransport transport, NetherNetProbe signalingProbe) {
      if (transport.kind() != TransportKind.NETHERNET) {
         throw new IllegalArgumentException("Not a NetherNet transport: " + transport.kind());
      }

      netherNet = transport;
      probe = signalingProbe;
      BedrockNative.LOGGER.info("NetherNet transport available");
   }

   static synchronized void forgetNetherNet() {
      netherNet = null;
      probe = NO_PROBE;
   }

   public static BedrockTransport transport(TransportKind kind) {
      return switch (kind) {
         case RAKNET -> RakNetTransport.INSTANCE;
         case NETHERNET -> {
            BedrockTransport wired = netherNet;
            if (wired == null) {
               throw new TransportUnavailableException(kind, "NetherNet is not available in this build");
            }

            yield wired;
         }
         case JAVA -> throw new IllegalArgumentException("Java servers are joined by vanilla's own connection, not a Bedrock transport");
      };
   }

   public static NetherNetProbe netherNetProbe() {
      return probe;
   }

   public static boolean netherNetAvailable() {
      return netherNet != null && probe.available();
   }
}
