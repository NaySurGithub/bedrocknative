package net.bedrocknative.net.client;

import java.net.InetSocketAddress;
import net.bedrocknative.camera.CameraPresetBook;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.protocol.BedrockProtocols;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

public final class BedrockSessionSetup {
   private BedrockSessionSetup() {
   }

   public static void init(BedrockClientSession session, ClientIdentity identity, InetSocketAddress address, BedrockGameListener listener) {
      init(session, identity, address, listener, BedrockProtocols.latest());
   }

   public static void init(
      BedrockClientSession session, ClientIdentity identity, InetSocketAddress address, BedrockGameListener listener, BedrockProtocol protocol
   ) {
      session.setCodec(protocol.codec());
      session.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.CLIENT);
      session.getPeer().getCodecHelper().setBlockDefinitions(HashedBlockDefinitions.INSTANCE);
      session.getPeer().getCodecHelper().setCameraPresetDefinitions(CameraPresetBook.definitions(CameraPresetBook.VANILLA));
      LoginHandler handler = new LoginHandler(session, identity, address, listener, protocol);
      session.setPacketHandler(handler);
      handler.start();
   }
}
