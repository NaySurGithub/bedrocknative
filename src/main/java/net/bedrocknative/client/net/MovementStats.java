package net.bedrocknative.client.net;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MovementStats {
   private static final int HISTORY = 512;
   private static final int VEHICLE_HISTORY = 512;
   private static final AtomicInteger TELEPORTS = new AtomicInteger();
   private final AtomicInteger vehicleCorrections = new AtomicInteger();
   private final long[] ticks = new long[512];
   private final Vec3[] positions = new Vec3[512];
   private final Vec3[] velocities = new Vec3[512];
   private final List<MovementStats.Correction> corrections = new CopyOnWriteArrayList<>();
   private final List<MovementStats.VehicleCorrection> vehicleDetails = new CopyOnWriteArrayList<>();
   private volatile long lastTick = -1L;
   private volatile boolean ended;

   void end() {
      this.ended = true;
   }

   public boolean ended() {
      return this.ended;
   }

   void sent(long tick, Vec3 feet, Vec3 velocity) {
      int index = (int)Math.floorMod(tick, 512L);
      this.ticks[index] = tick;
      this.positions[index] = feet;
      this.velocities[index] = velocity;
      this.lastTick = tick;
   }

   void corrected(long tick, Vec3 serverFeet, Vec3 serverDelta, boolean onGround) {
      int index = (int)Math.floorMod(tick, 512L);
      boolean known = this.ticks[index] == tick && this.positions[index] != null;
      this.corrections
         .add(
            new MovementStats.Correction(
               tick, this.lastTick, serverFeet, known ? this.positions[index] : null, serverDelta, known ? this.velocities[index] : null, onGround
            )
         );
   }

   static void teleported() {
      TELEPORTS.incrementAndGet();
   }

   public static int teleports() {
      return TELEPORTS.get();
   }

   public @Nullable Vec3 sentPosition(long tick) {
      int index = (int)Math.floorMod(tick, 512L);
      return this.ticks[index] == tick ? this.positions[index] : null;
   }

   public long lastTick() {
      return this.lastTick;
   }

   public List<MovementStats.Correction> since(long tick) {
      return this.corrections.stream().filter(correction -> correction.tick() > tick).toList();
   }

   void vehicleCorrected() {
      this.vehicleCorrections.incrementAndGet();
   }

   void vehicleCorrection(BedrockVehicles.Correction correction) {
      this.vehicleDetails.add(new MovementStats.VehicleCorrection(correction, this.lastTick));

      while (this.vehicleDetails.size() > 512) {
         this.vehicleDetails.remove(0);
      }
   }

   public List<MovementStats.VehicleCorrection> vehicleCorrectionsSince(long tick) {
      return this.vehicleDetails.stream().filter(details -> details.correction().tick() > tick).toList();
   }

   public int vehicleCount() {
      return this.vehicleCorrections.get();
   }

   public int count() {
      return this.corrections.size();
   }

   public @Nullable Double lastCorrectedY() {
      int size = this.corrections.size();
      return size == 0 ? null : this.corrections.get(size - 1).server().y;
   }

   public record Correction(
      long tick, long receivedAt, Vec3 server, @Nullable Vec3 client, Vec3 serverDelta, @Nullable Vec3 clientDelta, boolean serverOnGround
   ) {
      public double distance() {
         return this.client == null ? Double.NaN : this.server.distanceTo(this.client);
      }
   }

   public record VehicleCorrection(BedrockVehicles.Correction correction, long receivedAt) {
   }
}
