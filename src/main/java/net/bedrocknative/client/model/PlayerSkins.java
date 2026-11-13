package net.bedrocknative.client.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.content.FolderPack;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.prep.PrepPool;
import net.bedrocknative.model.skin.EmoteLibrary;
import net.bedrocknative.model.skin.PlayerSkin;
import net.bedrocknative.model.skin.PreparedSkin;
import net.bedrocknative.model.skin.SkinModel;
import net.minecraft.util.Util;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.jspecify.annotations.Nullable;

public final class PlayerSkins {
   private static final boolean TRUSTED_ONLY = BedrockMobs.switchOn(System.getProperty("bedrocknative.trustedSkinsOnly"));
   private static final PlayerSkin STEVE = PlayerSkin.defaultSkin(false, null);
   private static final PlayerSkin ALEX = PlayerSkin.defaultSkin(true, null);
   private static final int MAX_EXPLAINED = 64;
   private static final int MAX_UNLISTED_EARLY = 256;
   private static final long EXPLAIN_AFTER_NANOS = 2000000000L;
   private static volatile @Nullable EmoteLibrary emotes;
   private static boolean emotesLoading;
   private final Map<UUID, PlayerSkins.Entry> skins = new HashMap<>();
   private final Map<UUID, String> names = new HashMap<>();
   private final Map<String, PlayerSkins.Built> models = new HashMap<>();
   private final Map<String, Integer> wearers = new HashMap<>();
   private final Set<String> loggedFallbacks = new HashSet<>();
   private final Set<UUID> listed = new HashSet<>();
   private final Map<UUID, PlayerSkins.Spawn> spawned = new HashMap<>();
   private final Set<UUID> unlistedEarly = new HashSet<>();
   private final Set<UUID> explained = new HashSet<>();
   private final Map<UUID, PlayerSkins.Pending> pending = new HashMap<>();
   private final BooleanSupplier active;
   private final BiFunction<ModelRegistry, PlayerSkin, @Nullable SkinModel> builder;
   private final LongSupplier clock;
   private final @Nullable PrepPool prep;
   private final Supplier<@Nullable ModelRegistry> registry;
   private final Executor modelExecutor;
   private @Nullable UUID localUuid;
   private @Nullable SerializedSkin localSkin;
   private @Nullable String localKey;
   private boolean localStandIn;
   private PlayerSkins.@Nullable Skipped localSkipped;
   private int versions;
   private int decoded;
   private int changes;
   private int received;
   private int receivedOwn;
   private int keptAfterRemoval;
   private long submissions;
   private boolean loggedKeep;

   public PlayerSkins() {
      this(BedrockPlayers::enabled, PlayerSkins::build, System::nanoTime, ActorPrep.pool(), BedrockModels::registry, ActorPrep.modelExecutor());
   }

   PlayerSkins(BooleanSupplier active, BiFunction<ModelRegistry, PlayerSkin, @Nullable SkinModel> builder) {
      this(active, builder, System::nanoTime);
   }

   PlayerSkins(BooleanSupplier active, BiFunction<ModelRegistry, PlayerSkin, @Nullable SkinModel> builder, LongSupplier clock) {
      this(active, builder, clock, null, () -> null, Util.backgroundExecutor());
   }

   PlayerSkins(
      BooleanSupplier active,
      BiFunction<ModelRegistry, PlayerSkin, @Nullable SkinModel> builder,
      LongSupplier clock,
      @Nullable PrepPool prep,
      Supplier<@Nullable ModelRegistry> registry,
      Executor modelExecutor
   ) {
      this.active = active;
      this.builder = builder;
      this.clock = clock;
      this.prep = prep;
      this.registry = registry;
      this.modelExecutor = modelExecutor;
      if (active.getAsBoolean()) {
         emotes();
      }
   }

   void activate() {
      emotes();
      PlayerSkins.Skipped skipped = this.localSkipped;
      UUID local = this.localUuid;
      if (skipped != null && local != null) {
         this.setNow(local, skipped.skin(), skipped.trusted(), skipped.source());
      }
   }

   public void local(UUID uuid, SerializedSkin skin) {
      this.local(uuid, skin, false);
   }

   public void local(UUID uuid, SerializedSkin skin, boolean standIn) {
      this.localUuid = uuid;
      this.localSkin = skin;
      this.localStandIn = standIn;
      this.localKey = null;
      this.apply(uuid, skin, true, "own login skin", false);
   }

   public @Nullable SerializedSkin localSkin() {
      return this.localSkin;
   }

   public void listed(UUID uuid, String name, @Nullable SerializedSkin skin, boolean trusted) {
      this.listed.add(uuid);
      this.unlistedEarly.remove(uuid);
      if (!name.isEmpty()) {
         this.names.put(uuid, name);
      }

      if (skin != null && this.acceptsServerSkin(uuid, skin)) {
         this.apply(uuid, skin, trusted, "player list", true);
      }
   }

   public void unlisted(UUID uuid) {
      if (!uuid.equals(this.localUuid)) {
         this.listed.remove(uuid);
         if (this.spawned.containsKey(uuid)) {
            this.keptAfterRemoval++;
            if (!this.loggedKeep && (this.skins.containsKey(uuid) || this.pending.containsKey(uuid))) {
               this.loggedKeep = true;
               BedrockNative.LOGGER
                  .info(
                     "Player list entry of {} removed while its player is in the world: it keeps its skin, as on Bedrock (servers add NPCs and Geyser its custom heads this way)",
                     this.name(uuid)
                  );
            }
         } else {
            if ((this.skins.containsKey(uuid) || this.pending.containsKey(uuid)) && this.unlistedEarly.size() < 256) {
               this.unlistedEarly.add(uuid);
            }

            this.drop(uuid);
         }
      }
   }

   public void spawned(UUID uuid) {
      this.spawned(uuid, 0L);
   }

   public void spawned(UUID uuid, long distanceSq) {
      this.spawned.put(uuid, new PlayerSkins.Spawn(this.clock.getAsLong(), distanceSq));
      PlayerSkins.Pending waiting = this.pending.get(uuid);
      if (waiting != null && this.prep != null) {
         PrepPool.Ticket ticket = waiting.ticket;
         if (ticket != null && (ticket.live() || ticket.state() == PrepPool.State.FINISHED)) {
            this.prep.promote(ticket, distanceSq);
         } else {
            waiting.ticket = this.schedule(uuid, waiting, PrepPool.Tier.NEAR, distanceSq);
         }
      }
   }

   public void despawned(UUID uuid) {
      if (this.spawned.remove(uuid) != null && !uuid.equals(this.localUuid) && !this.listed.contains(uuid)) {
         this.drop(uuid);
      }
   }

   public void worldCleared() {
      for (UUID uuid : List.copyOf(this.spawned.keySet())) {
         this.despawned(uuid);
      }
   }

   private void drop(UUID uuid) {
      this.cancelPending(uuid);
      PlayerSkins.Entry removed = this.skins.remove(uuid);
      if (removed != null) {
         this.release(removed.skin());
      }

      this.names.remove(uuid);
   }

   private void cancelPending(UUID uuid) {
      PlayerSkins.Pending waiting = this.pending.remove(uuid);
      if (waiting != null && waiting.ticket != null && this.prep != null) {
         this.prep.cancel(waiting.ticket);
      }
   }

   public void changed(UUID uuid, @Nullable SerializedSkin skin, boolean trusted) {
      this.received++;
      if (uuid.equals(this.localUuid)) {
         this.receivedOwn++;
      }

      if (this.active.getAsBoolean() || uuid.equals(this.localUuid)) {
         if (skin != null && this.acceptsServerSkin(uuid, skin)) {
            if (this.apply(uuid, skin, trusted, "PlayerSkin", true)) {
               this.changes++;
            }
         } else {
            BedrockNative.LOGGER.info("PlayerSkin for {} kept the player's own skin (no overridingPlayerAppearance)", this.name(uuid));
         }
      }
   }

   private boolean acceptsServerSkin(UUID uuid, SerializedSkin skin) {
      return !uuid.equals(this.localUuid) || skin.isOverridingPlayerAppearance();
   }

   public void setOwn(UUID uuid, SerializedSkin skin) {
      if (this.apply(uuid, skin, true, "own skin change", false)) {
         this.changes++;
      }
   }

   private boolean apply(UUID uuid, SerializedSkin skin, boolean trusted, String source, boolean prepared) {
      if (!this.active.getAsBoolean()) {
         if (uuid.equals(this.localUuid)) {
            this.localSkipped = new PlayerSkins.Skipped(skin, trusted, source);
         }

         return false;
      } else {
         if (prepared && this.prep != null) {
            this.submit(uuid, skin, trusted, source);
         } else {
            this.setNow(uuid, skin, trusted, source);
         }

         return true;
      }
   }

   private void setNow(UUID uuid, SerializedSkin serialized, boolean trusted, String source) {
      this.cancelPending(uuid);
      this.set(uuid, PlayerSkin.of(serialized), trusted, source);
   }

   private void submit(UUID uuid, SerializedSkin serialized, boolean trusted, String source) {
      this.cancelPending(uuid);
      PlayerSkins.Pending waiting = new PlayerSkins.Pending(++this.submissions, serialized, trusted, source);
      this.pending.put(uuid, waiting);
      PlayerSkins.Spawn spawn = this.spawned.get(uuid);
      boolean inWorld = spawn != null || uuid.equals(this.localUuid);
      waiting.ticket = this.schedule(uuid, waiting, inWorld ? PrepPool.Tier.NEAR : PrepPool.Tier.LISTED, spawn != null ? spawn.distanceSq() : 0L);
   }

   private PrepPool.Ticket schedule(UUID uuid, PlayerSkins.Pending waiting, PrepPool.Tier tier, long distanceSq) {
      SerializedSkin serialized = waiting.skin;
      boolean trusted = waiting.trusted;
      long seq = waiting.seq;
      BiFunction<ModelRegistry, PlayerSkin, SkinModel> build = this.builder;
      Supplier<ModelRegistry> registries = this.registry;
      return this.prep
         .submit(
            tier,
            distanceSq,
            tier == PrepPool.Tier.LISTED,
            ticket -> prepare(serialized, trusted, () -> ticket.tier() == PrepPool.Tier.NEAR ? registries.get() : null, build),
            prepared -> this.finish(uuid, seq, prepared),
            PreparedSkin::bytes
         );
   }

   static PreparedSkin prepare(
      SerializedSkin serialized,
      boolean trusted,
      Supplier<@Nullable ModelRegistry> modelFor,
      BiFunction<ModelRegistry, PlayerSkin, @Nullable SkinModel> builder
   ) {
      PlayerSkin skin = PlayerSkin.of(serialized);
      PlayerSkin drawn = drawn(skin, trusted);
      ModelRegistry registry = drawn.isDefault() ? null : modelFor.get();
      SkinModel model = registry != null ? builder.apply(registry, drawn) : null;
      return new PreparedSkin(skin, model != null ? registry : null, model);
   }

   private void finish(UUID uuid, long seq, PreparedSkin prepared) {
      PlayerSkins.Pending waiting = this.pending.get(uuid);
      if (waiting != null && waiting.seq == seq) {
         this.pending.remove(uuid);
         this.set(uuid, prepared.skin(), waiting.trusted, waiting.source);
         SkinModel model = prepared.model();
         ModelRegistry built = prepared.registry();
         if (model != null && built != null && built == this.registry.get()) {
            String key = prepared.skin().key();
            PlayerSkins.Built known = this.models.get(key);
            if (this.wearers.containsKey(key) && (known == null || known.registry() != built || !known.model().isDone())) {
               this.models.put(key, new PlayerSkins.Built(built, CompletableFuture.completedFuture(model)));
            }
         }
      }
   }

   private void set(UUID uuid, PlayerSkin skin, boolean trusted, String source) {
      this.decoded++;
      PlayerSkins.Entry previous = this.skins.get(uuid);
      if (previous != null && previous.trusted() == trusted && previous.skin().key().equals(skin.key())) {
         if (uuid.equals(this.localUuid)) {
            this.localSkipped = null;
         }
      } else {
         SkinTextures.acquire(skin);
         this.wearers.merge(skin.key(), 1, Integer::sum);
         if (previous != null) {
            this.release(previous.skin());
         }

         if (uuid.equals(this.localUuid)) {
            this.localSkipped = null;
         }

         this.skins.put(uuid, new PlayerSkins.Entry(skin, trusted, ++this.versions));
         BedrockNative.LOGGER.info("Skin of {} ({}): {}", new Object[]{this.name(uuid), source, skin});
         if (skin.fallback() != null && this.loggedFallbacks.add(skin.key() + skin.fallback())) {
            BedrockNative.LOGGER.info("Skin of {} drawn as the default: {}", this.name(uuid), skin.fallback());
         }
      }
   }

   private void release(PlayerSkin skin) {
      SkinTextures.release(skin);
      String key = skin.key();
      if (this.wearers.computeIfPresent(key, (k, count) -> count > 1 ? count - 1 : null) == null) {
         this.models.remove(key);
      }
   }

   private String name(UUID uuid) {
      return this.names.getOrDefault(uuid, uuid.equals(this.localUuid) ? "the local player" : uuid.toString());
   }

   public PlayerSkins.@Nullable Entry entry(UUID uuid) {
      return this.skins.get(uuid);
   }

   public PlayerSkin drawn(PlayerSkins.@Nullable Entry entry) {
      return entry == null ? STEVE : drawn(entry.skin(), entry.trusted());
   }

   private static PlayerSkin drawn(PlayerSkin skin, boolean trusted) {
      if (TRUSTED_ONLY && !trusted && !skin.isDefault()) {
         return skin.slim() ? ALEX : STEVE;
      } else {
         return skin;
      }
   }

   public PlayerSkin drawnFor(UUID uuid) {
      PlayerSkins.Entry entry = this.skins.get(uuid);
      PlayerSkin drawn = entry != null ? this.drawn(entry) : this.waiting(uuid);
      if (this.explained.size() < 64 && !this.explained.contains(uuid) && !uuid.equals(this.localUuid)) {
         String reason = this.defaultReason(uuid, entry, drawn);
         if (reason != null) {
            this.explained.add(uuid);
            BedrockNative.LOGGER.info("Player {} {}", this.name(uuid), reason);
         }
      }

      return drawn;
   }

   private PlayerSkin waiting(UUID uuid) {
      PlayerSkins.Pending coming = this.pending.get(uuid);
      return coming != null && coming.slim ? ALEX : STEVE;
   }

   private @Nullable String defaultReason(UUID uuid, PlayerSkins.@Nullable Entry entry, PlayerSkin drawn) {
      if (entry == null) {
         PlayerSkins.Spawn since = this.spawned.get(uuid);
         return !this.pending.containsKey(uuid) && since != null && this.clock.getAsLong() - since.time() >= 2000000000L
            ? "drawn as the default Steve: "
               + (
                  this.unlistedEarly.contains(uuid)
                     ? "its player list entry (and skin) was removed before its AddPlayer, so the actor found none"
                     : "no skin: no player list entry or PlayerSkin gave it one"
               )
            : null;
      } else if (drawn != entry.skin()) {
         return "drawn as the default " + (drawn.slim() ? "Alex" : "Steve") + ": its skin is untrusted and trusted skins only is on";
      } else if (entry.skin().fallback() != null) {
         return "drawn as the default " + (drawn.slim() ? "Alex" : "Steve") + ": its skin was refused (" + entry.skin().fallback() + ")";
      } else {
         return this.localStandIn && entry.skin().key().equals(this.localKey())
            ? "wears a skin identical to the local player's login skin, which is a stand-in (the install's Steve): either it is the default skin of another player, or the server dresses it in yours (NPCs showing your skin look like Steve here); the capture relay saves the real one (config/bedrocknative/bedrock-skin.json)"
            : null;
      }
   }

   private @Nullable String localKey() {
      if (this.localKey == null && this.localSkin != null) {
         this.localKey = PlayerSkin.of(this.localSkin).key();
      }

      return this.localKey;
   }

   public void explainModel(UUID uuid, SkinModel model) {
      if (model.fallback() != null && this.explained.size() < 64 && !uuid.equals(this.localUuid) && this.explained.add(uuid)) {
         BedrockNative.LOGGER
            .info("Player {} drawn as the default {}: {}", new Object[]{this.name(uuid), model.drawn().slim() ? "Alex" : "Steve", model.fallback()});
      }
   }

   public int keptAfterRemoval() {
      return this.keptAfterRemoval;
   }

   public int explained() {
      return this.explained.size();
   }

   public boolean isListed(UUID uuid) {
      return this.listed.contains(uuid);
   }

   public boolean preparing(UUID uuid) {
      return this.pending.containsKey(uuid);
   }

   public int preparing() {
      return this.pending.size();
   }

   public @Nullable SkinModel model(ModelRegistry registry, PlayerSkin skin) {
      String key = skin.key();
      PlayerSkins.Built built = this.models.get(key);
      if (built == null || built.registry() != registry) {
         CompletableFuture<SkinModel> future = CompletableFuture.supplyAsync(() -> this.builder.apply(registry, skin), this.modelExecutor);
         built = new PlayerSkins.Built(registry, future);
         this.models.put(key, built);
      }

      SkinModel model;
      try {
         model = built.model().getNow(null);
      } catch (RuntimeException e) {
         if (this.loggedFallbacks.add(key + "/failed")) {
            BedrockNative.LOGGER.warn("The model of skin {} failed: the player is drawn by Java", key, e);
         }

         return null;
      }

      if (model != null && model.fallback() != null && this.loggedFallbacks.add(key + "/model")) {
         BedrockNative.LOGGER.info("Skin {} drawn as the default {}: {}", new Object[]{key, model.drawn().slim() ? "Alex" : "Steve", model.fallback()});
      }

      return model;
   }

   public @Nullable SkinModel defaultModel(ModelRegistry registry, boolean slim) {
      return this.model(registry, slim ? ALEX : STEVE);
   }

   private static SkinModel build(ModelRegistry registry, PlayerSkin skin) {
      try {
         SkinModel model = SkinModel.build(registry, skin);
         if (model != null) {
            return model;
         }
      } catch (RuntimeException e) {
         BedrockNative.LOGGER.warn("Couldn't build the model of skin {}", skin.key(), e);
      }

      return SkinModel.build(registry, PlayerSkin.defaultSkin(skin.slim(), "the skin's model couldn't be built"));
   }

   void registryChanged(ModelRegistry next) {
      this.models.clear();
      if (this.prep != null && this.active.getAsBoolean()) {
         this.defaultModel(next, false);
         this.defaultModel(next, true);
      }
   }

   void clear() {
      for (UUID uuid : List.copyOf(this.pending.keySet())) {
         this.cancelPending(uuid);
      }

      this.skins.clear();
      this.names.clear();
      this.models.clear();
      this.wearers.clear();
      this.listed.clear();
      this.spawned.clear();
      this.unlistedEarly.clear();
      this.explained.clear();
      this.localSkipped = null;
      SkinTextures.clear();
   }

   int models() {
      return this.models.size();
   }

   Map<String, Integer> wearerCounts() {
      return Map.copyOf(this.wearers);
   }

   public int decoded() {
      return this.decoded;
   }

   public int changes() {
      return this.changes;
   }

   public int received() {
      return this.received;
   }

   public int receivedOwn() {
      return this.receivedOwn;
   }

   public @Nullable UUID localUuid() {
      return this.localUuid;
   }

   static boolean emotesLoaded() {
      emotes();
      return emotes != null;
   }

   static EmoteLibrary emotes() {
      EmoteLibrary library = emotes;
      if (library != null) {
         return library;
      }

      if (!emotesLoading) {
         emotesLoading = true;
         CompletableFuture.runAsync(() -> {
            Path persona = BedrockInstall.dataFolder().resolve("resource_packs").resolve("persona");
            EmoteLibrary loaded = Files.isDirectory(persona) ? EmoteLibrary.load(new FolderPack(persona)) : EmoteLibrary.EMPTY;
            BedrockNative.LOGGER.info("Bedrock emotes of the install: {}", loaded.describe());
            emotes = loaded;
         }, Util.backgroundExecutor());
      }

      return EmoteLibrary.EMPTY;
   }

   private record Built(ModelRegistry registry, CompletableFuture<SkinModel> model) {
   }

   public record Entry(PlayerSkin skin, boolean trusted, int version) {
   }

   private static final class Pending {
      final long seq;
      final SerializedSkin skin;
      final boolean trusted;
      final String source;
      final boolean slim;
      PrepPool.@Nullable Ticket ticket;

      Pending(long seq, SerializedSkin skin, boolean trusted, String source) {
         this.seq = seq;
         this.skin = skin;
         this.trusted = trusted;
         this.source = source;
         this.slim = "slim".equalsIgnoreCase(skin.getArmSize());
      }
   }

   private record Skipped(SerializedSkin skin, boolean trusted, String source) {
   }

   private record Spawn(long time, long distanceSq) {
   }
}
