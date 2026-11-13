package net.bedrocknative.client.net;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.client.account.AccountsScreen;
import net.bedrocknative.client.camera.BedrockCamera;
import net.bedrocknative.client.command.BedrockCommands;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.bedrocknative.client.content.BedrockItemIcons;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.client.content.ServerPacks;
import net.bedrocknative.client.edit.BedrockBooks;
import net.bedrocknative.client.edit.BedrockSigns;
import net.bedrocknative.client.form.BedrockForms;
import net.bedrocknative.client.hud.BedrockCameraShake;
import net.bedrocknative.client.hud.BedrockCredits;
import net.bedrocknative.client.hud.BedrockHud;
import net.bedrocknative.client.hud.BedrockToasts;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.bedrocknative.client.inventory.BedrockInventory;
import net.bedrocknative.client.inventory.BedrockRecipeBook;
import net.bedrocknative.client.inventory.ContainerTrace;
import net.bedrocknative.client.inventory.Crafting;
import net.bedrocknative.client.inventory.CreativeItems;
import net.bedrocknative.client.inventory.ItemWorldData;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.inventory.Workstations;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.bedrocknative.client.model.ActorPrep;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockEmotes;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.movement.BedrockEffects;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.bedrocknative.client.movement.CorrectionLog;
import net.bedrocknative.client.movement.FreezeDiagnostics;
import net.bedrocknative.client.movement.MovementReplay;
import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.rules.BedrockAdventureSettings;
import net.bedrocknative.client.rules.BedrockGameRules;
import net.bedrocknative.client.rules.BedrockInputLocks;
import net.bedrocknative.client.rules.BedrockLevelRules;
import net.bedrocknative.client.rules.BedrockNameTags;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.client.world.BedrockBlocks;
import net.bedrocknative.client.world.BedrockBossBars;
import net.bedrocknative.client.world.BedrockChunkLoader;
import net.bedrocknative.client.world.BedrockChunks;
import net.bedrocknative.client.world.BedrockCommandBlocks;
import net.bedrocknative.client.world.BedrockDimensions;
import net.bedrocknative.client.world.BedrockEntities;
import net.bedrocknative.client.world.BedrockJigsawBlocks;
import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.bedrocknative.client.world.BedrockParticles;
import net.bedrocknative.client.world.BedrockScoreboard;
import net.bedrocknative.client.world.BedrockSleeping;
import net.bedrocknative.client.world.BedrockSounds;
import net.bedrocknative.client.world.BedrockStructureBlocks;
import net.bedrocknative.client.world.BedrockTimeWeather;
import net.bedrocknative.client.world.BlockCracks;
import net.bedrocknative.client.world.SpawnCheck;
import net.bedrocknative.client.world.TeleportCollision;
import net.bedrocknative.client.world.light.ThreadedLight;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.model.skin.SkinClaims;
import net.bedrocknative.movement.BelowWorldWatch;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.net.auth.PrivilegeCheck;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.net.client.BedrockGameListener;
import net.bedrocknative.net.client.LatencyAnswer;
import net.bedrocknative.net.client.PackChoice;
import net.bedrocknative.net.client.PackOffer;
import net.bedrocknative.net.client.PackProgress;
import net.bedrocknative.net.client.SessionEnd;
import net.bedrocknative.oreui.OreDisconnect;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.block.custom.SlotMemory;
import net.bedrocknative.world.chunk.DimensionRange;
import net.bedrocknative.world.chunk.cache.BlobCaches;
import net.bedrocknative.world.item.BedrockItemTags;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.MovementEffectType;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.data.camera.AimAssistAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemUseType;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketType;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistActorPriorityPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistInstructionPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraShakePacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraSplinePacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundStonecutterSetRecipePacket;
import org.cloudburstmc.protocol.bedrock.packet.CompletedUsingItemPacket;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovementEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundCursorItemDragPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundStonecutterSetRecipePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHudPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.Status;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundCursorItemDragPacket.State;
import org.jspecify.annotations.Nullable;

public final class ClientBedrockSession implements BedrockGameListener {
   @Nullable
   private static volatile ClientBedrockSession active;
   public static final float PLAYER_EYE_HEIGHT = 1.62001F;
   private static final int JOIN_INPUTS_BEFORE_AIM_ASSIST_CLEAR = 2;
   private static final int MAX_CHUNK_RADIUS = 28;
   private static final int PREDICTION_SYNC_DELAY_TICKS = 200;
   private static final int JAVA_PLAYER_ID = 1;
   private final Minecraft minecraft;
   private final Screen parent;
   private final ServerData serverData;
   private final InetSocketAddress address;
   private final ClientIdentity identity;
   private final CompletableFuture<Frozen> registries;
   private final Set<Class<?>> loggedUnhandled = ConcurrentHashMap.newKeySet();
   private final Set<BedrockPacketType> failedPacketTypes = ConcurrentHashMap.newKeySet();
   private volatile BedrockClientSession bedrock;
   private volatile BedrockSessionConnection connection;
   private volatile long runtimeEntityId;
   private volatile long uniqueEntityId;
   private volatile long seed;
   @Nullable
   private volatile Frozen access;
   @Nullable
   private volatile ClientBedrockSession.LoadingScreen loadingScreen;
   private volatile boolean serverDimensionReady;
   private int loadingScreenEndIn;
   private int noChunksCheckIn;
   private static final int NO_CHUNKS_TICKS = 60;
   private static final boolean FORCE_NO_CHUNKS_CHECK = Boolean.getBoolean("bedrocknative.forceNoChunksCheck");
   private boolean joinLevelShown;
   private int chunkRadiusSent = -1;
   private volatile int chunkRadiusAsked;
   private int predictionSyncIn = -1;
   private boolean playerInitialized;
   private boolean sneakDown;
   private volatile boolean closed;
   static final boolean JOIN_AHEAD = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.joinAhead", "").trim());
   private boolean contentStarted;
   private volatile boolean spawnStatusSeen;
   private volatile boolean spawned;
   private int aimAssistClearIn = -1;
   private boolean wasRiding;
   private boolean wasAlive = true;
   @Nullable
   private volatile Vector3f dimensionTarget;
   private static volatile int radiusUpdates;
   private final MovementStats movementStats = new MovementStats();
   private final MovementReplay replay = new MovementReplay();
   private final CorrectionLog corrections = new CorrectionLog();
   private final BelowWorldWatch belowWorld = new BelowWorldWatch();
   @Nullable
   private ClientLevel belowWorldLevel;
   private static final int DEV_LATENCY_MS = Integer.getInteger("bedrocknative.devLatency", 0);
   private static final boolean DEV_TEST = System.getProperty("bedrocknative.devTest") != null;
   private static final long DIMENSION_ACK_TIMEOUT_MS = 60000L;
   private final ScheduledExecutorService devLatency = DEV_LATENCY_MS > 0
      ? Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("Bedrock dev latency").factory())
      : null;
   private final PlayerInputSender input = new PlayerInputSender();
   private final BedrockActors actors = new BedrockActors();
   private final BedrockEntities entities;
   private final BedrockBlocks blocks;
   private final BedrockChunks chunks;
   private final BedrockChat chat;
   private final BedrockScoreboard scoreboard;
   private final BedrockBossBars bossBars;
   private final BedrockCommands commands;
   private final BedrockTimeWeather timeWeather;
   private final BedrockLevelRules levelRules;
   private final BedrockDimensions dimensions = new BedrockDimensions();
   private final SpawnCheck spawnCheck = new SpawnCheck();
   private final BedrockLocalPlayer localPlayer;
   private final BedrockSleeping sleeping;
   private final BedrockCompletion completion;
   private final BedrockInventory inventory;
   private final ItemWorldData itemWorld;
   private final CreativeItems creative = new CreativeItems();
   private final Crafting crafting = new Crafting();
   private final StackRequests requests;
   private final BedrockInteractions interactions;
   private final BlockCracks cracks;
   private final BedrockSounds sounds;
   private final BedrockParticles particles;
   private final BedrockForms forms;
   private final BedrockCredits credits;
   private final BedrockExtras extras;
   private final BedrockRecipeBook recipeBook;
   private final ServerPacks packs;
   @Nullable
   private volatile Channel channel;
   @Nullable
   private volatile Runnable loginSuccess;
   private volatile PrivilegeCheck privileges = PrivilegeCheck.none();
   @Nullable
   private volatile XboxAccountStore.AccountException refusal;
   private final AtomicBoolean refusalEnded = new AtomicBoolean();
   private long privilegesHeldSince;
   private boolean privilegesHoldLogged;
   @Nullable
   static volatile Consumer<BedrockPacket> devTap;

   @Nullable
   public static ClientBedrockSession active() {
      return active;
   }

   public ClientIdentity identity() {
      return this.identity;
   }

   public ClientBedrockSession(
      Minecraft minecraft,
      Screen parent,
      Screen connecting,
      ServerData serverData,
      InetSocketAddress address,
      ClientIdentity identity,
      CompletableFuture<Frozen> registries
   ) {
      this.minecraft = minecraft;
      this.parent = parent;
      this.serverData = serverData;
      this.address = address;
      this.identity = identity;
      this.registries = registries;
      this.entities = new BedrockEntities(minecraft, this.actors);
      this.blocks = new BedrockBlocks(minecraft);
      this.chunks = new BedrockChunks(minecraft);
      this.chat = new BedrockChat(minecraft, identity);
      this.scoreboard = new BedrockScoreboard(minecraft, this.entities);
      this.bossBars = new BedrockBossBars(minecraft, this.entities, packet -> this.bedrock.sendPacket(packet));
      this.commands = new BedrockCommands(minecraft);
      this.timeWeather = new BedrockTimeWeather(minecraft);
      this.levelRules = new BedrockLevelRules(minecraft);
      this.localPlayer = new BedrockLocalPlayer(minecraft, packet -> this.bedrock.sendPacket(packet));
      this.sleeping = new BedrockSleeping(minecraft, packet -> this.bedrock.sendPacket(packet));
      this.inventory = new BedrockInventory(minecraft, packet -> {
         ContainerTrace.out(packet);
         this.bedrock.sendPacket(packet);
      });
      this.completion = new BedrockCompletion(minecraft, packet -> this.bedrock.sendPacket(packet), identity, this.actors, this.inventory);
      this.itemWorld = new ItemWorldData(minecraft, packet -> this.bedrock.sendPacket(packet));
      this.itemWorld.onRefresh(() -> minecraft.execute(this.inventory::showPlayerSlots));
      Workstations workstations = new Workstations(
         minecraft,
         this.inventory,
         this.crafting,
         identifier -> this.bedrock == null ? null : this.bedrock.getPeer().getCodecHelper().getItemDefinitions().getDefinition(identifier),
         this.entities::byUniqueId,
         this.entities::tradeExperience
      );
      this.requests = new StackRequests(minecraft, this.inventory, this.creative, this.crafting, workstations, packet -> this.bedrock.sendPacket(packet));
      this.interactions = new BedrockInteractions(minecraft, this.inventory, this.entities, packet -> this.bedrock.sendPacket(packet));
      this.inventory.lecterns().takeBookWith(this.interactions::punchBlock);
      this.cracks = new BlockCracks(minecraft);
      this.sounds = new BedrockSounds(minecraft, this.entities, pos -> this.interactions.breaksLocally(pos) || this.interactions.usedLocally(pos));
      this.particles = new BedrockParticles(minecraft, pos -> this.interactions.breaksLocally(pos), this.entities::byUniqueId);
      this.forms = new BedrockForms(minecraft, packet -> this.bedrock.sendPacket(packet));
      BedrockTextures.trustServer(address.getAddress());
      BedrockBlockModels.sessionStarts(SlotMemory.key(address.getHostString(), address.getPort()));
      this.credits = new BedrockCredits(minecraft, packet -> this.bedrock.sendPacket(packet), () -> this.runtimeEntityId);
      this.extras = new BedrockExtras(minecraft, this.entities, this.inventory, packet -> this.bedrock.sendPacket(packet));
      this.recipeBook = new BedrockRecipeBook(
         minecraft, this.requests, this.extras.recipes(), this.extras::inventoryOptions, this.extras::changeInventoryOptions
      );
      if (BedrockRecipeBook.enabled()) {
         this.extras.onNewlyUnlocked(this.recipeBook::unlocked);
      }

      this.packs = new ServerPacks(minecraft, serverData, connecting, () -> {
         Channel current = this.channel;
         if (current != null) {
            current.close();
         }
      }, () -> {
         if (JOIN_AHEAD && !this.closed) {
            this.startContent("the pack phase");
         }
      });
      this.actors.skins().local(identity.uuid(), SkinClaims.toSerialized(identity.skin().claims()), identity.skin().standIn());
   }

   static int radiusUpdates() {
      return radiusUpdates;
   }

   public void attach(Channel channel) {
      this.channel = channel;
      if (this.refusal != null) {
         this.endRefused();
      }
   }

   public void privileges(PrivilegeCheck check) {
      this.privileges = check;
      check.whenRefused(this::refuse);
   }

   private void refuse(XboxAccountStore.AccountException failure) {
      this.refusal = failure;
      if (this.channel != null) {
         try {
            this.endRefused();
         } catch (RuntimeException var3) {
            BedrockNative.LOGGER.error("Couldn't end the session over the Xbox multiplayer check's refusal", var3);
         }
      }
   }

   private void endRefused() {
      XboxAccountStore.AccountException failure = this.refusal;
      if (failure != null && !this.closed && this.refusalEnded.compareAndSet(false, true)) {
         String when = this.spawned && this.loadingScreen == null ? "after the world showed (the answer came late)" : "before the world showed";
         BedrockNative.LOGGER
            .warn(
               "The Xbox account can't be used ({}{}): {}; the join ends {}",
               new Object[]{failure.problem(), failure.diagnosis() == null ? "" : ", " + failure.diagnosis(), failure.getMessage(), when}
            );
         this.onDisconnect(
            new SessionEnd(SessionEnd.Source.CLIENT, DisconnectFailReason.MULTIPLAYER_DISABLED, "", true, "Xbox multiplayer check: " + failure.getMessage())
         );
         BedrockClientSession session = this.bedrock;
         Channel open = this.channel;
         if (session != null && session.isConnected()) {
            session.disconnect("Disconnected");
         } else if (open != null) {
            open.close();
         }
      }
   }

   private Screen screenAfterEnd() {
      XboxAccountStore.AccountException failure = this.refusal;
      return (Screen)(failure != null && this.refusalEnded.get() ? AccountsScreen.forFailure(this.parent, failure) : this.parent);
   }

   private boolean heldForPrivileges() {
      PrivilegeCheck check = this.privileges;
      if (check.refuses() || this.refusal != null) {
         return true;
      } else if (check.holdsJoin()) {
         if (this.privilegesHeldSince == 0L) {
            this.privilegesHeldSince = Util.getMillis();
         }

         return true;
      } else {
         if (this.privilegesHeldSince != 0L && !this.privilegesHoldLogged) {
            this.privilegesHoldLogged = true;
            BedrockNative.LOGGER
               .info(
                  "Join loading screen waited {} ms for the Xbox multiplayer check ({})",
                  Util.getMillis() - this.privilegesHeldSince,
                  check.finished() ? "answered " + check.now().outcome() : "no answer 3000 ms after it started, joining anyway"
               );
         }

         return false;
      }
   }

   public void whenLoggedIn(Runnable action) {
      this.loginSuccess = action;
   }

   @Override
   public void onLoginSuccess() {
      SessionTimeline.mark("login");
      Runnable action = this.loginSuccess;
      if (action != null) {
         action.run();
      }
   }

   @Override
   public void onPackOffer(PackOffer offer, Consumer<PackChoice> answer) {
      this.packs.offer(offer, answer);
   }

   @Override
   public boolean serverPacksDisabled() {
      return this.packs.disabled();
   }

   @Override
   public void onPackProgress(PackProgress progress) {
      this.packs.progress(progress);
   }

   @Override
   public void onPacksApplied(List<? extends PackSource> topFirst, String baseGameVersion, List<String> experiments) {
      ItemMappings.useServerPacks(topFirst);
      this.packs.apply(topFirst, baseGameVersion, experiments);
   }

   public void cursorDrag(boolean start) {
      BedrockClientSession session = this.bedrock;
      if (session != null && session.getCodec().<ServerboundCursorItemDragPacket>getPacketDefinition(ServerboundCursorItemDragPacket.class) != null) {
         ServerboundCursorItemDragPacket packet = new ServerboundCursorItemDragPacket();
         packet.setState(start ? State.START : State.STOP);
         session.sendPacket(packet);
      }
   }

   private void stonecutterAnswer(ClientboundStonecutterSetRecipePacket recipe) {
      if (this.minecraft.player != null && this.minecraft.player.containerMenu instanceof StonecutterMenu menu && menu.containerId == recipe.getContainerId()) {
         BedrockNative.LOGGER
            .debug(
               "Stonecutter selection answered: container {} recipe {} (Java shows index {})",
               new Object[]{recipe.getContainerId(), recipe.getRecipeIndex(), menu.getSelectedRecipeIndex()}
            );
      }
   }

   public void stonecutterSelection() {
      BedrockClientSession session = this.bedrock;
      if (session != null
         && session.getCodec().<ServerboundStonecutterSetRecipePacket>getPacketDefinition(ServerboundStonecutterSetRecipePacket.class) != null
         && this.minecraft.player.containerMenu instanceof StonecutterMenu menu) {
         int index = this.requests.workstations().stonecutterServerIndex(menu);
         if (index >= 0) {
            ServerboundStonecutterSetRecipePacket packet = new ServerboundStonecutterSetRecipePacket();
            packet.setContainerId(menu.containerId);
            packet.setRecipeIndex(index);
            session.sendPacket(packet);
         }
      }
   }

   @Override
   public void onGamePacket(BedrockClientSession session, BedrockPacket packet) {
      this.bedrock = session;
      active = this;
      ContainerTrace.in(packet);
      if (this.devLatency != null && !(packet instanceof StartGamePacket)) {
         ReferenceCountUtil.retain(packet);
         this.devLatency.schedule(() -> {
            try {
               this.handleSafely(packet);
            } finally {
               ReferenceCountUtil.release(packet);
            }
         }, (long)DEV_LATENCY_MS, TimeUnit.MILLISECONDS);
      } else {
         this.handleSafely(packet);
      }
   }

   private void handleSafely(BedrockPacket packet) {
      long perf = PerfProbe.start();
      Consumer<BedrockPacket> tap = devTap;
      if (tap != null) {
         tap.accept(packet);
      }

      try {
         this.corrections.countServer(packet, this.runtimeEntityId);
         if (CorrectionLog.TRACE) {
            CorrectionLog.traceServer(packet, this.runtimeEntityId);
         }
      } catch (RuntimeException var10) {
         BedrockNative.LOGGER.debug("Movement log could not read {}", packet.getPacketType(), var10);
      }

      try {
         this.handleGamePacket(packet);
      } catch (RuntimeException var11) {
         if (this.failedPacketTypes.add(packet.getPacketType())) {
            BedrockNative.LOGGER.error("Could not apply {}; later ones of this type are skipped silently when they fail too", packet.getPacketType(), var11);
         }
      } finally {
         PerfProbe.end(PerfProbe.Part.PACKETS, perf);
      }
   }

   private void handleGamePacket(BedrockPacket packet) {
      Objects.requireNonNull(packet);
      switch (packet) {
         case StartGamePacket startGame:
            this.onStartGame(startGame);
            break;
         case LevelChunkPacket chunk: {
            long perf = PerfProbe.start();
            this.chunks.onLevelChunk(chunk);
            PerfProbe.end(PerfProbe.Part.CHUNKS_NETWORK, perf);
            break;
         }
         case SubChunkPacket subChunks: {
            long perf = PerfProbe.start();
            this.chunks.onSubChunks(subChunks);
            PerfProbe.end(PerfProbe.Part.CHUNKS_NETWORK, perf);
            break;
         }
         case BiomeDefinitionListPacket biomeList:
            this.chunks.onBiomeDefinitions(biomeList);
            break;
         case ClientCacheMissResponsePacket blobs:
            this.chunks.onBlobMiss(blobs);
            break;
         case NetworkChunkPublisherUpdatePacket publisher: {
            this.chunks.onPublisherUpdate(publisher.getPosition(), publisher.getRadius());
            long view = this.chunks.viewChangeReceived();
            this.minecraft.execute(() -> {
               if (this.minecraft.level != null) {
                  this.minecraft.level.getChunkSource().updateViewCenter(publisher.getPosition().getX() >> 4, publisher.getPosition().getZ() >> 4);
               }

               this.chunks.viewChangeApplied(view);
            });
            break;
         }
         case ChunkRadiusUpdatedPacket radius: {
            long view = this.chunks.viewChangeReceived();
            this.minecraft.execute(() -> {
               BedrockNative.LOGGER.info("Chunk radius asked {}, server gave {}", this.chunkRadiusAsked, radius.getRadius());
               radiusUpdates++;
               ClientPacketListener listener = this.minecraft.getConnection();
               if (this.minecraft.level != null && listener != null) {
                  BedrockChunkLoader.beforeViewRadius(this.minecraft.level, radius.getRadius());
                  listener.handleSetChunkCacheRadius(new ClientboundSetChunkCacheRadiusPacket(radius.getRadius()));
               }

               this.chunks.viewChangeApplied(view);
            });
            break;
         }
         case PlayStatusPacket status when status.getStatus() == Status.PLAYER_SPAWN:
            this.onPlayerSpawn();
            break;
         case CompletedUsingItemPacket completed when completed.getType() == ItemUseType.EAT || completed.getType() == ItemUseType.CONSUME:
            this.minecraft.execute(this.interactions::serverCompletedUse);
            break;
         case ChangeDimensionPacket change:
            this.onChangeDimension(change);
            break;
         case PlayerActionPacket action when action.getAction() == PlayerActionType.DIMENSION_CHANGE_SUCCESS:
            this.serverDimensionReady = true;
            break;
         case NetworkStackLatencyPacket latency when latency.isFromServer():
            this.minecraft.execute(() -> {
               NetworkStackLatencyPacket answer = LatencyAnswer.to(latency);
               ContainerTrace.out(answer);
               this.bedrock.sendPacket(answer);
            });
            break;
         case TransferPacket transfer:
            this.minecraft.execute(() -> {
               if (!this.closed && this.minecraft.getConnection() != null) {
                  BedrockNative.LOGGER.info("Transfer to {}:{}", transfer.getAddress(), transfer.getPort());
                  this.minecraft.getConnection().handleTransfer(new ClientboundTransferPacket(transfer.getAddress(), transfer.getPort()));
               }
            });
            break;
         case MovePlayerPacket move when move.getRuntimeEntityId() == this.runtimeEntityId:
            this.minecraft
               .execute(
                  () -> {
                     MovementStats.teleported();
                     FreezeDiagnostics.motion(
                        "MovePlayer " + move.getMode(), move.getPosition(), move.getTick(), "teleported there (eye position), velocity cleared"
                     );
                     LocalPlayer player = this.minecraft.player;
                     if (player != null) {
                        double feetY = move.getPosition().getY() - 1.62001F;
                        TeleportCollision.infer(this.minecraft.level, move.getPosition().getX(), feetY, move.getPosition().getZ());
                        this.corrections
                           .movePlayer(
                              player, move.getMode(), new Vec3(move.getPosition().getX(), feetY, move.getPosition().getZ()), move.isOnGround(), move.getTick()
                           );
                        player.snapTo(
                           move.getPosition().getX(),
                           move.getPosition().getY() - 1.62001F,
                           move.getPosition().getZ(),
                           move.getRotation().getY(),
                           move.getRotation().getX()
                        );
                        player.setDeltaMovement(Vec3.ZERO);
                        this.input.acknowledgeTeleportNextTick();
                        this.spawnCheck.arm("MovePlayer " + move.getMode());
                     }
                  }
               );
            break;
         case CorrectPlayerMovePredictionPacket correction when correction.getPredictionType() == PredictionType.VEHICLE:
            this.minecraft
               .execute(
                  () -> {
                     this.movementStats.vehicleCorrected();
                     this.schedulePredictionSync();
                     if (this.minecraft.player != null) {
                        Vector3f position = correction.getPosition();
                        Vector3f delta = correction.getDelta();
                        Vector2f rotation = correction.getVehicleRotation();
                        BedrockVehicles.Correction applied = BedrockVehicles.correct(
                           this.minecraft.player,
                           correction.getTick(),
                           new Vec3(position.getX(), position.getY(), position.getZ()),
                           new Vec3(delta.getX(), delta.getY(), delta.getZ()),
                           rotation == null ? 0.0F : rotation.getX(),
                           rotation == null ? Float.NaN : rotation.getY(),
                           correction.getVehicleAngularVelocity(),
                           correction.isOnGround()
                        );
                        if (applied != null) {
                           this.movementStats.vehicleCorrection(applied);
                        }
                     }
                  }
               );
            break;
         case CorrectPlayerMovePredictionPacket correctionx:
            this.minecraft
               .execute(
                  () -> {
                     this.schedulePredictionSync();
                     FreezeDiagnostics.motion(
                        "CorrectPlayerMovePrediction delta",
                        correction.getDelta(),
                        correction.getTick(),
                        "rewound to " + correction.getPosition() + (BedrockMovement.immobile() ? ", later ticks held still" : "")
                     );
                     Vector3f corrected = correction.getPosition();
                     this.movementStats
                        .corrected(
                           correction.getTick(),
                           new Vec3(corrected.getX(), corrected.getY() - 1.62001F, corrected.getZ()),
                           new Vec3(correction.getDelta().getX(), correction.getDelta().getY(), correction.getDelta().getZ()),
                           correction.isOnGround()
                        );
                     LocalPlayer player = this.minecraft.player;
                     if (player != null) {
                        Vec3 position = new Vec3(corrected.getX(), corrected.getY() - 1.62001F, corrected.getZ());
                        Vec3 velocity = new Vec3(correction.getDelta().getX(), correction.getDelta().getY(), correction.getDelta().getZ());
                        boolean rewound = this.replay.rewind(player, correction.getTick(), position, velocity, correction.isOnGround());
                        if (!rewound) {
                           player.setPos(position);
                           player.setDeltaMovement(velocity);
                           player.setOnGround(correction.isOnGround());
                        }

                        this.corrections.prediction(player, correction.getTick(), position, velocity, correction.isOnGround(), rewound);
                     }
                  }
               );
            break;
         case MovementEffectPacket effect when effect.getEntityRuntimeId() == this.runtimeEntityId && effect.getEffectType() != MovementEffectType.GLIDE_BOOST:
            BedrockNative.LOGGER.debug("Movement effect {} for {} ticks: not predicted", effect.getEffectType(), effect.getDuration());
            break;
         case MobEffectPacket effectx when effectx.getRuntimeEntityId() == this.runtimeEntityId:
            this.minecraft.execute(() -> {
               FreezeDiagnostics.effect(effect);
               LocalPlayer player = this.minecraft.player;
               if (player != null && BedrockEffects.apply(player, effect)) {
                  this.replay.resimulateAfter(player, effect.getTick());
               }
            });
            break;
         case MovementEffectPacket effectxx when effectxx.getEntityRuntimeId() == this.runtimeEntityId
   && effectxx.getEffectType() == MovementEffectType.GLIDE_BOOST:
            this.minecraft.execute(() -> {
               BedrockNative.LOGGER.debug("Glide boost for {} ticks from input tick {}", effect.getDuration(), effect.getTick());
               if (this.minecraft.player != null && !this.replay.startGlideBoost(this.minecraft.player, effect.getTick(), effect.getDuration())) {
                  BedrockMovement.startGlideBoost(effect.getDuration());
               }
            });
            break;
         case SetEntityMotionPacket motion when motion.getRuntimeEntityId() == this.runtimeEntityId:
            this.minecraft.execute(() -> {
               FreezeDiagnostics.motion("SetActorMotion", motion.getMotion(), motion.getTick(), FreezeDiagnostics.motionOutcome());
               LocalPlayer player = this.minecraft.player;
               if (player != null) {
                  Vec3 velocity = new Vec3(motion.getMotion().getX(), motion.getMotion().getY(), motion.getMotion().getZ());
                  if (!this.replay.setMotion(player, motion.getTick(), velocity)) {
                     player.setDeltaMovement(velocity);
                  }
               }
            });
            break;
         case MobEquipmentPacket equipment when equipment.getRuntimeEntityId() == this.runtimeEntityId:
            this.minecraft.execute(() -> this.inventory.onLocalEquipment(equipment));
            break;
         case SetEntityDataPacket data when data.getRuntimeEntityId() == this.runtimeEntityId:
            this.minecraft.execute(() -> {
               this.localPlayer.applyData(data);
               this.sleeping.onLocalData(data.getMetadata());
               this.actors.onLocalMetadata(data.getMetadata(), data.getProperties());
            });
            break;
         case AnimatePacket animate when animate.getRuntimeEntityId() == this.runtimeEntityId && animate.getAction() == Action.WAKE_UP:
            this.minecraft.execute(this.sleeping::onWakeUp);
            break;
         case UpdateAttributesPacket attributes when attributes.getRuntimeEntityId() != this.runtimeEntityId:
            this.minecraft.execute(() -> this.entities.onAttributes(attributes));
            break;
         case EntityEventPacket event:
            this.minecraft.execute(() -> {
               this.entities.apply(event);
               this.sounds.onEntityEvent(event);
            });
            break;
         case ItemStackResponsePacket response:
            this.minecraft.execute(() -> this.requests.onResponse(response));
            break;
         case CreativeContentPacket content:
            this.minecraft.execute(() -> this.creative.apply(content));
            break;
         case ItemComponentPacket ignored:
            this.minecraft.execute(BedrockItemIcons::reload);
            break;
         case CraftingDataPacket recipes:
            this.minecraft.execute(() -> this.crafting.apply(recipes));
            break;
         case UpdatePlayerGameTypePacket gameType when this.localPlayer.handles(gameType):
            this.minecraft.execute(() -> {
               LocalPlayer player = this.minecraft.player;
               boolean flying = player != null && player.getAbilities().flying;
               this.localPlayer.apply(gameType);
               if (player != null && player.getAbilities().flying != flying) {
                  this.replay.setFlyingAfter(player, gameType.getTick(), player.getAbilities().flying);
               } else if (player != null) {
                  this.replay.resimulateAfter(player, gameType.getTick());
               }
            });
            break;
         case UpdateAbilitiesPacket abilities:
            this.minecraft.execute(() -> {
               this.commands.onAbilities(abilities);
               this.localPlayer.applyAbilities(abilities);
            });
            break;
         case UpdateAdventureSettingsPacket adventure:
            this.minecraft.execute(() -> BedrockAdventureSettings.apply(adventure));
            break;
         case UpdateClientInputLocksPacket locks:
            this.minecraft.execute(() -> BedrockInputLocks.apply(locks));
            break;
         case SetDifficultyPacket difficulty:
            this.minecraft.execute(() -> this.levelRules.onDifficulty(difficulty));
            break;
         case LevelEventGenericPacket eventx:
            this.minecraft.execute(() -> {
               if (!this.completion.onGeneric(event)) {
                  this.levelRules.onGenericEvent(event);
               }
            });
            break;
         case DimensionDataPacket dimensionData:
            this.dimensions.apply(dimensionData);
            break;
         case SetHudPacket hud:
            this.minecraft.execute(() -> BedrockHud.apply(hud));
            break;
         case ToastRequestPacket toast:
            this.minecraft.execute(() -> BedrockToasts.show(this.minecraft, toast));
            break;
         case CameraShakePacket shake:
            this.minecraft.execute(() -> BedrockCameraShake.apply(shake));
            break;
         case CameraPresetsPacket presets:
            this.minecraft.execute(() -> BedrockCamera.presets(presets));
            break;
         case CameraInstructionPacket instruction:
            this.minecraft.execute(() -> BedrockCamera.apply(this.minecraft, instruction));
            break;
         case CameraSplinePacket splines:
            this.minecraft.execute(() -> BedrockCamera.splines(splines));
            break;
         case CameraAimAssistPacket aim:
            BedrockCamera.aimAssist(aim);
            break;
         case CameraAimAssistPresetsPacket aimx:
            BedrockCamera.aimAssist(aimx);
            break;
         case CameraAimAssistActorPriorityPacket aimxx:
            BedrockCamera.aimAssist(aimxx);
            break;
         case ShowCreditsPacket showCredits:
            this.minecraft.execute(() -> this.credits.apply(showCredits));
            break;
         case ClientboundStonecutterSetRecipePacket recipe:
            this.minecraft.execute(() -> this.stonecutterAnswer(recipe));
            break;
         case DisconnectPacket disconnect:
            this.onDisconnect(
               SessionEnd.disconnectPacket(disconnect.getReason(), disconnect.isMessageSkipped(), disconnect.getKickMessage(), disconnect.getFilteredMessage())
            );
            break;
         default:
            if (packet instanceof AddEntityPacket add) {
               ActorPrep.announced(add.getIdentifier(), add.getPosition().getX(), add.getPosition().getY(), add.getPosition().getZ());
            }

            if (BedrockEntities.handles(packet)) {
               this.minecraft.execute(() -> FrameTimes.timeAround(FrameWatch.Bucket.ENTITIES, () -> this.entities.apply(packet)));
            } else if (BedrockActors.handles(packet)) {
               this.minecraft.execute(() -> FrameTimes.timeAround(FrameWatch.Bucket.ENTITIES, () -> this.actors.apply(packet)));
            } else if (BedrockBlocks.handles(packet)) {
               Runnable apply = () -> this.blocks.apply(packet);
               int[] column = BedrockBlocks.chunkOf(packet);
               if (column == null || !this.chunks.deferUntilChunkArrives(column[0], column[1], apply)) {
                  this.minecraft.execute(apply);
               }
            } else if (BedrockCommands.handles(packet)) {
               this.minecraft.execute(() -> this.commands.apply(packet));
            } else if (BedrockChat.handles(packet)) {
               this.minecraft.execute(() -> this.chat.apply(packet));
            } else if (BedrockScoreboard.handles(packet)) {
               this.minecraft.execute(() -> this.scoreboard.apply(packet));
            } else if (BedrockTimeWeather.handles(packet)) {
               this.minecraft.execute(() -> this.timeWeather.apply(packet));
            } else if (this.localPlayer.handles(packet)) {
               this.minecraft.execute(() -> this.localPlayer.apply(packet));
            } else if (BedrockInventory.handles(packet)) {
               this.minecraft.execute(() -> this.inventory.apply(packet));
            } else if (Workstations.handles(packet)) {
               this.minecraft.execute(() -> this.requests.workstations().apply(packet));
            } else if (ItemWorldData.handles(packet)) {
               this.minecraft.execute(() -> this.itemWorld.apply(packet));
            } else if (BedrockSounds.handles(packet)) {
               this.minecraft.execute(() -> this.sounds.apply(packet));
            } else if (BedrockParticles.handles(packet)) {
               this.minecraft.execute(() -> this.particles.apply(packet));
            } else if (BedrockBossBars.handles(packet)) {
               this.minecraft.execute(() -> this.bossBars.apply(packet));
            } else if (BedrockForms.handles(packet)) {
               this.minecraft.execute(() -> this.forms.apply(packet));
            } else if (BlockCracks.handles(packet)) {
               this.minecraft.execute(() -> this.cracks.apply(packet, this.interactions.breaking()));
            } else if (BedrockExtras.handles(packet)) {
               this.minecraft.execute(() -> this.extras.apply(packet));
            } else if (BedrockCompletion.handles(packet)) {
               this.minecraft.execute(() -> this.completion.apply(packet));
            } else if (this.loggedUnhandled.add(packet.getClass())) {
               if (DEV_TEST) {
                  BedrockNative.LOGGER.info("Bedrock packet not handled yet: {}", packet.getClass().getSimpleName());
               } else {
                  BedrockNative.LOGGER.debug("Bedrock packet not handled yet: {}", packet.getClass().getSimpleName());
               }
            }
      }
   }

   private void onStartGame(StartGamePacket start) {
      if (!this.closed) {
         SessionTimeline.mark("StartGame");
         BedrockNative.LOGGER
            .info(
               "StartGame: {} {}, block ids {}, {} block definitions, experiments {}",
               new Object[]{
                  start.getServerEngine(),
                  start.getVanillaVersion(),
                  start.isBlockNetworkIdsHashed() ? "hashed" : "by palette index",
                  start.getBlockProperties().size(),
                  start.getExperiments().stream().filter(ExperimentData::isEnabled).map(ExperimentData::getName).toList()
               }
            );
         CorrectionLog.startGame(start);
         this.chunks.serverLabel("\"" + start.getServerEngine() + "\" at " + this.address.getHostString() + ":" + this.address.getPort());
         Frozen access = this.registries.join();
         SessionTimeline.mark("registries");
         this.access = access;
         BedrockItemTags.useRegistries(access);
         ResourceKey<Level> levelKey = levelKey(start.getDimensionId());
         Holder<DimensionType> dimensionType = this.dimensions.typeOf(access, start.getDimensionId());
         DimensionRange range = this.dimensions.rangeOf((DimensionType)dimensionType.value(), start.getDimensionId());
         BedrockNative.LOGGER.info("StartGame world: {}", SpawnCheck.startGameLine(start, (DimensionType)dimensionType.value(), range));
         int chunkGeneration = this.chunks.start(access, (DimensionType)dimensionType.value(), start.getDimensionId(), range);
         this.runtimeEntityId = start.getRuntimeEntityId();
         long uniqueEntityId = start.getUniqueEntityId();
         this.uniqueEntityId = uniqueEntityId;
         this.seed = start.getSeed();
         GameType gameType = BedrockLocalPlayer.toJava(start.getPlayerGameType(), start.getLevelGameType());
         CommonPlayerSpawnInfo spawnInfo = new CommonPlayerSpawnInfo(
            dimensionType,
            levelKey,
            start.getSeed(),
            gameType,
            Optional.empty(),
            false,
            start.getGeneratorId() == 2 || this.dimensions.flat(start.getDimensionId()),
            Optional.empty(),
            0,
            63
         );
         this.localPlayer.start(this.runtimeEntityId, uniqueEntityId, spawnInfo, start.getPlayerGameType(), start.getLevelGameType());
         this.minecraft
            .execute(
               () -> {
                  if (!this.closed) {
                     long[] clock = new long[]{System.nanoTime()};
                     StringBuilder parts = new StringBuilder();
                     this.connection = new BedrockSessionConnection(this.address, this::onJavaPacketOut, this::closeBedrock);
                     BedrockMovement.setActive(true);
                     BedrockTextures.selectBaseGame(
                        start.getVanillaVersion(), start.getExperiments().stream().filter(ExperimentData::isEnabled).map(ExperimentData::getName).toList()
                     );
                     BedrockVanillaTextures.startGame();
                     part(parts, clock, "textures");
                     this.startContent("StartGame");
                     part(parts, clock, "content");
                     BedrockBlockModels.start();
                     part(parts, clock, "blocks");
                     this.forms.start();
                     SessionTimeline.mark("content");
                     CommonListenerCookie cookie = new CommonListenerCookie(
                        new LevelLoadTracker(),
                        new GameProfile(this.identity.uuid(), this.identity.username()),
                        this.minecraft.getTelemetryManager().createWorldSessionManager(false, null, null, UUID.randomUUID()),
                        access,
                        FeatureFlags.DEFAULT_FLAGS,
                        "Bedrock " + start.getVanillaVersion(),
                        this.serverData,
                        this.parent,
                        Map.of(),
                        null,
                        Map.of(),
                        ServerLinks.EMPTY,
                        Map.of(),
                        true
                     );
                     ClientPacketListener listener = new ClientPacketListener(this.minecraft, this.connection, cookie);
                     this.connection.setupInboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access)), listener);
                     part(parts, clock, "Java protocol");
                     int radius = (Integer)this.minecraft.options.renderDistance().get();
                     ThreadedLight.arm();
                     listener.handleLogin(
                        new ClientboundLoginPacket(
                           1, false, Set.of(Level.OVERWORLD, Level.NETHER, Level.END), 20, radius, radius, false, true, false, spawnInfo, false, false
                        )
                     );
                     SessionTimeline.mark("Java level");
                     part(parts, clock, "Java level");
                     this.resetRules();
                     this.levelRules.levelCreated();
                     this.timeWeather.start(start.getGamerules(), start.getRainLevel(), start.getLightningLevel());
                     this.entities.reset(this.runtimeEntityId, uniqueEntityId, 1);
                     this.sleeping.start(this.runtimeEntityId);
                     this.completion.start(this.runtimeEntityId);
                     this.scoreboard.reset();
                     this.bossBars.reset();
                     JsonUiHud.reset();
                     BedrockHud.reset();
                     BedrockCameraShake.reset();
                     BedrockCamera.start(this.entities::byUniqueId);
                     this.extras.start(this.runtimeEntityId, uniqueEntityId);
                     this.recipeBook.start();
                     this.commands.start(uniqueEntityId, start.isCommandsEnabled());
                     this.interactions.start(this.runtimeEntityId);
                     this.inventory.reset(this.runtimeEntityId);
                     this.itemWorld.reset();
                     this.requests.reset();
                     LocalPlayer player = this.minecraft.player;
                     player.snapTo(
                        start.getPlayerPosition().getX(),
                        start.getPlayerPosition().getY() - 1.62001F,
                        start.getPlayerPosition().getZ(),
                        start.getRotation().getY(),
                        start.getRotation().getX()
                     );
                     player.setYHeadRot(player.getYRot());
                     this.minecraft.level.getChunkSource().updateViewCenter(player.getBlockX() >> 4, player.getBlockZ() >> 4);
                     this.chunks.activate(chunkGeneration);
                     this.spawnCheck.reset();
                     this.spawnCheck.arm("join");
                     this.bedrock.sendPacket(chunkRadiusRequest(radius));
                     this.chunkRadiusAsked = Math.min(radius, 28);
                     this.chunkRadiusSent = radius;
                     this.sendLoadingScreen(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN, null);
                     this.loadingScreen = new ClientBedrockSession.LoadingScreen(null, false, Util.getMillis());
                     LoadingScreenHold.hold();
                     SessionTimeline.mark("level");
                     part(parts, clock, "the rest");
                     BedrockNative.LOGGER.info("StartGame task on the client thread: {}", parts);
                  }
               }
            );
      }
   }

   private void startContent(String when) {
      if (!this.contentStarted) {
         this.contentStarted = true;
         long started = System.nanoTime();
         BedrockGlyphs.start();
         BedrockModels.reload();
         BedrockSoundEngine.reload();
         BedrockParticleEngine.reload();
         JsonUi.definitions();
         BedrockNative.LOGGER
            .info(
               "Bedrock content started at {}: {} ms on the client thread (the glyph pages); the models, sounds, particles and JSON UI load in the background",
               when,
               (System.nanoTime() - started) / 1000000L
            );
      }
   }

   private static void part(StringBuilder parts, long[] clock, String name) {
      long now = System.nanoTime();
      parts.append(parts.isEmpty() ? "" : ", ").append(name).append(' ').append((now - clock[0]) / 1000000L).append(" ms");
      clock[0] = now;
   }

   static RequestChunkRadiusPacket chunkRadiusRequest(int renderDistance) {
      RequestChunkRadiusPacket request = new RequestChunkRadiusPacket();
      request.setRadius(Math.min(renderDistance, 28));
      request.setMaxRadius(28);
      return request;
   }

   private void resendChunkRadius() {
      int distance = (Integer)this.minecraft.options.renderDistance().get();
      if (this.spawned && this.chunkRadiusSent >= 0 && distance != this.chunkRadiusSent) {
         RequestChunkRadiusPacket request = chunkRadiusRequest(distance);
         this.chunkRadiusAsked = request.getRadius();
         this.bedrock.sendPacket(request);
         BedrockNative.LOGGER
            .info("Render distance {} -> {}: asking the server for chunk radius {}", new Object[]{this.chunkRadiusSent, distance, request.getRadius()});
      }

      if (this.spawned) {
         this.chunkRadiusSent = distance;
      }
   }

   private static ResourceKey<Level> levelKey(int dimension) {
      return switch (dimension) {
         case 1 -> Level.NETHER;
         case 2 -> Level.END;
         default -> Level.OVERWORLD;
      };
   }

   private void onChangeDimension(ChangeDimensionPacket change) {
      Frozen access = this.access;
      if (access != null) {
         BedrockNative.LOGGER
            .info("Changing dimension to {} at {}{}", new Object[]{change.getDimension(), change.getPosition(), change.isRespawn() ? " (respawn)" : ""});
         Holder<DimensionType> type = this.dimensions.typeOf(access, change.getDimension());
         int chunkGeneration = this.chunks
            .start(access, (DimensionType)type.value(), change.getDimension(), this.dimensions.rangeOf((DimensionType)type.value(), change.getDimension()));
         this.serverDimensionReady = false;
         this.dimensionTarget = change.getPosition();
         this.sendLoadingScreen(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN, change.getLoadingScreenId());
         this.minecraft
            .execute(
               () -> {
                  this.dimensionTarget = null;
                  if (!this.closed && this.minecraft.player != null) {
                     CommonPlayerSpawnInfo info = new CommonPlayerSpawnInfo(
                        type,
                        levelKey(change.getDimension()),
                        this.seed,
                        this.minecraft.gameMode.getPlayerMode(),
                        Optional.empty(),
                        false,
                        this.dimensions.flat(change.getDimension()),
                        Optional.empty(),
                        0,
                        63
                     );
                     ThreadedLight.closeCurrent();
                     ThreadedLight.arm();
                     this.localPlayer.changeDimension(info, change.getPosition(), change.isRespawn());
                     this.inventory.showPlayerSlots();
                     this.entities.reset(this.runtimeEntityId, this.uniqueEntityId, 1);
                     this.sleeping.reset();
                     this.completion.changeDimension();
                     this.extras.changeDimension();
                     BedrockCamera.changeDimension();
                     LocalPlayer player = this.minecraft.player;
                     this.minecraft.level.getChunkSource().updateViewCenter(player.getBlockX() >> 4, player.getBlockZ() >> 4);
                     this.chunks.activate(chunkGeneration);
                     this.spawnCheck.arm("dimension change to " + change.getDimension());
                     this.loadingScreenEndIn = 0;
                     this.loadingScreen = new ClientBedrockSession.LoadingScreen(change.getLoadingScreenId(), true, Util.getMillis());
                     LoadingScreenHold.hold();
                  }
               }
            );
      }
   }

   private void tickLoadingScreen() {
      ClientBedrockSession.LoadingScreen screen = this.loadingScreen;
      if (screen != null && this.spawned) {
         if (this.loadingScreenEndIn > 0) {
            if (--this.loadingScreenEndIn == 0) {
               this.sendLoadingScreen(ServerboundLoadingScreenPacketType.END_LOADING_SCREEN, screen.id());
               this.loadingScreen = null;
               LoadingScreenHold.release();
               this.initializePlayer();
               BedrockNative.LOGGER.info("Dimension change done in {} ms", Util.getMillis() - screen.startedAt());
            }
         } else if (LoadingScreenHold.javaReady() && !this.extras.holdsLoadingScreen()) {
            if (screen.dimensionChange() || !this.heldForPrivileges()) {
               if (!screen.dimensionChange()) {
                  if (!this.joinLevelShown) {
                     this.joinLevelShown = true;
                     this.input.levelShown();
                  } else {
                     this.sendLoadingScreen(ServerboundLoadingScreenPacketType.END_LOADING_SCREEN, null);
                     this.initializePlayer();
                     this.loadingScreen = null;
                     LoadingScreenHold.release();
                     BedrockNative.LOGGER.info("Join loading screen closed after {} ms", Util.getMillis() - screen.startedAt());
                     DevPerfTest.joined(Util.getMillis() - screen.startedAt());
                     SessionTimeline.joinShown();
                  }
               } else if (this.serverDimensionReady || Util.getMillis() - screen.startedAt() >= 60000L) {
                  PlayerActionPacket done = new PlayerActionPacket();
                  done.setRuntimeEntityId(this.runtimeEntityId);
                  done.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
                  done.setBlockPosition(Vector3i.ZERO);
                  done.setResultPosition(Vector3i.ZERO);
                  this.bedrock.sendPacket(done);
                  this.loadingScreenEndIn = 2;
                  this.aimAssistClearIn = 2;
               }
            }
         }
      }
   }

   private void initializePlayer() {
      if (!this.playerInitialized) {
         this.playerInitialized = true;
         SetLocalPlayerAsInitializedPacket initialized = new SetLocalPlayerAsInitializedPacket();
         initialized.setRuntimeEntityId(this.runtimeEntityId);
         this.bedrock.sendPacket(initialized);
         this.input.screenShown();
         this.noChunksCheckIn = 60;
      }
   }

   private void tickNoChunksCheck() {
      if (this.noChunksCheckIn > 0 && --this.noChunksCheckIn == 0 && !this.closed && (this.chunks.chunkPacketsReceived() == 0 || FORCE_NO_CHUNKS_CHECK)) {
         int distance = (Integer)this.minecraft.options.renderDistance().get();
         RequestChunkRadiusPacket request = chunkRadiusRequest(distance);
         this.chunkRadiusAsked = request.getRadius();
         this.bedrock.sendPacket(request);
         BedrockNative.LOGGER.info("no chunks after 3 s: radius asked again ({})", request.getRadius());
      }
   }

   private void watchBelowWorld(LocalPlayer player, long tick) {
      ClientLevel level = this.minecraft.level;
      if (level != null) {
         if (level != this.belowWorldLevel) {
            this.belowWorldLevel = level;
            this.belowWorld.reset();
         }

         String line = this.belowWorld
            .tick(
               level.getMinY(),
               new BelowWorldWatch.Sample(
                  tick,
                  player.getX(),
                  player.getY(),
                  player.getZ(),
                  player.getDeltaMovement().y,
                  this.movementStats.count(),
                  MovementStats.teleports(),
                  this.movementStats.lastCorrectedY(),
                  player.getHealth(),
                  this.minecraft.gameMode == null ? "?" : this.minecraft.gameMode.getPlayerMode().getName()
               )
            );
         if (line != null) {
            BedrockNative.LOGGER.info(line);
         }
      }
   }

   private void reportVehicle(LocalPlayer player, PlayerAuthInputPacket authInput) {
      Entity vehicle = player.getVehicle();
      if (vehicle != null) {
         authInput.setDelta(Vector3f.ZERO);
         if (BedrockVehicles.predicts(vehicle)) {
            Vec3 position = BedrockVehicles.networkPosition();
            Vec3 delta = BedrockVehicles.reportedDelta();
            authInput.getInputData().add(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
            authInput.setPredictedVehicle(this.entities.uniqueIdOf(vehicle));
            authInput.setPosition(Vector3f.from(position.x, position.y, position.z));
            authInput.setDelta(Vector3f.from(delta.x, delta.y, delta.z));
            authInput.setVehicleRotation(Vector2f.from(BedrockVehicles.bedrockPitch(), BedrockVehicles.bedrockYaw()));
         } else {
            Vec3 seat = this.localPlayer.seatPosition(vehicle);
            if (seat != null) {
               authInput.setPosition(Vector3f.from(seat.x, seat.y, seat.z));
            }
         }
      }
   }

   private void leaveVehicle(LocalPlayer player) {
      Entity vehicle = player.getVehicle();
      long vehicleId = this.entities.runtimeIdOf(vehicle.getId());
      Vec3 exit = exitPosition(player, vehicle);
      player.stopRiding();
      player.snapTo(exit.x, exit.y, exit.z, player.getYRot(), player.getXRot());
      player.setDeltaMovement(Vec3.ZERO);
      InteractPacket leave = new InteractPacket();
      leave.setAction(org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action.LEAVE_VEHICLE);
      leave.setRuntimeEntityId(vehicleId);
      leave.setMousePosition(Vector3f.from(exit.x, exit.y + 1.62001F, exit.z));
      this.bedrock.sendPacket(leave);
   }

   private static Vec3 exitPosition(LocalPlayer player, Entity vehicle) {
      float yaw = vehicle.getYRot() * (float) (Math.PI / 180.0);
      double y = Math.floor(vehicle.getY() + 0.1) + 0.001;

      for (int side : new int[]{1, -1}) {
         Vec3 exit = new Vec3(vehicle.getX() + Mth.cos(yaw) * side, y, vehicle.getZ() + Mth.sin(yaw) * side);
         if (player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(exit))) {
            return exit;
         }
      }

      return vehicle.getDismountLocationForPassenger(player).add(0.0, 0.001, 0.0);
   }

   private void clearAimAssist() {
      this.aimAssistClearIn = -1;
      CameraAimAssistInstructionPacket clear = new CameraAimAssistInstructionPacket();
      clear.setPresetId("");
      clear.setAction(AimAssistAction.CLEAR);
      clear.setAllowAimAssist(false);
      this.bedrock.sendPacket(clear);
   }

   private void schedulePredictionSync() {
      if (this.predictionSyncIn < 0) {
         this.predictionSyncIn = 200;
      }
   }

   private void sendLoadingScreen(ServerboundLoadingScreenPacketType type, @Nullable Integer id) {
      ServerboundLoadingScreenPacket packet = new ServerboundLoadingScreenPacket();
      packet.setType(type);
      packet.setLoadingScreenId(id);
      this.bedrock.sendPacket(packet);
   }

   private void onPlayerSpawn() {
      if (!this.spawnStatusSeen) {
         this.spawnStatusSeen = true;
         SessionTimeline.mark("spawn");
         this.minecraft.execute(() -> {
            this.spawned = true;
            if (this.minecraft.getConnection() != null && this.minecraft.getConnection().levelLoadTracker != null) {
               this.minecraft.getConnection().levelLoadTracker.loadingPacketsReceived();
            }
         });
         if (Boolean.getBoolean("bedrocknative.devScreenshot")) {
            new DevTest(
                  this.minecraft,
                  this.movementStats,
                  this.blocks::updateCount,
                  this.cracks::eventCount,
                  this.requests,
                  this.sounds,
                  this.particles,
                  this.forms,
                  this.blocks::blockEventCount,
                  this.bossBars::count,
                  this::handleGamePacket,
                  packet -> this.bedrock.sendPacket(packet),
                  this.chunks::blobCacheSummary,
                  this.inventory::closeGuard
               )
               .start();
         }
      }
   }

   private void onJavaPacketOut(Packet<?> packet) {
      Objects.requireNonNull(packet);
      switch (packet) {
         case ServerboundClientTickEndPacket ignored:
            this.chunks.tickRequests(this.bedrock::sendPacket);
            this.timeWeather.tick();
            this.levelRules.tick();
            this.cracks.tick();
            this.requests.tick();
            this.recipeBook.tick();
            this.forms.tick();
            this.bossBars.tick();
            this.extras.tick();
            this.sleeping.tick();
            this.completion.tick();
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
               this.entities.tickSeat(player);
               this.inventory.tickArmor(player);
               this.inventory.tickScreens();
            }

            this.tickLoadingScreen();
            this.spawnCheck.tick(this.minecraft.level, player);
            this.tickNoChunksCheck();
            this.resendChunkRadius();
            if (this.predictionSyncIn > 0 && --this.predictionSyncIn == 0) {
               this.predictionSyncIn = -1;
               this.bedrock.sendPacket(this.localPlayer.predictionSync(player != null && player.getAbilities().flying));
            }

            if (this.spawned && player != null && !player.isAlive()) {
               this.input.skipTick();
            }

            boolean alive = player == null || player.isAlive();
            if (this.spawned && player != null && player.isAlive()) {
               boolean sneakPressed = player.input.keyPresses.shift() && !this.sneakDown;
               this.sneakDown = player.input.keyPresses.shift();
               if (sneakPressed && player.isPassenger() && !BedrockInputLocks.dismountLocked()) {
                  this.leaveVehicle(player);
               }

               if (this.aimAssistClearIn > 0) {
                  this.aimAssistClearIn--;
               }

               if (this.aimAssistClearIn == 0
                  || this.input.sent() == 2
                  || this.input.teleportToAcknowledge()
                  || player.isPassenger() != this.wasRiding
                  || !this.wasAlive) {
                  this.clearAimAssist();
               }

               this.wasRiding = player.isPassenger();
               this.completion.tickPlayer(player);
               BedrockInventory.HeldReport held = this.inventory.takeHeldReport();
               if (held != null) {
                  if (held.otherItem()) {
                     this.interactions.resendHover();
                  }

                  this.bedrock.sendPacket(held.equipment());
               }

               MobEquipmentPacket offhand = this.inventory.takeOffhandReport();
               if (offhand != null) {
                  this.bedrock.sendPacket(offhand);
               }

               boolean jumped = BedrockMovement.takeJumped();
               PlayerAuthInputPacket authInput = this.input.build(player, jumped);
               this.reportVehicle(player, authInput);
               Vector3f target = this.dimensionTarget;
               if (target != null) {
                  authInput.setPosition(target.add(0.0F, 1.62001F, 0.0F));
               }

               ClientBedrockSession.LoadingScreen screen = this.loadingScreen;
               if (target != null || screen != null && screen.dimensionChange()) {
                  authInput.setDelta(Vector3f.ZERO);
                  authInput.getInputData().add(PlayerAuthInputData.VERTICAL_COLLISION);
               }

               BedrockVehicles.record(player, authInput.getTick());
               this.replay.record(authInput.getTick(), player, jumped);
               this.movementStats.sent(authInput.getTick(), player.position(), player.getDeltaMovement());
               this.interactions.onTickEnd(authInput);
               PlayerInputSender.lastBuilt = authInput;
               this.corrections
                  .sent(
                     player,
                     authInput.getTick(),
                     authInput.getInputData(),
                     jumped && player.input.keyPresses.jump() && !this.minecraft.options.keyJump.isDown()
                  );
               this.bedrock.sendPacket(authInput);
               this.watchBelowWorld(player, authInput.getTick());
            }

            this.wasAlive = alive;
            return;
         case ServerboundPlayerAbilitiesPacket abilities:
            this.input.flightToggled(abilities.isFlying());
            return;
         case ServerboundSetCarriedItemPacket ignoredx:
            return;
         case ServerboundContainerClickPacket click:
            this.requests.onClick(click);
            return;
         case ServerboundSetCreativeModeSlotPacket creativeSlot:
            this.requests.onCreativeSlot(creativeSlot.slotNum(), creativeSlot.itemStack());
            return;
         case ServerboundContainerClosePacket close when this.inventory.lecterns().onJavaClose(close.getContainerId()):
            return;
         case ServerboundContainerClosePacket closex:
            this.requests.onJavaClose(closex.getContainerId());
            return;
         case ServerboundContainerButtonClickPacket button when this.inventory.lecterns().isOpen(button.containerId()):
            this.inventory.lecterns().onButton(button.buttonId());
            return;
         case ServerboundSetJigsawBlockPacket jigsaw:
            this.bedrock.sendPacket(BedrockJigsawBlocks.toBedrock(jigsaw));
            return;
         case ServerboundJigsawGeneratePacket ignoredxx:
            BedrockNative.LOGGER.info("Jigsaw Generate not sent: Bedrock's jigsaw editor has no such button");
            return;
         case ServerboundChatPacket message:
            this.bedrock.sendPacket(this.chat.chat(message.message()));
            return;
         case ServerboundChatCommandPacket command:
            this.bedrock.sendPacket(this.chat.command(command.command()));
            return;
         case ServerboundChatCommandSignedPacket commandx:
            this.bedrock.sendPacket(this.chat.command(commandx.command()));
            return;
         case ServerboundClientCommandPacket commandxx when commandxx.getAction()
   == net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN:
            this.localPlayer.requestRespawn();
            return;
         case ServerboundSetCommandBlockPacket commandBlock:
            this.bedrock.sendPacket(BedrockCommandBlocks.toBedrock(commandBlock));
            return;
         case ServerboundSetStructureBlockPacket structureBlock:
            this.bedrock.sendPacket(BedrockStructureBlocks.toBedrock(structureBlock));
            return;
         case ServerboundSignUpdatePacket sign when this.minecraft.level != null:
            this.bedrock.sendPacket(BedrockSigns.toBedrock(this.minecraft.level, sign));
            return;
         case ServerboundEditBookPacket book:
            BedrockBooks.toBedrock(book, this.identity).forEach(this.bedrock::sendPacket);
            return;
         case ServerboundPlayerCommandPacket commandxxx when commandxxx.getAction()
   == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY:
            this.extras.openMountInventory();
            return;
         case ServerboundPlayerCommandPacket commandxxxx when commandxxxx.getAction()
   == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SLEEPING:
            this.sleeping.leaveBed();
            return;
         default:
            if (!this.completion.onJavaPacket(packet) && !this.interactions.onJavaPacket(packet) && !this.requests.onJavaPacket(packet)) {
               this.logUnsent(packet);
            }
      }
   }

   private void logUnsent(Packet<?> packet) {
      if (this.loggedUnhandled.add(packet.getClass())) {
         BedrockNative.LOGGER.debug("Client action not sent to Bedrock yet: {}", packet.getClass().getSimpleName());
      }
   }

   @Override
   public void onDisconnect(String reason) {
      this.onDisconnect(SessionEnd.connection(reason));
   }

   @Override
   public void onDisconnect(SessionEnd end) {
      if (!this.closed) {
         this.closed = true;
         if (active == this) {
            active = null;
         }

         SessionTimeline.leaveStarted();
         BedrockLobby.sessionEnded();
         this.corrections.sessionEnded();
         this.movementStats.end();
         this.chunks.close();
         ThreadedLight.closeCurrent();
         BedrockMovement.setActive(false);
         BedrockHud.reset();
         this.resetRules();
         BedrockCameraShake.reset();
         BedrockCamera.reset();
         BedrockEmotes.stop();
         LoadingScreenHold.release();
         this.commands.close();
         this.completion.close();
         this.inventory.lecterns().sessionEnded();
         BedrockItemTags.sessionEnded(this.inventory);
         BedrockItemTags.sessionEnded(this.itemWorld);
         this.minecraft.execute(this.forms::close);
         this.minecraft.execute(this.extras::close);
         this.minecraft.execute(ClientBedrockSession::releaseServerItems);
         this.minecraft.execute(() -> {
            this.actors.close();
            SessionTimeline.leaveMark("released");
         });
         BedrockGlyphs.clear();
         this.minecraft.execute(() -> SessionTimeline.leaveMark("font"));
         this.packs.close();
         this.minecraft.execute(() -> SessionTimeline.leaveMark("packs dropped"));
         Component message = javaMessage(end);
         this.minecraft.execute(() -> {
            BedrockSessionConnection current = this.connection;
            boolean left = this.packs.leaving() || end.source() == SessionEnd.Source.LEFT;
            Screen back = this.screenAfterEnd();
            if (current != null && current.isConnected()) {
               if (!left) {
                  BedrockDisconnects.ended(end, this.serverData, this.address.getHostString(), this.address.getPort(), back);
               }

               current.disconnect(message);
            } else if (current != null || !left && end.showsScreen()) {
               if (current == null) {
                  BedrockDisconnects.ended(end, this.serverData, this.address.getHostString(), this.address.getPort(), back);
                  this.minecraft.gui.setScreen(new DisconnectedScreen(back, Component.translatable("connect.failed"), message));
               }
            } else {
               this.minecraft.gui.setScreen(back);
            }
         });
      }
   }

   private static Component javaMessage(SessionEnd end) {
      if (!end.message().isBlank()) {
         return BedrockText.ui(end.message());
      } else {
         String said = end.detail().isBlank() ? null : BedrockText.lookupKey(end.detail().strip());
         if (said != null) {
            return BedrockText.format(said);
         } else {
            String body = BedrockText.lookupKey(OreDisconnect.entry(end.reason()).body());
            return body != null ? BedrockText.format(body) : Component.literal(end.logText());
         }
      }
   }

   private void resetRules() {
      BedrockAdventureSettings.reset();
      BedrockInputLocks.reset();
      BedrockNameTags.reset();
      BedrockGameRules.reset();
   }

   private static void releaseServerItems() {
      long[] clock = new long[]{System.nanoTime()};
      long started = clock[0];
      StringBuilder parts = new StringBuilder();
      ItemMappings.useServerPacks(List.of());
      part(parts, clock, "item mappings");
      BedrockItemIcons.release();
      part(parts, clock, "item icons");
      BedrockBlockModels.release();
      part(parts, clock, "blocks");
      BedrockVanillaTextures.release();
      part(parts, clock, "textures");
      BedrockCreativeTabs.clear();
      part(parts, clock, "creative tabs");
      BedrockModels.release();
      part(parts, clock, "models");
      BedrockSoundEngine.release();
      part(parts, clock, "sounds");
      BedrockParticleEngine.release();
      part(parts, clock, "particles");
      JsonUiHud.reset();
      part(parts, clock, "HUD");
      BlobCaches.sessionEnded();
      part(parts, clock, "blob memory");
      BedrockNative.LOGGER.info("Leave steps on the client thread: {} ({} ms in all)", parts, (System.nanoTime() - started) / 1000000L);
   }

   private void closeBedrock() {
      this.closed = true;
      SessionTimeline.leaveStarted();
      this.corrections.sessionEnded();
      this.movementStats.end();
      this.chunks.close();
      ThreadedLight.closeCurrent();
      BedrockMovement.setActive(false);
      BedrockHud.reset();
      this.resetRules();
      BedrockCameraShake.reset();
      BedrockCamera.reset();
      BedrockEmotes.stop();
      LoadingScreenHold.release();
      this.commands.close();
      this.completion.close();
      this.inventory.lecterns().sessionEnded();
      BedrockItemTags.sessionEnded(this.inventory);
      BedrockItemTags.sessionEnded(this.itemWorld);
      if (this.minecraft.isSameThread()) {
         this.forms.close();
         this.extras.close();
         this.recipeBook.close();
         releaseServerItems();
         this.actors.close();
         SessionTimeline.leaveMark("released");
         BedrockGlyphs.clear();
         SessionTimeline.leaveMark("font");
         this.packs.close();
         SessionTimeline.leaveMark("packs dropped");
      } else {
         this.minecraft.execute(this.forms::close);
         this.minecraft.execute(this.extras::close);
         this.minecraft.execute(this.recipeBook::close);
         this.minecraft.execute(ClientBedrockSession::releaseServerItems);
         this.minecraft.execute(() -> {
            this.actors.close();
            SessionTimeline.leaveMark("released");
         });
         BedrockGlyphs.clear();
         this.minecraft.execute(() -> SessionTimeline.leaveMark("font"));
         this.packs.close();
         this.minecraft.execute(() -> SessionTimeline.leaveMark("packs dropped"));
      }

      BedrockClientSession session = this.bedrock;
      if (session != null && session.isConnected()) {
         session.disconnect("Disconnected");
      }
   }

   private record LoadingScreen(@Nullable Integer id, boolean dimensionChange, long startedAt) {
   }
}
