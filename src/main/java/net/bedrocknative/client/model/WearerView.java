package net.bedrocknative.client.model;

import java.util.IdentityHashMap;
import java.util.Map;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.runtime.FirstPersonRig;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.world.entity.EntityMappings;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

final class WearerView implements MolangActor {
   private @Nullable LivingEntity entity;
   private @Nullable ActorData data;
   private String identifier = "";
   private boolean firstPerson;
   private float partialTick;
   private static final Map<Item, String> ITEM_NAMES = new IdentityHashMap<>();

   void prepare(LivingEntity entity, boolean firstPerson, float partialTick) {
      this.entity = entity;
      this.firstPerson = firstPerson;
      this.partialTick = partialTick;
      ActorState state = BedrockActors.state(entity);
      this.data = state != null ? state.data() : null;
      this.identifier = identifier(entity, this.data);
   }

   void detach() {
      this.entity = null;
      this.data = null;
   }

   static String identifier(Entity entity, @Nullable ActorData data) {
      if (data != null) {
         return data.identifier();
      } else {
         return entity instanceof Player ? "minecraft:player" : EntityMappings.toBedrock(entity.getType());
      }
   }

   static String itemIdentifier(ItemStack stack) {
      if (stack.isEmpty()) {
         return "";
      }

      CustomItem custom = ItemMappings.custom(stack);
      return custom != null ? custom.identifier() : ITEM_NAMES.computeIfAbsent(stack.getItem(), item -> BuiltInRegistries.ITEM.getKey(item).toString());
   }

   private static @Nullable EquipmentSlot equipmentSlot(String slot) {
      return switch (slot) {
         case "slot.weapon.mainhand", "main_hand" -> EquipmentSlot.MAINHAND;
         case "slot.weapon.offhand", "off_hand" -> EquipmentSlot.OFFHAND;
         case "slot.armor.head" -> EquipmentSlot.HEAD;
         case "slot.armor.chest" -> EquipmentSlot.CHEST;
         case "slot.armor.legs" -> EquipmentSlot.LEGS;
         case "slot.armor.feet" -> EquipmentSlot.FEET;
         case "slot.armor.body" -> EquipmentSlot.BODY;
         default -> null;
      };
   }

   @Override
   public boolean valid() {
      return this.entity != null && !this.entity.isRemoved();
   }

   @Override
   public String identifier() {
      return this.identifier;
   }

   @Override
   public String name() {
      LivingEntity living = this.entity;
      if (this.data != null && !this.data.name().isEmpty()) {
         return this.data.name();
      } else {
         return living != null ? living.getName().getString() : "";
      }
   }

   @Override
   public boolean flag(EntityFlag flag) {
      LivingEntity living = this.entity;
      if (living == null) {
         return false;
      }

      boolean local = living == Minecraft.getInstance().player;
      if (!local && this.data != null) {
         return this.data.flag(flag);
      }

      return switch (flag) {
         case SNEAKING -> living.isCrouching();
         case SPRINTING -> living.isSprinting();
         case SWIMMING -> living.isSwimming();
         case GLIDING -> living.isFallFlying();
         case USING_ITEM -> living.isUsingItem();
         case BLOCKING -> living.isBlocking();
         case ON_FIRE -> living.isOnFire();
         case INVISIBLE -> living.isInvisible();
         case BABY -> living.isBaby();
         case SLEEPING -> living.isSleeping();
         case RIDING -> living.isPassenger();
         case MOVING -> living.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6;
         case HAS_GRAVITY -> !living.isNoGravity();
         case BREATHING -> living.getAirSupply() >= living.getMaxAirSupply();
         default -> this.data != null && this.data.flag(flag);
      };
   }

   @Override
   public float data(EntityDataType<?> type) {
      return this.data != null ? this.data.number(type) : 0.0F;
   }

   @Override
   public @Nullable Object property(String name) {
      return this.data != null ? this.data.property(name) : null;
   }

   @Override
   public float lifeTime() {
      LivingEntity living = this.entity;
      return living != null ? (living.tickCount + this.partialTick) / 20.0F : 0.0F;
   }

   @Override
   public boolean isFirstPerson() {
      return this.firstPerson;
   }

   @Override
   public boolean isLocalPlayer() {
      return this.entity != null && this.entity == Minecraft.getInstance().player;
   }

   @Override
   public float distanceFromCamera() {
      LivingEntity living = this.entity;
      if (living == null) {
         return 0.0F;
      }

      Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
      return (float)living.getPosition(this.partialTick).distanceTo(camera);
   }

   @Override
   public float bodyYRotation() {
      LivingEntity living = this.entity;
      return living != null ? Mth.rotLerp(this.partialTick, living.yBodyRotO, living.yBodyRot) : 0.0F;
   }

   @Override
   public float bodyXRotation() {
      LivingEntity living = this.entity;
      return living != null ? living.getXRot(this.partialTick) : 0.0F;
   }

   @Override
   public float headXRotation(int head) {
      return this.bodyXRotation();
   }

   @Override
   public float headYRotation(int head) {
      LivingEntity living = this.entity;
      return living != null
         ? Mth.wrapDegrees(Mth.rotLerp(this.partialTick, living.yHeadRotO, living.yHeadRot) - Mth.rotLerp(this.partialTick, living.yBodyRotO, living.yBodyRot))
         : 0.0F;
   }

   @Override
   public float targetXRotation() {
      return FirstPersonRig.targetRotation(this.firstPerson, this.bodyXRotation());
   }

   @Override
   public float targetYRotation() {
      return FirstPersonRig.targetRotation(this.firstPerson, this.headYRotation(0));
   }

   @Override
   public float position(int axis) {
      LivingEntity living = this.entity;
      if (living == null) {
         return 0.0F;
      }

      Vec3 position = living.getPosition(this.partialTick);
      return (float)(axis == 0 ? position.x : (axis == 1 ? position.y : (axis == 2 ? position.z : 0.0)));
   }

   @Override
   public float positionDelta(int axis) {
      LivingEntity living = this.entity;
      if (living == null) {
         return 0.0F;
      }

      Vec3 delta = living.getDeltaMovement();
      return (float)(axis == 0 ? delta.x : (axis == 1 ? delta.y : (axis == 2 ? delta.z : 0.0)));
   }

   @Override
   public float groundSpeed() {
      LivingEntity living = this.entity;
      return living != null ? (float)Math.sqrt(living.getDeltaMovement().horizontalDistanceSqr()) * 20.0F : 0.0F;
   }

   @Override
   public float verticalSpeed() {
      LivingEntity living = this.entity;
      return living != null ? (float)living.getDeltaMovement().y * 20.0F : 0.0F;
   }

   @Override
   public float modifiedDistanceMoved() {
      LivingEntity living = this.entity;
      return living != null ? living.walkAnimation.position(this.partialTick) : 0.0F;
   }

   @Override
   public float modifiedMoveSpeed() {
      LivingEntity living = this.entity;
      return living != null ? living.walkAnimation.speed(this.partialTick) : 0.0F;
   }

   @Override
   public boolean isOnGround() {
      return this.entity != null && this.entity.onGround();
   }

   @Override
   public boolean isMoving() {
      return this.flag(EntityFlag.MOVING);
   }

   @Override
   public float swimAmount() {
      LivingEntity living = this.entity;
      return living != null ? living.getSwimAmount(this.partialTick) : 0.0F;
   }

   @Override
   public boolean isAlive() {
      return this.entity != null && this.entity.isAlive();
   }

   @Override
   public boolean isSpectator() {
      return this.entity != null && this.entity.isSpectator();
   }

   @Override
   public boolean isInWater() {
      return this.entity != null && this.entity.isInWater();
   }

   @Override
   public boolean isInLava() {
      return this.entity != null && this.entity.isInLava();
   }

   @Override
   public float health() {
      return this.entity != null ? this.entity.getHealth() : 0.0F;
   }

   @Override
   public float maxHealth() {
      return this.entity != null ? this.entity.getMaxHealth() : 0.0F;
   }

   @Override
   public float deathTicks() {
      return this.entity != null ? this.entity.deathTime : 0.0F;
   }

   @Override
   public float modelScale() {
      return this.data != null ? this.data.scale() : 1.0F;
   }

   @Override
   public boolean hasOwner() {
      return this.entity != null;
   }

   @Override
   public String ownerIdentifier() {
      return this.identifier;
   }

   @Override
   public String itemName(String slot, int index) {
      LivingEntity living = this.entity;
      EquipmentSlot equipment = equipmentSlot(slot);
      if (living != null && equipment != null) {
         return itemIdentifier(living.getItemBySlot(equipment));
      } else {
         return this.data != null ? this.data.item(slot) : "";
      }
   }

   @Override
   public float equipmentCount() {
      LivingEntity living = this.entity;
      if (living == null) {
         return 0.0F;
      }

      int count = 0;

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         if (!living.getItemBySlot(slot).isEmpty()) {
            count++;
         }
      }

      return count;
   }

   @Override
   public boolean hasArmorSlot(int slot) {
      LivingEntity living = this.entity;
      if (living == null) {
         return false;
      }

      EquipmentSlot equipment = switch (slot) {
         case 0 -> EquipmentSlot.HEAD;
         case 1 -> EquipmentSlot.CHEST;
         case 2 -> EquipmentSlot.LEGS;
         case 3 -> EquipmentSlot.FEET;
         default -> EquipmentSlot.BODY;
      };
      return !living.getItemBySlot(equipment).isEmpty();
   }

   @Override
   public String itemSlotToBoneName(String slot) {
      LivingEntity living = this.entity;
      boolean rightMain = this.firstPerson || living == null || living.getMainArm() == HumanoidArm.RIGHT;

      return switch (slot) {
         case "main_hand" -> rightMain ? "rightItem" : "leftItem";
         case "off_hand" -> rightMain ? "leftItem" : "rightItem";
         default -> "";
      };
   }

   @Override
   public float mainHandItemUseDuration() {
      LivingEntity living = this.entity;
      return living != null && living.isUsingItem() && living.getUsedItemHand() == InteractionHand.MAIN_HAND ? living.getUseItemRemainingTicks() : 0.0F;
   }

   @Override
   public float mainHandItemMaxDuration() {
      LivingEntity living = this.entity;
      return living != null && living.isUsingItem() && living.getUsedItemHand() == InteractionHand.MAIN_HAND
         ? living.getUseItem().getUseDuration(living)
         : 0.0F;
   }
}
