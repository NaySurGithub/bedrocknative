package net.bedrocknative.client.world;

import java.lang.runtime.SwitchBootstraps;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.FreezeDiagnostics;
import net.bedrocknative.client.rules.BedrockGameRules;
import net.bedrocknative.movement.LocalFlags;
import net.bedrocknative.movement.MovementSpeed;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.DeathInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovementPredictionSyncPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerStartItemCooldownPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDefaultGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.State;
import org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket.Type;
import org.jspecify.annotations.Nullable;

public final class BedrockLocalPlayer {
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private volatile long runtimeId;
   private volatile long uniqueId;
   private volatile CommonPlayerSpawnInfo spawnInfo;
   private volatile GameType playerGameType;
   private volatile GameType worldGameType;
   private Component deathMessage;
   private boolean flyingBeforeSpectator;
   private final Set<EntityFlag> serverFlags = EnumSet.noneOf(EntityFlag.class);
   private final Map<String, Float> attributes = new HashMap<>();
   private AttributeData movement;
   private static volatile Map<String, Float> lastAttributes = Map.of();
   private static volatile boolean serverBlocking;
   private static volatile long lastRuntimeId = -1L;
   private Vector3f boundingBox = Vector3f.from(1.0F, 0.6F, 1.8F);
   @Nullable
   private static volatile Vector2f localSize;
   @Nullable
   private Vector3f seatOffset;
   private boolean respawnAsked;
   private PermissionSet permissions;

   public BedrockLocalPlayer(Minecraft minecraft, Consumer<BedrockPacket> send) {
      lastAttributes = this.attributes;
      this.minecraft = minecraft;
      this.send = send;
   }

   public void start(long runtimeId, long uniqueId, CommonPlayerSpawnInfo spawnInfo, GameType playerGameType, GameType worldGameType) {
      this.runtimeId = runtimeId;
      lastRuntimeId = runtimeId;
      this.uniqueId = uniqueId;
      this.spawnInfo = spawnInfo;
      this.playerGameType = playerGameType;
      this.worldGameType = worldGameType;
      this.deathMessage = null;
      this.serverFlags.clear();
      serverBlocking = false;
      this.attributes.clear();
      this.movement = null;
      this.boundingBox = Vector3f.from(1.0F, 0.6F, 1.8F);
      localSize = null;
      this.respawnAsked = false;
      this.permissions = null;
      this.seatOffset = null;
   }

   public boolean handles(BedrockPacket packet) {
      return packet instanceof UpdateAttributesPacket attributes
         ? attributes.getRuntimeEntityId() == this.runtimeId
         : (
            packet instanceof UpdatePlayerGameTypePacket gameType
               ? gameType.getEntityId() == this.uniqueId
               : packet instanceof SetHealthPacket
                  || packet instanceof SetPlayerGameTypePacket
                  || packet instanceof SetDefaultGameTypePacket
                  || packet instanceof RespawnPacket
                  || packet instanceof SetSpawnPositionPacket
                  || packet instanceof DeathInfoPacket
                  || packet instanceof PlayerStartItemCooldownPacket
         );
   }

   public void apply(BedrockPacket packet) {
      LocalPlayer player = this.minecraft.player;
      if (player != null && this.minecraft.level != null) {
         Objects.requireNonNull(packet);
         BedrockPacket var3 = packet;
         byte var4 = 0;

         while (true) {
            switch (SwitchBootstraps.typeSwitch<"typeSwitch",UpdateAttributesPacket,SetHealthPacket,SetPlayerGameTypePacket,UpdatePlayerGameTypePacket,SetDefaultGameTypePacket,SetSpawnPositionPacket,DeathInfoPacket,PlayerStartItemCooldownPacket,RespawnPacket>(
               var3, var4
            )) {
               case 0:
                  UpdateAttributesPacket attributes = (UpdateAttributesPacket)var3;

                  for (AttributeData attribute : attributes.getAttributes()) {
                     this.attributes.put(attribute.getName(), attribute.getValue());
                     FreezeDiagnostics.attribute(attribute.getName(), attribute.getValue());
                     this.applyAttribute(player, attribute);
                  }

                  return;
               case 1:
                  SetHealthPacket health = (SetHealthPacket)var3;
                  this.setHealth(player, health.getHealth());
                  return;
               case 2: {
                  SetPlayerGameTypePacket gameType = (SetPlayerGameTypePacket)var3;
                  this.setGameType(GameType.from(gameType.getGamemode()));
                  return;
               }
               case 3: {
                  UpdatePlayerGameTypePacket gameType = (UpdatePlayerGameTypePacket)var3;
                  this.setGameType(gameType.getGameType());
                  return;
               }
               case 4: {
                  SetDefaultGameTypePacket gameType = (SetDefaultGameTypePacket)var3;
                  this.worldGameType = GameType.from(gameType.getGamemode());
                  this.setGameType(this.playerGameType);
                  return;
               }
               case 5:
                  SetSpawnPositionPacket spawn = (SetSpawnPositionPacket)var3;
                  if (spawn.getSpawnType() != Type.WORLD_SPAWN) {
                     var4 = 6;
                     break;
                  }

                  Vector3i pos = spawn.getBlockPosition();
                  this.minecraft
                     .level
                     .setRespawnData(
                        new RespawnData(GlobalPos.of(this.minecraft.level.dimension(), new BlockPos(pos.getX(), pos.getY(), pos.getZ())), 0.0F, 0.0F)
                     );
                  return;
               case 6:
                  DeathInfoPacket death = (DeathInfoPacket)var3;
                  this.deathMessage = BedrockText.message(death.getCauseAttackName(), death.getMessageList(), true);
                  if (player.isDeadOrDying()) {
                     this.showDeathScreen(player);
                  }

                  return;
               case 7:
                  PlayerStartItemCooldownPacket cooldown = (PlayerStartItemCooldownPacket)var3;
                  startCooldown(player, cooldown);
                  return;
               case 8:
                  RespawnPacket respawn = (RespawnPacket)var3;
                  if (respawn.getState() != State.SERVER_READY || !player.isDeadOrDying()) {
                     var4 = 9;
                     break;
                  } else {
                     this.respawn(respawn.getPosition());
                     return;
                  }
               default:
                  return;
            }
         }
      }
   }

   @Nullable
   public static Vector2f localSize() {
      return localSize;
   }

   public void applyData(SetEntityDataPacket packet) {
      LocalPlayer player = this.minecraft.player;
      EntityDataMap data = packet.getMetadata();
      if (player != null && data != null) {
         Map<EntityFlag, Boolean> flags = data.getFlags();
         if (flags != null) {
            boolean serverSprinting = this.serverFlags.contains(EntityFlag.SPRINTING);
            LocalFlags.merge(this.serverFlags, flags);
            player.setSharedFlagOnFire(this.serverFlags.contains(EntityFlag.ON_FIRE));
            player.setInvisible(this.serverFlags.contains(EntityFlag.INVISIBLE));
            BedrockMovement.setImmobile(this.serverFlags.contains(EntityFlag.NO_AI));
            Boolean gravity = LocalFlags.gravity(flags);
            if (gravity != null) {
               player.setNoGravity(!gravity);
            }

            Boolean collision = LocalFlags.collision(flags);
            if (collision != null) {
               BedrockMovement.setCollision(collision);
            }

            FreezeDiagnostics.flags(this.serverFlags);
            serverBlocking = this.serverFlags.contains(EntityFlag.BLOCKING);
            if (this.serverFlags.contains(EntityFlag.SPRINTING) != serverSprinting) {
               this.applySpeed(player);
            }
         }

         if (data.get(EntityDataTypes.SCALE) instanceof Float scale) {
            this.boundingBox = Vector3f.from(scale, this.boundingBox.getY(), this.boundingBox.getZ());
            AttributeInstance size = player.getAttribute(Attributes.SCALE);
            if (size != null) {
               size.setBaseValue(Mth.clamp(scale, 0.0625F, 16.0F));
            }
         }

         if (data.get(EntityDataTypes.WIDTH) instanceof Float width) {
            this.boundingBox = Vector3f.from(this.boundingBox.getX(), width, this.boundingBox.getZ());
         }

         if (data.get(EntityDataTypes.HEIGHT) instanceof Float height) {
            this.boundingBox = Vector3f.from(this.boundingBox.getX(), this.boundingBox.getY(), height);
         }

         if (data.get(EntityDataTypes.WIDTH) instanceof Float || data.get(EntityDataTypes.HEIGHT) instanceof Float) {
            boolean posed = this.serverFlags.contains(EntityFlag.SNEAKING)
               || this.serverFlags.contains(EntityFlag.SWIMMING)
               || this.serverFlags.contains(EntityFlag.GLIDING)
               || this.serverFlags.contains(EntityFlag.SLEEPING);
            Vector2f size = Vector2f.from(this.boundingBox.getY(), this.boundingBox.getZ());
            if (!posed && !size.equals(localSize)) {
               localSize = size;
               player.refreshDimensions();
            }
         }

         if (data.get(EntityDataTypes.AIR_SUPPLY) instanceof Short air) {
            player.setAirSupply(air);
         }

         if (data.get(EntityDataTypes.FREEZING_EFFECT_STRENGTH) instanceof Float strength) {
            player.setTicksFrozen(Math.round(strength * player.getTicksRequiredToFreeze()));
         }

         if (data.get(EntityDataTypes.SEAT_OFFSET) instanceof Vector3f seat) {
            this.seatOffset = seat;
         }
      }
   }

   @Nullable
   public Vec3 seatPosition(Entity vehicle) {
      Vector3f seat = this.seatOffset;
      if (seat == null) {
         return null;
      } else {
         Vec3 offset = new Vec3(seat.getX(), 0.0, seat.getZ()).yRot(-vehicle.getYRot() * (float) (Math.PI / 180.0));
         return new Vec3(vehicle.getX() + offset.x, (float)(vehicle.getY() + ActorOffsets.move(vehicle)) + seat.getY(), vehicle.getZ() + offset.z);
      }
   }

   private static void startCooldown(LocalPlayer player, PlayerStartItemCooldownPacket packet) {
      Identifier group = Identifier.tryParse(packet.getItemCategory());
      if (group != null) {
         if (packet.getCooldownDuration() > 0) {
            player.getCooldowns().addCooldown(group, packet.getCooldownDuration());
         } else {
            player.getCooldowns().removeCooldown(group);
         }
      }
   }

   public void requestRespawn() {
      RespawnPacket ready = new RespawnPacket();
      ready.setPosition(Vector3f.ZERO);
      ready.setState(State.CLIENT_READY);
      ready.setRuntimeEntityId(this.runtimeId);
      this.send.accept(ready);
   }

   public void changeDimension(CommonPlayerSpawnInfo info, Vector3f feet, boolean respawn) {
      this.spawnInfo = info;
      LocalPlayer old = this.minecraft.player;
      this.recreatePlayer(feet.getX(), feet.getY(), feet.getZ(), (byte)(respawn ? 1 : 3));
      LocalPlayer player = this.minecraft.player;
      player.setDeltaMovement(Vec3.ZERO);
      if (respawn) {
         this.deathMessage = null;
      } else {
         keepState(old, player);
      }
   }

   private static void keepState(LocalPlayer old, LocalPlayer player) {
      player.getInventory().setSelectedSlot(old.getInventory().getSelectedSlot());
      player.getAbilities().apply(old.getAbilities().pack());

      for (MobEffectInstance effect : old.getActiveEffects()) {
         player.forceAddEffect(new MobEffectInstance(effect), null);
      }

      player.getFoodData().setFoodLevel(old.getFoodData().getFoodLevel());
      player.getFoodData().setSaturation(old.getFoodData().getSaturationLevel());
      player.setExperienceValues(old.experienceProgress, old.totalExperience, old.experienceLevel);
   }

   private void recreatePlayer(double x, double feetY, double z, byte keep) {
      ClientPacketListener connection = this.minecraft.getConnection();
      CommonPlayerSpawnInfo info = new CommonPlayerSpawnInfo(
         this.spawnInfo.dimensionType(),
         this.spawnInfo.dimension(),
         this.spawnInfo.seed(),
         this.minecraft.gameMode.getPlayerMode(),
         Optional.empty(),
         this.spawnInfo.isDebug(),
         this.spawnInfo.isFlat(),
         Optional.empty(),
         0,
         this.spawnInfo.seaLevel()
      );
      connection.handleRespawn(new ClientboundRespawnPacket(info, keep));
      LocalPlayer player = this.minecraft.player;
      if (this.permissions != null) {
         player.setPermissions(this.permissions);
      }

      player.snapTo(x, feetY, z, player.getYRot(), player.getXRot());
      if (connection.levelLoadTracker != null) {
         connection.levelLoadTracker.loadingPacketsReceived();
      }
   }

   private void respawn(Vector3f position) {
      this.respawnAsked = false;
      this.recreatePlayer(position.getX(), position.getY() - 1.62001F, position.getZ(), (byte)1);
      this.deathMessage = null;
      PlayerActionPacket action = new PlayerActionPacket();
      action.setRuntimeEntityId(this.runtimeId);
      action.setAction(PlayerActionType.RESPAWN);
      action.setBlockPosition(Vector3i.ZERO);
      action.setResultPosition(Vector3i.ZERO);
      action.setFace(-1);
      this.send.accept(action);
   }

   public static long runtimeId() {
      return lastRuntimeId;
   }

   public static boolean serverBlocking() {
      return serverBlocking;
   }

   public static float serverAttribute(String name) {
      Float value = lastAttributes.get(name);
      return value == null ? Float.NaN : value;
   }

   private void applyAttribute(LocalPlayer player, AttributeData attribute) {
      String var3 = attribute.getName();
      switch (var3) {
         case "minecraft:health":
            setBase(player.getAttribute(Attributes.MAX_HEALTH), attribute.getMaximum());
            this.setHealth(player, attribute.getValue());
            break;
         case "minecraft:absorption":
            player.setAbsorptionAmount(attribute.getValue());
            break;
         case "minecraft:player.hunger":
            player.getFoodData().setFoodLevel((int)attribute.getValue());
            break;
         case "minecraft:player.saturation":
            player.getFoodData().setSaturation(attribute.getValue());
            break;
         case "minecraft:player.experience":
            player.setExperienceValues(attribute.getValue(), player.totalExperience, player.experienceLevel);
            break;
         case "minecraft:player.level":
            player.setExperienceValues(player.experienceProgress, player.totalExperience, (int)attribute.getValue());
            break;
         case "minecraft:movement":
            this.movement = attribute;
            this.applySpeed(player);
      }
   }

   private void applySpeed(LocalPlayer player) {
      if (this.movement != null) {
         setBase(
            player.getAttribute(Attributes.MOVEMENT_SPEED),
            MovementSpeed.walking(this.movement.getValue(), this.movement.getModifiers(), this.serverFlags.contains(EntityFlag.SPRINTING))
         );
      }
   }

   public MovementPredictionSyncPacket predictionSync(boolean flying) {
      MovementPredictionSyncPacket sync = new MovementPredictionSyncPacket();
      sync.setUniqueEntityId(this.uniqueId);
      sync.getFlags().addAll(this.serverFlags);
      sync.setBoundingBox(this.boundingBox);
      sync.setSpeed(this.attributes.getOrDefault("minecraft:movement", 0.1F));
      sync.setUnderwaterSpeed(this.attributes.getOrDefault("minecraft:underwater_movement", 0.02F));
      sync.setLavaSpeed(this.attributes.getOrDefault("minecraft:lava_movement", 0.02F));
      sync.setJumpStrength(this.attributes.getOrDefault("minecraft:jump_strength", 0.0F));
      sync.setHealth(this.attributes.getOrDefault("minecraft:health", 20.0F));
      sync.setHunger(this.attributes.getOrDefault("minecraft:player.hunger", 20.0F));
      sync.setUnknown1(1.0F);
      sync.setUnknown2(0.0F);
      sync.setUnknown3(1.0F);
      sync.setFlying(flying);
      return sync;
   }

   private void setHealth(LocalPlayer player, float health) {
      boolean wasAlive = !player.isDeadOrDying();
      player.hurtTo(health);
      if (wasAlive && player.isDeadOrDying()) {
         this.showDeathScreen(player);
      }
   }

   private void showDeathScreen(LocalPlayer player) {
      if (BedrockGameRules.immediateRespawn()) {
         if (!this.respawnAsked) {
            this.respawnAsked = true;
            this.requestRespawn();
         }
      } else {
         this.minecraft.gui.setScreen(new DeathScreen(this.deathMessage, this.minecraft.level.getLevelData().isHardcore(), player));
      }
   }

   public void applyAbilities(UpdateAbilitiesPacket packet) {
      LocalPlayer player = this.minecraft.player;
      if (player != null && packet.getUniqueEntityId() == this.uniqueId) {
         this.permissions = permissions(packet.getCommandPermission());
         player.setPermissions(this.permissions);
         Boolean flying = BedrockAbilities.apply(player, packet);
         if (player.isSpectator() && flying != null) {
            this.flyingBeforeSpectator = flying;
         }
      }
   }

   static PermissionSet permissions(CommandPermission permission) {
      if (permission == null) {
         return LevelBasedPermissionSet.ALL;
      } else {
         return switch (permission) {
            case ANY -> LevelBasedPermissionSet.ALL;
            case GAME_DIRECTORS -> LevelBasedPermissionSet.GAMEMASTER;
            default -> LevelBasedPermissionSet.OWNER;
         };
      }
   }

   private void setGameType(GameType gameType) {
      this.playerGameType = gameType;
      net.minecraft.world.level.GameType mode = toJava(gameType, this.worldGameType);
      LocalPlayer player = this.minecraft.player;
      boolean wasSpectator = player.isSpectator();
      boolean flying = player.getAbilities().flying;
      this.minecraft.gameMode.setLocalMode(mode);
      PlayerInfo info = this.minecraft.getConnection() == null ? null : this.minecraft.getConnection().getPlayerInfo(player.getUUID());
      if (info != null) {
         info.setGameMode(mode);
      }

      if (mode == net.minecraft.world.level.GameType.SPECTATOR && !wasSpectator) {
         this.flyingBeforeSpectator = flying;
      } else if (mode != net.minecraft.world.level.GameType.SPECTATOR && wasSpectator) {
         player.getAbilities().flying = this.flyingBeforeSpectator && player.getAbilities().mayfly;
      }
   }

   public static net.minecraft.world.level.GameType toJava(GameType player, GameType world) {
      GameType effective = player == GameType.DEFAULT ? world : player;

      return switch (effective) {
         case CREATIVE, CREATIVE_VIEWER -> net.minecraft.world.level.GameType.CREATIVE;
         case ADVENTURE -> net.minecraft.world.level.GameType.ADVENTURE;
         case SPECTATOR -> net.minecraft.world.level.GameType.SPECTATOR;
         default -> net.minecraft.world.level.GameType.SURVIVAL;
      };
   }

   private static void setBase(AttributeInstance instance, double value) {
      if (instance != null && instance.getBaseValue() != value) {
         instance.setBaseValue(value);
      }
   }
}
