package net.bedrocknative.client.model;

import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.entity.ActorMotion;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.runtime.FirstPersonRig;
import net.bedrocknative.model.skin.PlayerEngine;
import net.bedrocknative.molang.CameraAngles;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.molang.VariableStore;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

public final class ClientActorView implements MolangActor {
   private static final float[] MOON_BRIGHTNESS = new float[]{1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};
   private static final int PLAYER_SLEEP = 2;
   private static final int LEVITATION = 24;
   private final BedrockActors actors;
   private final ActorState state;
   private @Nullable Entity entity;
   private @Nullable LivingEntity living;
   private @Nullable MobEngineState mob;
   private @Nullable PlayerRuntime player;
   private @Nullable Direction bed;
   private float partialTick;
   private float lifeTime;
   private float bodyYaw;
   private float headYaw;
   private float pitch;
   private double x;
   private double y;
   private double z;
   private double distanceSq;
   private static final String WITHER = "minecraft:wither";
   private static final String XP_ORB = "minecraft:xp_orb";
   private static final int[] ORB_STEPS = new int[]{3, 7, 17, 37, 73, 149, 307, 617, 1237, 2477};

   ClientActorView(BedrockActors actors, ActorState state) {
      this.actors = actors;
      this.state = state;
   }

   void prepare(Entity entity, EntityRenderState render, float partialTick, float lifeTime, @Nullable MobEngineState mob) {
      this.entity = entity;
      this.living = entity instanceof LivingEntity livingEntity ? livingEntity : null;
      this.mob = mob;
      this.player = null;
      this.partialTick = partialTick;
      this.lifeTime = lifeTime;
      if (render instanceof LivingEntityRenderState livingRender) {
         this.bodyYaw = livingRender.bodyRot;
         this.headYaw = livingRender.yRot;
         this.pitch = livingRender.xRot;
         this.bed = livingRender.bedOrientation;
      } else if (this.living != null) {
         this.bodyYaw = entity.getYRot(partialTick);
         this.headYaw = 0.0F;
         this.pitch = entity.getXRot(partialTick);
         this.bed = null;
      } else {
         this.bodyYaw = 0.0F;
         this.headYaw = entity.getYRot(partialTick);
         this.pitch = entity.getXRot(partialTick);
         this.bed = null;
      }

      this.x = render.x;
      this.y = render.y;
      this.z = render.z;
      this.distanceSq = render.distanceToCameraSq;
   }

   void player(@Nullable PlayerRuntime runtime) {
      this.player = runtime;
   }

   void detach() {
      this.entity = null;
      this.living = null;
      this.mob = null;
      this.player = null;
   }

   float bodyYaw() {
      return this.bodyYaw;
   }

   private boolean localPlayer() {
      return this.entity != null && this.entity == Minecraft.getInstance().player;
   }

   private ActorData data() {
      return this.state.data();
   }

   private @Nullable ActorMotion motion() {
      return this.entity instanceof BedrockActorEntity carrier ? carrier.motion() : null;
   }

   @Override
   public boolean valid() {
      return this.entity != null && !this.entity.isRemoved();
   }

   @Override
   public String identifier() {
      return this.data().identifier();
   }

   @Override
   public String name() {
      return this.data().name();
   }

   @Override
   public boolean flag(EntityFlag flag) {
      if (this.player != null) {
         if (flag == EntityFlag.EMOTING && this.player.emoting()) {
            return true;
         }

         if (flag == EntityFlag.SLEEPING && ((int)this.data().number(EntityDataTypes.PLAYER_FLAGS) & 2) != 0) {
            return true;
         }

         LivingEntity local = this.living;
         if (local != null && this.localPlayer()) {
            switch (flag) {
               case SNEAKING:
                  return local.isCrouching();
               case SPRINTING:
                  return local.isSprinting();
               case SWIMMING:
                  return local.isSwimming();
               case CRAWLING:
                  return local.isVisuallyCrawling();
               case GLIDING:
                  return local.isFallFlying();
               case USING_ITEM:
                  return local.isUsingItem();
               case BLOCKING:
                  return local.isBlocking() || BedrockLocalPlayer.serverBlocking();
               case SLEEPING:
                  return local.isSleeping();
               case RIDING:
                  return local.isPassenger();
               case ON_FIRE:
                  return local.isOnFire();
               case INVISIBLE:
                  return local.isInvisible();
            }
         }
      }

      return this.data().flag(flag);
   }

   @Override
   public float data(EntityDataType<?> type) {
      return this.data().number(type);
   }

   @Override
   public @Nullable Object property(String name) {
      return this.data().property(name);
   }

   @Override
   public @Nullable VariableStore publicVariables() {
      ActorInstance instance = this.state.instance();
      return instance != null ? instance.variables() : null;
   }

   @Override
   public float lifeTime() {
      return this.lifeTime;
   }

   @Override
   public float timeStamp() {
      ClientLevel level = Minecraft.getInstance().level;
      return level != null ? (float)level.getGameTime() : 0.0F;
   }

   private long dayTime() {
      ClientLevel level = Minecraft.getInstance().level;
      return level != null ? level.getDefaultClockTime() : 0L;
   }

   @Override
   public float timeOfDay() {
      return (float)Math.floorMod(this.dayTime() + 6000L, 24000L) / 24000.0F;
   }

   @Override
   public float day() {
      return (float)Math.floorDiv(this.dayTime(), 24000L);
   }

   @Override
   public float moonPhase() {
      return (int)Math.floorMod(Math.floorDiv(this.dayTime(), 24000L), 8L);
   }

   @Override
   public float moonBrightness() {
      return MOON_BRIGHTNESS[(int)this.moonPhase()];
   }

   @Override
   public float distanceFromCamera() {
      return (float)Math.sqrt(this.distanceSq);
   }

   @Override
   public float cameraRotation(int axis) {
      Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
      return CameraAngles.cameraRotation(camera.xRot(), camera.yRot(), axis);
   }

   @Override
   public float rotationToCamera(int axis) {
      Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
      return CameraAngles.rotationToCamera(camera.x - this.x, camera.y - this.y, camera.z - this.z, axis);
   }

   @Override
   public float bodyXRotation() {
      return this.mob != null && this.entity instanceof Squid squid ? Mth.lerp(this.partialTick, squid.xBodyRotO, squid.xBodyRot) : this.pitch;
   }

   @Override
   public float bodyYRotation() {
      return this.bodyYaw;
   }

   @Override
   public float headXRotation(int head) {
      return this.pitch;
   }

   @Override
   public float headYRotation(int head) {
      return this.headYaw;
   }

   @Override
   public float targetXRotation() {
      return FirstPersonRig.targetRotation(this.isFirstPerson(), this.pitch);
   }

   @Override
   public float targetYRotation() {
      return FirstPersonRig.targetRotation(this.isFirstPerson(), this.headYaw);
   }

   @Override
   public float position(int axis) {
      return (float)(axis == 0 ? this.x : (axis == 1 ? this.y : (axis == 2 ? this.z : 0.0)));
   }

   @Override
   public float positionDelta(int axis) {
      ActorMotion motion = this.motion();
      if (motion != null) {
         return motion.positionDelta(axis);
      }

      Entity current = this.entity;
      if (current == null) {
         return 0.0F;
      }

      double delta = switch (axis) {
         case 0 -> current.getX() - current.xo;
         case 1 -> current.getY() - current.yo;
         case 2 -> current.getZ() - current.zo;
         default -> 0.0;
      };
      return (float)delta;
   }

   @Override
   public float groundSpeed() {
      ActorMotion motion = this.motion();
      if (motion != null) {
         return motion.groundSpeed();
      }

      float dx = this.positionDelta(0);
      float dz = this.positionDelta(2);
      return (float)Math.sqrt(dx * dx + dz * dz) * 20.0F;
   }

   @Override
   public float verticalSpeed() {
      ActorMotion motion = this.motion();
      return motion != null ? motion.verticalSpeed() : this.positionDelta(1) * 20.0F;
   }

   @Override
   public float yawSpeed() {
      ActorMotion motion = this.motion();
      if (motion != null) {
         return motion.yawSpeed();
      }

      LivingEntity current = this.living;
      if (current != null) {
         return Mth.wrapDegrees(current.yBodyRot - current.yBodyRotO) * 20.0F;
      }

      Entity other = this.entity;
      return other != null ? Mth.wrapDegrees(other.getYRot() - other.yRotO) * 20.0F : 0.0F;
   }

   @Override
   public float modifiedDistanceMoved() {
      ActorMotion motion = this.motion();
      if (motion != null) {
         return motion.distanceMoved();
      }

      LivingEntity current = this.living;
      return (this.mob != null || this.player != null) && current != null ? current.walkAnimation.position(this.partialTick) : 0.0F;
   }

   @Override
   public float modifiedMoveSpeed() {
      LivingEntity current = this.living;
      return current != null ? current.walkAnimation.speed(this.partialTick) : 0.0F;
   }

   @Override
   public float walkDistance() {
      ActorMotion motion = this.motion();
      if (motion != null) {
         return motion.walkDistance();
      } else if (this.player != null && this.living instanceof ClientAvatarEntity avatar) {
         return avatar.avatarState().getInterpolatedWalkDistance(this.partialTick);
      } else {
         Entity other = this.entity;
         return other != null ? other.moveDist : 0.0F;
      }
   }

   @Override
   public boolean isOnGround() {
      Entity current = this.entity;
      return current != null && current.onGround();
   }

   @Override
   public boolean isMoving() {
      if (this.data().flag(EntityFlag.MOVING)) {
         return true;
      }

      ActorMotion motion = this.motion();
      return motion != null ? motion.moving() : this.positionDelta(0) != 0.0F || this.positionDelta(1) != 0.0F || this.positionDelta(2) != 0.0F;
   }

   @Override
   public float swimAmount() {
      LivingEntity current = this.living;
      return current != null ? current.getSwimAmount(this.partialTick) : 0.0F;
   }

   @Override
   public boolean isAlive() {
      Entity current = this.entity;
      return current != null && current.isAlive();
   }

   @Override
   public boolean isInWater() {
      Entity current = this.entity;
      return current != null && current.isInWater();
   }

   @Override
   public boolean isInLava() {
      Entity current = this.entity;
      return current != null && current.isInLava();
   }

   @Override
   public float health() {
      LivingEntity current = this.living;
      return current != null ? current.getHealth() : 0.0F;
   }

   @Override
   public float maxHealth() {
      LivingEntity current = this.living;
      return current != null ? current.getMaxHealth() : 0.0F;
   }

   @Override
   public float deathTicks() {
      LivingEntity current = this.living;
      if (current instanceof EnderDragon dragon) {
         return dragon.dragonDeathTime;
      } else {
         return current != null ? current.deathTime : 0.0F;
      }
   }

   @Override
   public float modelScale() {
      return this.data().scale();
   }

   @Override
   public float swellAmount() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.swellAmount(this.partialTick) : 0.0F;
   }

   @Override
   public float swellingDir() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.swellingDir() : 0.0F;
   }

   @Override
   public float shakeAngle() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.shakeAngle() : 0.0F;
   }

   @Override
   public boolean isShakingWetness() {
      MobEngineState engine = this.mob;
      return engine != null && engine.isShakingWetness();
   }

   @Override
   public float headRollAngle() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.headRollAngle(this.partialTick) : 0.0F;
   }

   @Override
   public float tailAngle() {
      MobEngineState engine = this.mob;
      LivingEntity current = this.living;
      return engine != null && current != null ? engine.tailAngle(this.data(), current.getHealth(), current.getMaxHealth()) : 0.0F;
   }

   @Override
   public boolean isGrazing() {
      MobEngineState engine = this.mob;
      return engine != null && engine.isGrazing(this.data());
   }

   @Override
   public boolean isLevitating() {
      if (this.data().value(EntityDataTypes.VISIBLE_MOB_EFFECTS) instanceof Long effects) {
         for (int i = 0; i < 8; i++) {
            if ((effects >>> 7 * i & 127L) >>> 1 == 24L) {
               return true;
            }
         }
      }

      LivingEntity current = this.living;
      return current != null && current.hasEffect(MobEffects.LEVITATION);
   }

   @Override
   public float eyeTargetXRotation() {
      return this.eye(true);
   }

   @Override
   public float eyeTargetYRotation() {
      return this.eye(false);
   }

   private float eye(boolean sideways) {
      Entity current = this.entity;
      if (this.mob != null && current != null) {
         double eyeX = Mth.lerp(this.partialTick, current.xo, current.getX());
         double eyeY = Mth.lerp(this.partialTick, current.yo, current.getY()) + current.getEyeHeight();
         double eyeZ = Mth.lerp(this.partialTick, current.zo, current.getZ());
         Entity target = this.data().value(EntityDataTypes.TARGET_EID) instanceof Long id && id != 0L && id != -1L ? this.actors.byUniqueId(id) : null;
         double atY;
         double atZ;
         double atX;
         if (target != null) {
            atX = Mth.lerp(this.partialTick, target.xo, target.getX());
            atY = Mth.lerp(this.partialTick, target.yo, target.getY()) + target.getEyeHeight();
            atZ = Mth.lerp(this.partialTick, target.zo, target.getZ());
         } else {
            Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
            atX = camera.x;
            atY = camera.y;
            atZ = camera.z;
         }

         if (!sideways) {
            return atY - eyeY > 0.0 ? 0.0F : -1.0F;
         }

         float yaw = this.headYaw + this.bodyYaw;
         double level = Math.cos(Math.toRadians(this.pitch));
         double lookX = -Math.sin(Math.toRadians(yaw)) * level;
         double lookZ = Math.cos(Math.toRadians(yaw)) * level;
         double awayX = eyeX - atX;
         double awayZ = eyeZ - atZ;
         double length = Math.sqrt(awayX * awayX + awayZ * awayZ);
         if (length < 1.0E-4) {
            return 0.0F;
         }

         double sideX = awayZ / length;
         double sideZ = -awayX / length;
         double dot = lookX * sideX + lookZ * sideZ;
         return (float)(Math.sqrt(Math.abs(dot)) * 2.0 * Math.signum(dot));
      } else {
         return 0.0F;
      }
   }

   @Override
   public float wingFlapPosition() {
      Entity current = this.entity;
      if (current instanceof Parrot parrot) {
         return Mth.lerp(this.partialTick, parrot.oFlap, parrot.flap);
      } else {
         return current instanceof EnderDragon dragon ? Mth.lerp(this.partialTick, dragon.oFlapTime, dragon.flapTime) : 0.0F;
      }
   }

   @Override
   public float wingFlapSpeed() {
      return this.entity instanceof Parrot parrot ? Mth.lerp(this.partialTick, parrot.oFlapSpeed, parrot.flapSpeed) : 0.0F;
   }

   @Override
   public boolean isCarryingBlock() {
      return this.data().value(EntityDataTypes.CARRY_BLOCK_STATE) instanceof BlockDefinition block && !BlockMappings.get().toJava(block.getRuntimeId()).isAir();
   }

   @Override
   public boolean leashes(String identifier) {
      long unique = this.state.uniqueId();
      return unique != 0L && this.actors.leashedTo(unique, identifier);
   }

   @Override
   public float squish(boolean current) {
      if (this.entity instanceof AbstractCubeMob cube) {
         return current ? cube.squish : cube.oSquish;
      } else {
         return 0.0F;
      }
   }

   @Override
   public float fuseTime() {
      return this.data().value(EntityDataTypes.FUSE_TIME) instanceof Number fuse ? fuse.floatValue() : -1.0F;
   }

   @Override
   public float sitAmount() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.sitAmount(this.data(), this.partialTick) : 0.0F;
   }

   @Override
   public float lieAmount() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.lieAmount(this.data(), this.partialTick) : 0.0F;
   }

   @Override
   public float rollCounter() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.rollCounter(this.partialTick) : 0.0F;
   }

   @Override
   public float sneezeCounter() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.sneezeCounter(this.partialTick) : 0.0F;
   }

   @Override
   public float standingScale() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.standingScale(this.partialTick) : 0.0F;
   }

   @Override
   public float heartbeatPhase() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.heartbeatPhase(this.data(), this.partialTick) : 0.0F;
   }

   @Override
   public float heartbeatInterval() {
      return Math.max(0.0F, this.data().number(EntityDataTypes.HEARTBEAT_INTERVAL_TICKS)) / 20.0F;
   }

   @Override
   public float timeSinceLastVibration() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.timeSinceLastVibration(this.partialTick) : -1.0F;
   }

   @Override
   public boolean isShieldPowered() {
      LivingEntity current = this.living;
      return current != null && "minecraft:wither".equals(this.data().identifier()) && current.getHealth() <= current.getMaxHealth() / 2.0F;
   }

   @Override
   public float shakeTime() {
      MobEngineState engine = this.mob;
      return engine != null ? engine.shakeTime() : 0.0F;
   }

   @Override
   public float lifeSpan() {
      return this.data().number(EntityDataTypes.DATA_LIFETIME_TICKS);
   }

   @Override
   public boolean isAttachedToEntity() {
      Entity current = this.entity;
      return current instanceof FireworkRocketEntity && current.isPassenger();
   }

   @Override
   public float textureFrameIndex() {
      return "minecraft:xp_orb".equals(this.data().identifier()) ? orbIcon((int)this.data().number(EntityDataTypes.VALUE)) : 0.0F;
   }

   private static int orbIcon(int value) {
      for (int i = ORB_STEPS.length - 1; i >= 0; i--) {
         if (value >= ORB_STEPS[i]) {
            return i + 1;
         }
      }

      return 0;
   }

   @Override
   public boolean hasTarget() {
      return this.data().value(EntityDataTypes.TARGET_EID) instanceof Long target && target != 0L && target != -1L;
   }

   private @Nullable Entity owner() {
      return this.data().value(EntityDataTypes.OWNER_EID) instanceof Long owner && owner != 0L && owner != -1L ? this.actors.byUniqueId(owner) : null;
   }

   @Override
   public boolean hasOwner() {
      return this.owner() != null;
   }

   @Override
   public String ownerIdentifier() {
      Entity owner = this.owner();
      ActorState ownerState = owner != null ? BedrockActors.state(owner) : null;
      return ownerState != null ? ownerState.data().identifier() : "";
   }

   @Override
   public boolean hasRider() {
      Entity current = this.entity;
      return current != null && current.isVehicle();
   }

   @Override
   public String vehicleIdentifier() {
      Entity current = this.entity;
      Entity vehicle = current != null ? current.getVehicle() : null;
      ActorState vehicleState = vehicle != null ? BedrockActors.state(vehicle) : null;
      return vehicleState != null ? vehicleState.data().identifier() : "";
   }

   @Override
   public String itemName(String slot, int index) {
      LivingEntity current = this.living;
      if (this.player != null && current != null && this.localPlayer()) {
         EquipmentSlot equipment = javaSlot(slot);
         return equipment != null ? WearerView.itemIdentifier(current.getItemBySlot(equipment)) : "";
      } else {
         return this.data().item(slot);
      }
   }

   private static @Nullable EquipmentSlot javaSlot(String slot) {
      return switch (slot) {
         case "slot.weapon.mainhand" -> EquipmentSlot.MAINHAND;
         case "slot.weapon.offhand" -> EquipmentSlot.OFFHAND;
         case "slot.armor.head" -> EquipmentSlot.HEAD;
         case "slot.armor.chest" -> EquipmentSlot.CHEST;
         case "slot.armor.legs" -> EquipmentSlot.LEGS;
         case "slot.armor.feet" -> EquipmentSlot.FEET;
         case "slot.armor.body" -> EquipmentSlot.BODY;
         default -> null;
      };
   }

   @Override
   public boolean isLocalPlayer() {
      return this.localPlayer();
   }

   @Override
   public boolean isFirstPerson() {
      return this.player != null && this.player.firstPerson();
   }

   @Override
   public boolean isSpectator() {
      Entity current = this.entity;
      return current != null && current.isSpectator();
   }

   @Override
   public float capeFlapAmount() {
      return this.player != null ? this.player.capeFlap() : 0.0F;
   }

   @Override
   public boolean hasCape() {
      return this.player != null && this.player.model() != null && this.player.model().cape();
   }

   @Override
   public boolean isPersonaOrPremiumSkin() {
      return this.player != null && this.player.model() != null && this.player.model().drawn().personaOrPremium();
   }

   @Override
   public float sleepRotation() {
      Direction direction = this.bed;
      ClientLevel level = Minecraft.getInstance().level;
      if (direction == null && level != null && this.data().value(EntityDataTypes.BED_POSITION) instanceof Vector3i position) {
         BlockState block = level.getBlockState(new BlockPos(position.getX(), position.getY(), position.getZ()));
         direction = block.getBlock() instanceof BedBlock ? block.getValue(BedBlock.FACING) : null;
      }

      return direction != null && direction.getAxis().isHorizontal() ? PlayerEngine.sleepRotation(direction.get2DDataValue()) : 0.0F;
   }

   private ItemStack hand(String slot) {
      LivingEntity current = this.living;
      return current == null ? ItemStack.EMPTY : current.getItemBySlot(slot.equals("slot.weapon.offhand") ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
   }

   @Override
   public boolean itemIsCharged(String slot) {
      return this.player != null && CrossbowItem.isCharged(this.hand(slot));
   }

   @Override
   public float itemRemainingUseTicks(String slot) {
      LivingEntity current = this.living;
      return this.player != null
            && current != null
            && current.isUsingItem()
            && current.getUsedItemHand() == (slot.equals("slot.weapon.offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
         ? current.getUseItemRemainingTicks() - this.partialTick
         : 0.0F;
   }

   @Override
   public float itemMaxUseTicks(String slot) {
      LivingEntity current = this.living;
      return this.player != null && current != null && current.isUsingItem() ? current.getUseItem().getUseDuration(current) : 0.0F;
   }

   @Override
   public float mainHandItemUseDuration() {
      return this.itemRemainingUseTicks("slot.weapon.mainhand");
   }

   @Override
   public float mainHandItemMaxDuration() {
      LivingEntity current = this.living;
      return current != null && current.isUsingItem() && current.getUsedItemHand() == InteractionHand.MAIN_HAND
         ? this.itemMaxUseTicks("slot.weapon.mainhand")
         : 0.0F;
   }

   @Override
   public float equipmentCount() {
      return this.data().equipment();
   }

   @Override
   public boolean hasArmorSlot(int slot) {
      return !this.data().item(armorSlot(slot)).isEmpty();
   }

   @Override
   public float armorTextureSlot(int slot) {
      if (this.player == null) {
         if (this.mob != null && slot == 4) {
            return switch (this.data().item("slot.armor.body")) {
               case "minecraft:leather_horse_armor" -> 1.0F;
               case "minecraft:iron_horse_armor" -> 2.0F;
               case "minecraft:golden_horse_armor" -> 3.0F;
               case "minecraft:diamond_horse_armor" -> 4.0F;
               case "minecraft:copper_horse_armor" -> 5.0F;
               case "minecraft:netherite_horse_armor" -> 6.0F;
               default -> 0.0F;
            };
         } else {
            return -1.0F;
         }
      } else {
         return slot == 1 && this.itemName("slot.armor.chest", 0).equals("minecraft:elytra") ? 5.0F : -1.0F;
      }
   }

   @Override
   public float armorColorSlot(int slot, int channel) {
      LivingEntity current = this.living;
      if (this.mob != null && current != null) {
         ItemStack stack = current.getItemBySlot(switch (slot) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            default -> EquipmentSlot.BODY;
         });
         DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
         int rgb = dyed != null ? dyed.rgb() : 16777215;

         return switch (channel) {
            case 0 -> (rgb >> 16 & 0xFF) / 255.0F;
            case 1 -> (rgb >> 8 & 0xFF) / 255.0F;
            case 2 -> (rgb & 0xFF) / 255.0F;
            default -> 1.0F;
         };
      } else {
         return 0.0F;
      }
   }

   private static String armorSlot(int slot) {
      return switch (slot) {
         case 0 -> "slot.armor.head";
         case 1 -> "slot.armor.chest";
         case 2 -> "slot.armor.legs";
         case 3 -> "slot.armor.feet";
         default -> "slot.armor.body";
      };
   }
}
