package net.bedrocknative.client.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

interface VehiclePrediction {
   int HISTORY = 64;

   Entity vehicle();

   void tick(LocalPlayer var1);

   void record(long var1);

   BedrockVehicles.Correction correct(LocalPlayer var1, long var2, Vec3 var4, Vec3 var5, float var6, float var7, @Nullable Float var8, boolean var9);

   Vec3 networkPosition();

   Vec3 reportedDelta();

   float bedrockPitch();

   float bedrockYaw();

   String describe();

   static int index(long tick) {
      return (int)Math.floorMod(tick, 64L);
   }
}
