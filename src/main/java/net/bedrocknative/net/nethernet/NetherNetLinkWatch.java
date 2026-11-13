package net.bedrocknative.net.nethernet;

import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicLong;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelMetrics;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.config.NetherConnectionFailure;
import org.jspecify.annotations.Nullable;
import tel.schich.libdatachannel.IceState;
import tel.schich.libdatachannel.PeerState;

public final class NetherNetLinkWatch implements NetherChannelMetrics {
   private volatile @Nullable PeerState peer;
   private volatile @Nullable IceState ice;
   private volatile @Nullable String localType;
   private volatile @Nullable String remoteType;
   private volatile @Nullable NetherConnectionFailure failure;
   private final AtomicLong bytesIn = new AtomicLong();
   private final AtomicLong bytesOut = new AtomicLong();
   private final AtomicLong fragmentsIn = new AtomicLong();
   private final AtomicLong fragmentsOut = new AtomicLong();
   private final AtomicLong decodeFails = new AtomicLong();

   @Override
   public void peerStateChange(PeerState state) {
      this.peer = state;
   }

   @Override
   public void iceStateChange(IceState state) {
      this.ice = state;
   }

   @Override
   public void pathSelected(String localType, String remoteType) {
      this.localType = localType;
      this.remoteType = remoteType;
   }

   @Override
   public void connectionFailed(NetherConnectionFailure reason) {
      this.failure = reason;
   }

   @Override
   public void bytesIn(int count) {
      this.bytesIn.addAndGet(count);
   }

   @Override
   public void bytesOut(int count) {
      this.bytesOut.addAndGet(count);
   }

   @Override
   public void fragmentsIn(int count) {
      this.fragmentsIn.addAndGet(count);
   }

   @Override
   public void fragmentsOut(int count) {
      this.fragmentsOut.addAndGet(count);
   }

   @Override
   public void decodeFail(int count) {
      this.decodeFails.addAndGet(count);
   }

   public @Nullable PeerState peer() {
      return this.peer;
   }

   public @Nullable IceState ice() {
      return this.ice;
   }

   public @Nullable String pairTypes() {
      return this.localType == null ? null : this.localType + "/" + this.remoteType;
   }

   public @Nullable NetherConnectionFailure failure() {
      return this.failure;
   }

   public boolean peerFailed() {
      return this.peer == PeerState.RTC_FAILED || this.ice == IceState.RTC_ICE_FAILED;
   }

   public String traffic() {
      return this.bytesIn.get()
         + " bytes in ("
         + this.fragmentsIn.get()
         + " extra segments), "
         + this.bytesOut.get()
         + " out ("
         + this.fragmentsOut.get()
         + " extra segments), "
         + this.decodeFails.get()
         + " segments dropped";
   }

   public long fragmentsIn() {
      return this.fragmentsIn.get();
   }

   public String describe() {
      return "peer "
         + this.peer
         + ", ICE "
         + this.ice
         + (this.failure == null ? "" : ", failure " + this.failure)
         + ", pair "
         + this.pairTypes()
         + "; "
         + this.traffic();
   }

   public static @Nullable String describe(Channel channel) {
      try {
         return channel.config().getOption(NetherChannelOption.NETHER_METRICS) instanceof NetherNetLinkWatch watch ? watch.describe() : null;
      } catch (RuntimeException e) {
         return null;
      }
   }

   public long fragmentsOut() {
      return this.fragmentsOut.get();
   }
}
