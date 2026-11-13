package net.bedrocknative.protocol;

import java.util.List;
import net.bedrocknative.net.transport.TransportKind;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;

public final class BedrockVersion {
   public static final BedrockProtocol PROTOCOL = BedrockProtocols.latest();
   public static final int PROTOCOL_VERSION = 2225;
   public static final String GAME_VERSION = "1.26.63";
   public static final int RAKNET_PROTOCOL = 11;
   public static final int DEFAULT_PORT = 19132;
   public static final int DEFAULT_PORT_V6 = 19133;
   public static final List<TransportKind> TRANSPORT_ORDER = List.of(TransportKind.NETHERNET, TransportKind.RAKNET);

   private BedrockVersion() {
   }

   public static BedrockCodec codec() {
      return PROTOCOL.codec();
   }
}
