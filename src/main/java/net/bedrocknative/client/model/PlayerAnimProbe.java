package net.bedrocknative.client.model;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.skin.PlayerEngine;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class PlayerAnimProbe {
   public static final String[] FIELDS = new String[]{
      "ms",
      "tick",
      "x",
      "y",
      "z",
      "distance",
      "speed",
      "attack",
      "swim",
      "glide",
      "body",
      "head",
      "pitch",
      "rightLeg",
      "leftLeg",
      "rightArmX",
      "rightArmY",
      "rightArmZ",
      "leftArmX",
      "bodyY",
      "headX",
      "headY",
      "rootX",
      "groundSpeed"
   };
   public static final int MS = 0;
   public static final int TICK = 1;
   public static final int X = 2;
   public static final int Z = 4;
   public static final int DISTANCE = 5;
   public static final int SPEED = 6;
   public static final int ATTACK = 7;
   public static final int SWIM = 8;
   public static final int GLIDE = 9;
   public static final int BODY = 10;
   public static final int RIGHT_LEG = 13;
   public static final int LEFT_LEG = 14;
   public static final int RIGHT_ARM_X = 15;
   public static final int RIGHT_ARM_Y = 16;
   public static final int RIGHT_ARM_Z = 17;
   public static final int LEFT_ARM_X = 18;
   public static final int BODY_Y = 19;
   public static final int ROOT_X = 22;
   private static final String[] BONES = new String[]{"rightLeg", "leftLeg", "rightArm", "rightArm", "rightArm", "leftArm", "body", "head", "head", "root"};
   private static final int[] AXES = new int[]{0, 0, 0, 1, 2, 0, 1, 0, 1, 0};
   private static final int CAPACITY = 32768;
   private static final long SAMPLE_NANOS = 4000000L;
   private static volatile int watched = Integer.MIN_VALUE;
   private static final float[][] FRAMES = new float[32768][];
   private static volatile long count;
   private static volatile long started;
   private static volatile long lastSample;

   private PlayerAnimProbe() {
   }

   public static void watch(int entityId) {
      count = 0L;
      started = System.nanoTime();
      lastSample = 0L;
      watched = entityId;
   }

   static boolean watching(int entityId) {
      return entityId == watched;
   }

   static void record(Player entity, ActorInstance instance, ClientActorView view, PlayerEngine engine, float partialTick, float glide) {
      long now = System.nanoTime();
      if (lastSample == 0L || now - lastSample >= 4000000L) {
         lastSample = now;
         int slot = (int)(count % 32768L);
         float[] frame = FRAMES[slot];
         if (frame == null) {
            frame = new float[FIELDS.length];
            FRAMES[slot] = frame;
         }

         frame[0] = (float)(now - started) / 1000000.0F;
         frame[1] = (float)entity.level().getGameTime() + partialTick;
         frame[2] = (float)Mth.lerp(partialTick, entity.xo, entity.getX());
         frame[3] = (float)Mth.lerp(partialTick, entity.yo, entity.getY());
         frame[4] = (float)Mth.lerp(partialTick, entity.zo, entity.getZ());
         frame[5] = view.modifiedDistanceMoved();
         frame[6] = view.modifiedMoveSpeed();
         frame[7] = engine.attackTime;
         frame[8] = engine.swimAmount;
         frame[9] = glide;
         frame[10] = view.bodyYRotation();
         frame[11] = view.headYRotation(0);
         frame[12] = view.headXRotation(0);
         Skeleton skeleton = instance.drawnSkeleton();

         for (int i = 0; i < BONES.length; i++) {
            int bone = skeleton != null ? skeleton.geometry().bone(BONES[i]) : -1;
            frame[13 + i] = bone >= 0 ? skeleton.rotation(bone, AXES[i]) : Float.NaN;
         }

         frame[23] = view.groundSpeed();
         count++;
      }
   }

   public static List<float[]> frames() {
      long first = Math.max(0L, count - 32768L);
      List<float[]> copy = new ArrayList<>((int)(count - first));

      for (long i = first; i < count; i++) {
         copy.add((float[])FRAMES[(int)(i % 32768L)].clone());
      }

      return copy;
   }
}
