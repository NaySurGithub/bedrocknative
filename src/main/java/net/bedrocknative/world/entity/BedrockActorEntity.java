package net.bedrocknative.world.entity;

import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.entity.ActorMotion;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class BedrockActorEntity extends LivingEntity {
   public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
      Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("bedrocknative", "bedrock_actor")
   );
   public static final EntityType<BedrockActorEntity> TYPE = (EntityType<BedrockActorEntity>)Registry.register(
      BuiltInRegistries.ENTITY_TYPE,
      KEY,
      Builder.of(BedrockActorEntity::new, MobCategory.MISC).noLootTable().noSave().noSummon().sized(0.0F, 0.0F).clientTrackingRange(10).build(KEY)
   );
   private final ActorMotion motion = new ActorMotion();
   private String identifier = "";
   private float width;
   private float height;

   public BedrockActorEntity(EntityType<? extends BedrockActorEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   public static void register() {
      FabricDefaultAttributeRegistry.register(TYPE, LivingEntity.createLivingAttributes());
      BedrockNative.LOGGER.debug("Registered {}", KEY.identifier());
   }

   public String bedrockIdentifier() {
      return this.identifier;
   }

   public void setBedrockIdentifier(String identifier) {
      this.identifier = identifier;
   }

   public void setBox(float width, float height) {
      boolean changed = false;
      if (!Float.isNaN(width) && width != this.width) {
         this.width = Math.max(0.0F, width);
         changed = true;
      }

      if (!Float.isNaN(height) && height != this.height) {
         this.height = Math.max(0.0F, height);
         changed = true;
      }

      if (changed) {
         this.refreshDimensions();
      }
   }

   public ActorMotion motion() {
      return this.motion;
   }

   protected EntityDimensions getDefaultDimensions(Pose pose) {
      return EntityDimensions.scalable(this.width, this.height);
   }

   public void tick() {
      super.tick();
      this.motion.tick(this.getX(), this.getY(), this.getZ(), this.yBodyRot, this.onGround(), this.isShiftKeyDown());
   }

   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }

   public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
      return (InteractionResult)(this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS);
   }

   public boolean isPushable() {
      return false;
   }

   protected void pushEntities() {
   }

   public boolean canBeCollidedWith(@Nullable Entity other) {
      return false;
   }

   public PushReaction getPistonPushReaction() {
      return PushReaction.IGNORE_ENTITY;
   }

   public boolean isIgnoringBlockTriggers() {
      return true;
   }

   public boolean canBeHitByProjectile() {
      return false;
   }
}
