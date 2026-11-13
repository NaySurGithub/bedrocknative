package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.Window;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.account.AccountManager;
import net.bedrocknative.client.account.AccountWarmUp;
import net.bedrocknative.client.account.AccountsScreen;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.bedrocknative.client.world.VanillaRegistries;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.net.BedrockPing;
import net.bedrocknative.net.ServerLookup;
import net.bedrocknative.net.auth.BedrockSkin;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.net.auth.PrivilegeCheck;
import net.bedrocknative.net.auth.XboxAccount;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.net.auth.XboxPrivileges;
import net.bedrocknative.net.client.ConnectDiagnosis;
import net.bedrocknative.net.client.RakHandshakeTrace;
import net.bedrocknative.net.client.SessionEnd;
import net.bedrocknative.net.nethernet.NetherNetAttempts;
import net.bedrocknative.net.nethernet.NetherNetDiagnosis;
import net.bedrocknative.net.nethernet.NetherNetLinkWatch;
import net.bedrocknative.net.nethernet.NetherNetRejoin;
import net.bedrocknative.net.nethernet.TrustPrompt;
import net.bedrocknative.net.nethernet.UntrustedServerException;
import net.bedrocknative.net.transport.BedrockTransport;
import net.bedrocknative.net.transport.ConnectAttempt;
import net.bedrocknative.net.transport.JavaProbeGate;
import net.bedrocknative.net.transport.JavaStatus;
import net.bedrocknative.net.transport.JavaStatusProbe;
import net.bedrocknative.net.transport.PingOutcome;
import net.bedrocknative.net.transport.ProbeResult;
import net.bedrocknative.net.transport.RakNetTransport;
import net.bedrocknative.net.transport.ServerTarget;
import net.bedrocknative.net.transport.SignalingScheme;
import net.bedrocknative.net.transport.TransportChooser;
import net.bedrocknative.net.transport.TransportKind;
import net.bedrocknative.net.transport.TransportMemory;
import net.bedrocknative.net.transport.TransportRegistry;
import net.bedrocknative.net.transport.TransportTexts;
import net.bedrocknative.net.transport.TransportUnavailableException;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.protocol.BedrockProtocols;
import net.bedrocknative.protocol.BedrockVersion;
import net.bedrocknative.text.BedrockLanguage;
import net.bedrocknative.text.BedrockText;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jspecify.annotations.Nullable;

public final class BedrockConnector {
   public static final String SAVED_SKIN = "bedrock-skin.json";
   private static final AtomicInteger THREAD_ID = new AtomicInteger();
   private static final long PREVIOUS_CLOSE_WAIT_MS = 2000L;
   private static final int LOCATE_TIMEOUT_MS = 1000;
   private static final boolean LOCATE = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.locatePing", "").trim());
   @Nullable
   private static final TransportKind DEV_TRANSPORT = TransportKind.byId(System.getProperty("bedrocknative.transport"));
   private static final Duration JAVA_STATUS_TIMEOUT = Duration.ofMillis(1500L);
   private static final Duration NETHERNET_PROBE_TIMEOUT = Duration.ofMillis(1500L);
   private static final long NETHERNET_PROBE_BOUND_MS = NETHERNET_PROBE_TIMEOUT.toMillis() * 4L + 1000L;
   private static final long NETHERNET_JOIN_BOUND_MS = 15000L;
   private static final NetherNetAttempts NETHERNET_ATTEMPTS = new NetherNetAttempts();
   private static final NetherNetRejoin NETHERNET_REJOIN = NetherNetRejoin.live();
   private static final long PROBES_GUARD_MS = 30000L;
   private static final long JAVA_GRACE_MS = 250L;
   private static final long PING_OVERDUE_MS = 600L;
   private static final int JAVA_TYPICAL_PORT = 25565;
   private static final long PING_FRESH_MS = 3000L;
   private static final String LOCATING_KEY = "progressScreen.message.locating";
   private static final String SIGNING_IN_KEY = "xbox.signingin";
   private static final ThreadLocal<Boolean> HANDING_TO_JAVA = ThreadLocal.withInitial(() -> false);
   private static volatile Channel lastChannel;
   private static volatile boolean loggedStandIn;
   @Nullable
   private static volatile TransportKind lastTransport;
   @Nullable
   private static volatile TransportMemory memory;
   @Nullable
   private static volatile BedrockConnector.JoinReport lastJoin;
   private static final boolean DEV_OFFLINE_IDENTITY = Boolean.getBoolean("bedrocknative.offlineIdentity");

   private BedrockConnector() {
   }

   private static ServerLookup lookUp(String host, int port) {
      return ServerLookup.start(host, port, ServerLookup.SYSTEM, line -> BedrockNative.LOGGER.info(line));
   }

   @Nullable
   private static BedrockPing.Located locate(InetSocketAddress address) {
      try {
         BedrockPing.Located located = BedrockPing.locate(address, 1000);
         BedrockPing.Pong pong = located.pong();
         if (pong == null) {
            BedrockNative.LOGGER.info("Server-list ping of {} from port {}: no answer in {} ms", new Object[]{hostPort(address), located.localPort(), 1000});
         } else {
            BedrockNative.LOGGER
               .info(
                  "Server-list ping of {} from port {}: protocol {} {}, {}/{} players, {} ms",
                  new Object[]{
                     hostPort(address), located.localPort(), pong.protocol(), pong.version(), pong.players(), pong.maxPlayers(), pong.latencyMillis()
                  }
               );
         }

         return located;
      } catch (RuntimeException | IOException var3) {
         BedrockNative.LOGGER.info("Server-list ping of {} failed: {}", hostPort(address), var3.toString());
         return null;
      }
   }

   private static String hostPort(InetSocketAddress address) {
      return (address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress()) + ":" + address.getPort();
   }

   @Nullable
   public static BedrockConnector.JoinReport lastJoin() {
      return lastJoin;
   }

   private static void report(
      BedrockConnector.Entry entry,
      @Nullable InetSocketAddress address,
      TransportKind kind,
      @Nullable BedrockConnector.Attempt attempt,
      BedrockConnector.Probes probes,
      TransportChooser chooser
   ) {
      InetSocketAddress shown = address != null ? address : InetSocketAddress.createUnresolved(entry.host(), entry.bedrockPort());
      lastJoin = attempt == null
         ? new BedrockConnector.JoinReport(
            entry.host(),
            shown,
            0,
            probes.pinged(),
            probes.pong(),
            probes.pingPort(),
            0,
            "",
            kind == TransportKind.JAVA,
            kind,
            chooser.ping(),
            probes.netherNet(),
            probes.java(),
            chooser.trail(),
            probes.javaProbeSent()
         )
         : new BedrockConnector.JoinReport(
            entry.host(),
            shown,
            attempt.attempts(),
            attempt.pinged(),
            attempt.pong(),
            attempt.pingPort(),
            attempt.localPort(),
            attempt.trace().summary(),
            attempt.future().isSuccess(),
            kind,
            chooser.ping(),
            probes.netherNet(),
            probes.java(),
            chooser.trail(),
            probes.javaProbeSent()
         );
   }

   public static void connect(ConnectScreen screen, Minecraft minecraft, ServerAddress hostAndPort, ServerData server, @Nullable TransferState transferState) {
      BedrockDisconnects.connecting();
      BedrockLobby.connecting();
      Thread thread = new Thread(() -> run(screen, minecraft, hostAndPort, server, transferState), "Bedrock Connector #" + THREAD_ID.incrementAndGet());
      thread.setDaemon(true);
      thread.start();
   }

   public static boolean handingToJava() {
      return HANDING_TO_JAVA.get();
   }

   @Nullable
   public static TransportKind lastTransport() {
      return lastTransport;
   }

   public static TransportMemory memory() {
      TransportMemory loaded = memory;
      if (loaded == null) {
         synchronized (BedrockConnector.class) {
            loaded = memory;
            if (loaded == null) {
               loaded = TransportMemory.load(configFolder().resolve("transports.json"));
               memory = loaded;
            }
         }
      }

      return loaded;
   }

   private static XboxAccountStore.Prepared xboxAccount() throws XboxAccountStore.AccountException {
      XboxAccountStore.Prepared prepared = AccountManager.prepareActive();
      BedrockNative.LOGGER.info("Xbox account: {}", XboxAccount.describe(prepared.account()));
      return prepared;
   }

   private static Path configFolder() {
      return FabricLoader.getInstance().getConfigDir().resolve("bedrocknative");
   }

   public static BedrockSkin skin() {
      Path saved = configFolder().resolve("bedrock-skin.json");
      if (Files.isRegularFile(saved)) {
         try {
            BedrockSkin skin = BedrockSkin.load(saved);
            BedrockNative.LOGGER.info("Using the {} saved from the vanilla client ({})", skin.describe(), "bedrock-skin.json");
            return skin;
         } catch (IOException var4) {
            BedrockNative.LOGGER.warn("Couldn't use the saved Bedrock skin, using the classic one: {}", var4.getMessage());
         }
      }

      Path vanillaPack = BedrockInstall.dataFolder().resolve("skin_packs").resolve("vanilla");

      try {
         if (Files.isDirectory(vanillaPack)) {
            BedrockSkin steve = BedrockSkin.classicFromSkinPack(vanillaPack, "steve.png", false);
            logStandIn("the install's classic Steve");
            return steve;
         }
      } catch (Exception var3) {
         BedrockNative.LOGGER.warn("Couldn't read the vanilla Bedrock skin pack, using the default persona", var3);
      }

      logStandIn("vanilla's default persona Steve");
      return BedrockSkin.defaultSteve();
   }

   private static void logStandIn(String which) {
      if (!loggedStandIn) {
         loggedStandIn = true;
         BedrockNative.LOGGER
            .info(
               "Login skin: {} (no {} saved from your vanilla client by the capture relay): others see you, and NPCs wearing your skin, as Steve",
               which,
               "bedrock-skin.json"
            );
      }
   }

   private static ClientIdentity.Settings settings(Minecraft minecraft) {
      Window window = minecraft.getWindow();
      int chosen = (Integer)minecraft.options.guiScale().get();
      boolean unicode = minecraft.isEnforceUnicode();
      return ClientIdentity.Settings.of(
         minecraft.options.languageCode,
         minecraft.options.graphicsPreset().get() == GraphicsPreset.FAST,
         chosen == 0,
         window.calculateScale(chosen, unicode),
         window.calculateScale(0, unicode)
      );
   }

   private static BedrockConnector.Bedrock prepare(Minecraft minecraft, boolean transfer) throws Exception {
      long started = System.nanoTime();
      CompletableFuture<Frozen> registries = VanillaRegistries.get(minecraft);
      BedrockLanguage.baseTexts(minecraft.options.languageCode).ifPresent(BedrockText::useBedrockLanguage);
      long texts = System.nanoTime();
      if (DEV_OFFLINE_IDENTITY) {
         ClientIdentity identity = ClientIdentity.offline("BedrockNative", BedrockSkin.defaultSteve(), () -> settings(minecraft));
         BedrockNative.LOGGER.warn("DEV OFFLINE IDENTITY: joining as {} (self-signed; only for a BDS with online-mode=false)", identity.username());
         return new BedrockConnector.Bedrock(identity, registries, PrivilegeCheck.none());
      } else {
         XboxAccountStore.Prepared account = xboxAccount();
         PrivilegeCheck privileges = transfer ? account.privileges().unwaited() : account.privileges();
         long tokens = System.nanoTime();
         BedrockSkin skin = skin();
         long skinRead = System.nanoTime();
         ClientIdentity identity = ClientIdentity.xbox(account.account(), skin, () -> settings(minecraft));
         long now = System.nanoTime();
         BedrockNative.LOGGER
            .info(
               "Account ready in {} ms: texts {} ms, tokens {} ms, skin {} ms, identity {} ms; multiplayer check {}; token warm-up {}",
               new Object[]{
                  (now - started) / 1000000L,
                  (texts - started) / 1000000L,
                  (tokens - texts) / 1000000L,
                  (skinRead - tokens) / 1000000L,
                  (now - skinRead) / 1000000L,
                  describe(privileges),
                  AccountWarmUp.describe()
               }
            );
         return new BedrockConnector.Bedrock(identity, registries, privileges);
      }
   }

   private static String describe(PrivilegeCheck check) {
      XboxPrivileges.Result answer = check.now();
      if (answer != null) {
         return answer.outcome().name();
      } else {
         return check.waited() ? "running (the join's loading screen waits for it 3000 ms at most)" : "running (not waited for)";
      }
   }

   private static CompletableFuture<BedrockConnector.Bedrock> prepareAsync(Minecraft minecraft, boolean transfer) {
      CompletableFuture<BedrockConnector.Bedrock> future = new CompletableFuture<>();
      Thread.ofVirtual().name("Bedrock account").start(() -> {
         try {
            future.complete(prepare(minecraft, transfer));
         } catch (Throwable var4) {
            future.completeExceptionally(var4);
         }
      });
      return future;
   }

   private static BedrockConnector.Bedrock await(CompletableFuture<BedrockConnector.Bedrock> preparing) throws Exception {
      try {
         return preparing.join();
      } catch (CompletionException var4) {
         if (var4.getCause() instanceof Exception cause) {
            throw cause;
         } else {
            throw var4;
         }
      }
   }

   private static void status(ConnectScreen screen, Minecraft minecraft, String text) {
      minecraft.execute(() -> screen.updateStatus(Component.literal(text)));
   }

   private static void run(ConnectScreen screen, Minecraft minecraft, ServerAddress hostAndPort, ServerData server, @Nullable TransferState transferState) {
      boolean transfer = transferState != null;
      BedrockConnector.Entry entry = BedrockConnector.Entry.of(server.ip, hostAndPort, transfer);
      String host = entry.host();
      InetSocketAddress address = null;
      BedrockConnector.Attempt attempt = null;
      TransportChooser chooser = null;
      BedrockConnector.Probes probes = null;
      ClientBedrockSession session = null;
      AtomicReference<TrustPrompt> asked = new AtomicReference<>();
      SessionTimeline.joinStarted(host + ":" + entry.bedrockPort());

      try {
         String locating = BedrockText.translate("progressScreen.message.locating", List.of());
         status(screen, minecraft, locating.equals("progressScreen.message.locating") ? "Locating server" : locating);
         TransportKind forced = DEV_TRANSPORT != null ? DEV_TRANSPORT : entry.forced();
         ServerLookup lookup = forced != TransportKind.JAVA ? lookUp(host, entry.bedrockPort()) : null;
         TransportMemory.Entry remembered = transfer ? null : memory().get(entry.host(), entry.bedrockPort());
         TransportKind preferred = transfer ? lastTransport : (remembered == null ? null : remembered.kind());
         boolean javaAllowed = !transfer || lastTransport == null || !lastTransport.bedrock();
         chooser = new TransportChooser(
            new TransportChooser.Inputs(
               forced, preferred, BedrockVersion.TRANSPORT_ORDER, TransportRegistry.netherNetAvailable(), entry.portTyped(), javaAllowed
            )
         );
         boolean knownBedrock = forced != null && forced.bedrock() || preferred != null && preferred.bedrock();
         if (knownBedrock) {
            JoinWarmUp.startBedrock("connect");
         }

         CompletableFuture<BedrockConnector.Bedrock> preparing = knownBedrock && ClientBedrockSession.JOIN_AHEAD ? prepareAsync(minecraft, transfer) : null;
         Channel previous = lastChannel;
         if (previous != null && previous.isOpen()) {
            long waited = System.nanoTime();
            previous.closeFuture().awaitUninterruptibly(2000L);
            BedrockNative.LOGGER
               .info(
                  "The previous connection closed in {} ms{}{}",
                  new Object[]{
                     (System.nanoTime() - waited) / 1000000L,
                     previous.isOpen() ? " (still open after the wait; joining anyway)" : "",
                     lookup != null && lookup.done() ? "" : " (the lookup still runs)"
                  }
               );
         }

         if (lookup != null) {
            try {
               address = lookup.await();
               SessionTimeline.mark("address");
            } catch (UnknownHostException var40) {
               if (forced != null) {
                  throw var40;
               }

               BedrockNative.LOGGER.info("No address for {}: only Java's lookup (SRV) is left", host);
            }
         }

         BedrockNative.LOGGER
            .info(
               "Connecting to {}:{}{}{}",
               new Object[]{
                  host,
                  entry.bedrockPort(),
                  forced != null
                     ? ", forced to " + forced.shown()
                     : (preferred != null ? ", " + (transfer ? "previous session" : "last joined") + " over " + preferred.shown() : ""),
                  transfer ? " (Transfer)" : ""
               }
            );
         BedrockConnector.Bedrock bedrock = knownBedrock && preparing == null ? prepare(minecraft, transfer) : null;
         if (bedrock != null) {
            SessionTimeline.mark("account");
         }

         boolean javaImmediate = preferred == TransportKind.JAVA || entry.portTyped() && entry.javaPort() == 25565;
         probes = new BedrockConnector.Probes(chooser, entry, address, remembered == null ? null : remembered.scheme(), javaImmediate);

         while (!screen.aborted) {
            TransportChooser.Step step = chooser.next();
            Objects.requireNonNull(step);
            switch (step) {
               case TransportChooser.Start start:
                  start.probes().forEach(probes::start);
                  break;
               case TransportChooser.Wait wait:
                  probes.awaitOne(screen);
                  break;
               case TransportChooser.Join join when join.kind() == TransportKind.JAVA:
                  report(entry, address, TransportKind.JAVA, null, probes, chooser);
                  handToJava(screen, minecraft, server, entry, transferState, probes.java(), forced == TransportKind.JAVA, chooser);
                  return;
               case TransportChooser.Join joinx:
                  JoinWarmUp.startBedrock("connect");
                  if (address == null) {
                     address = (lookup != null ? lookup : lookUp(host, entry.bedrockPort())).await();
                  }

                  if (bedrock == null) {
                     if (preparing != null ? !preparing.isDone() : AccountWarmUp.running()) {
                        String signingIn = BedrockText.translate("xbox.signingin", List.of());
                        status(screen, minecraft, signingIn.equals("xbox.signingin") ? "Signing in with your Microsoft account..." : signingIn);
                     }

                     bedrock = preparing != null ? await(preparing) : prepare(minecraft, transfer);
                     SessionTimeline.mark("account");
                  }

                  PrivilegeCheck privileges = bedrock.privileges();
                  if (privileges.refuses()) {
                     throw privileges.refusal();
                  }

                  if (session == null) {
                     session = new ClientBedrockSession(minecraft, screen.parent, screen, server, address, bedrock.identity(), bedrock.registries());
                     session.privileges(privileges);
                  }

                  TransportKind joining = joinx.kind();
                  ProbeResult scheme = probes.netherNet();
                  session.whenLoggedIn(() -> rememberLogin(entry, joining, scheme));
                  attempt = joinx.kind() == TransportKind.RAKNET
                     ? rakNet(screen, minecraft, address, bedrock.identity(), session, probes)
                     : netherNet(screen, minecraft, entry, address, forced, bedrock.identity(), session, probes, asked);
                  report(entry, address, joinx.kind(), attempt, probes, chooser);
                  if (attempt.future().isSuccess()) {
                     connected(screen, minecraft, entry, address, attempt, session, chooser);
                     return;
                  }

                  if (screen.aborted) {
                     return;
                  }

                  chooser.joinFailed(joinx.kind(), failureOf(attempt));
                  break;
               case TransportChooser.TrustPrompt prompt:
                  chooser.trust(askTrust(screen, minecraft, entry, attempt, asked));
                  break;
               case TransportChooser.Fail var51:
                  TransportChooser.Fail fail = (TransportChooser.Fail)step;
                  if (preparing != null && preparing.isCompletedExceptionally()) {
                     await(preparing);
                  }

                  if (bedrock != null && bedrock.privileges().refuses()) {
                     throw bedrock.privileges().refusal();
                  }

                  fail(screen, minecraft, server, entry, address, fail.kind(), attempt, probes, chooser);
                  return;
               case TransportChooser.Stop var52:
                  TransportChooser.Stop stop = (TransportChooser.Stop)step;
                  SessionTimeline.joinAbandoned();
                  BedrockNative.LOGGER.info("Join of {}:{} ended by the player ({})", new Object[]{host, entry.bedrockPort(), chooser.trail()});
                  minecraft.execute(() -> {
                     if (minecraft.gui.screen() == screen || minecraft.gui.screen() instanceof ServerTrustScreen) {
                        minecraft.gui.setScreen(screen.parent);
                     }
                  });
                  return;
               default:
                  throw new MatchException(null, null);
            }
         }
      } catch (XboxAccountStore.AccountException var41) {
         SessionTimeline.joinAbandoned();
         if (!screen.aborted) {
            BedrockNative.LOGGER
               .warn(
                  "The Xbox account can't be used ({}{}): {}",
                  new Object[]{var41.problem(), var41.diagnosis() == null ? "" : ", " + var41.diagnosis(), var41.getMessage()}
               );
            minecraft.execute(() -> minecraft.gui.setScreen(AccountsScreen.forFailure(screen.parent, var41)));
         }
      } catch (Exception var42) {
         SessionTimeline.joinAbandoned();
         if (!screen.aborted) {
            ConnectDiagnosis.Explanation explanation = address != null && attempt != null && attempt.kind() == TransportKind.RAKNET
               ? ConnectDiagnosis.explain(var42, host, address, attempt.pinged(), attempt.pong(), attempt.trace())
               : null;
            failed(
               screen, minecraft, server, entry, var42, explanation == null ? null : explanation.full(), explanation == null ? null : explanation.headline()
            );
         }
      } finally {
         if (probes != null) {
            probes.close();
         }
      }
   }

   private static void rememberLogin(BedrockConnector.Entry entry, TransportKind kind, @Nullable ProbeResult probe) {
      if (entry.forced() == null) {
         SignalingScheme scheme = kind == TransportKind.NETHERNET && probe != null ? probe.scheme() : null;
         memory().put(entry.host(), entry.bedrockPort(), kind, scheme, entry.bedrockPort());
      }
   }

   private static BedrockConnector.Attempt rakNet(
      ConnectScreen screen,
      Minecraft minecraft,
      InetSocketAddress address,
      ClientIdentity identity,
      ClientBedrockSession session,
      BedrockConnector.Probes probes
   ) {
      status(screen, minecraft, TransportTexts.connecting(TransportKind.RAKNET));
      BedrockPing.Located located = probes.located();
      boolean pinged = probes.pinged();
      if (pinged && probes.pingAgeMillis() > 3000L) {
         located = locate(address);
         pinged = located != null;
      }

      BedrockConnector.Attempt attempt = rakNetAttempt(
         address, identity, session, located, pinged, 1, protocolFor(located == null ? null : located.pong(), null)
      );
      if (!attempt.future().isSuccess() && !screen.aborted && ConnectDiagnosis.worthAnotherTry(attempt.future().cause(), attempt.pong(), attempt.trace())) {
         BedrockNative.LOGGER.info("{}; the server answers pings, so the join is tried once more", attempt.trace().summary());
         status(screen, minecraft, TransportTexts.connecting(TransportKind.RAKNET).replace("...", "") + ", second try...");
         BedrockPing.Located again = LOCATE ? locate(address) : null;
         attempt = rakNetAttempt(address, identity, session, again, again != null, 2, protocolFor(again == null ? null : again.pong(), null));
      }

      return attempt;
   }

   private static BedrockConnector.Attempt rakNetAttempt(
      InetSocketAddress address,
      ClientIdentity identity,
      ClientBedrockSession session,
      @Nullable BedrockPing.Located located,
      boolean pinged,
      int attempts,
      BedrockProtocol protocol
   ) {
      int localPort = located == null ? 0 : located.localPort();
      ServerTarget target = new ServerTarget(address.getHostString(), address, null);
      RakHandshakeTrace trace = new RakHandshakeTrace();
      ChannelFuture future = RakNetTransport.INSTANCE
         .connect(
            EventLoopGroupHolder.remote(false).eventLoopGroup(),
            target,
            identity,
            session,
            ConnectAttempt.rakNet(trace, localPort > 0 ? new InetSocketAddress(localPort) : null, protocol)
         );
      future.awaitUninterruptibly();
      if (!future.isSuccess() && localPort > 0 && ConnectDiagnosis.bindFailed(future.cause())) {
         BedrockNative.LOGGER.info("RakNet: port {} of the server-list ping is taken now, connecting from another port", localPort);
         trace = new RakHandshakeTrace();
         future = RakNetTransport.INSTANCE
            .connect(EventLoopGroupHolder.remote(false).eventLoopGroup(), target, identity, session, ConnectAttempt.rakNet(trace, null, protocol));
         future.awaitUninterruptibly();
      }

      return new BedrockConnector.Attempt(TransportKind.RAKNET, future, trace, pinged, located == null ? null : located.pong(), localPort, attempts, protocol);
   }

   private static BedrockProtocol protocolFor(@Nullable BedrockPing.Pong pong, @Nullable ProbeResult probe) {
      if (pong != null && pong.protocol() > 0) {
         return BedrockProtocols.forPong(pong.protocol());
      } else {
         return probe != null && probe.status() != null && probe.status().protocol() > 0
            ? BedrockProtocols.forPong(probe.status().protocol())
            : BedrockProtocols.latest();
      }
   }

   private static BedrockConnector.Attempt netherNet(
      ConnectScreen screen,
      Minecraft minecraft,
      BedrockConnector.Entry entry,
      InetSocketAddress address,
      @Nullable TransportKind forced,
      ClientIdentity identity,
      ClientBedrockSession session,
      BedrockConnector.Probes probes,
      AtomicReference<TrustPrompt> asked
   ) throws InterruptedException {
      ProbeResult probe = probes.netherNet();
      BedrockProtocol protocol = protocolFor(probes.pong(), probe);
      waitForRejoin(screen, minecraft, entry, address);
      status(screen, minecraft, TransportTexts.connecting(TransportKind.NETHERNET));
      ChannelFuture future;
      if (!screen.aborted && !NETHERNET_ATTEMPTS.stopping()) {
         try {
            BedrockTransport transport = TransportRegistry.transport(TransportKind.NETHERNET);
            future = transport.connect(
               EventLoopGroupHolder.remote(false).eventLoopGroup(),
               new ServerTarget(entry.host(), address, forced),
               identity,
               session,
               ConnectAttempt.netherNet(
                  probe == null ? ProbeResult.unavailable() : probe,
                  untrusted -> untrusted instanceof UntrustedServerException key && askDuringJoin(screen, minecraft, entry, key, asked),
                  protocol
               )
            );
         } catch (TransportUnavailableException var13) {
            future = failedFuture(var13);
         }

         awaitNetherNet(screen, entry, future);
         if (!future.isSuccess() && future.cause() != null && NetherNetDiagnosis.serverAnswered(future.cause())) {
            NETHERNET_REJOIN.abandoned(address);
         }
      } else {
         future = failedFuture(new ConnectException("The NetherNet join was cancelled"));
      }

      return new BedrockConnector.Attempt(
         TransportKind.NETHERNET, future, new RakHandshakeTrace(), probes.pinged(), probes.pong(), probes.pingPort(), 1, protocol
      );
   }

   private static ChannelFuture failedFuture(Throwable cause) {
      EmbeddedChannel nothing = new EmbeddedChannel();
      ChannelFuture future = nothing.newFailedFuture(cause);
      nothing.close();
      return future;
   }

   private static void awaitNetherNet(ConnectScreen screen, BedrockConnector.Entry entry, ChannelFuture future) {
      long deadline = System.nanoTime() + 15000000000L;

      try (NetherNetAttempts.Tracked tracked = NETHERNET_ATTEMPTS.track(future.channel())) {
         while (!future.awaitUninterruptibly(100L)) {
            boolean cancelled = screen.aborted || NETHERNET_ATTEMPTS.stopping();
            if (cancelled || System.nanoTime() > deadline) {
               BedrockNative.LOGGER
                  .info(
                     "NetherNet: join of {}:{} {}, closing its channel",
                     new Object[]{entry.host(), entry.bedrockPort(), cancelled ? "cancelled" : "not over after 15000 ms"}
                  );
               future.channel().close();
               future.awaitUninterruptibly(2000L);
               return;
            }
         }
      }
   }

   private static void waitForRejoin(ConnectScreen screen, Minecraft minecraft, BedrockConnector.Entry entry, InetSocketAddress address) throws InterruptedException {
      long remaining = NETHERNET_REJOIN.remainingMillis(address);
      if (remaining > 0L) {
         BedrockNative.LOGGER
            .info(
               "NetherNet: {}:{}: {}; waiting {} ms before joining it",
               new Object[]{entry.host(), entry.bedrockPort(), NETHERNET_REJOIN.reason(address), remaining}
            );
         long shown = -1L;

         while ((remaining = NETHERNET_REJOIN.remainingMillis(address)) > 0L && !screen.aborted && !NETHERNET_ATTEMPTS.stopping()) {
            long seconds = (remaining + 999L) / 1000L;
            if (seconds != shown) {
               shown = seconds;
               status(screen, minecraft, "Joining in " + seconds + " s (the server is still closing the last connection)...");
            }

            Thread.sleep(Math.min(remaining, 200L));
         }
      }
   }

   private static TransportChooser.JoinFailure failureOf(BedrockConnector.Attempt attempt) {
      if (attempt.kind() == TransportKind.RAKNET) {
         return attempt.trace().answered() ? TransportChooser.JoinFailure.RAKNET_ANSWERED : TransportChooser.JoinFailure.RAKNET_UNANSWERED;
      } else {
         Throwable cause = attempt.future().cause();
         return cause == null ? TransportChooser.JoinFailure.NETHERNET_LINK : NetherNetDiagnosis.kind(cause);
      }
   }

   public static void shutdown() {
      NETHERNET_ATTEMPTS.stop();
   }

   static long netherNetRejoinRemaining(InetSocketAddress server) {
      return NETHERNET_REJOIN.remainingMillis(server);
   }

   private static boolean askDuringJoin(
      ConnectScreen screen, Minecraft minecraft, BedrockConnector.Entry entry, UntrustedServerException untrusted, AtomicReference<TrustPrompt> asked
   ) {
      if (!screen.aborted && !NETHERNET_ATTEMPTS.stopping()) {
         TrustPrompt prompt = ServerTrustScreen.ask(minecraft, screen, untrusted);
         asked.set(prompt);
         return awaitTrust(screen, entry, prompt);
      } else {
         return false;
      }
   }

   private static boolean askTrust(
      ConnectScreen screen, Minecraft minecraft, BedrockConnector.Entry entry, @Nullable BedrockConnector.Attempt attempt, AtomicReference<TrustPrompt> asked
   ) {
      TrustPrompt during = asked.getAndSet(null);
      if (during != null) {
         return awaitTrust(screen, entry, during);
      } else {
         UntrustedServerException untrusted = attempt == null ? null : UntrustedServerException.in(attempt.future().cause());
         if (untrusted == null) {
            BedrockNative.LOGGER.warn("NetherNet: asked to trust {}:{} without an untrusted key, not trusted", entry.host(), entry.bedrockPort());
            return false;
         } else {
            return awaitTrust(screen, entry, ServerTrustScreen.ask(minecraft, screen, untrusted));
         }
      }
   }

   private static boolean awaitTrust(ConnectScreen screen, BedrockConnector.Entry entry, TrustPrompt prompt) {
      boolean var5;
      try (NetherNetAttempts.Tracked tracked = NETHERNET_ATTEMPTS.track(prompt)) {
         boolean trusted = prompt.await(() -> screen.aborted || NETHERNET_ATTEMPTS.stopping());
         if (!trusted) {
            BedrockNative.LOGGER
               .info(
                  "NetherNet: join of {}:{} ended at the trust question{}",
                  new Object[]{entry.host(), entry.bedrockPort(), screen.aborted ? "" : " (no answer: cancelled or the game is closing)"}
               );
         }

         var5 = trusted;
      }

      return var5;
   }

   private static void connected(
      ConnectScreen screen,
      Minecraft minecraft,
      BedrockConnector.Entry entry,
      InetSocketAddress address,
      BedrockConnector.Attempt attempt,
      ClientBedrockSession session,
      TransportChooser chooser
   ) {
      ChannelFuture future = attempt.future();
      Channel channel = future.channel();
      lastChannel = channel;
      channel.closeFuture().addListener(closed -> {
         if (lastChannel == channel) {
            lastChannel = null;
         }
      });
      lastTransport = attempt.kind();
      SessionTimeline.mark("transport");
      BedrockNative.LOGGER
         .info(
            "Joined {}:{} over {} as Bedrock {} ({}){}",
            new Object[]{
               entry.host(),
               entry.bedrockPort(),
               attempt.kind().shown(),
               attempt.protocol().shownVersion(),
               chooser.trail(),
               attempt.kind() == TransportKind.RAKNET ? "; " + attempt.trace().summary() : ""
            }
         );
      if (attempt.kind() == TransportKind.NETHERNET) {
         long openedAt = System.nanoTime();
         future.channel()
            .closeFuture()
            .addListener(
               closed -> {
                  NETHERNET_REJOIN.left(address);
                  BedrockNative.LOGGER
                     .info(
                        "NetherNet: link to {}:{} closed after {} ms: {}",
                        new Object[]{
                           entry.host(), entry.bedrockPort(), (System.nanoTime() - openedAt) / 1000000L, NetherNetLinkWatch.describe(future.channel())
                        }
                     );
               }
            );
      }

      session.attach(future.channel());
      if (screen.aborted) {
         future.channel().close();
      } else {
         status(screen, minecraft, "Logging in...");
         future.channel().closeFuture().addListener(closed -> session.onDisconnect("Connection closed"));
      }
   }

   private static void handToJava(
      ConnectScreen screen,
      Minecraft minecraft,
      ServerData server,
      BedrockConnector.Entry entry,
      @Nullable TransferState transferState,
      @Nullable JavaStatus status,
      boolean forced,
      TransportChooser chooser
   ) {
      boolean javaToJava = transferState != null && lastTransport == TransportKind.JAVA;
      lastTransport = TransportKind.JAVA;
      SessionTimeline.joinAbandoned();
      if (!forced && entry.forced() == null && status != null && status.answered() && status.address() != null) {
         memory().put(entry.host(), entry.bedrockPort(), TransportKind.JAVA, null, status.address().getPort());
      }

      BedrockNative.LOGGER
         .info(
            "Joining {}:{} as a Java Edition server ({}): vanilla's connection and login{}",
            new Object[]{
               entry.host(),
               entry.javaPort(),
               forced ? "forced" : chooser.trail(),
               transferState == null ? "" : (javaToJava ? ", Java transfer" : ", plain login after a Bedrock transfer")
            }
         );
      ServerAddress javaAddress = new ServerAddress(entry.host(), entry.javaPort());
      TransferState state = javaToJava ? transferState : null;
      minecraft.execute(() -> {
         if (!screen.aborted) {
            screen.updateStatus(Component.translatable("connect.connecting"));
            HANDING_TO_JAVA.set(true);

            try {
               screen.connect(minecraft, javaAddress, server, state);
            } finally {
               HANDING_TO_JAVA.set(false);
            }
         }
      });
   }

   private static void fail(
      ConnectScreen screen,
      Minecraft minecraft,
      ServerData server,
      BedrockConnector.Entry entry,
      @Nullable InetSocketAddress address,
      TransportKind kind,
      @Nullable BedrockConnector.Attempt attempt,
      BedrockConnector.Probes probes,
      TransportChooser chooser
   ) {
      BedrockNative.LOGGER.info("No way into {}:{} ({})", new Object[]{entry.host(), entry.bedrockPort(), chooser.trail()});
      SessionTimeline.joinAbandoned();
      report(entry, address, kind, attempt, probes, chooser);
      if (kind == TransportKind.RAKNET && attempt != null && attempt.kind() == TransportKind.RAKNET && address != null) {
         Throwable failure = attempt.future().cause();
         ConnectDiagnosis.Explanation explanation = ConnectDiagnosis.explain(failure, entry.host(), address, attempt.pinged(), attempt.pong(), attempt.trace());
         boolean bedrockAnswered = attempt.pong() != null || attempt.trace().answered();
         String javaAddress = chooser.inputs().javaAllowed() && entry.forced() == null
            ? entry.host() + (entry.portTyped() ? ":" + entry.javaPort() : "")
            : null;
         String tcp = TransportTexts.nothingAnsweredSuffix(
            bedrockAnswered, probes.java(), entry.javaPort(), probes.netherNet(), entry.bedrockPort(), javaAddress
         );
         String headline = explanation.headline() + tcp;
         String full = explanation.handshake().isEmpty() ? headline : headline + "; " + explanation.handshake();
         failed(screen, minecraft, server, entry, failure, full, headline);
      } else {
         Throwable failure = attempt != null && attempt.kind() == TransportKind.NETHERNET ? attempt.future().cause() : null;
         NetherNetDiagnosis.Explanation words = NetherNetDiagnosis.forJoin(probes.netherNet(), failure, entry.bedrockPort());
         failed(
            screen, minecraft, server, entry, (Throwable)(failure != null ? failure : new ConnectException(words.headline())), words.full(), words.headline()
         );
      }
   }

   private static void failed(
      ConnectScreen screen, Minecraft minecraft, ServerData server, BedrockConnector.Entry entry, Throwable e, @Nullable String full, @Nullable String headline
   ) {
      if (full != null) {
         BedrockNative.LOGGER.error("Couldn't connect to Bedrock server: {}", full, e);
      } else {
         BedrockNative.LOGGER.error("Couldn't connect to Bedrock server", e);
      }

      Throwable cause = e.getCause() != null ? e.getCause() : e;
      SessionEnd end = SessionEnd.connectFailed(e, full);
      String said = headline != null ? headline : (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
      minecraft.execute(() -> {
         BedrockDisconnects.ended(end, server, entry.host(), entry.bedrockPort(), screen.parent);
         minecraft.gui.setScreen(new DisconnectedScreen(screen.parent, Component.translatable("connect.failed"), Component.literal(said)));
      });
   }

   @Nullable
   private static InetSocketAddress vanillaLookup(String host, int port) {
      return ServerNameResolver.DEFAULT
         .resolveAddress(new ServerAddress(host, port))
         .<InetSocketAddress>map(ResolvedServerAddress::asInetSocketAddress)
         .orElse(null);
   }

   private record Attempt(
      TransportKind kind,
      ChannelFuture future,
      RakHandshakeTrace trace,
      boolean pinged,
      @Nullable BedrockPing.Pong pong,
      int pingPort,
      int attempts,
      BedrockProtocol protocol
   ) {
      int localPort() {
         return this.future.channel().localAddress() instanceof InetSocketAddress local ? local.getPort() : 0;
      }
   }

   private record Bedrock(ClientIdentity identity, CompletableFuture<Frozen> registries, PrivilegeCheck privileges) {
   }

   record Entry(String host, int bedrockPort, int javaPort, boolean portTyped, @Nullable TransportKind forced) {
      static BedrockConnector.Entry of(String stored, ServerAddress hostAndPort, boolean transfer) {
         if (transfer) {
            return new BedrockConnector.Entry(hostAndPort.getHost(), hostAndPort.getPort(), hostAndPort.getPort(), true, null);
         } else {
            BedrockAddress typed = BedrockAddress.parse(stored);
            boolean portTyped = BedrockAddress.portTyped(stored);
            int javaPort = !portTyped && typed.forced() != TransportKind.JAVA ? 25565 : typed.port();
            return new BedrockConnector.Entry(typed.host(), typed.port(), javaPort, portTyped, typed.forced());
         }
      }
   }

   public record JoinReport(
      String host,
      InetSocketAddress address,
      int attempts,
      boolean pinged,
      @Nullable BedrockPing.Pong pong,
      int pingPort,
      int handshakePort,
      String handshake,
      boolean connected,
      TransportKind transport,
      @Nullable PingOutcome ping,
      @Nullable ProbeResult netherNetProbe,
      @Nullable JavaStatus javaStatus,
      String choice,
      boolean javaProbeSent
   ) {
   }

   private static final class Probes {
      private final TransportChooser chooser;
      private final BedrockConnector.Entry entry;
      @Nullable
      private final InetSocketAddress address;
      @Nullable
      private final SignalingScheme pinnedScheme;
      private final boolean javaImmediate;
      private final BlockingQueue<Runnable> outcomes = new LinkedBlockingQueue<>();
      @Nullable
      private BedrockPing.Located located;
      private long pingedAt;
      private long pingStartedAt;
      private boolean overdueReported;
      @Nullable
      private JavaStatus javaStatus;
      @Nullable
      private JavaProbeGate javaGate;
      @Nullable
      private ProbeResult netherNet;
      @Nullable
      private volatile CompletableFuture<ProbeResult> netherNetAsked;

      Probes(
         TransportChooser chooser,
         BedrockConnector.Entry entry,
         @Nullable InetSocketAddress address,
         @Nullable SignalingScheme pinnedScheme,
         boolean javaImmediate
      ) {
         this.chooser = chooser;
         this.entry = entry;
         this.address = address;
         this.pinnedScheme = pinnedScheme;
         this.javaImmediate = javaImmediate;
      }

      void start(TransportChooser.Probe probe) {
         switch (probe) {
            case RAKNET_PING:
               this.pingStartedAt = System.nanoTime();
               if (this.address == null || !BedrockConnector.LOCATE) {
                  this.outcomes.add(() -> this.pingEnded(null, PingOutcome.NOT_PINGED));
                  return;
               }

               InetSocketAddress target = this.address;
               Thread.ofVirtual().name("Bedrock locating ping").start(() -> {
                  BedrockPing.Located result = BedrockConnector.locate(target);
                  long at = System.nanoTime();
                  this.outcomes.add(() -> {
                     this.pingedAt = at;
                     this.pingEnded(result, PingOutcome.of(result));
                  });
               });
               break;
            case JAVA_STATUS:
               boolean now = this.javaImmediate || this.chooser.ping() != null;
               JavaProbeGate gate = new JavaProbeGate(this::launchJava, now ? 0L : 250L);
               this.javaGate = gate;
               if (now) {
                  gate.tick();
               }
               break;
            case NETHERNET_PROBE:
               if (this.address == null) {
                  this.outcomes.add(() -> {
                     this.netherNet = ProbeResult.failed(ProbeResult.Reason.ERROR, List.of(), 0L);
                     this.chooser.probed(this.netherNet);
                  });
                  return;
               }

               CompletableFuture<ProbeResult> asked = TransportRegistry.netherNetProbe()
                  .probe(this.entry.host(), this.address, this.pinnedScheme, BedrockConnector.NETHERNET_PROBE_TIMEOUT);
               this.netherNetAsked = asked;
               asked.copy()
                  .completeOnTimeout(
                     ProbeResult.failed(ProbeResult.Reason.TIMEOUT, List.of(), BedrockConnector.NETHERNET_PROBE_BOUND_MS),
                     BedrockConnector.NETHERNET_PROBE_BOUND_MS,
                     TimeUnit.MILLISECONDS
                  )
                  .whenComplete((result, error) -> this.outcomes.add(() -> {
                     this.netherNet = result != null ? result : ProbeResult.failed(ProbeResult.Reason.ERROR, List.of(), 0L);
                     if (this.netherNet.reason() != ProbeResult.Reason.UNAVAILABLE) {
                        BedrockNative.LOGGER.info("{} for {}:{}", new Object[]{this.netherNet, this.entry.host(), this.entry.bedrockPort()});
                     }

                     this.chooser.probed(this.netherNet);
                  }));
         }
      }

      private void pingEnded(@Nullable BedrockPing.Located result, PingOutcome outcome) {
         this.located = result;
         SessionTimeline.mark("ping");
         this.chooser.pinged(outcome);
         JavaProbeGate gate = this.javaGate;
         if (gate != null) {
            if (outcome == PingOutcome.PONG) {
               gate.withdraw();
            } else {
               gate.launchNow();
            }
         }
      }

      private CompletableFuture<JavaStatus> launchJava() {
         CompletableFuture<JavaStatus> future = JavaStatusProbe.probeAsync(
            this.entry.host(),
            this.entry.javaPort(),
            BedrockConnector::vanillaLookup,
            SharedConstants.getProtocolVersion(),
            BedrockConnector.JAVA_STATUS_TIMEOUT,
            BedrockConnector.JAVA_STATUS_TIMEOUT,
            JavaStatusProbe.Options.JOIN
         );
         future.whenComplete((status, error) -> {
            if (!future.isCancelled() && (this.javaGate == null || !this.javaGate.withdrawn())) {
               this.outcomes.add(() -> {
                  this.javaStatus = status != null ? status : JavaStatus.none(null, String.valueOf(error));
                  BedrockNative.LOGGER.info("{}", this.javaStatus);
                  this.chooser.javaStatus(this.javaStatus.answered());
               });
            }
         });
         return future;
      }

      void close() {
         JavaProbeGate gate = this.javaGate;
         if (gate != null) {
            gate.withdraw();
         }

         CompletableFuture<ProbeResult> asked = this.netherNetAsked;
         if (asked != null && !asked.isDone()) {
            asked.cancel(true);
         }
      }

      boolean javaProbeSent() {
         return this.javaGate != null && this.javaGate.launched();
      }

      void awaitOne(ConnectScreen screen) throws InterruptedException, ConnectTimeoutException {
         long deadline = System.currentTimeMillis() + 30000L;

         while (!screen.aborted) {
            JavaProbeGate gate = this.javaGate;
            if (gate != null) {
               gate.tick();
            }

            if (!this.overdueReported && this.pingStartedAt != 0L && this.chooser.ping() == null && (System.nanoTime() - this.pingStartedAt) / 1000000L >= 600L
               )
             {
               this.overdueReported = true;
               this.chooser.pingOverdue();
               return;
            }

            Runnable outcome = this.outcomes.poll(25L, TimeUnit.MILLISECONDS);
            if (outcome != null) {
               outcome.run();
               return;
            }

            if (System.currentTimeMillis() > deadline) {
               throw new ConnectTimeoutException("The server probes did not end in 30000 ms");
            }
         }
      }

      @Nullable
      BedrockPing.Located located() {
         return this.located;
      }

      boolean pinged() {
         return this.located != null;
      }

      long pingAgeMillis() {
         return this.located == null ? 0L : (System.nanoTime() - this.pingedAt) / 1000000L;
      }

      @Nullable
      BedrockPing.Pong pong() {
         return this.located == null ? null : this.located.pong();
      }

      int pingPort() {
         return this.located == null ? 0 : this.located.localPort();
      }

      @Nullable
      JavaStatus java() {
         return this.javaStatus;
      }

      @Nullable
      ProbeResult netherNet() {
         return this.netherNet;
      }
   }
}
