package net.bedrocknative.net.nethernet;

import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import javax.crypto.SecretKey;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.BedrockCrypto;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;

public final class NetherNetBedrockPeer extends BedrockPeer {
   public static final String DATA_CHANNEL_CLOSED = "nethernet.data_channel_closed";
   public static final String LINK_FAILED = "nethernet.link_failed";

   public NetherNetBedrockPeer(Channel channel, BedrockSessionFactory sessionFactory) {
      super(channel, sessionFactory);
   }

   @Override
   public int getRakVersion() {
      return 11;
   }

   @Override
   public void enableEncryption(SecretKey secretKey) {
      BedrockCrypto.enableEncryption(this, secretKey);
   }

   @Override
   protected void onClose() {
      if (!this.closing.get() && !this.closed.get() && !this.channel.isOpen()) {
         String reason = this.channel.config().getOption(NetherChannelOption.NETHER_METRICS) instanceof NetherNetLinkWatch watch && watch.peerFailed()
            ? "nethernet.link_failed"
            : "nethernet.data_channel_closed";
         BedrockNative.LOGGER.info("NetherNet: the link ended without this client closing it ({})", reason);
         ObjectIterator var4 = this.sessions.values().iterator();

         while (var4.hasNext()) {
            BedrockSession session = (BedrockSession)var4.next();
            session.setDisconnectReason(reason);
         }
      }

      super.onClose();
   }
}
