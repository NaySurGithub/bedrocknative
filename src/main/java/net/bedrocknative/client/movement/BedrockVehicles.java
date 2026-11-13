package net.bedrocknative.client.movement;

import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class BedrockVehicles {
   private static final float BOAT_HALF_WIDTH = 0.7F;
   private static final float BOAT_HEIGHT = 0.455F;
   private static final float HORSE_STEP = 0.5625F;
   private static final float CAMEL_STEP = 1.5625F;
   private static volatile @Nullable VehiclePrediction current;
   public static volatile @Nullable BiConsumer<Float, Float> rowTimeListener;

   private BedrockVehicles() {
   }

   public static void reset() {
      current = null;
   }

   public static boolean predicts(@Nullable Object entity) {
      VehiclePrediction prediction = current;
      return entity != null && prediction != null && prediction.vehicle() == entity;
   }

   public static boolean predictable(@Nullable Object entity) {
      if (BedrockMovement.active() && (entity instanceof AbstractBoat || entity instanceof AbstractHorse)) {
         Minecraft minecraft = Minecraft.getInstance();
         return minecraft.player != null && ((Entity)entity).getControllingPassenger() == minecraft.player;
      } else {
         return false;
      }
   }

   public static boolean serverDriven(Object entity) {
      return BedrockMovement.active()
         && (entity instanceof Pig || entity instanceof Strider || entity instanceof HappyGhast || entity instanceof AbstractNautilus);
   }

   public static float stepHeight(Object entity) {
      return predicts(entity) && entity instanceof AbstractHorse ? (entity instanceof Camel ? 1.5625F : 0.5625F) : Float.NaN;
   }

   public static @Nullable Entity vehicle() {
      VehiclePrediction prediction = current;
      return prediction == null ? null : prediction.vehicle();
   }

   public static void tick(LocalPlayer player) {
      Entity riding = player.getVehicle();
      if (!predictable(riding)) {
         current = null;
      } else {
         VehiclePrediction prediction = current;
         if (prediction == null || prediction.vehicle() != riding) {
            prediction = start(riding);
            current = prediction;
         }

         prediction.tick(player);
      }
   }

   private static VehiclePrediction start(Entity vehicle) {
      return vehicle instanceof AbstractBoat boat ? new BoatPrediction(boat) : new HorsePrediction((AbstractHorse)vehicle);
   }

   static void stopInterpolating(Entity vehicle) {
      PositionAndRotation target = vehicle.getInterpolation().target();
      if (target != null) {
         vehicle.snapTo(target.position(), target.yRot(), target.xRot());
      }

      vehicle.getInterpolation().cancel();
   }

   public static void record(LocalPlayer player, long tick) {
      VehiclePrediction prediction = current;
      if (prediction != null) {
         if (player.getVehicle() != prediction.vehicle()) {
            current = null;
         } else {
            prediction.record(tick);
         }
      }
   }

   public static BedrockVehicles.@Nullable Correction correct(
      LocalPlayer player, long tick, Vec3 networkPosition, Vec3 delta, float pitch, float yaw, @Nullable Float angularVelocity, boolean onGround
   ) {
      Entity riding = player.getVehicle();
      if (!predictable(riding)) {
         return null;
      }

      VehiclePrediction prediction = current;
      if (prediction == null || prediction.vehicle() != riding) {
         prediction = start(riding);
         current = prediction;
      }

      return prediction.correct(player, tick, networkPosition, delta, pitch, yaw, angularVelocity, onGround);
   }

   public static AABB boatBox(AABB java) {
      double x = (java.minX + java.maxX) / 2.0;
      double z = (java.minZ + java.maxZ) / 2.0;
      return new AABB(x - 0.7F, java.minY, z - 0.7F, x + 0.7F, java.minY + 0.455F, z + 0.7F);
   }

   public static Vec3 networkPosition() {
      VehiclePrediction prediction = current;
      return prediction == null ? Vec3.ZERO : prediction.networkPosition();
   }

   public static Vec3 reportedDelta() {
      VehiclePrediction prediction = current;
      return prediction == null ? Vec3.ZERO : prediction.reportedDelta();
   }

   public static float bedrockPitch() {
      VehiclePrediction prediction = current;
      return prediction == null ? 0.0F : prediction.bedrockPitch();
   }

   public static float bedrockYaw() {
      VehiclePrediction prediction = current;
      return prediction == null ? 0.0F : prediction.bedrockYaw();
   }

   public static String describe() {
      VehiclePrediction prediction = current;
      return prediction == null ? "no predicted vehicle" : prediction.describe();
   }

   public record Correction(
      long tick,
      Vec3 server,
      Vec3 serverDelta,
      float serverYaw,
      float serverAngularVelocity,
      boolean serverOnGround,
      @Nullable Vec3 client,
      @Nullable Vec3 clientDelta,
      float clientYaw,
      float clientAngularVelocity
   ) {
      public double distance() {
         return this.client == null ? Double.NaN : this.server.distanceTo(this.client);
      }
   }
}
