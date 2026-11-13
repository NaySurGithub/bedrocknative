package net.bedrocknative.client.movement;

import net.bedrocknative.movement.Depenetration;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BedrockDepenetration {
   private static final boolean ENABLED = !"off".equals(System.getProperty("bedrocknative.depenetration"));
   private static boolean penetratedLast;
   private static boolean stuck;

   private BedrockDepenetration() {
   }

   public static void reset() {
      penetratedLast = false;
      stuck = false;
   }

   public static byte state() {
      return (byte)((penetratedLast ? 1 : 0) | (stuck ? 2 : 0));
   }

   public static void restore(byte state) {
      penetratedLast = (state & 1) != 0;
      stuck = (state & 2) != 0;
   }

   public static Vec3 apply(Entity entity, Vec3 movement) {
      if (!ENABLED) {
         return movement;
      } else {
         AABB box = BedrockMovement.floatBox(entity.getBoundingBox());
         double[] min = new double[]{box.minX, box.minY, box.minZ};
         double[] max = new double[]{box.maxX, box.maxY, box.maxZ};
         double[] velocity = new double[]{movement.x, movement.y, movement.z};
         boolean oneWay = stuck;
         boolean penetrated = false;

         for (VoxelShape shape : entity.level().getBlockCollisions(entity, box)) {
            for (AABB block : shape.toAabbs()) {
               Depenetration.Push push = Depenetration.push(
                  min, max, new double[]{block.minX, block.minY, block.minZ}, new double[]{block.maxX, block.maxY, block.maxZ}
               );
               if (push != null) {
                  penetrated = true;
                  if (!oneWay) {
                     Depenetration.apply(velocity, push);
                  }
               }
            }
         }

         stuck = penetratedLast && penetrated;
         penetratedLast = penetrated;
         return penetrated && !oneWay ? new Vec3(velocity[0], velocity[1], velocity[2]) : movement;
      }
   }
}
