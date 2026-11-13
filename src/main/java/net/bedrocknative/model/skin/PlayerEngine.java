package net.bedrocknative.model.skin;

import java.util.stream.Stream;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.VariableStore;

public final class PlayerEngine {
   private static final int ATTACK_TIME = MolangNames.id("attack_time");
   private static final int IS_FIRST_PERSON = MolangNames.id("is_first_person");
   private static final int IS_PAPERDOLL = MolangNames.id("is_paperdoll");
   private static final int MAP_FACE_ICON = MolangNames.id("map_face_icon");
   private static final int GLIDING_SPEED_VALUE = MolangNames.id("gliding_speed_value");
   private static final int SWIM_AMOUNT = MolangNames.id("swim_amount");
   private static final int IS_HOLDING_RIGHT = MolangNames.id("is_holding_right");
   private static final int IS_HOLDING_LEFT = MolangNames.id("is_holding_left");
   private static final int PLAYER_X_ROTATION = MolangNames.id("player_x_rotation");
   private static final int HAS_TARGET = MolangNames.id("has_target");
   private static final int DAMAGE_NEARBY_MOBS = MolangNames.id("damage_nearby_mobs");
   private static final int CHARGE_AMOUNT = MolangNames.id("charge_amount");
   private static final int IS_BRANDISHING_SPEAR = MolangNames.id("is_brandishing_spear");
   private static final int IS_HOLDING_SPYGLASS = MolangNames.id("is_holding_spyglass");
   private static final int USE_ITEM_INTERVAL_PROGRESS = MolangNames.id("use_item_interval_progress");
   private static final int USE_ITEM_STARTUP_PROGRESS = MolangNames.id("use_item_startup_progress");
   private static final int RAISE_ARMS = MolangNames.id("raise_arms");
   private static final int[] ARM_SWIM = new int[]{
      MolangNames.id("left_arm_swim_amount"),
      MolangNames.id("right_arm_swim_amount"),
      MolangNames.id("leftarmswim_amount"),
      MolangNames.id("rightarmswim_amount")
   };
   private static final int BOB_ANIMATION = MolangNames.id("bob_animation");
   private static final int IS_HORIZONTAL_SPLITSCREEN = MolangNames.id("is_horizontal_splitscreen");
   private static final int IS_VERTICAL_SPLITSCREEN = MolangNames.id("is_vertical_splitscreen");
   private static final int IS_SNEAKING = MolangNames.id("is_sneaking");
   private static final int IS_TOOTING_GOAT_HORN = MolangNames.id("is_tooting_goat_horn");
   private static final int IS_USING_BRUSH = MolangNames.id("is_using_brush");
   private static final int IS_USING_VR = MolangNames.id("is_using_vr");
   private static final int PLAYER_ARM_HEIGHT = MolangNames.id("player_arm_height");
   private static final int PLAYER_OFFHAND_ARM_HEIGHT = MolangNames.id("player_offhand_arm_height");
   private static final int FIRST_PERSON_ITEM_ROTATION_FACTOR = MolangNames.id("first_person_item_rotation_factor");
   private static final int[] SPEAR = Stream.of(
         "melee_spear_equipped",
         "fp_melee_spear_attack_item_position_x",
         "fp_melee_spear_attack_item_position_y",
         "fp_melee_spear_attack_item_position_z",
         "fp_melee_spear_attack_item_rotation_y",
         "fp_melee_spear_attack_item_rotation_z",
         "fp_melee_spear_use_item_position_x",
         "fp_melee_spear_use_item_position_y",
         "fp_melee_spear_use_item_position_z",
         "fp_melee_spear_use_item_rotation_y",
         "fp_melee_spear_use_item_rotation_z",
         "tp_melee_spear_attack_arm_rotation_x",
         "tp_melee_spear_attack_item_rotation_x",
         "tp_melee_spear_base_arm_rotation_x",
         "tp_melee_spear_use_arm_rotation_x",
         "tp_melee_spear_use_arm_rotation_y",
         "tp_melee_spear_use_arm_rotation_z",
         "tp_melee_spear_use_item_rotation_x"
      )
      .mapToInt(MolangNames::id)
      .toArray();
   private static final float STARTUP_TICKS = 4.0F;
   private static final float INTERVAL_TICKS = 4.0F;
   public float attackTime;
   public boolean firstPerson;
   public boolean paperdoll;
   public float swimAmount;
   public float glidingSpeed = 1.0F;
   public boolean holdingRight;
   public boolean holdingLeft;
   public float xRotation;
   public boolean sneaking;
   public float eatingTicks = -1.0F;
   public boolean holdingSpyglass;
   public boolean tootingGoatHorn;
   public boolean usingBrush;
   public boolean brandishingSpear;
   public float chargeAmount;
   public float armHeight = 1.0F;
   public float offhandArmHeight = 1.0F;

   public void apply(ActorInstance instance, SkinModel model) {
      VariableStore variables = instance.variables();
      variables.set(ATTACK_TIME, this.attackTime, null);
      variables.set(IS_FIRST_PERSON, this.firstPerson ? 1.0F : 0.0F, null);
      variables.set(IS_PAPERDOLL, this.paperdoll ? 1.0F : 0.0F, null);
      variables.set(MAP_FACE_ICON, 0.0F, null);
      variables.set(GLIDING_SPEED_VALUE, Math.max(1.0F, this.glidingSpeed), null);
      variables.set(SWIM_AMOUNT, this.swimAmount, null);
      variables.set(IS_HOLDING_RIGHT, this.holdingRight ? 1.0F : 0.0F, null);
      variables.set(IS_HOLDING_LEFT, this.holdingLeft ? 1.0F : 0.0F, null);
      variables.set(PLAYER_X_ROTATION, this.xRotation, null);
      variables.set(HAS_TARGET, 0.0F, null);
      variables.set(DAMAGE_NEARBY_MOBS, 0.0F, null);
      variables.set(CHARGE_AMOUNT, this.chargeAmount, null);
      variables.set(IS_BRANDISHING_SPEAR, this.brandishingSpear ? 1.0F : 0.0F, null);
      variables.set(IS_HOLDING_SPYGLASS, this.holdingSpyglass ? 1.0F : 0.0F, null);
      variables.set(USE_ITEM_STARTUP_PROGRESS, this.eatingTicks < 0.0F ? 0.0F : Math.min(1.0F, this.eatingTicks / 4.0F), null);
      variables.set(USE_ITEM_INTERVAL_PROGRESS, this.eatingTicks < 4.0F ? 0.0F : (float)Math.abs(Math.sin((this.eatingTicks - 4.0F) / 4.0F * Math.PI)), null);
      variables.set(RAISE_ARMS, 0.0F, null);

      for (int id : ARM_SWIM) {
         variables.set(id, 0.0F, null);
      }

      variables.set(BOB_ANIMATION, 0.0F, null);
      variables.set(IS_HORIZONTAL_SPLITSCREEN, 0.0F, null);
      variables.set(IS_VERTICAL_SPLITSCREEN, 0.0F, null);
      variables.set(IS_SNEAKING, this.sneaking ? 1.0F : 0.0F, null);
      variables.set(IS_TOOTING_GOAT_HORN, this.tootingGoatHorn ? 1.0F : 0.0F, null);
      variables.set(IS_USING_BRUSH, this.usingBrush ? 1.0F : 0.0F, null);
      variables.set(IS_USING_VR, 0.0F, null);
      variables.set(PLAYER_ARM_HEIGHT, this.armHeight, null);
      variables.set(FIRST_PERSON_ITEM_ROTATION_FACTOR, (float)Math.sin(Math.toRadians((1.0F - this.attackTime) * 180.0F)), null);

      for (int id : SPEAR) {
         variables.set(id, 0.0F, null);
      }

      instance.context().set(PLAYER_OFFHAND_ARM_HEIGHT, this.offhandArmHeight, null);
      model.setVariables(variables);
   }

   public static float capeFlapAmount(float capeFlap, float capeLean) {
      return Math.clamp((capeLean / 2.0F + capeFlap) / 126.0F, 0.0F, 1.0F);
   }

   public static float sleepRotation(int facing2d) {
      return switch (facing2d & 3) {
         case 0 -> 90.0F;
         case 1 -> 0.0F;
         case 2 -> 270.0F;
         default -> 180.0F;
      };
   }
}
