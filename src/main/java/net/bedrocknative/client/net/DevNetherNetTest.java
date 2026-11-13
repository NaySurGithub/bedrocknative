package net.bedrocknative.client.net;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.net.nethernet.NetherNetTransport;
import net.bedrocknative.net.nethernet.NetherNetTrustStore;
import net.bedrocknative.net.transport.PingOutcome;
import net.bedrocknative.net.transport.ServerTarget;
import net.bedrocknative.net.transport.TransportKind;
import net.bedrocknative.net.transport.TransportMemory;
import net.bedrocknative.net.transport.TransportRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DevNetherNetTest {
   private static final String PREFIX = "Dev nethernet test: ";
   private final Minecraft minecraft;
   private final String typed;
   private final BedrockAddress address;
   private int passed;
   private int failed;
   private static final boolean KEEP_KEY = Boolean.getBoolean("bedrocknative.nethernetKeepKey");

   private DevNetherNetTest(Minecraft minecraft, String typed) {
      this.minecraft = minecraft;
      this.typed = typed;
      this.address = BedrockAddress.parse(typed);
   }

   public static void start() {
      Thread.ofVirtual().name("Dev nethernet test").start(() -> {
         Minecraft instance;
         while ((instance = Minecraft.getInstance()) == null || instance.getWindow() == null) {
            sleep(200L);
         }

         DevNetherNetTest test = new DevNetherNetTest(instance, System.getProperty("bedrocknative.nethernetJoin", "127.0.0.1:19150"));

         try {
            test.run();
         } catch (Throwable e) {
            BedrockNative.LOGGER.error("Dev nethernet test: FAIL crashed", e);
            test.failed++;
         } finally {
            BedrockNative.LOGGER.info("Dev nethernet test: SUMMARY {} passed, {} failed", test.passed, test.failed);
            BedrockNative.LOGGER.info("Dev nethernet test: done");
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
      this.check("NetherNet is wired in at client start (transport and signaling probe)", TransportRegistry.netherNetAvailable());
      if (TransportRegistry.netherNetAvailable()) {
         this.forgetServer();
         if (KEEP_KEY || this.refuse()) {
            if (this.trustAndPlay()) {
               this.rejoin();
            }
         }
      }
   }

   private void forgetServer() {
      NetherNetTrustStore store = ((NetherNetTransport)TransportRegistry.transport(TransportKind.NETHERNET)).trustStore();
      String digest = store.trustedAt(this.target());
      if (digest != null && !KEEP_KEY) {
         store.forget(digest);
         log("forgot the key {} trusted at {}", digest.substring(0, 8), this.target());
      }

      BedrockConnector.memory().forget(this.address.host(), this.address.port());
   }

   private boolean refuse() {
      this.join();
      ServerTrustScreen question = this.waitForQuestion(60000L);
      this.check("the first join stops at Bedrock's trust question", question != null);
      if (question == null) {
         return false;
      }

      this.screenshot("nethernet-trust");
      String title = this.onClient(() -> question.getTitle().getString());
      log(
         "1 question: \"{}\", focus on {}",
         title,
         this.onClient(() -> question.getFocused() == null ? "nothing" : question.getFocused().getClass().getSimpleName())
      );
      this.check(
         "it is a first-contact question, not the changed-key warning (\"" + title + "\")", !title.isBlank() && !title.toLowerCase().contains("changed")
      );
      this.check(
         "the refusing button has the focus (Enter never trusts by accident)", this.onClient(() -> question.getFocused() == question.doNotTrustButton())
      );
      this.run(() -> question.doNotTrustButton().onPress(new KeyEvent(257, 0, 0)));
      boolean back = this.waitFor("the screen the join started from", () -> this.onClient(() -> this.minecraft.gui.screen() instanceof TitleScreen), 10000L);
      sleep(1500L);
      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      log("1 first join: {}", join == null ? "no report" : join.choice());
      this.check(
         "found NetherNet by itself: no pong, the probe answered (" + (join == null ? null : join.ping() + ", " + join.netherNetProbe()) + ")",
         join != null
            && join.transport() == TransportKind.NETHERNET
            && join.ping() != PingOutcome.PONG
            && join.netherNetProbe() != null
            && join.netherNetProbe().supported()
      );
      this.check("Don't Trust ends the join on the screen it started from", back && this.onClient(() -> this.minecraft.gui.screen() instanceof TitleScreen));
      this.check(
         "no session and no disconnect screen after Don't Trust",
         this.onClient(
            () -> this.minecraft.level == null && this.minecraft.getConnection() == null && !BedrockDisconnects.isDisconnectScreen(this.minecraft.gui.screen())
         )
      );
      this.check(
         "the refused key is not kept",
         ((NetherNetTransport)TransportRegistry.transport(TransportKind.NETHERNET)).trustStore().trustedAt(this.target()) == null
      );
      return true;
   }

   private boolean trustAndPlay() {
      this.join();
      if (KEEP_KEY) {
         log("2 the key stays trusted: no question expected");
      } else {
         ServerTrustScreen question = this.waitForQuestion(60000L);
         this.check("the second join asks again (the key was refused)", question != null);
         if (question == null) {
            return false;
         }

         this.run(() -> question.trustButton().onPress(new KeyEvent(257, 0, 0)));
      }

      if (!this.waitForWorld("2 after Trust and Join", false)) {
         return false;
      }

      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      log("2 joined: {}", join == null ? "no report" : join.choice());
      this.check(
         "joined over NetherNet after the trust (" + (join == null ? null : join.transport()) + ")",
         join != null && join.transport() == TransportKind.NETHERNET && join.connected()
      );
      this.check(
         "the key is kept for the address",
         ((NetherNetTransport)TransportRegistry.transport(TransportKind.NETHERNET)).trustStore().trustedAt(this.target()) != null
      );
      TransportMemory.Entry remembered = BedrockConnector.memory().get(this.address.host(), this.address.port());
      this.check(
         "remembered as NetherNet with its scheme at LOGIN_SUCCESS (" + remembered + ")",
         remembered != null && remembered.kind() == TransportKind.NETHERNET && remembered.scheme() != null
      );
      this.screenshot("nethernet-world");
      Vec3 before = this.onClient(() -> this.minecraft.player.position());
      Vec3 after = before;

      for (int turn = 0; turn < 4 && after.distanceTo(before) <= 1.0; turn++) {
         int quarter = turn;
         this.run(() -> this.minecraft.player.setYRot(this.minecraft.player.getYRot() + 90.0F * Math.min(1, quarter)));
         this.run(() -> this.minecraft.options.keyUp.setDown(true));
         sleep(1500L);
         this.run(() -> this.minecraft.options.keyUp.setDown(false));
         sleep(800L);
         after = this.onClient(() -> this.minecraft.player == null ? before : this.minecraft.player.position());
      }

      Vec3 walkedTo = after;
      log("2 walked from {} to {}", before, walkedTo);
      this.check("walks (" + String.format("%.2f", walkedTo.distanceTo(before)) + " blocks)", walkedTo.distanceTo(before) > 1.0);
      String marker = "BN NetherNet chat " + Integer.toHexString((int)(Math.random() * 1.6777215E7));
      JsonUiHud.forgetLastChat();
      this.run(() -> this.minecraft.getConnection().sendChat(marker));
      boolean echoed = this.waitFor("the chat line back from the server", () -> JsonUiHud.lastChat().contains(marker), 10000L);
      log("2 last chat line: {}", JsonUiHud.lastChat());
      this.check("a chat line comes back from the server", echoed);
      this.screenshot("nethernet-chat");
      long stay = Long.getLong("bedrocknative.nethernetStayMs", 0L);
      if (stay <= 0L) {
         return true;
      }

      long until = System.currentTimeMillis() + stay;

      while (System.currentTimeMillis() < until && this.onClient(() -> this.minecraft.level != null)) {
         sleep(250L);
      }

      boolean stayed = this.onClient(() -> this.minecraft.level != null && this.minecraft.getConnection() != null);
      BedrockDisconnects.Ended last = BedrockDisconnects.last();
      log("2 stayed {} ms more: still connected {}{}", stay, stayed, !stayed && last != null ? ", ended " + last.end().logText() : "");
      this.check("the session stays " + stay + " ms more", stayed);
      return stayed;
   }

   private void rejoin() {
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
      this.waitFor("the menus after leaving", () -> this.onClient(() -> this.minecraft.level == null), 15000L);
      sleep(200L);
      InetSocketAddress server = new InetSocketAddress(this.address.host(), this.address.port());
      long waitLeft = BedrockConnector.netherNetRejoinRemaining(server);
      log("3 rejoin wait left right after leaving: {} ms", waitLeft);
      this.check("leaving noted the rejoin wait (" + waitLeft + " ms left)", waitLeft > 0L);
      long started = System.currentTimeMillis();
      this.join();
      boolean[] asked = new boolean[1];
      boolean joined = this.waitFor("the world after joining again", () -> {
         Screen screen = this.onClient(() -> this.minecraft.gui.screen());
         if (screen instanceof ServerTrustScreen question) {
            asked[0] = true;
            this.run(() -> question.doNotTrustButton().onPress(new KeyEvent(257, 0, 0)));
            return true;
         } else {
            return this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null) || BedrockDisconnects.isDisconnectScreen(screen);
         }
      }, 120000L) && this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null);
      long took = System.currentTimeMillis() - started;
      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      log("3 joined again after {} ms: {}", took, join == null ? "no report" : join.choice());
      this.check("no trust question when joining again (the key is known)", !asked[0]);
      this.check("joined again over NetherNet", joined && join != null && join.transport() == TransportKind.NETHERNET);
      this.check("the connector waited out the rejoin wait (" + took + " ms for the join, " + waitLeft + " ms were left)", took >= waitLeft - 500L);
      if (joined && this.waitForWorld("3 joined again", true)) {
         sleep(8000L);
         int chunks = this.onClient(() -> this.minecraft.level == null ? 0 : this.minecraft.level.getChunkSource().getLoadedChunksCount());
         boolean stayed = this.onClient(
            () -> this.minecraft.level != null && this.minecraft.getConnection() != null && !BedrockDisconnects.isDisconnectScreen(this.minecraft.gui.screen())
         );
         log("3 after 8 s: {} chunks, still connected {}", chunks, stayed);
         this.check("the session stays after joining again (" + chunks + " chunks)", stayed && chunks > 0);
         this.screenshot("nethernet-rejoined");
         if (stayed) {
            this.transfer();
         }

         this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
         sleep(2000L);
      }
   }

   private void transfer() {
      log("4 transfer to {}", this.typed);
      long started = System.currentTimeMillis();
      this.run(() -> this.minecraft.getConnection().handleTransfer(new ClientboundTransferPacket(this.address.host(), this.address.port())));
      boolean[] asked = new boolean[1];
      this.waitFor("the old world to go", () -> this.onClient(() -> this.minecraft.level == null), 15000L);
      boolean joined = this.waitFor("the world after the transfer", () -> {
         Screen screen = this.onClient(() -> this.minecraft.gui.screen());
         if (screen instanceof ServerTrustScreen question) {
            asked[0] = true;
            this.run(() -> question.doNotTrustButton().onPress(new KeyEvent(257, 0, 0)));
            return true;
         } else {
            return this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null) || BedrockDisconnects.isDisconnectScreen(screen);
         }
      }, 120000L) && this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null);
      BedrockConnector.JoinReport join = BedrockConnector.lastJoin();
      log("4 transferred after {} ms: {}", System.currentTimeMillis() - started, join == null ? "no report" : join.choice());
      this.check("no trust question on a transfer to the same operator's key", !asked[0]);
      this.check(
         "the transfer stays on NetherNet, its probe first and no Java (" + (join == null ? null : join.choice()) + ")",
         joined
            && join != null
            && join.transport() == TransportKind.NETHERNET
            && join.choice().startsWith("start [NETHERNET_PROBE]")
            && join.javaStatus() == null
            && !join.javaProbeSent()
      );
      if (joined && this.waitForWorld("4 after the transfer", true)) {
         sleep(5000L);
         boolean stayed = this.onClient(
            () -> this.minecraft.level != null && this.minecraft.getConnection() != null && !BedrockDisconnects.isDisconnectScreen(this.minecraft.gui.screen())
         );
         this.check("the session stays after the transfer", stayed);
         this.screenshot("nethernet-transferred");
      }
   }

   private boolean waitForWorld(String what, boolean quiet) {
      boolean joined = this.waitFor(
            "the world (" + what + ")",
            () -> this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null)
               || BedrockDisconnects.isDisconnectScreen(this.onClient(() -> this.minecraft.gui.screen())),
            120000L
         )
         && this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null);
      if (!quiet) {
         this.check("joins (" + what + ")", joined);
      }

      if (!joined) {
         BedrockDisconnects.Ended last = BedrockDisconnects.last();
         log("{}: no world; last end {}", what, last == null ? "none" : last.end().logText());
         return false;
      }

      boolean loaded = this.waitFor(
         "the loading screen to end (" + what + ")", () -> this.onClient(() -> this.minecraft.gui.screen() == null || this.minecraft.level == null), 60000L
      );
      sleep(1000L);
      boolean inWorld = this.onClient(() -> this.minecraft.level != null && this.minecraft.player != null);
      if (!inWorld) {
         BedrockDisconnects.Ended last = BedrockDisconnects.last();
         this.check("the session lasts past the loading screen (" + what + "; ended " + (last == null ? "?" : last.end().logText()) + ")", false);
      }

      return loaded && inWorld;
   }

   private @Nullable ServerTrustScreen waitForQuestion(long millis) {
      ServerTrustScreen[] found = new ServerTrustScreen[1];
      this.waitFor("Bedrock's trust question", () -> {
         Screen screen = this.onClient(() -> this.minecraft.gui.screen());
         if (screen instanceof ServerTrustScreen question) {
            found[0] = question;
            return true;
         } else {
            return BedrockDisconnects.isDisconnectScreen(screen) || this.onClient(() -> this.minecraft.level != null);
         }
      }, millis);
      if (found[0] == null) {
         BedrockDisconnects.Ended last = BedrockDisconnects.last();
         log(
            "no trust question; screen {}, last end {}",
            this.onClient(() -> this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName()),
            last == null ? "none" : last.end().logText()
         );
      }

      sleep(800L);
      return found[0];
   }

   private String target() {
      return ServerTarget.hostPort(this.address.host(), this.address.port());
   }

   private void join() {
      ServerData server = new ServerData("BN NetherNet test", this.typed, Type.OTHER);
      server.setResourcePackStatus(ServerPackStatus.ENABLED);
      this.run(() -> ConnectScreen.startConnecting(new TitleScreen(), this.minecraft, ServerAddress.parseString(this.typed), server, false, null));
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
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
         BedrockNative.LOGGER.info("Dev nethernet test: PASS {}", what);
      } else {
         this.failed++;
         BedrockNative.LOGGER.warn("Dev nethernet test: FAIL {}", what);
      }
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev nethernet test: " + message, args);
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private <T> T onClient(Supplier<@Nullable T> task) {
      return this.minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
