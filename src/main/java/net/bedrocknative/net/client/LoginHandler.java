package net.bedrocknative.net.client;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.camera.CameraPresetBook;
import net.bedrocknative.content.UrlGuard;
import net.bedrocknative.net.BedrockCrypto;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.net.nethernet.NetherNetBedrockPeer;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.ServerBlockPalette;
import net.bedrocknative.world.chunk.cache.BlobCaches;
import net.bedrocknative.world.item.ItemMappings;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackDataInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.Status;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.jspecify.annotations.Nullable;

final class LoginHandler implements BedrockPacketHandler {
   static final int MAX_EARLY_PACKETS = 512;
   static final String NETHERNET_ENCRYPTION = "bedrocknative.nethernetEncryption";
   static final String NETHERNET_HANDSHAKE_WAIT = "bedrocknative.nethernetHandshakeWaitMs";
   static final long HANDSHAKE_WAIT_MILLIS = 10000L;
   static final String NETHERNET_STALLED = "The server sent an encryption handshake and did not continue; it may require encryption";
   private final BedrockClientSession session;
   private final ClientIdentity identity;
   private final InetSocketAddress address;
   private final BedrockProtocol protocol;
   private final BedrockGameListener listener;
   private final ResourcePackPhase packs;
   private final List<BedrockPacket> beforeStartGame = new ArrayList<>();
   private final ItemPacketHold itemPackets;
   @Nullable
   private Map<Integer, String> lastRegistry;
   private LoginHandler.State state = LoginHandler.State.NETWORK_SETTINGS;
   @Nullable
   private ScheduledFuture<?> handshakeGuard;

   LoginHandler(final BedrockClientSession session, ClientIdentity identity, InetSocketAddress address, BedrockGameListener listener, BedrockProtocol protocol) {
      this.session = session;
      this.identity = identity;
      this.address = address;
      this.protocol = protocol;
      this.listener = listener;
      this.itemPackets = new ItemPacketHold(reason -> {
         this.close(SessionEnd.client(DisconnectFailReason.BAD_PACKET, reason));
         session.disconnect(reason);
      });
      session.getPeer().getChannel().pipeline().addBefore("bedrock-packet-codec", "bedrocknative-item-hold", this.itemPackets);
      this.packs = new ResourcePackPhase(new ResourcePackPhase.Server() {
         {
            Objects.requireNonNull(LoginHandler.this);
         }

         @Override
         public void send(BedrockPacket packet) {
            session.sendPacketImmediately(packet);
         }

         @Override
         public void fail(@Nullable String message) {
            String reason = message != null ? message : "Left from the resource pack prompt";
            LoginHandler.this.close(message != null ? SessionEnd.client(DisconnectFailReason.RESOURCE_PACK_PROBLEM, message) : SessionEnd.left(reason));
            session.disconnect(reason);
         }
      }, listener, serverGuard(address));
   }

   private static UrlGuard serverGuard(InetSocketAddress address) {
      return address.getAddress() == null ? UrlGuard.PUBLIC_ONLY : UrlGuard.forServer(address.getAddress());
   }

   void start() {
      RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
      request.setProtocolVersion(this.protocol.protocol());
      this.session.sendPacketImmediately(request);
   }

   @Override
   public PacketSignal handlePacket(BedrockPacket packet) {
      this.stopWatchingHandshake();
      if (packet instanceof PacketViolationWarningPacket warning) {
         BedrockNative.LOGGER
            .error(
               "Server rejected our packet id {} ({} {}): {}",
               new Object[]{warning.getPacketCauseId(), warning.getSeverity(), warning.getType(), warning.getContext()}
            );
         return PacketSignal.HANDLED;
      } else {
         if (packet instanceof CameraPresetsPacket presets) {
            this.session.getPeer().getCodecHelper().setCameraPresetDefinitions(CameraPresetBook.definitions(presets.getPresets()));
         }

         if (this.state == LoginHandler.State.GAME) {
            if (packet instanceof ItemComponentPacket items) {
               this.onItemRegistry(items);
               return PacketSignal.HANDLED;
            } else {
               this.listener.onGamePacket(this.session, packet);
               return PacketSignal.HANDLED;
            }
         } else {
            switch (packet) {
               case NetworkSettingsPacket settings:
                  this.onNetworkSettings(settings);
                  break;
               case ServerToClientHandshakePacket handshake:
                  this.onServerHandshake(handshake);
                  break;
               case PlayStatusPacket status:
                  this.onPlayStatus(status);
                  break;
               case ResourcePacksInfoPacket info:
                  this.packs.execute(() -> this.packs.onInfo(info));
                  break;
               case ResourcePackDataInfoPacket infox:
                  this.packs.execute(() -> this.packs.onDataInfo(info));
                  break;
               case ResourcePackChunkDataPacket chunk:
                  byte[] data = ByteBufUtil.getBytes(chunk.getData());
                  UUID id = chunk.getPackId();
                  int index = chunk.getChunkIndex();
                  long offset = chunk.getProgress();
                  this.packs.execute(() -> this.packs.onChunkData(id, index, offset, data));
                  break;
               case ResourcePackStackPacket stack:
                  this.packs.execute(() -> this.packs.onStack(stack));
                  break;
               case StartGamePacket startGame:
                  this.state = LoginHandler.State.GAME;
                  this.packs.close();
                  ServerBlockPalette blocks = BlockMappings.get()
                     .useServerBlocks(startGame.isBlockNetworkIdsHashed(), startGame.getBlockProperties(), this.protocol);
                  BedrockNative.LOGGER
                     .info(
                        "Server blocks: {} definitions, {} custom states, ids {}",
                        new Object[]{startGame.getBlockProperties().size(), blocks.customStateCount(), blocks.numbering()}
                     );
                  if (BedrockNative.LOGGER.isDebugEnabled()) {
                     startGame.getBlockProperties()
                        .forEach(
                           definition -> BedrockNative.LOGGER
                              .debug(
                                 "Block definition {}: keys {}, properties {}, traits {}",
                                 new Object[]{
                                    definition.getName(),
                                    definition.getProperties().keySet(),
                                    definition.getProperties().get("properties"),
                                    definition.getProperties().get("traits")
                                 }
                              )
                        );
                  }

                  this.listener.onGamePacket(this.session, startGame);
                  this.itemPackets.startGame();
                  synchronized (this.beforeStartGame) {
                     BedrockNative.LOGGER.debug("Applying {} game packets sent before StartGame", this.beforeStartGame.size());

                     for (BedrockPacket early : this.beforeStartGame) {
                        try {
                           if (early instanceof ItemComponentPacket items) {
                              this.onItemRegistry(items);
                           } else {
                              this.listener.onGamePacket(this.session, early);
                           }
                        } finally {
                           ReferenceCountUtil.release(early);
                        }
                     }

                     this.beforeStartGame.clear();
                     break;
                  }
               case DisconnectPacket disconnect:
                  this.close(
                     SessionEnd.disconnectPacket(
                        disconnect.getReason(), disconnect.isMessageSkipped(), disconnect.getKickMessage(), disconnect.getFilteredMessage()
                     )
                  );
                  break;
               default:
                  if (packet instanceof DimensionDataPacket && this.state == LoginHandler.State.RESOURCE_PACKS) {
                     this.listener.onGamePacket(this.session, packet);
                  } else if (this.state == LoginHandler.State.RESOURCE_PACKS) {
                     boolean overflow;
                     synchronized (this.beforeStartGame) {
                        overflow = this.beforeStartGame.size() >= 512;
                        if (!overflow) {
                           this.beforeStartGame.add((BedrockPacket)ReferenceCountUtil.retain(packet));
                        }
                     }

                     if (overflow) {
                        String reason = "Server sent too many packets before StartGame";
                        this.close(SessionEnd.client(DisconnectFailReason.BAD_PACKET, reason));
                        this.session.disconnect(reason);
                     }
                  } else {
                     BedrockNative.LOGGER.debug("Ignoring {} during login state {}", packet.getPacketType(), this.state);
                  }
            }

            return PacketSignal.HANDLED;
         }
      }
   }

   private void onNetworkSettings(NetworkSettingsPacket settings) {
      this.session.setCompression(settings.getCompressionAlgorithm());
      this.state = LoginHandler.State.LOGIN;
      this.session.sendPacketImmediately(this.identity.createLoginPacket(this.address.getHostString(), this.address.getPort(), this.protocol));
   }

   private void onServerHandshake(ServerToClientHandshakePacket handshake) {
      try {
         BedrockCrypto.SelfSignedJwt jwt = BedrockCrypto.verifySelfSignedJwt(handshake.getJwt());
         boolean netherNet = this.session.getPeer() instanceof NetherNetBedrockPeer;
         if (netherNet && !netherNetEncryption()) {
            BedrockNative.LOGGER.info("NetherNet: encryption handshake answered without Bedrock encryption (DTLS protects the channel)");
            this.state = LoginHandler.State.ENCRYPTION;
            this.session.sendPacketImmediately(new ClientToServerHandshakePacket());
            this.watchHandshake();
            return;
         }

         if (netherNet) {
            BedrockNative.LOGGER.info("NetherNet: encryption handshake honoured (-D{}=on)", "bedrocknative.nethernetEncryption");
         }

         byte[] salt = Base64.getDecoder().decode(JsonParser.parseString(jwt.payload()).getAsJsonObject().get("salt").getAsString());
         SecretKey key = BedrockCrypto.deriveKey(this.identity.sessionKeyPair().getPrivate(), jwt.signerKey(), salt);
         BedrockCrypto.enableEncryption(this.session.getPeer(), key);
         this.state = LoginHandler.State.ENCRYPTION;
         this.session.sendPacketImmediately(new ClientToServerHandshakePacket());
      } catch (Exception var6) {
         BedrockNative.LOGGER.error("Bedrock encryption handshake failed", var6);
         this.close(SessionEnd.client(DisconnectFailReason.UNRECOVERABLE_ERROR, "Encryption handshake failed: " + var6.getMessage()));
         this.session.disconnect("Encryption handshake failed");
      }
   }

   private static boolean netherNetEncryption() {
      return "on".equalsIgnoreCase(System.getProperty("bedrocknative.nethernetEncryption", "").trim());
   }

   private static long handshakeWaitMillis() {
      try {
         return Math.max(1L, Long.parseLong(System.getProperty("bedrocknative.nethernetHandshakeWaitMs", "").trim()));
      } catch (NumberFormatException var1) {
         return 10000L;
      }
   }

   private void watchHandshake() {
      long wait = handshakeWaitMillis();
      this.handshakeGuard = this.session
         .getPeer()
         .getChannel()
         .eventLoop()
         .schedule(
            () -> {
               if (this.state == LoginHandler.State.ENCRYPTION) {
                  BedrockNative.LOGGER.error("NetherNet: nothing came {} ms after the encryption handshake was answered plain", wait);
                  this.close(
                     SessionEnd.client(
                        DisconnectFailReason.UNRECOVERABLE_ERROR, "The server sent an encryption handshake and did not continue; it may require encryption"
                     )
                  );
                  this.session.disconnect("The server sent an encryption handshake and did not continue; it may require encryption");
               }
            },
            wait,
            TimeUnit.MILLISECONDS
         );
   }

   private void stopWatchingHandshake() {
      ScheduledFuture<?> guard = this.handshakeGuard;
      if (guard != null) {
         this.handshakeGuard = null;
         guard.cancel(false);
      }
   }

   private void onPlayStatus(PlayStatusPacket status) {
      if (status.getStatus() == Status.LOGIN_SUCCESS) {
         if (this.state == LoginHandler.State.LOGIN && this.session.getPeer() instanceof NetherNetBedrockPeer) {
            BedrockNative.LOGGER.info("NetherNet: no encryption handshake (DTLS protects the channel)");
         }

         this.state = LoginHandler.State.RESOURCE_PACKS;

         try {
            this.listener.onLoginSuccess();
         } catch (RuntimeException var3) {
            BedrockNative.LOGGER.warn("Login success listener failed", var3);
         }

         ClientCacheStatusPacket cache = new ClientCacheStatusPacket();
         cache.setSupported(BlobCaches.enabled());
         this.session.sendPacket(cache);
         if (cache.isSupported()) {
            BlobCaches.warmUp();
         }
      } else {
         this.close(SessionEnd.loginFailed(status.getStatus()));
      }
   }

   private void onItemRegistry(ItemComponentPacket items) {
      boolean installed = this.registerItems(items);

      try {
         this.listener.onGamePacket(this.session, items);
      } finally {
         if (installed) {
            this.itemPackets.release();
         } else {
            this.itemPackets.registryFailed();
         }
      }
   }

   private boolean registerItems(ItemComponentPacket items) {
      ItemRegistryEntries entries;
      try {
         entries = ItemRegistryEntries.of(items.getItems());
         if (!entries.duplicates().isEmpty()) {
            BedrockNative.LOGGER
               .warn(
                  "Item registry lists {} runtime ids more than once, the later entry kept: {}",
                  entries.duplicates().size(),
                  entries.duplicates().stream().limit(8L).map(ItemRegistryEntries.Duplicate::toString).collect(Collectors.joining(", "))
                     + (entries.duplicates().size() > 8 ? ", ..." : "")
               );
         }

         this.session.getPeer().getCodecHelper().setItemDefinitions(SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(entries.definitions()).build());
      } catch (RuntimeException var6) {
         BedrockNative.LOGGER
            .error("Item registry of {} entries could not be installed; item packets are dropped until one can be", items.getItems().size(), var6);
         return false;
      }

      Map<Integer, String> identifiers = entries.identifiers();
      if (this.lastRegistry != null) {
         BedrockNative.LOGGER
            .info(
               "Item registry sent again ({} items, was {}): {}",
               new Object[]{identifiers.size(), this.lastRegistry.size(), ItemRegistryEntries.changes(this.lastRegistry, identifiers)}
            );
      }

      this.lastRegistry = identifiers;

      try {
         ItemMappings.registerDefinitions(entries.definitions());
         List<ItemDefinition> custom = entries.definitions().stream().filter(item -> !item.getIdentifier().startsWith("minecraft:")).toList();
         BedrockNative.LOGGER
            .info(
               "Item registry: {} items, {} custom ({} component-based, versions {})",
               new Object[]{
                  entries.definitions().size(),
                  custom.size(),
                  custom.stream().filter(ItemDefinition::isComponentBased).count(),
                  custom.stream().collect(Collectors.groupingBy(item -> String.valueOf(item.getVersion()), TreeMap::new, Collectors.counting()))
               }
            );
         if (BedrockNative.LOGGER.isDebugEnabled()) {
            custom.forEach(
               item -> BedrockNative.LOGGER
                  .debug(
                     "Custom item {} ({}): components {}",
                     new Object[]{
                        item.getIdentifier(),
                        item.getRuntimeId(),
                        item.getComponentData() == null ? "none" : item.getComponentData().getCompound("components", NbtMap.EMPTY).keySet()
                     }
                  )
            );
         }
      } catch (RuntimeException var5) {
         BedrockNative.LOGGER.error("Item registry installed for the network, but its item mappings failed", var5);
      }

      return true;
   }

   private void close(SessionEnd end) {
      if (this.state != LoginHandler.State.CLOSED) {
         this.state = LoginHandler.State.CLOSED;
         this.stopWatchingHandshake();
         this.packs.close();
         synchronized (this.beforeStartGame) {
            this.beforeStartGame.forEach(ReferenceCountUtil::release);
            this.beforeStartGame.clear();
         }

         this.listener.onDisconnect(end);
      }
   }

   @Override
   public void onDisconnect(CharSequence reason) {
      this.close(SessionEnd.connection(reason.toString()));
   }

   private static enum State {
      NETWORK_SETTINGS,
      LOGIN,
      ENCRYPTION,
      RESOURCE_PACKS,
      GAME,
      CLOSED;
   }
}
