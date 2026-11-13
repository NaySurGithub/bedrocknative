package net.bedrocknative.net.transport;

import java.net.InetSocketAddress;
import java.util.function.Predicate;
import net.bedrocknative.net.client.RakHandshakeTrace;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.protocol.BedrockProtocols;
import org.jspecify.annotations.Nullable;

public record ConnectAttempt(
   @Nullable RakHandshakeTrace rakTrace,
   @Nullable InetSocketAddress localAddress,
   @Nullable ProbeResult probe,
   @Nullable Predicate<Exception> trustQuestion,
   BedrockProtocol protocol
) {
   public ConnectAttempt(@Nullable RakHandshakeTrace rakTrace, @Nullable InetSocketAddress localAddress, @Nullable ProbeResult probe) {
      this(rakTrace, localAddress, probe, null, BedrockProtocols.latest());
   }

   public ConnectAttempt(
      @Nullable RakHandshakeTrace rakTrace, @Nullable InetSocketAddress localAddress, @Nullable ProbeResult probe, @Nullable Predicate<Exception> trustQuestion
   ) {
      this(rakTrace, localAddress, probe, trustQuestion, BedrockProtocols.latest());
   }

   public static ConnectAttempt rakNet(RakHandshakeTrace trace, @Nullable InetSocketAddress localAddress) {
      return rakNet(trace, localAddress, BedrockProtocols.latest());
   }

   public static ConnectAttempt rakNet(RakHandshakeTrace trace, @Nullable InetSocketAddress localAddress, BedrockProtocol protocol) {
      return new ConnectAttempt(trace, localAddress, null, null, protocol);
   }

   public static ConnectAttempt netherNet(ProbeResult probe) {
      return netherNet(probe, null, BedrockProtocols.latest());
   }

   public static ConnectAttempt netherNet(ProbeResult probe, @Nullable Predicate<Exception> trustQuestion) {
      return netherNet(probe, trustQuestion, BedrockProtocols.latest());
   }

   public static ConnectAttempt netherNet(ProbeResult probe, @Nullable Predicate<Exception> trustQuestion, BedrockProtocol protocol) {
      return new ConnectAttempt(null, null, probe, trustQuestion, protocol);
   }
}
