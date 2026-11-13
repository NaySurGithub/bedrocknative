package net.bedrocknative.molang;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

public interface MolangActor {
   MolangActor NONE = new MolangActor() {
      @Override
      public boolean valid() {
         return false;
      }
   };

   default boolean valid() {
      return true;
   }

   default String identifier() {
      return "";
   }

   default String name() {
      return "";
   }

   default boolean flag(EntityFlag flag) {
      return false;
   }

   default float data(EntityDataType<?> type) {
      return 0.0F;
   }

   @Nullable
   default Object property(String name) {
      return null;
   }

   @Nullable
   default VariableStore publicVariables() {
      return null;
   }

   default float lifeTime() {
      return 0.0F;
   }

   default float timeStamp() {
      return 0.0F;
   }

   default float timeOfDay() {
      return 0.0F;
   }

   default float day() {
      return 0.0F;
   }

   default float moonPhase() {
      return 0.0F;
   }

   default float moonBrightness() {
      return 0.0F;
   }

   default boolean isInUi() {
      return false;
   }

   default boolean isFirstPerson() {
      return false;
   }

   default boolean isLocalPlayer() {
      return false;
   }

   default float distanceFromCamera() {
      return 0.0F;
   }

   default float cameraRotation(int axis) {
      return 0.0F;
   }

   default float rotationToCamera(int axis) {
      return 0.0F;
   }

   default float actorCount() {
      return 0.0F;
   }

   default float bodyXRotation() {
      return 0.0F;
   }

   default float bodyYRotation() {
      return 0.0F;
   }

   default float headXRotation(int head) {
      return 0.0F;
   }

   default float headYRotation(int head) {
      return 0.0F;
   }

   default float targetXRotation() {
      return 0.0F;
   }

   default float targetYRotation() {
      return 0.0F;
   }

   default float position(int axis) {
      return 0.0F;
   }

   default float positionDelta(int axis) {
      return 0.0F;
   }

   default float groundSpeed() {
      return 0.0F;
   }

   default float verticalSpeed() {
      return 0.0F;
   }

   default float yawSpeed() {
      return 0.0F;
   }

   default float modifiedDistanceMoved() {
      return 0.0F;
   }

   default float modifiedMoveSpeed() {
      return 0.0F;
   }

   default float walkDistance() {
      return 0.0F;
   }

   default boolean isOnGround() {
      return false;
   }

   default boolean isMoving() {
      return this.flag(EntityFlag.MOVING);
   }

   default boolean isJumping() {
      return false;
   }

   default float swimAmount() {
      return 0.0F;
   }

   default boolean isAlive() {
      return false;
   }

   default boolean isSpectator() {
      return false;
   }

   default boolean isInWater() {
      return false;
   }

   default boolean isInLava() {
      return false;
   }

   default float health() {
      return 0.0F;
   }

   default float maxHealth() {
      return 0.0F;
   }

   default float deathTicks() {
      return 0.0F;
   }

   default float swellAmount() {
      return 0.0F;
   }

   default float swellingDir() {
      return 0.0F;
   }

   default float shakeAngle() {
      return 0.0F;
   }

   default boolean isShakingWetness() {
      return false;
   }

   default float headRollAngle() {
      return 0.0F;
   }

   default float tailAngle() {
      return 0.0F;
   }

   default boolean isGrazing() {
      return false;
   }

   default boolean isLevitating() {
      return false;
   }

   default float eyeTargetXRotation() {
      return 0.0F;
   }

   default float eyeTargetYRotation() {
      return 0.0F;
   }

   default boolean isGhost() {
      return "minecraft:elder_guardian_ghost".equals(this.identifier());
   }

   default float wingFlapPosition() {
      return 0.0F;
   }

   default float wingFlapSpeed() {
      return 0.0F;
   }

   default boolean isCarryingBlock() {
      return false;
   }

   default boolean leashes(String identifier) {
      return false;
   }

   default float squish(boolean current) {
      return 0.0F;
   }

   default float fuseTime() {
      return -1.0F;
   }

   default float sitAmount() {
      return 0.0F;
   }

   default float lieAmount() {
      return 0.0F;
   }

   default float rollCounter() {
      return 0.0F;
   }

   default float sneezeCounter() {
      return 0.0F;
   }

   default float standingScale() {
      return 0.0F;
   }

   default float heartbeatPhase() {
      return 0.0F;
   }

   default float heartbeatInterval() {
      return 0.0F;
   }

   default float timeSinceLastVibration() {
      return -1.0F;
   }

   default boolean isShieldPowered() {
      return false;
   }

   default float shakeTime() {
      return 0.0F;
   }

   default float lifeSpan() {
      return 0.0F;
   }

   default boolean isAttachedToEntity() {
      return false;
   }

   default float textureFrameIndex() {
      return 0.0F;
   }

   default float modelScale() {
      return 0.0F;
   }

   default boolean hasTarget() {
      return false;
   }

   default boolean hasOwner() {
      return false;
   }

   default String ownerIdentifier() {
      return "";
   }

   default boolean hasRider() {
      return false;
   }

   default String vehicleIdentifier() {
      return "";
   }

   default String itemName(String slot, int index) {
      return "";
   }

   default boolean itemHasTag(String slot, int index, String tag) {
      return false;
   }

   default float equipmentCount() {
      return 0.0F;
   }

   default boolean hasArmorSlot(int slot) {
      return false;
   }

   default float armorTextureSlot(int slot) {
      return -1.0F;
   }

   default float armorColorSlot(int slot, int channel) {
      return 0.0F;
   }

   default float mainHandItemUseDuration() {
      return 0.0F;
   }

   default float mainHandItemMaxDuration() {
      return 0.0F;
   }

   default boolean itemIsCharged(String slot) {
      return false;
   }

   default float itemRemainingUseTicks(String slot) {
      return 0.0F;
   }

   default float itemMaxUseTicks(String slot) {
      return 0.0F;
   }

   default float capeFlapAmount() {
      return 0.0F;
   }

   default boolean hasCape() {
      return false;
   }

   default boolean isPersonaOrPremiumSkin() {
      return false;
   }

   default float sleepRotation() {
      return 0.0F;
   }

   default float baseSwingDuration() {
      return 0.3F;
   }

   default String itemSlotToBoneName(String slot) {
      return switch (slot) {
         case "main_hand" -> "rightItem";
         case "off_hand" -> "leftItem";
         default -> "";
      };
   }
}
