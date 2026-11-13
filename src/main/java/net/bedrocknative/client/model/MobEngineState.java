package net.bedrocknative.client.model;

import java.util.List;
import java.util.SplittableRandom;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.render.DyeColors;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.MolangStruct;
import net.bedrocknative.molang.VariableStore;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory.Sample;
import net.minecraft.world.item.Items;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

final class MobEngineState {
   private static final int MAX_CATCH_UP = 100;
   private static final int MAX_SWELL = 30;
   private static final int GRAZE_PULSE = 5;
   private static final int SWING_TICKS = 6;
   private static final float CROSSBOW_CHARGE_TICKS = 25.0F;
   private static final int DARK_TICKS = 100;
   private static final int STAND_TICKS = 6;
   private static final float TWO_PI = (float) (Math.PI * 2);
   private static final int ATTACK_TIME = MolangNames.id("attack_time");
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
   private static final int IS_HOLDING_ITEM = MolangNames.id("isholdingitem");
   private static final int IS_CHARGING = MolangNames.id("ischarging");
   private static final int WING_FLAP = MolangNames.id("wing_flap");
   private static final int STATE = MolangNames.id("state");
   private static final int LIE_DOWN_AMOUNT = MolangNames.id("liedownamount");
   private static final int LIE_DOWN_AMOUNT_TAIL = MolangNames.id("liedownamounttail");
   private static final int LIE_ON_PLAYER = MolangNames.id("lieonplayer");
   private static final int ATTACK_ANIMATION_TICK = MolangNames.id("attack_animation_tick");
   private static final int OFFER_FLOWER_TICK = MolangNames.id("offer_flower_tick");
   private static final int STAND_ANIM = MolangNames.id("stand_anim");
   private static final int SHAKE_TAIL = MolangNames.id("shake_tail");
   private static final int ANIMATION_AMOUNT = MolangNames.id("animationamount");
   private static final int ANIMATION_AMOUNT_PREV = MolangNames.id("animationamountprev");
   private static final int TROPICAL_FISH = MolangNames.id("tropicalfish");
   private static final int BASE = MolangNames.id("base");
   private static final int PATTERN = MolangNames.id("pattern");
   private static final int SQUID = MolangNames.id("squid");
   private static final int TENTACLE_ANGLE = MolangNames.id("tentacle_angle");
   private static final int SWIM_ROTATION = MolangNames.id("swim_rotation");
   private static final int GLOW_ALPHA = MolangNames.id("glow_alpha");
   private static final int GOAT_HAS_LEFT_HORN = MolangNames.id("goat_has_left_horn");
   private static final int GOAT_HAS_RIGHT_HORN = MolangNames.id("goat_has_right_horn");
   private static final int SHOULD_BOW_HEAD = MolangNames.id("should_bow_head");
   private static final int DECOR_TEXTURE_INDEX = MolangNames.id("decortextureindex");
   private static final int ATTACK_STATE = MolangNames.id("attack_state");
   private static final int CROSSBOW_CHARGE = MolangNames.id("crossbow_charge");
   private static final int POS_Y = MolangNames.id("pos_y");
   private static final int ROT_Y = MolangNames.id("rot_y");
   private static final int[] HISTORICAL_FRAMES = historicalFrames();
   private static final int ARMOR_STAND_STRUCT = MolangNames.id("armor_stand");
   private static final int POSE_INDEX = MolangNames.id("pose_index");
   private static final int HURT_TIME = MolangNames.id("hurt_time");
   private static final int ARMOR_STAND_WIGGLE = 5;
   private final MobEngineState.Kind kind;
   private final boolean tropical;
   private final boolean glowing;
   private final SplittableRandom random = new SplittableRandom();
   private final @Nullable MolangStruct struct;
   private final MolangStruct @Nullable [] history;
   private long lastTick = Long.MIN_VALUE;
   private long ticks;
   private int swell;
   private int swellOld;
   private boolean ignited;
   private boolean shaking;
   private boolean shakingFlag;
   private float shake;
   private float shakeOld;
   private float interested;
   private float interestedOld;
   private int grazeTicks;
   private int attackTick;
   private int flowerTick;
   private float lieDown;
   private float lieDownOld;
   private float lieDownTail;
   private float lieDownTailOld;
   private float stand;
   private float standOld;
   private int tailCounter;
   private boolean swinging;
   private int swingTime;
   private int animationAmount;
   private int darkTicks;
   private int chargeTicks;
   private int rollTicks;
   private int sneezeTicks;
   private float sit;
   private float sitOld;
   private float lie;
   private float lieOld;
   private int standTicks;
   private int standTicksOld;
   private long lastVibration = Long.MIN_VALUE;
   private int shakeTicks;
   private int standHurt;

   MobEngineState(String identifier) {
      this.kind = MobEngineState.Kind.of(identifier);
      this.tropical = identifier.equals("minecraft:tropicalfish");
      this.glowing = identifier.equals("minecraft:glow_squid");
      this.struct = !this.tropical && this.kind != MobEngineState.Kind.SQUID ? null : new MolangStruct();
      if (this.kind == MobEngineState.Kind.DRAGON) {
         this.history = new MolangStruct[HISTORICAL_FRAMES.length];

         for (int i = 0; i < this.history.length; i++) {
            this.history[i] = new MolangStruct();
         }
      } else {
         this.history = null;
      }
   }

   private static int[] historicalFrames() {
      int[] ids = new int[64];

      for (int i = 0; i < ids.length; i++) {
         ids[i] = MolangNames.id("historical_frame_" + i);
      }

      return ids;
   }

   MobEngineState.Kind kind() {
      return this.kind;
   }

   void onEvent(EntityEventType type) {
      this.onEvent(type, 0, this.lastTick);
   }

   void onEvent(EntityEventType type, int data, long gameTime) {
      switch (type) {
         case ATTACK_START:
            this.attackTick = 10;
            this.swinging = true;
            this.swingTime = 0;
            break;
         case GOLEM_FLOWER_OFFER:
            this.flowerTick = 400;
            break;
         case GOLEM_FLOWER_WITHDRAW:
            this.flowerTick = 0;
            break;
         case EAT_GRASS:
            this.grazeTicks = 5;
            break;
         case SHAKE_WETNESS:
            this.startShake();
            break;
         case SHAKE_WETNESS_STOP:
            this.stopShake();
            break;
         case HURT:
            if (this.glowing) {
               this.darkTicks = 100;
            }

            if (this.kind == MobEngineState.Kind.ARMOR_STAND) {
               this.standHurt = 5;
            }
            break;
         case VIBRATION_DETECTED:
            this.lastVibration = gameTime;
            break;
         case ARROW_SHAKE:
            this.shakeTicks = data > 0 ? data : 7;
      }
   }

   void catchUp(long gameTime, ActorData data, @Nullable Entity entity) {
      if (this.lastTick != Long.MIN_VALUE && gameTime >= this.lastTick) {
         long elapsed = gameTime - this.lastTick;
         long run = Math.min(elapsed, 100L);
         this.lastTick = gameTime;
         this.ticks += elapsed - run;

         for (long i = 0L; i < run; i++) {
            this.tick(data, entity);
         }
      } else {
         if (this.lastVibration != Long.MIN_VALUE && this.lastVibration > gameTime) {
            this.lastVibration = gameTime;
         }

         this.lastTick = gameTime;
      }
   }

   private void tick(ActorData data, @Nullable Entity entity) {
      this.ticks++;
      switch (this.kind) {
         case CREEPER:
            this.swellOld = this.swell;
            this.ignited = data.flag(EntityFlag.IGNITED);
            this.swell = Math.clamp(this.swell + (this.ignited ? 1 : -1), 0, 30);
            break;
         case WOLF:
            this.interestedOld = this.interested;
            this.interested = this.interested + ((data.flag(EntityFlag.INTERESTED) ? 1.0F : 0.0F) - this.interested) * 0.4F;
            boolean flag = data.flag(EntityFlag.SHAKING);
            if (flag && !this.shakingFlag) {
               this.startShake();
            }

            this.shakingFlag = flag;
            if (this.shaking) {
               this.shakeOld = this.shake;
               this.shake += 0.05F;
               if (this.shakeOld >= 2.0F) {
                  this.stopShake();
               }
            }
         case SHEEP:
         case CHICKEN:
         case WITCH:
         case GHAST:
         case GOAT:
         case LLAMA:
         case WARDEN:
         case DRAGON:
         default:
            break;
         case IRON_GOLEM:
            if (this.attackTick > 0) {
               this.attackTick--;
            }

            if (this.flowerTick > 0) {
               this.flowerTick--;
            }
            break;
         case FELINE:
            this.lieDownOld = this.lieDown;
            this.lieDownTailOld = this.lieDownTail;
            if (!data.flag(EntityFlag.LAYING_DOWN) && !data.flag(EntityFlag.SLEEPING)) {
               this.lieDown = Math.max(0.0F, this.lieDown - 0.22F);
               this.lieDownTail = Math.max(0.0F, this.lieDownTail - 0.13F);
            } else {
               this.lieDown = Math.min(1.0F, this.lieDown + 0.15F);
               this.lieDownTail = Math.min(1.0F, this.lieDownTail + 0.08F);
            }
            break;
         case HORSE:
            this.standOld = this.stand;
            if (data.flag(EntityFlag.STANDING)) {
               this.stand = Math.min(1.0F, this.stand + (1.0F - this.stand) * 0.4F + 0.05F);
            } else {
               this.stand = Math.max(0.0F, this.stand + (0.8F * this.stand * this.stand * this.stand - this.stand) * 0.6F - 0.05F);
            }

            if (this.tailCounter == 0 && this.random.nextInt(200) == 0) {
               this.tailCounter = 1;
            }

            if (this.tailCounter > 0 && ++this.tailCounter > 8) {
               this.tailCounter = 0;
            }
            break;
         case FISH:
            this.animationAmount++;
            break;
         case SQUID:
            if (this.darkTicks > 0) {
               this.darkTicks--;
            }

            if (entity instanceof Squid squid && squid.tentacleMovement >= (float) (Math.PI * 2)) {
               squid.handleEntityEvent((byte)19);
            }
            break;
         case CROSSBOW:
            this.chargeTicks = data.flag(EntityFlag.CHARGING) ? this.chargeTicks + 1 : 0;
            break;
         case PANDA:
            this.rollTicks = data.flag(EntityFlag.ROLLING) ? this.rollTicks + 1 : 0;
            this.sneezeTicks = data.flag(EntityFlag.SNEEZING) ? this.sneezeTicks + 1 : 0;
            this.sitOld = this.sit;
            this.sit = data.flag(EntityFlag.SITTING) ? Math.min(1.0F, this.sit + 0.15F) : Math.max(0.0F, this.sit - 0.19F);
            this.lieOld = this.lie;
            this.lie = data.flag(EntityFlag.LAYING_DOWN) ? Math.min(1.0F, this.lie + 0.15F) : Math.max(0.0F, this.lie - 0.19F);
            break;
         case POLAR_BEAR:
            this.standTicksOld = this.standTicks;
            this.standTicks = Math.clamp(this.standTicks + (data.flag(EntityFlag.STANDING) ? 1 : -1), 0, 6);
            break;
         case ARROW:
            if (this.shakeTicks > 0) {
               this.shakeTicks--;
            }
            break;
         case ARMOR_STAND:
            if (this.standHurt > 0) {
               this.standHurt--;
            }
      }

      if (this.grazeTicks > 0) {
         this.grazeTicks--;
      }

      if (this.swinging && ++this.swingTime >= 6) {
         this.swinging = false;
         this.swingTime = 0;
      }
   }

   private void startShake() {
      this.shaking = true;
      this.shake = 0.0F;
      this.shakeOld = 0.0F;
   }

   private void stopShake() {
      this.shaking = false;
      this.shake = 0.0F;
      this.shakeOld = 0.0F;
   }

   void setVariables(VariableStore variables, ActorData data, @Nullable Entity entity, float partialTick) {
      if (this.swinging) {
         variables.set(ATTACK_TIME, Math.max(variables.get(ATTACK_TIME), (this.swingTime + partialTick) / 6.0F), null);
      }

      variables.set(DAMAGE_NEARBY_MOBS, data.flag(EntityFlag.DAMAGE_NEARBY_MOBS) ? 1.0F : 0.0F, null);
      variables.set(CHARGE_AMOUNT, 0.0F, null);
      variables.set(IS_BRANDISHING_SPEAR, 0.0F, null);
      boolean spyglass = entity instanceof LivingEntity living && living.getMainHandItem().is(Items.SPYGLASS);
      variables.set(IS_HOLDING_SPYGLASS, spyglass && data.flag(EntityFlag.USING_ITEM) ? 1.0F : 0.0F, null);
      variables.set(USE_ITEM_INTERVAL_PROGRESS, 0.0F, null);
      variables.set(USE_ITEM_STARTUP_PROGRESS, 0.0F, null);
      variables.set(RAISE_ARMS, 0.0F, null);

      for (int id : ARM_SWIM) {
         variables.set(id, 0.0F, null);
      }

      switch (this.kind) {
         case CHICKEN:
            float flap = 0.0F;
            if (entity instanceof Chicken chicken) {
               float position = Mth.lerp(partialTick, chicken.oFlap, chicken.flap);
               float speed = Mth.lerp(partialTick, chicken.oFlapSpeed, chicken.flapSpeed);
               flap = (Mth.sin(position) + 1.0F) * speed * (180.0F / (float)Math.PI);
            }

            variables.set(WING_FLAP, flap, null);
            break;
         case IRON_GOLEM:
            variables.set(ATTACK_ANIMATION_TICK, this.attackTick, null);
            variables.set(OFFER_FLOWER_TICK, this.flowerTick, null);
            break;
         case FELINE:
            variables.set(STATE, felineState(data), null);
            variables.set(LIE_DOWN_AMOUNT, Mth.lerp(partialTick, this.lieDownOld, this.lieDown), null);
            variables.set(LIE_DOWN_AMOUNT_TAIL, Mth.lerp(partialTick, this.lieDownTailOld, this.lieDownTail), null);
            variables.set(LIE_ON_PLAYER, 0.0F, null);
            break;
         case HORSE:
            variables.set(STAND_ANIM, Mth.lerp(partialTick, this.standOld, this.stand), null);
            variables.set(SHAKE_TAIL, this.tailCounter > 0 ? 1.0F : 0.0F, null);
            break;
         case WITCH:
            variables.set(IS_HOLDING_ITEM, data.item("slot.weapon.mainhand").isEmpty() ? 0.0F : 1.0F, null);
            break;
         case GHAST:
            variables.set(IS_CHARGING, !data.flag(EntityFlag.CHARGING) && !data.flag(EntityFlag.CHARGED) ? 0.0F : 1.0F, null);
            break;
         case FISH:
            variables.set(ANIMATION_AMOUNT, this.animationAmount, null);
            variables.set(ANIMATION_AMOUNT_PREV, this.animationAmount - 1, null);
            if (this.tropical && this.struct != null) {
               int base = Math.clamp((int)data.number(EntityDataTypes.VARIANT), 0, 1);
               this.struct.members().set(BASE, base, null);
               this.struct.members().set(PATTERN, base * 6 + Math.clamp((int)data.number(EntityDataTypes.MARK_VARIANT), 0, 5), null);
               variables.set(TROPICAL_FISH, 0.0F, this.struct);
            }
            break;
         case SQUID:
            if (this.struct != null) {
               float angle = 0.0F;
               float swim = 0.0F;
               if (entity instanceof Squid squid) {
                  angle = Mth.lerp(partialTick, squid.oldTentacleAngle, squid.tentacleAngle) * (180.0F / (float)Math.PI);
                  swim = Mth.lerp(partialTick, squid.zBodyRotO, squid.zBodyRot);
               }

               this.struct.members().set(TENTACLE_ANGLE, angle, null);
               this.struct.members().set(SWIM_ROTATION, swim, null);
               variables.set(SQUID, 0.0F, this.struct);
            }

            if (this.glowing) {
               variables.set(GLOW_ALPHA, Math.clamp((this.darkTicks - partialTick) / 10.0F, 0.0F, 1.0F), null);
            }
            break;
         case GOAT:
            int horns = data.value(EntityDataTypes.GOAT_HORN_COUNT) instanceof Number count ? count.intValue() : 2;
            variables.set(GOAT_HAS_RIGHT_HORN, horns >= 1 ? 1.0F : 0.0F, null);
            variables.set(GOAT_HAS_LEFT_HORN, horns >= 2 ? 1.0F : 0.0F, null);
            variables.set(SHOULD_BOW_HEAD, data.flag(EntityFlag.RAM_ATTACK) ? 1.0F : 0.0F, null);
            break;
         case LLAMA:
            variables.set(DECOR_TEXTURE_INDEX, decor(data), null);
            break;
         case CROSSBOW:
            boolean charging = data.flag(EntityFlag.CHARGING);
            boolean charged = data.flag(EntityFlag.CHARGED);
            variables.set(ATTACK_STATE, charging ? 2.0F : (charged ? 1.0F : 0.0F), null);
            variables.set(CROSSBOW_CHARGE, charging ? Math.min(1.0F, (this.chargeTicks + partialTick) / 25.0F) : (charged ? 1.0F : 0.0F), null);
         case PANDA:
         case POLAR_BEAR:
         case WARDEN:
         case ARROW:
         default:
            break;
         case DRAGON:
            this.history(variables, entity, partialTick);
            break;
         case ARMOR_STAND:
            if (variables.object(ARMOR_STAND_STRUCT) instanceof MolangStruct stand) {
               stand.members().set(POSE_INDEX, data.number(EntityDataTypes.ARMOR_STAND_POSE_INDEX), null);
               stand.members().set(HURT_TIME, this.standHurt, null);
            }
      }
   }

   private void history(VariableStore variables, @Nullable Entity entity, float partialTick) {
      MolangStruct[] frames = this.history;
      if (frames != null) {
         DragonFlightHistory flight = entity instanceof EnderDragon dragon ? dragon.flightHistory : null;

         for (int i = 0; i < frames.length; i++) {
            float y = 0.0F;
            float yaw = 0.0F;
            if (flight != null) {
               Sample now = flight.get(i);
               Sample before = flight.get(i + 1);
               y = (float)Mth.lerp(partialTick, before.y(), now.y());
               yaw = Mth.rotLerp(partialTick, before.yRot(), now.yRot());
            } else if (entity != null) {
               y = (float)entity.getY();
               yaw = entity.getYRot();
            }

            frames[i].members().set(POS_Y, y, null);
            frames[i].members().set(ROT_Y, yaw, null);
            variables.set(HISTORICAL_FRAMES[i], 0.0F, frames[i]);
         }
      }
   }

   private static float decor(ActorData data) {
      int dye = DyeColors.ofItem(data.item("slot.armor.body"), "_carpet");
      if (dye < 0) {
         dye = DyeColors.ofItem(data.item("slot.armor.chest"), "_carpet");
      }

      return dye + 1;
   }

   private static float felineState(ActorData data) {
      if (data.flag(EntityFlag.LAYING_DOWN) || data.flag(EntityFlag.SLEEPING)) {
         return 4.0F;
      } else if (data.flag(EntityFlag.SITTING)) {
         return 2.0F;
      } else if (data.flag(EntityFlag.SNEAKING)) {
         return 0.0F;
      } else {
         return data.flag(EntityFlag.SPRINTING) ? 1.0F : 3.0F;
      }
   }

   float swellAmount(float partialTick) {
      return this.kind == MobEngineState.Kind.CREEPER ? Mth.lerp(partialTick, this.swellOld, this.swell) / 28.0F : 0.0F;
   }

   float swellingDir() {
      return this.kind == MobEngineState.Kind.CREEPER ? (this.ignited ? 1.0F : -1.0F) : 0.0F;
   }

   float shakeAngle() {
      return this.shaking ? this.shakeOld : 0.0F;
   }

   boolean isShakingWetness() {
      return this.shaking;
   }

   float headRollAngle(float partialTick) {
      return Mth.lerp(partialTick, this.interestedOld, this.interested) * 0.15F * (float) Math.PI;
   }

   float tailAngle(ActorData data, float health, float maxHealth) {
      if (data.flag(EntityFlag.ANGRY)) {
         return 1.5393804F;
      } else if (data.flag(EntityFlag.TAMED)) {
         float lost = maxHealth > 0.0F ? (maxHealth - health) / maxHealth : 0.0F;
         return (0.55F - lost * 0.4F) * (float) Math.PI;
      } else {
         return (float) (Math.PI / 5);
      }
   }

   boolean isGrazing(ActorData data) {
      return this.grazeTicks > 0 || data.flag(EntityFlag.EATING);
   }

   float sitAmount(ActorData data, float partialTick) {
      if (data.value(EntityDataTypes.SITTING_AMOUNT) != null) {
         return Mth.lerp(partialTick, data.number(EntityDataTypes.SITTING_AMOUNT_PREVIOUS), data.number(EntityDataTypes.SITTING_AMOUNT));
      } else {
         return this.kind == MobEngineState.Kind.PANDA ? Mth.lerp(partialTick, this.sitOld, this.sit) : 0.0F;
      }
   }

   float lieAmount(ActorData data, float partialTick) {
      if (data.value(EntityDataTypes.LAYING_AMOUNT) != null) {
         return Mth.lerp(partialTick, data.number(EntityDataTypes.LAYING_AMOUNT_PREVIOUS), data.number(EntityDataTypes.LAYING_AMOUNT));
      } else {
         return this.kind == MobEngineState.Kind.PANDA ? Mth.lerp(partialTick, this.lieOld, this.lie) : 0.0F;
      }
   }

   float rollCounter(float partialTick) {
      return this.rollTicks > 0 ? this.rollTicks + partialTick : 0.0F;
   }

   float sneezeCounter(float partialTick) {
      return this.sneezeTicks > 0 ? this.sneezeTicks + partialTick : 0.0F;
   }

   float standingScale(float partialTick) {
      return this.kind == MobEngineState.Kind.POLAR_BEAR ? Mth.lerp(partialTick, this.standTicksOld, this.standTicks) / 6.0F : 0.0F;
   }

   float timeSinceLastVibration(float partialTick) {
      return this.lastVibration == Long.MIN_VALUE ? -1.0F : Math.max(0.0F, (float)(this.lastTick - this.lastVibration) + partialTick) / 20.0F;
   }

   float heartbeatPhase(ActorData data, float partialTick) {
      int interval = (int)data.number(EntityDataTypes.HEARTBEAT_INTERVAL_TICKS);
      return interval <= 0 ? 0.0F : ((float)(this.ticks % interval) + partialTick) / interval % 1.0F;
   }

   float shakeTime() {
      return this.shakeTicks;
   }

   enum Kind {
      OTHER(List.of()),
      CREEPER(List.of("minecraft:creeper")),
      WOLF(List.of("minecraft:wolf")),
      SHEEP(List.of("minecraft:sheep")),
      CHICKEN(List.of("minecraft:chicken")),
      IRON_GOLEM(List.of("minecraft:iron_golem")),
      FELINE(List.of("minecraft:cat", "minecraft:ocelot")),
      HORSE(List.of("minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse")),
      WITCH(List.of("minecraft:witch")),
      GHAST(List.of("minecraft:ghast")),
      FISH(List.of("minecraft:cod", "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropicalfish")),
      SQUID(List.of("minecraft:squid", "minecraft:glow_squid")),
      GOAT(List.of("minecraft:goat")),
      LLAMA(List.of("minecraft:llama", "minecraft:trader_llama")),
      CROSSBOW(List.of("minecraft:piglin", "minecraft:piglin_brute", "minecraft:pillager")),
      PANDA(List.of("minecraft:panda")),
      POLAR_BEAR(List.of("minecraft:polar_bear")),
      WARDEN(List.of("minecraft:warden")),
      DRAGON(List.of("minecraft:ender_dragon")),
      ARROW(List.of("minecraft:arrow")),
      ARMOR_STAND(List.of("minecraft:armor_stand"));

      final List<String> identifiers;

      Kind(List<String> identifiers) {
         this.identifiers = identifiers;
      }

      static MobEngineState.Kind of(String identifier) {
         for (MobEngineState.Kind kind : values()) {
            if (kind.identifiers.contains(identifier)) {
               return kind;
            }
         }

         return OTHER;
      }
   }
}
