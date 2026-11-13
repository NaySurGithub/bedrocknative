package net.bedrocknative.client.net;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.nethernet.NetherNetNative;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.NetherNetClientChannel;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings.Scheme;

public final class DevNetherNetLoadTest {
   private static final List<String> CLASSES = List.of(
      "org.cloudburstmc.netty.channel.nethernet.NetherNetClientChannel",
      "org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling",
      "org.cloudburstmc.netty.util.nethernet.OperatorIdentity",
      "org.cloudburstmc.netty.util.nethernet.IdentityUtils",
      "org.cloudburstmc.netty.util.nethernet.TokenTrust",
      "org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption",
      "org.cloudburstmc.netty.channel.TransportChannel"
   );
   private int passed;
   private int failed;

   private DevNetherNetLoadTest() {
   }

   public static void start() {
      Thread.ofVirtual().name("Dev nethernet-load test").start(() -> {
         Minecraft minecraft;
         while ((minecraft = Minecraft.getInstance()) == null || minecraft.getWindow() == null || !minecraft.isGameLoadFinished()) {
            sleep(200L);
         }

         DevNetherNetLoadTest test = new DevNetherNetLoadTest();

         try {
            test.run();
         } catch (Throwable e) {
            BedrockNative.LOGGER.error("Dev nethernet-load test: FAIL crashed", e);
            test.failed++;
         } finally {
            BedrockNative.LOGGER.info("Dev nethernet-load test: SUMMARY {} passed, {} failed", test.passed, test.failed);
            BedrockNative.LOGGER.info("Dev nethernet-load test: done");
            sleep(500L);
            Minecraft game = minecraft;
            game.execute(game::stop);
         }
      });
   }

   private void run() throws Exception {
      BedrockNative.LOGGER
         .info(
            "Dev nethernet-load test: Java {} ({}), {} {}",
            new Object[]{Runtime.version(), System.getProperty("java.vendor"), System.getProperty("os.name"), System.getProperty("os.arch")}
         );
      NetherNetNative.Status status = NetherNetNative.load(FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("natives"));
      this.check(status.describe(), status.loaded());

      for (String name : CLASSES) {
         try {
            Class.forName(name, true, DevNetherNetLoadTest.class.getClassLoader());
            this.check("class " + name + " loads", true);
         } catch (ClassNotFoundException | LinkageError e) {
            BedrockNative.LOGGER.warn("Dev nethernet-load test: {} did not load", name, e);
            this.check("class " + name + " loads (" + e + ")", false);
         }
      }

      if (status.loaded()) {
         NetherNetNative.SelfCheck offer = NetherNetNative.selfCheck(10000L);
         BedrockNative.LOGGER
            .info("Dev nethernet-load test: offer {} candidates ({}), gathered in {} ms", new Object[]{offer.candidates(), offer.types(), offer.gatherMillis()});
         this.check("a peer with NetherNet's two data channels gathers an offer with candidates", offer.candidates() > 0 && offer.dataChannelSection());
         EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

         try {
            NetherNetClientChannel channel = (NetherNetClientChannel)NetherNetChannelFactory.client(
                  new NetherNetHTTPClientSignaling(HttpSignalingSettings.DEFAULT.withScheme(Scheme.HTTP))
               )
               .newChannel();
            group.register(channel).syncUninterruptibly();
            channel.close().syncUninterruptibly();
            this.check("a NetherNet client channel registers and closes on a Netty event loop", !channel.isOpen());
         } finally {
            group.shutdownGracefully(0L, 1L, TimeUnit.SECONDS).syncUninterruptibly();
         }
      }
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
         BedrockNative.LOGGER.info("Dev nethernet-load test: PASS {}", what);
      } else {
         this.failed++;
         BedrockNative.LOGGER.warn("Dev nethernet-load test: FAIL {}", what);
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
