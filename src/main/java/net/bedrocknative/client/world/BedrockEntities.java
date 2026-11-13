package net.bedrocknative.client.world;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.world.InteractionAccessor;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.bedrocknative.client.movement.ServerBoxed;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.client.ui.CloseOnHurt;
import net.bedrocknative.movement.ServerBox;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.bedrocknative.world.entity.EntityMappings;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.state.BlockSeat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.PassengerOfBlockArguments;
import org.cloudburstmc.protocol.bedrock.data.PassengerOfBlockArguments.EmoteType;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData.Type;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPaintingPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPassengerOfBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TakeItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.Flag;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action;
import org.jspecify.annotations.Nullable;

public final class BedrockEntities {
   private static final int OFFHAND_CONTAINER = 119;
   private static final long UUID_MARKER = -4697254411347427328L;
   public static final float BOAT_Y_OFFSET = 0.375F;
   public static final float BOAT_YAW_OFFSET = 90.0F;
   private static final String HEALTH = "minecraft:health";
   private final Minecraft minecraft;
   private final Long2IntMap javaIds = new Long2IntOpenHashMap();
   private final Long2LongMap runtimeIds = new Long2LongOpenHashMap();
   private final Map<Long, String> playerNames = new HashMap<>();
   private final Int2IntMap tradeExperience = new Int2IntOpenHashMap();
   private final Map<Long, Pose> blockSeats = new HashMap<>();
   private @Nullable BlockSeat localSeat;
   private int nextJavaId;
   private int localJavaId;
   private long localRuntimeId;
   private int turnedPlayersLogged;
   private final BedrockActors actors;
   private static final boolean SERVER_BOXES = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.serverBoxes", "").trim());

   public BedrockEntities(Minecraft minecraft, BedrockActors actors) {
      this.minecraft = minecraft;
      this.actors = actors;
      actors.useUniqueIds(this::byUniqueId);
      this.javaIds.defaultReturnValue(-1);
      this.runtimeIds.defaultReturnValue(Long.MIN_VALUE);
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof AddEntityPacket
         || packet instanceof AddPlayerPacket
         || packet instanceof AddItemEntityPacket
         || packet instanceof RemoveEntityPacket
         || packet instanceof MoveEntityAbsolutePacket
         || packet instanceof MoveEntityDeltaPacket
         || packet instanceof MovePlayerPacket
         || packet instanceof SetEntityMotionPacket
         || packet instanceof SetEntityDataPacket
         || packet instanceof AnimatePacket
         || packet instanceof EntityEventPacket
         || packet instanceof MobEquipmentPacket
         || packet instanceof MobArmorEquipmentPacket
         || packet instanceof PlayerListPacket
         || packet instanceof TakeItemEntityPacket
         || packet instanceof AddPaintingPacket
         || packet instanceof SetEntityLinkPacket
         || packet instanceof PlayerSkinPacket
         || packet instanceof SetPassengerOfBlockPacket;
   }

   public void reset(long localRuntimeId, long localUniqueId, int localJavaId) {
      this.actors.clear();
      this.javaIds.clear();
      this.playerNames.clear();
      this.runtimeIds.clear();
      this.tradeExperience.clear();
      this.blockSeats.clear();
      this.localSeat = null;
      this.localRuntimeId = localRuntimeId;
      this.localJavaId = localJavaId;
      this.nextJavaId = localJavaId + 1;
      this.javaIds.put(localRuntimeId, localJavaId);
      this.runtimeIds.put(localUniqueId, localRuntimeId);
      this.actors.setLocal(localJavaId, localRuntimeId);
   }

   public void apply(BedrockPacket packet) {
      if (this.minecraft.level != null && this.minecraft.getConnection() != null) {
         switch (packet) {
            case AddEntityPacket add:
               this.onAddEntity(add);
               break;
            case AddPlayerPacket add:
               this.onAddPlayer(add);
               break;
            case AddItemEntityPacket add:
               this.onAddItem(add);
               break;
            case AddPaintingPacket add:
               this.onAddPainting(add);
               break;
            case SetEntityLinkPacket link:
               this.onLink(link.getEntityLink());
               break;
            case SetPassengerOfBlockPacket seat:
               this.onPassengerOfBlock(seat.getEntityUniqueId(), seat.getData());
               break;
            case RemoveEntityPacket remove:
               this.onRemove(remove.getUniqueEntityId());
               break;
            case MoveEntityAbsolutePacket move:
               Entity entityxx = this.remote(move.getRuntimeEntityId());
               if (entityxx != null) {
                  Vector3f pos = move.getPosition();
                  Vector3f rot = move.getRotation();
                  this.moveTo(
                     entityxx,
                     pos.getX(),
                     pos.getY() - eyeOffset(entityxx),
                     pos.getZ(),
                     rot.getY(),
                     rot.getX(),
                     rot.getZ(),
                     move.isTeleported(),
                     move.isOnGround()
                  );
               }
               break;
            case MoveEntityDeltaPacket move:
               this.onMoveDelta(move);
               break;
            case MovePlayerPacket move:
               Entity entityxxx = this.remote(move.getRuntimeEntityId());
               if (entityxxx != null) {
                  if (move.getMode() == Mode.RESPAWN) {
                     revive(entityxxx);
                  }

                  Vector3f pos = move.getPosition();
                  Vector3f rot = move.getRotation();
                  this.moveTo(
                     entityxxx,
                     pos.getX(),
                     pos.getY() - eyeOffset(entityxxx),
                     pos.getZ(),
                     rot.getY(),
                     rot.getX(),
                     rot.getZ(),
                     move.getMode() == Mode.TELEPORT || move.getMode() == Mode.RESPAWN,
                     move.isOnGround()
                  );
               }
               break;
            case SetEntityMotionPacket motion:
               Entity entityx = this.remote(motion.getRuntimeEntityId());
               if (entityx != null && !entityx.isLocalInstanceAuthoritative()) {
                  entityx.lerpMotion(toVec3(motion.getMotion()));
               }
               break;
            case SetEntityDataPacket data:
               Entity entity = this.remote(data.getRuntimeEntityId());
               if (entity != null) {
                  this.actors.onMetadata(entity, data.getMetadata(), data.getProperties());
                  this.applyMetadata(entity, data.getMetadata());
               }
               break;
            case AnimatePacket animate:
               this.onAnimate(animate);
               break;
            case TakeItemEntityPacket take:
               this.onTake(take);
               break;
            case EntityEventPacket event:
               this.onEntityEvent(event);
               break;
            case MobEquipmentPacket equipment:
               if (this.remote(equipment.getRuntimeEntityId()) instanceof LivingEntity living) {
                  this.actors.onEquipment(living, equipment.getContainerId() == 119 ? "slot.weapon.offhand" : "slot.weapon.mainhand", equipment.getItem());
                  living.setItemSlot(
                     equipment.getContainerId() == 119 ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND, ItemMappings.toJava(equipment.getItem())
                  );
               }
               break;
            case MobArmorEquipmentPacket armor:
               if (this.remote(armor.getRuntimeEntityId()) instanceof LivingEntity living) {
                  this.onArmor(living, armor);
                  living.setItemSlot(EquipmentSlot.HEAD, ItemMappings.toJava(armor.getHelmet()));
                  living.setItemSlot(EquipmentSlot.CHEST, ItemMappings.toJava(armor.getChestplate()));
                  living.setItemSlot(EquipmentSlot.LEGS, ItemMappings.toJava(armor.getLeggings()));
                  living.setItemSlot(EquipmentSlot.FEET, ItemMappings.toJava(armor.getBoots()));
                  if (!(living instanceof Player) && armor.getBody() != null) {
                     living.setItemSlot(EquipmentSlot.BODY, ItemMappings.toJava(armor.getBody()));
                  }
               }
               break;
            case PlayerListPacket list:
               this.onPlayerList(list);
               break;
            case PlayerSkinPacket skin:
               long skins = FrameTimes.start();
               this.actors.skins().changed(skin.getUuid(), skin.getSkin(), skin.isTrustedSkin());
               FrameTimes.end(FrameWatch.Bucket.SKINS, skins);
               break;
            default:
         }
      }
   }

   private void onAddEntity(AddEntityPacket add) {
      EntityType<?> type = EntityMappings.toJava(add.getIdentifier()).orElse(null);
      if (type == null) {
         type = EntityTypes.INTERACTION;
      }

      if (type == EntityTypes.INTERACTION ? this.actors.drawn(add.getIdentifier()) : this.actors.replacesJavaType(add.getIdentifier())) {
         type = BedrockActorEntity.TYPE;
      }

      if (type != null && type != EntityTypes.PLAYER) {
         int data = 0;
         if (type == EntityTypes.FALLING_BLOCK && add.getMetadata().get(EntityDataTypes.VARIANT) instanceof Integer blockHash) {
            data = Block.getId(BlockMappings.get().toJava(blockHash));
         }

         if (type == EntityTypes.FISHING_BOBBER
            && add.getMetadata().get(EntityDataTypes.OWNER_EID) instanceof Long ownerId
            && this.byUniqueId(ownerId) instanceof Entity owner) {
            data = owner.getId();
         }

         boolean boat = type == EntityTypes.OAK_BOAT || type == EntityTypes.OAK_CHEST_BOAT;
         float yaw = add.getRotation().getY() - (boat ? 90.0F : 0.0F);
         Entity entity = this.spawn(
            add.getRuntimeEntityId(),
            add.getUniqueEntityId(),
            type,
            uuidOf(add.getUniqueEntityId()),
            add.getPosition(),
            (float)ActorOffsets.spawn(type),
            add.getMotion(),
            add.getRotation().getX(),
            yaw,
            boat ? yaw : add.getHeadRotation(),
            data
         );
         if (entity instanceof LivingEntity living) {
            living.setYBodyRot(add.getBodyRotation());
            living.yBodyRotO = add.getBodyRotation();
            applyAttributes(living, add.getAttributes());
         }

         if (entity != null) {
            long spawn = FrameTimes.start();
            this.actors.onSpawn(entity, add.getIdentifier(), add.getRuntimeEntityId(), add.getUniqueEntityId(), add.getMetadata(), add.getProperties());
            FrameTimes.end(FrameWatch.Bucket.ACTOR_SPAWN, spawn);
            this.applyMetadata(entity, add.getMetadata());
            add.getEntityLinks().forEach(this::onLink);
            if (add.getPassengerOfBlockArguments() != null) {
               this.onPassengerOfBlock(add.getUniqueEntityId(), add.getPassengerOfBlockArguments());
            }

            spawn = FrameTimes.start();
            this.actors.facingAdded(entity, add.getRotation().getX(), add.getRotation().getY(), add.getHeadRotation(), add.getBodyRotation());
            FrameTimes.end(FrameWatch.Bucket.ACTOR_SPAWN, spawn);
         }
      }
   }

   private void onAddPlayer(AddPlayerPacket add) {
      long javaAdd = FrameTimes.start();
      this.addPlayerInfo(add.getUuid(), add.getUsername(), false);
      FrameTimes.end(FrameWatch.Bucket.JAVA_ADD, javaAdd);
      Vector3f rot = add.getRotation();
      if (this.spawn(
         add.getRuntimeEntityId(),
         add.getUniqueEntityId(),
         EntityTypes.PLAYER,
         add.getUuid(),
         add.getPosition(),
         (float)ActorOffsets.addPlayer(),
         add.getMotion(),
         rot.getX(),
         rot.getY(),
         rot.getZ(),
         0
      ) instanceof Player player) {
         player.setYBodyRot(rot.getY());
         player.yBodyRotO = rot.getY();
         if (Math.abs(Mth.wrapDegrees(rot.getZ() - rot.getY())) > 10.0F && this.turnedPlayersLogged < 16) {
            this.turnedPlayersLogged++;
            BedrockNative.LOGGER
               .info("Player facing {}: added with body yaw {}, head yaw {} (pitch {})", new Object[]{add.getUsername(), rot.getY(), rot.getZ(), rot.getX()});
         }

         long skins = FrameTimes.start();
         this.actors.skins().spawned(add.getUuid(), this.distanceSq(player));
         FrameTimes.end(FrameWatch.Bucket.SKINS, skins);
         BedrockNameTag.getOrCreate(player).setName(add.getUsername());
         BedrockNameTag.trace(player, "AddPlayer", add.getUsername(), null);
         long spawn = FrameTimes.start();
         this.actors.onSpawn(player, "minecraft:player", add.getRuntimeEntityId(), add.getUniqueEntityId(), add.getMetadata(), add.getProperties());
         FrameTimes.end(FrameWatch.Bucket.ACTOR_SPAWN, spawn);
         this.actors.onEquipment(player, "slot.weapon.mainhand", add.getHand());
         player.setItemSlot(EquipmentSlot.MAINHAND, ItemMappings.toJava(add.getHand()));
         this.applyMetadata(player, add.getMetadata());
         add.getEntityLinks().forEach(this::onLink);
         if (add.getPassengerOfBlockArguments() != null) {
            this.onPassengerOfBlock(add.getUniqueEntityId(), add.getPassengerOfBlockArguments());
         }
      }
   }

   private void onAddPainting(AddPaintingPacket add) {
      Holder<PaintingVariant> variant = this.paintingVariant(add.getMotive());
      Direction facing = Direction.from2DDataValue(add.getDirection());
      Vector3f center = add.getPosition();
      Vec3 anchor = new Vec3(center.getX(), center.getY(), center.getZ())
         .relative(facing.getCounterClockWise(), -paintingShift(variant.value().width()))
         .relative(Direction.UP, -paintingShift(variant.value().height()));
      BlockPos pos = BlockPos.containing(anchor);
      Entity entity = this.spawn(
         add.getRuntimeEntityId(),
         add.getUniqueEntityId(),
         EntityTypes.PAINTING,
         uuidOf(add.getUniqueEntityId()),
         Vector3f.from(pos.getX(), pos.getY(), pos.getZ()),
         0.0F,
         null,
         0.0F,
         0.0F,
         0.0F,
         facing.get3DDataValue()
      );
      if (entity != null) {
         entity.setComponent(DataComponents.PAINTING_VARIANT, variant);
      }
   }

   private static double paintingShift(int size) {
      return size % 2 == 0 ? 0.5 : 0.0;
   }

   private Holder<PaintingVariant> paintingVariant(String motive) {
      Registry<PaintingVariant> variants = this.minecraft.level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
      String name = motive.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
      Identifier id = Identifier.tryParse(name);
      return (id == null ? Optional.<Holder.Reference<PaintingVariant>>empty() : variants.get(id))
         .or(() -> variants.get(PaintingVariants.KEBAB))
         .map(holder -> (Holder<PaintingVariant>)holder)
         .orElseGet(() -> variants.listElements().findFirst().orElseThrow());
   }

   private void onPassengerOfBlock(long uniqueId, PassengerOfBlockArguments arguments) {
      Entity entity = this.byUniqueId(uniqueId);
      if (entity != null) {
         if (arguments == null) {
            this.blockSeats.remove(uniqueId);
            if (entity == this.minecraft.player) {
               this.localSeat = null;
            }

            entity.setPose(Pose.STANDING);
         } else {
            BlockSeat seat = BlockSeat.of(arguments);
            Vector3f position = seat.position();
            entity.setPos(position.getX(), position.getY(), position.getZ());
            entity.setYRot(seat.rotation());
            entity.setYHeadRot(seat.rotation());
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof LivingEntity living) {
               living.yBodyRot = seat.rotation();
               living.yBodyRotO = seat.rotation();
            }
            Pose pose = switch (seat.emote() == null ? EmoteType.STANDING : seat.emote()) {
               case RIDING -> Pose.SITTING;
               case LAYING -> Pose.SLEEPING;
               case STANDING -> Pose.STANDING;
            };
            entity.setPose(pose);
            this.blockSeats.put(uniqueId, pose);
            if (entity == this.minecraft.player) {
               this.localSeat = seat;
            }
         }
      }
   }

   public void tickSeat(Player player) {
      BlockSeat seat = this.localSeat;
      if (seat != null && player != null) {
         Vector3f position = seat.position();
         player.setPos(position.getX(), position.getY(), position.getZ());
         player.setDeltaMovement(Vec3.ZERO);
         float yaw = seat.clampYaw(player.getYRot());
         player.setYRot(yaw);
         player.setYHeadRot(yaw);
         player.yBodyRot = yaw;
         player.yBodyRotO = yaw;
      }
   }

   private void onLink(EntityLinkData link) {
      Entity vehicle = this.byUniqueId(link.getFrom());
      Entity rider = this.byUniqueId(link.getTo());
      if (vehicle != null && rider != null && vehicle != this.minecraft.player) {
         if (link.getType() == Type.REMOVE) {
            if (rider.getVehicle() == vehicle) {
               rider.stopRiding();
            }
         } else if (rider.getVehicle() != vehicle) {
            rider.startRiding(vehicle, true, false);
         }
      }
   }

   public long uniqueIdOf(Entity entity) {
      long runtimeId = this.runtimeIdOf(entity.getId());
      ObjectIterator var4 = this.runtimeIds.long2LongEntrySet().iterator();

      while (var4.hasNext()) {
         Entry entry = (Entry)var4.next();
         if (entry.getLongValue() == runtimeId) {
            return entry.getLongKey();
         }
      }

      return -1L;
   }

   private void onAddItem(AddItemEntityPacket add) {
      if (this.spawn(
         add.getRuntimeEntityId(),
         add.getUniqueEntityId(),
         EntityTypes.ITEM,
         uuidOf(add.getUniqueEntityId()),
         add.getPosition(),
         0.125F,
         add.getMotion(),
         0.0F,
         0.0F,
         0.0F,
         0
      ) instanceof ItemEntity item) {
         item.setItem(ItemMappings.toJava(add.getItemInHand()));
         this.applyMetadata(item, add.getMetadata());
      }
   }

   private Entity spawn(
      long runtimeId,
      long uniqueId,
      EntityType<?> type,
      UUID uuid,
      Vector3f pos,
      float yOffset,
      Vector3f motion,
      float pitch,
      float yaw,
      float headYaw,
      int data
   ) {
      ClientLevel level = this.minecraft.level;
      int existing = this.javaIds.get(runtimeId);
      if (existing == this.localJavaId) {
         return null;
      }

      if (existing != -1) {
         if (level.getEntity(existing) instanceof Player old && !old.getUUID().equals(uuid)) {
            this.actors.skins().despawned(old.getUUID());
         }

         level.removeEntity(existing, RemovalReason.DISCARDED);
         this.actors.remove(existing);
      }

      int id = this.nextJavaId++;
      long javaAdd = FrameTimes.start();
      this.minecraft
         .getConnection()
         .handleAddEntity(
            new ClientboundAddEntityPacket(
               id, uuid, pos.getX(), pos.getY() - yOffset, pos.getZ(), pitch, yaw, type, data, motion == null ? Vec3.ZERO : toVec3(motion), headYaw
            )
         );
      FrameTimes.end(FrameWatch.Bucket.JAVA_ADD, javaAdd);
      Entity entity = level.getEntity(id);
      if (entity == null) {
         return null;
      }

      entity.setYRot(yaw);
      entity.setXRot(pitch);
      entity.setYHeadRot(headYaw);
      entity.setOldPosAndRot();
      this.javaIds.put(runtimeId, id);
      this.runtimeIds.put(uniqueId, runtimeId);
      return entity;
   }

   private long distanceSq(Entity entity) {
      Entity local = this.minecraft.player;
      return local == null ? 0L : (long)Math.min(entity.distanceToSqr(local), 9.223372E18F);
   }

   public Entity spawnUnaddressed(EntityType<?> type, Vector3f pos) {
      ClientLevel level = this.minecraft.level;
      if (level != null && this.minecraft.getConnection() != null) {
         int id = this.nextJavaId++;
         this.minecraft
            .getConnection()
            .handleAddEntity(new ClientboundAddEntityPacket(id, UUID.randomUUID(), pos.getX(), pos.getY(), pos.getZ(), 0.0F, 0.0F, type, 0, Vec3.ZERO, 0.0));
         return level.getEntity(id);
      } else {
         return null;
      }
   }

   public long runtimeIdOfUnique(long uniqueId) {
      long runtimeId = this.runtimeIds.get(uniqueId);
      return runtimeId == Long.MIN_VALUE ? uniqueId : runtimeId;
   }

   public String playerName(long uniqueEntityId) {
      return this.playerNames.get(uniqueEntityId);
   }

   private static void revive(Entity entity) {
      if (entity instanceof LivingEntity living && living.getHealth() <= 0.0F) {
         living.deathTime = 0;
         living.hurtTime = 0;
         living.hurtDuration = 0;
         living.setHealth(living.getMaxHealth());
      }
   }

   private void onRemove(long uniqueId) {
      this.blockSeats.remove(uniqueId);
      long runtimeId = this.runtimeIds.remove(uniqueId);
      if (runtimeId != Long.MIN_VALUE && runtimeId != this.localRuntimeId) {
         int id = this.javaIds.remove(runtimeId);
         if (id != -1) {
            if (this.minecraft.level.getEntity(id) instanceof Player player) {
               this.actors.skins().despawned(player.getUUID());
            }

            this.minecraft.level.removeEntity(id, RemovalReason.DISCARDED);
            this.actors.remove(id);
         }
      } else {
         if (runtimeId == this.localRuntimeId) {
            this.localSeat = null;
         }
      }
   }

   private void onArmor(LivingEntity living, MobArmorEquipmentPacket armor) {
      this.actors.onEquipment(living, "slot.armor.head", armor.getHelmet());
      this.actors.onEquipment(living, "slot.armor.chest", armor.getChestplate());
      this.actors.onEquipment(living, "slot.armor.legs", armor.getLeggings());
      this.actors.onEquipment(living, "slot.armor.feet", armor.getBoots());
      this.actors.onEquipment(living, "slot.armor.body", armor.getBody());
   }

   private void onMoveDelta(MoveEntityDeltaPacket move) {
      Entity entity = this.remote(move.getRuntimeEntityId());
      if (entity != null) {
         PositionAndRotation target = entity.getInterpolation().target();
         Vec3 last = target != null ? target.position() : entity.position();
         float lastYaw = target != null ? target.yRot() : entity.getYRot();
         float lastPitch = target != null ? target.xRot() : entity.getXRot();
         Set<Flag> flags = move.getFlags();
         double eye = eyeOffset(entity);
         double x = flags.contains(Flag.HAS_X) ? move.getX() : last.x;
         double y = flags.contains(Flag.HAS_Y) ? move.getY() - eye : last.y;
         double z = flags.contains(Flag.HAS_Z) ? move.getZ() : last.z;
         float yaw = flags.contains(Flag.HAS_YAW) ? move.getYaw() : lastYaw;
         float pitch = flags.contains(Flag.HAS_PITCH) ? move.getPitch() : lastPitch;
         float headYaw = flags.contains(Flag.HAS_HEAD_YAW) ? move.getHeadYaw() : Float.NaN;
         this.moveTo(entity, x, y, z, yaw, pitch, headYaw, flags.contains(Flag.TELEPORTING), move.isOnGround() || flags.contains(Flag.ON_GROUND));
      }
   }

   private void moveTo(Entity entity, double x, double y, double z, float yaw, float pitch, float headYaw, boolean teleport, boolean onGround) {
      if (!entity.isLocalInstanceAuthoritative()) {
         if (entity instanceof AbstractBoat) {
            yaw -= 90.0F;
         }

         Vec3 pos = new Vec3(x, y, z);
         entity.getPositionCodec().setBase(pos);
         double distanceSquared = entity.position().distanceToSqr(pos);
         if (teleport
            || !this.minecraft.level.isTickingEntity(entity)
            || distanceSquared > 4096.0
            || entity instanceof ItemEntity && ItemSmoothing.snaps(false, distanceSquared)) {
            entity.getInterpolation().cancel();
            entity.snapTo(pos, yaw, pitch);
            if (!Float.isNaN(headYaw)) {
               entity.setYHeadRot(headYaw);
            }
         } else {
            entity.moveOrInterpolateTo(pos, yaw, pitch);
            if (!Float.isNaN(headYaw)) {
               entity.lerpHeadTo(headYaw, 3);
            }
         }

         entity.setOnGround(onGround);
      }
   }

   private void applyServerBox(Entity entity, EntityDataMap data) {
      if (SERVER_BOXES
         && entity instanceof LivingEntity
         && !(entity instanceof BedrockActorEntity)
         && entity != this.minecraft.player
         && !(entity instanceof Shulker)
         && !(entity instanceof EnderDragon)) {
         ServerBoxed boxed = (ServerBoxed)entity;
         ServerBox old = boxed.bedrocknative$serverBox();
         ServerBox box = (old == null ? ServerBox.NONE : old)
            .with(
               data.get(EntityDataTypes.WIDTH) instanceof Float width ? width : null,
               data.get(EntityDataTypes.HEIGHT) instanceof Float height ? height : null,
               data.get(EntityDataTypes.SCALE) instanceof Float scale ? scale : null
            );
         if (box.known() && !box.equals(old)) {
            boxed.bedrocknative$setServerBox(box);
            entity.setPos(entity.getX(), entity.getY(), entity.getZ());
         }
      }
   }

   private void applyMetadata(Entity entity, EntityDataMap data) {
      if (data != null && !data.isEmpty()) {
         if (entity instanceof Interaction box) {
            InteractionAccessor accessor = (InteractionAccessor)box;
            accessor.bedrocknative$setResponse(true);
            if (data.get(EntityDataTypes.WIDTH) instanceof Float width) {
               accessor.bedrocknative$setWidth(width);
            }

            if (data.get(EntityDataTypes.HEIGHT) instanceof Float height) {
               accessor.bedrocknative$setHeight(height);
            }
         }

         if (entity instanceof BedrockActorEntity carrier) {
            carrier.setBox(
               data.get(EntityDataTypes.WIDTH) instanceof Float width ? width : Float.NaN,
               data.get(EntityDataTypes.HEIGHT) instanceof Float height ? height : Float.NaN
            );
         }

         BiConsumer<Float, Float> rowTimes = BedrockVehicles.rowTimeListener;
         if (rowTimes != null && (data.get(EntityDataTypes.ROW_TIME_LEFT) != null || data.get(EntityDataTypes.ROW_TIME_RIGHT) != null)) {
            rowTimes.accept(data.get(EntityDataTypes.ROW_TIME_LEFT), data.get(EntityDataTypes.ROW_TIME_RIGHT));
         }

         BedrockNameTag tag = BedrockNameTag.getOrCreate(entity);
         BedrockNameTag.trace(entity, "data", null, data);
         if (tag.update(data) && !(entity instanceof Player)) {
            entity.setCustomName(tag.joined());
         }

         if (entity instanceof Player player && data.get(EntityDataTypes.SCALE) instanceof Float scale) {
            AttributeInstance size = player.getAttribute(Attributes.SCALE);
            if (size != null) {
               size.setBaseValue(Math.clamp(scale.floatValue(), 0.0625, 16.0));
            }
         }

         this.applyServerBox(entity, data);
         if (data.get(EntityDataTypes.TRADE_EXPERIENCE) instanceof Integer xp) {
            this.tradeExperience.put(entity.getId(), xp.intValue());
         }

         if (entity instanceof Enderman enderman && data.get(EntityDataTypes.CARRY_BLOCK_STATE) instanceof BlockDefinition block) {
            BlockState carried = BlockMappings.get().toJava(block.getRuntimeId());
            enderman.setCarriedBlock(carried.isAir() ? null : carried);
         }

         if (data.get(EntityDataTypes.NAMETAG_ALWAYS_SHOW) instanceof Byte || data.getFlags() != null) {
            entity.setCustomNameVisible(tag.alwaysShown(entity) && (entity instanceof Player || entity.hasCustomName()));
         } else if (!(entity instanceof Player) && !entity.hasCustomName()) {
            entity.setCustomNameVisible(false);
         }

         Map<EntityFlag, Boolean> flags = data.getFlags();
         if (flags != null) {
            entity.setSharedFlagOnFire(flag(flags, EntityFlag.ON_FIRE));
            entity.setShiftKeyDown(flag(flags, EntityFlag.SNEAKING));
            entity.setSprinting(flag(flags, EntityFlag.SPRINTING));
            entity.setSwimming(flag(flags, EntityFlag.SWIMMING));
            entity.setInvisible(flag(flags, EntityFlag.INVISIBLE));
            boolean baby = flag(flags, EntityFlag.BABY);
            if (entity instanceof AgeableMob ageable) {
               ageable.setAge(baby ? -24000 : 0);
            } else if (entity instanceof Mob mob) {
               mob.setBaby(baby);
            }

            applyRiding(entity, flags);
         }
      }
   }

   private static void applyRiding(Entity entity, Map<EntityFlag, Boolean> flags) {
      if (entity instanceof AbstractHorse horse) {
         horse.setTamed(flag(flags, EntityFlag.TAMED));
      }

      if (entity instanceof Mob mob
         && (entity instanceof AbstractHorse || entity instanceof Pig || entity instanceof Strider || entity instanceof AbstractNautilus)) {
         boolean saddled = flag(flags, EntityFlag.SADDLED);
         if (saddled != mob.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE)) {
            mob.setItemSlot(EquipmentSlot.SADDLE, saddled ? new ItemStack(Items.SADDLE) : ItemStack.EMPTY);
         }
      }
   }

   private void onAnimate(AnimatePacket animate) {
      Entity entity = this.remote(animate.getRuntimeEntityId());
      if (entity != null) {
         switch (animate.getAction()) {
            case SWING_ARM:
               if (entity instanceof LivingEntity living) {
                  this.swingArm(living);
               }
               break;
            case CRITICAL_HIT:
               this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
               break;
            case MAGIC_CRITICAL_HIT:
               this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
               break;
            case WAKE_UP:
               if (entity instanceof Player player) {
                  player.stopSleepInBed(false, false);
               }
         }
      }
   }

   private void swingArm(LivingEntity living) {
      living.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
      this.actors.onSwing(living);
   }

   private void onEntityEvent(EntityEventPacket event) {
      int id = this.javaIds.get(event.getRuntimeEntityId());
      Entity entity = id == -1 ? null : this.minecraft.level.getEntity(id);
      if (entity != null) {
         this.actors.onEvent(entity, event.getType(), event.getData());
         switch (event.getType()) {
            case HURT:
               entity.animateHurt(0.0F);
               if (id == this.localJavaId) {
                  CloseOnHurt.playerHurt(this.minecraft);
               }
               break;
            case HURT_WITHOUT_RECEIVING_DAMAGE:
               entity.animateHurt(0.0F);
               break;
            case ATTACK_START:
               if (id != this.localJavaId && entity instanceof Player player) {
                  this.swingArm(player);
               } else {
                  entity.handleEntityEvent((byte)4);
               }
               break;
            case DEATH:
               if (id != this.localJavaId && entity instanceof LivingEntity living) {
                  living.setHealth(0.0F);
               }
               break;
            case DEATH_SMOKE_CLOUD:
               entity.handleEntityEvent((byte)60);
               break;
            case TAME_FAILED:
               entity.handleEntityEvent((byte)6);
               break;
            case TAME_SUCCEEDED:
               entity.handleEntityEvent((byte)7);
               break;
            case LOVE_PARTICLES:
            case IN_LOVE_HEARTS:
               entity.handleEntityEvent((byte)(entity instanceof AbstractVillager ? 12 : 18));
               break;
            case VILLAGER_ANGRY:
               entity.handleEntityEvent((byte)13);
               break;
            case VILLAGER_HAPPY:
               entity.handleEntityEvent((byte)14);
               break;
            case FIREWORK_EXPLODE:
               entity.handleEntityEvent((byte)17);
               break;
            case CONSUME_TOTEM:
               this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
               if (entity == this.minecraft.player) {
                  this.minecraft.player.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING));
               }
               break;
            default:
               byte javaEvent = BedrockEntityEvents.javaEvent(event.getType());
               if (javaEvent >= 0) {
                  entity.handleEntityEvent(javaEvent);
               } else {
                  this.onOtherEvent(entity, event);
               }
         }
      }
   }

   private void onOtherEvent(Entity entity, EntityEventPacket event) {
      switch (event.getType()) {
         case ENDER_DRAGON_DEATH:
            if (entity instanceof LivingEntity living) {
               living.setHealth(0.0F);
            }
            break;
         case ELDER_GUARDIAN_CURSE:
            if (entity == this.minecraft.player) {
               this.minecraft.level.addParticle(ParticleTypes.ELDER_GUARDIAN, entity.getX(), entity.getY(), entity.getZ(), 0.0, 0.0, 0.0);
            }
            break;
         case BABY_ANIMAL_FEED:
            this.burst(entity, ParticleTypes.HAPPY_VILLAGER, 7);
            break;
         case ENTITY_SPAWN:
            this.burst(entity, ParticleTypes.POOF, 20);
            break;
         case FISH_HOOK_BUBBLE:
            this.burst(entity, ParticleTypes.FISHING, 6);
            break;
         case UPDATE_ITEM_STACK_SIZE:
            if (entity instanceof ItemEntity item && event.getData() > 0) {
               item.setItem(item.getItem().copyWithCount(event.getData()));
            }
            break;
         case ENTITY_GROW_UP:
            if (entity instanceof AgeableMob ageable) {
               ageable.setAge(0);
            } else if (entity instanceof Mob mob) {
               mob.setBaby(false);
            }
            break;
         default:
            if (!BedrockEntityEvents.consumed(event.getType())) {
               BedrockNative.LOGGER.debug("ActorEvent {} (data {}): nothing to show", event.getType(), event.getData());
            }
      }
   }

   private void burst(Entity entity, ParticleOptions particle, int count) {
      for (int i = 0; i < count; i++) {
         this.minecraft.level.addParticle(particle, entity.getRandomX(1.0), entity.getRandomY(), entity.getRandomZ(1.0), 0.0, 0.05, 0.0);
      }
   }

   private void onPlayerList(PlayerListPacket list) {
      for (org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Entry entry : list.getEntries()) {
         Action action = entry.getAction() != null ? entry.getAction() : list.getAction();
         if (action == Action.ADD) {
            long javaAdd = FrameTimes.start();
            this.addPlayerInfo(entry.getUuid(), entry.getName() == null ? "" : entry.getName().toString(), true);
            FrameTimes.end(FrameWatch.Bucket.JAVA_ADD, javaAdd);
            if (entry.getName() != null) {
               this.playerNames.put(entry.getEntityId(), entry.getName().toString());
            }

            long skins = FrameTimes.start();
            this.actors.skins().listed(entry.getUuid(), entry.getName() == null ? "" : entry.getName().toString(), entry.getSkin(), entry.isTrustedSkin());
            FrameTimes.end(FrameWatch.Bucket.SKINS, skins);
         } else if (!entry.getUuid().equals(this.minecraft.player == null ? null : this.minecraft.player.getUUID())) {
            this.minecraft.getConnection().handlePlayerInfoRemove(new ClientboundPlayerInfoRemovePacket(List.of(entry.getUuid())));
            long skins = FrameTimes.start();
            this.actors.skins().unlisted(entry.getUuid());
            FrameTimes.end(FrameWatch.Bucket.SKINS, skins);
         }
      }
   }

   private void addPlayerInfo(UUID uuid, String name, boolean listed) {
      ClientPacketListener connection = this.minecraft.getConnection();
      PlayerInfo info = connection.playerInfoMap.get(uuid);
      if (info == null) {
         info = new PlayerInfo(new GameProfile(uuid, name), false);
         connection.playerInfoMap.put(uuid, info);
         this.minecraft.getPlayerSocialManager().addPlayer(info);
         if (this.minecraft.player != null && uuid.equals(this.minecraft.player.getUUID())) {
            info.setGameMode(this.minecraft.gameMode.getPlayerMode());
         }
      }

      if (listed && name.indexOf(167) >= 0) {
         info.setTabListDisplayName(BedrockText.format(name.replace('\n', ' ')));
      }

      if (listed) {
         connection.listedPlayers.add(info);
      }
   }

   public long runtimeIdOf(int javaId) {
      ObjectIterator var2 = this.javaIds.long2IntEntrySet().iterator();

      while (var2.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var2.next();
         if (entry.getIntValue() == javaId) {
            return entry.getLongKey();
         }
      }

      return -1L;
   }

   public Entity byUniqueId(long uniqueId) {
      long runtimeId = this.runtimeIds.get(uniqueId);
      int id = runtimeId == Long.MIN_VALUE ? -1 : this.javaIds.get(runtimeId);
      return id != -1 && this.minecraft.level != null ? this.minecraft.level.getEntity(id) : null;
   }

   private void onTake(TakeItemEntityPacket take) {
      Entity item = this.byRuntimeId(take.getItemRuntimeEntityId());
      Entity collector = this.byRuntimeId(take.getRuntimeEntityId());
      if (item != null && collector != null) {
         item.setSilent(true);
         int count = item instanceof ItemEntity itemEntity ? itemEntity.getItem().getCount() : 1;
         this.minecraft.getConnection().handleTakeItemEntity(new ClientboundTakeItemEntityPacket(item.getId(), collector.getId(), count));
      }
   }

   public int tradeExperience(long uniqueId) {
      Entity entity = this.byUniqueId(uniqueId);
      return entity == null ? 0 : this.tradeExperience.getOrDefault(entity.getId(), 0);
   }

   public Entity byRuntimeId(long runtimeId) {
      int id = this.javaIds.get(runtimeId);
      return id != -1 && this.minecraft.level != null ? this.minecraft.level.getEntity(id) : null;
   }

   public boolean isLocal(long uniqueId) {
      return this.runtimeIds.get(uniqueId) == this.localRuntimeId;
   }

   public void onAttributes(UpdateAttributesPacket packet) {
      if (this.minecraft.level != null && this.remote(packet.getRuntimeEntityId()) instanceof LivingEntity living) {
         applyAttributes(living, packet.getAttributes());
      }
   }

   private static void applyAttributes(LivingEntity living, List<AttributeData> attributes) {
      for (AttributeData attribute : attributes) {
         switch (attribute.getName()) {
            case "minecraft:health":
               AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
               if (maxHealth != null && attribute.getMaximum() > 0.0F) {
                  maxHealth.setBaseValue(attribute.getMaximum());
               }

               if (attribute.getValue() > 0.0F && living.deathTime > 0) {
                  living.deathTime = 0;
                  living.hurtTime = 0;
                  living.hurtDuration = 0;
               }

               living.setHealth(attribute.getValue());
               break;
            case "minecraft:movement":
               setBase(living, Attributes.MOVEMENT_SPEED, attribute.getValue());
               break;
            case "minecraft:horse.jump_strength":
               setBase(living, Attributes.JUMP_STRENGTH, attribute.getValue());
         }
      }
   }

   private static void setBase(LivingEntity living, Holder<Attribute> attribute, float value) {
      AttributeInstance instance = living.getAttribute(attribute);
      if (instance != null && Float.isFinite(value) && value >= 0.0F) {
         instance.setBaseValue(value);
      }
   }

   private Entity remote(long runtimeId) {
      int id = this.javaIds.get(runtimeId);
      return id != -1 && id != this.localJavaId ? this.minecraft.level.getEntity(id) : null;
   }

   private static double eyeOffset(Entity entity) {
      return ActorOffsets.move(entity);
   }

   private static boolean flag(Map<EntityFlag, Boolean> flags, EntityFlag flag) {
      return Boolean.TRUE.equals(flags.get(flag));
   }

   private static UUID uuidOf(long uniqueId) {
      return new UUID(-4697254411347427328L, uniqueId);
   }

   private static Vec3 toVec3(Vector3f v) {
      return new Vec3(v.getX(), v.getY(), v.getZ());
   }
}
