package net.bedrocknative.client.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.entity.ActorPropertyTypes;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityProperties;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeMobPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerUpdateEntityOverridesPacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockActors {
   private static final boolean DEV_TEST = System.getProperty("bedrocknative.devTest") != null;
   @Nullable
   private static volatile BedrockActors current;
   private static long frame;
   private final ActorPropertyTypes propertyTypes = new ActorPropertyTypes();
   private final Map<String, BedrockActors.ActorIdentifier> identifiers = new LinkedHashMap<>();
   private final Int2ObjectOpenHashMap<ActorState> byJavaId = new Int2ObjectOpenHashMap();
   private final List<ActorState> ordered = new ArrayList<>();
   private final Long2ObjectOpenHashMap<ActorState> byRuntimeId = new Long2ObjectOpenHashMap();
   private final Set<String> loggedUnlisted = new HashSet<>();
   private final Set<String> loggedUndefined = new HashSet<>();
   private final MolangEnv env = new MolangEnv();
   private final List<String> animateLog = Collections.synchronizedList(new ArrayList<>());
   private final PlayerSkins skins = new PlayerSkins();
   private final ActorFacing facing = new ActorFacing();
   private final List<String> emoteLog = Collections.synchronizedList(new ArrayList<>());
   private static final Set<String> MISSING_EMOTES = Collections.synchronizedSet(new HashSet<>());
   private LongFunction<Entity> byUniqueId = id -> null;
   @Nullable
   private ActorState local;

   public BedrockActors() {
      current = this;
   }

   @Nullable
   public static BedrockActors current() {
      return current;
   }

   @Nullable
   public static ActorState state(Entity entity) {
      BedrockActors actors = current;
      return actors != null ? (ActorState)actors.byJavaId.get(entity.getId()) : null;
   }

   static long frame() {
      return frame;
   }

   public static void endFrame() {
      frame++;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof AvailableEntityIdentifiersPacket
         || packet instanceof SyncEntityPropertyPacket
         || packet instanceof AnimateEntityPacket
         || packet instanceof EmotePacket
         || packet instanceof ChangeMobPropertyPacket
         || packet instanceof PlayerUpdateEntityOverridesPacket;
   }

   public PlayerSkins skins() {
      return this.skins;
   }

   ActorFacing facing() {
      return this.facing;
   }

   public void facingAdded(Entity entity, float pitch, float yaw, float headYaw, float bodyYaw) {
      ActorState state = (ActorState)this.byJavaId.get(entity.getId());
      if (state != null) {
         ModelRegistry registry = BedrockModels.registry();
         ClientEntityDef definition = registry != null ? registry.entity(state.data().identifier()) : null;
         if (state.carrier() || definition != null && definition.serverPack()) {
            this.facing.spawn(state.data().identifier(), pitch, yaw, headYaw, bodyYaw, state.data());
         }
      }
   }

   public void setLocal(int javaId, long runtimeId) {
      ActorState state = new ActorState(this, javaId, runtimeId, new ActorData("minecraft:player", this.propertyTypes), false, false, true);
      this.local = state;
      this.put(javaId, state);
      this.byRuntimeId.put(runtimeId, state);
   }

   public void emoteLocal(String pieceId, int durationTicks) {
      ActorState state = this.local;
      if (state != null && state.player() != null) {
         state.player().emote(pieceId, durationTicks);
      }
   }

   public void onLocalMetadata(@Nullable EntityDataMap metadata, @Nullable EntityProperties properties) {
      ActorState state = this.local;
      if (state != null) {
         state.data().apply(metadata, properties);
      }
   }

   public void useUniqueIds(LongFunction<Entity> lookup) {
      this.byUniqueId = lookup;
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case AvailableEntityIdentifiersPacket list:
            this.onIdentifiers(list.getIdentifiers());
            break;
         case SyncEntityPropertyPacket properties:
            this.propertyTypes.register(properties.getData());
            BedrockNative.LOGGER
               .debug(
                  "Entity properties of {}: {}",
                  properties.getData().getString("type", "?"),
                  this.propertyTypes.properties(properties.getData().getString("type", ""))
               );
            break;
         case AnimateEntityPacket animate:
            this.onAnimate(animate);
            break;
         case EmotePacket emote:
            this.onEmote(emote);
            break;
         case ChangeMobPropertyPacket change:
            this.onChangeProperty(change);
            break;
         case PlayerUpdateEntityOverridesPacket overrides:
            this.onOverride(overrides);
            break;
         default:
      }
   }

   private void onChangeProperty(ChangeMobPropertyPacket packet) {
      Entity entity = this.byUniqueId.apply(packet.getUniqueEntityId());
      ActorState state = entity != null ? state(entity) : null;
      if (state != null) {
         if (!state.data()
            .properties()
            .change(packet.getProperty(), packet.isBoolValue(), packet.getStringValue(), packet.getIntValue(), packet.getFloatValue())) {
            BedrockNative.LOGGER.debug("ChangeMobProperty {} on {}: the type has no such property or value", packet.getProperty(), state.data().identifier());
         }
      }
   }

   private void onOverride(PlayerUpdateEntityOverridesPacket packet) {
      Entity entity = this.byUniqueId.apply(packet.getEntityUniqueId());
      ActorState state = entity != null ? state(entity) : null;
      if (state != null) {
         ActorPropertyTypes.Values values = state.data().properties();
         switch (packet.getUpdateType()) {
            case CLEAR_OVERRIDES:
               values.clearOverrides();
               break;
            case REMOVE_OVERRIDE:
               values.removeOverride(packet.getPropertyIndex());
               break;
            case SET_INT_OVERRIDE:
               values.override(packet.getPropertyIndex(), packet.getIntValue());
               break;
            case SET_FLOAT_OVERRIDE:
               values.override(packet.getPropertyIndex(), packet.getFloatValue());
         }
      }
   }

   private void onEmote(EmotePacket packet) {
      ActorState state = (ActorState)this.byRuntimeId.get(packet.getRuntimeEntityId());
      String fields = "emote="
         + packet.getEmoteId()
         + " duration="
         + packet.getEmoteDuration()
         + " flags="
         + packet.getFlags()
         + " actor="
         + packet.getRuntimeEntityId()
         + (state != null ? " (" + state.data().identifier() + ")" : " (unknown)");
      this.emoteLog.add(fields);
      BedrockNative.LOGGER.info("Emote {}", fields);
      if (state != null && state.player() != null) {
         state.player().emote(packet.getEmoteId(), packet.getEmoteDuration());
      }
   }

   static void logMissingEmote(String pieceId) {
      if (MISSING_EMOTES.add(pieceId.toLowerCase(Locale.ROOT))) {
         BedrockNative.LOGGER.info("Emote {} is not in the player's Bedrock install (a marketplace emote?): not played", pieceId);
      }
   }

   public List<String> emoteLog() {
      synchronized (this.emoteLog) {
         return List.copyOf(this.emoteLog);
      }
   }

   private void onIdentifiers(NbtMap list) {
      this.identifiers.clear();
      int based = 0;

      for (NbtMap entry : list.getList("idlist", NbtType.COMPOUND, List.of())) {
         String id = entry.getString("id", "");
         if (!id.isEmpty()) {
            BedrockActors.ActorIdentifier identifier = new BedrockActors.ActorIdentifier(
               id, entry.getString("bid", ""), entry.getInt("rid", 0), entry.getByte("summonable", (byte)0) != 0, entry.getByte("hasspawnegg", (byte)0) != 0
            );
            this.identifiers.put(id, identifier);
            if (!identifier.baseId().isEmpty()) {
               based++;
            }
         }
      }

      BedrockNative.LOGGER.info("Server actor identifiers: {} ({} with a base identifier)", this.identifiers.size(), based);
      this.reportDefinitions(BedrockModels.registry());
   }

   private void reportDefinitions(@Nullable ModelRegistry registry) {
      if (registry != null && !this.identifiers.isEmpty()) {
         List<String> defined = new ArrayList<>();

         for (String id : this.identifiers.keySet()) {
            ClientEntityDef definition = registry.entity(id);
            if (definition != null && definition.serverPack()) {
               defined.add(id);
            }
         }

         BedrockNative.LOGGER
            .info(
               "Server packs define client entities for {} of the {} listed actor identifiers{}",
               new Object[]{defined.size(), this.identifiers.size(), defined.isEmpty() ? "" : ": " + defined.subList(0, Math.min(40, defined.size()))}
            );
      }
   }

   private void onAnimate(AnimateEntityPacket packet) {
      LongList ids = packet.getRuntimeEntityIds();
      String fields = "animation="
         + packet.getAnimation()
         + " nextState="
         + packet.getNextState()
         + " stopExpression="
         + packet.getStopExpression()
         + " stopExpressionVersion="
         + packet.getStopExpressionVersion()
         + " controller="
         + packet.getController()
         + " blendOutTime="
         + packet.getBlendOutTime()
         + " actors="
         + ids;
      if (DEV_TEST) {
         BedrockNative.LOGGER.info("AnimateEntity {}", fields);
         this.animateLog.add(fields);
      } else {
         BedrockNative.LOGGER.debug("AnimateEntity {}", fields);
      }

      for (int i = 0; i < ids.size(); i++) {
         ActorState state = (ActorState)this.byRuntimeId.get(ids.getLong(i));
         if (state != null) {
            state.animate(
               packet.getAnimation(),
               packet.getNextState(),
               packet.getStopExpression(),
               packet.getStopExpressionVersion(),
               packet.getController(),
               packet.getBlendOutTime()
            );
         }
      }
   }

   public boolean drawn(String identifier) {
      ModelRegistry registry = BedrockModels.registry();
      if (registry == null) {
         return BedrockModels.loading();
      } else {
         boolean defined = registry.entity(identifier) != null;
         if (!defined && this.loggedUndefined.add(identifier)) {
            BedrockNative.LOGGER.info("No client entity for Bedrock actor {} in the pack stack: an invisible box of its size", identifier);
         }

         return defined;
      }
   }

   public boolean replacesJavaType(String identifier) {
      ModelRegistry registry = BedrockModels.registry();
      return registry != null && registry.hasBaseGame() && registry.serverDefines(identifier);
   }

   public void onSpawn(Entity entity, String identifier, long runtimeId, @Nullable EntityDataMap metadata, @Nullable EntityProperties properties) {
      this.onSpawn(entity, identifier, runtimeId, 0L, metadata, properties);
   }

   public void onSpawn(Entity entity, String identifier, long runtimeId, long uniqueId, @Nullable EntityDataMap metadata, @Nullable EntityProperties properties) {
      this.remove(entity.getId());
      ActorData data = new ActorData(identifier, this.propertyTypes);
      data.apply(metadata, properties);
      boolean isCarrier = entity instanceof BedrockActorEntity;
      boolean isPlayer = entity instanceof Player;
      ActorState state = new ActorState(this, entity.getId(), runtimeId, data, isCarrier, !isCarrier && !isPlayer, isPlayer);
      state.uniqueId(uniqueId);
      this.put(entity.getId(), state);
      this.byRuntimeId.put(runtimeId, state);
      ModelRegistry registry = BedrockModels.registry();
      if (registry != null && !isPlayer && (isCarrier || BedrockMobs.enabled())) {
         ActorPrep.spawned(registry, registry.entity(identifier), entity);
      }

      if (entity instanceof BedrockActorEntity carrier) {
         carrier.setBedrockIdentifier(identifier);
         state.bind(registry, registry != null ? registry.entity(identifier) : null);
      }

      if (!this.identifiers.isEmpty() && !this.identifiers.containsKey(identifier) && this.loggedUnlisted.add(identifier)) {
         BedrockNative.LOGGER.info("Actor {} is not in the server's actor identifier list", identifier);
      }
   }

   public void onMetadata(Entity entity, @Nullable EntityDataMap metadata, @Nullable EntityProperties properties) {
      ActorState state = (ActorState)this.byJavaId.get(entity.getId());
      if (state != null) {
         state.data().apply(metadata, properties);
      }
   }

   public void onEvent(Entity entity, EntityEventType type) {
      this.onEvent(entity, type, 0);
   }

   public void onEvent(Entity entity, EntityEventType type, int data) {
      ActorState state = (ActorState)this.byJavaId.get(entity.getId());
      if (state != null) {
         state.onEvent(type, data, entity.level().getGameTime());
      }
   }

   public void onSwing(Entity entity) {
      ActorState state = (ActorState)this.byJavaId.get(entity.getId());
      PlayerRuntime player = state != null ? state.player() : null;
      if (player != null) {
         player.swing(entity.level().getGameTime());
      }
   }

   boolean leashedTo(long holder, String identifier) {
      for (int i = 0; i < this.ordered.size(); i++) {
         ActorData data = this.ordered.get(i).data();
         if (data.value(EntityDataTypes.LEASH_HOLDER) instanceof Long leash && leash == holder && data.identifier().equals(identifier)) {
            return true;
         }
      }

      return false;
   }

   private void put(int javaId, ActorState state) {
      ActorState previous = (ActorState)this.byJavaId.put(javaId, state);
      if (previous != null) {
         this.ordered.remove(previous);
      }

      this.ordered.add(state);
   }

   public void onEquipment(Entity entity, String slot, @Nullable ItemData item) {
      ActorState state = (ActorState)this.byJavaId.get(entity.getId());
      if (state != null) {
         state.data().equip(slot, item != null && item.getDefinition() != null ? item.getDefinition().getIdentifier() : null);
      }
   }

   public void remove(int javaId) {
      ActorState state = (ActorState)this.byJavaId.remove(javaId);
      if (state != null) {
         this.ordered.remove(state);
         this.byRuntimeId.remove(state.runtimeId());
         state.release();
      }
   }

   public void clear() {
      this.byJavaId.values().forEach(ActorState::release);
      this.byJavaId.clear();
      this.ordered.clear();
      this.byRuntimeId.clear();
      this.local = null;
      this.skins.worldCleared();
   }

   public void close() {
      this.clear();
      this.skins.clear();
      this.propertyTypes.clear();
      this.identifiers.clear();
      if (current == this) {
         current = null;
      }
   }

   void registryChanged(ModelRegistry registry) {
      this.skins.registryChanged(registry);
      int bound = 0;
      ObjectIterator var3 = this.byJavaId.int2ObjectEntrySet().iterator();

      while (var3.hasNext()) {
         Entry<ActorState> entry = (Entry<ActorState>)var3.next();
         ActorState state = (ActorState)entry.getValue();
         if (state.carrier()) {
            state.bind(registry, registry.entity(state.data().identifier()));
            if (state.instance() != null) {
               bound++;
            }
         } else {
            state.unbindMob();
         }
      }

      if (bound > 0) {
         BedrockNative.LOGGER.info("Bedrock models re-bound {} live actors", bound);
      }

      this.reportDefinitions(registry);
   }

   MolangEnv env() {
      return this.env;
   }

   @Nullable
   Entity byUniqueId(long uniqueId) {
      return this.byUniqueId.apply(uniqueId);
   }

   public ActorPropertyTypes propertyTypes() {
      return this.propertyTypes;
   }

   public Map<String, BedrockActors.ActorIdentifier> identifiers() {
      return Collections.unmodifiableMap(this.identifiers);
   }

   public List<ActorState> states() {
      return List.copyOf(this.byJavaId.values());
   }

   public List<String> animateLog() {
      synchronized (this.animateLog) {
         return List.copyOf(this.animateLog);
      }
   }

   public record ActorIdentifier(String id, String baseId, int runtimeId, boolean summonable, boolean spawnEgg) {
   }
}
