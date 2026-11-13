package net.bedrocknative.model.runtime;

import java.util.Locale;
import net.bedrocknative.model.geometry.GeometryMesher;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class FirstPersonRig implements WearerBones {
   public static final float EYE = 27.41F;
   private static final float SCALE = 0.05859375F;
   private static final float[] RIGHT_ARM_PIVOT = new float[]{5.0F, 22.0F, 0.0F};
   private static final float[] RIGHT_ITEM_PIVOT = new float[]{6.0F, 15.0F, 1.0F};
   private static final float[] LEFT_ARM_PIVOT = new float[]{-5.0F, 22.0F, 0.0F};
   private static final float[] LEFT_ITEM_PIVOT = new float[]{-6.0F, 15.0F, 1.0F};
   private static final float[] ROOT_PIVOT = new float[]{0.0F, 0.0F, 0.0F};
   private final Matrix4f camera = new Matrix4f();
   private final Matrix4f rightArm = new Matrix4f();
   private final Matrix4f rightItem = new Matrix4f();
   private final Matrix4f leftArm = new Matrix4f();
   private final Matrix4f leftItem = new Matrix4f();

   public FirstPersonRig() {
      this.camera.scale(0.05859375F).rotateY((float) Math.PI).translate(0.0F, -27.41F, 0.0F);
      local(this.rightArm.set(this.camera), -5.0F, 22.0F, 0.0F, 95.0F, -45.0F, 115.0F, 13.5F, -10.0F, 12.0F);
      local(this.rightItem.set(this.rightArm), -6.0F, 15.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F);
      this.leftArm.set(this.camera);
      local(this.leftItem.set(this.leftArm), 6.0F, 15.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F);
   }

   public static float targetRotation(boolean firstPerson, float rotation) {
      return firstPerson ? 0.0F : rotation;
   }

   private static void local(Matrix4f matrix, float px, float py, float pz, float rx, float ry, float rz, float x, float y, float z) {
      matrix.translate(-x, y, z).translate(-px, py, pz);
      GeometryMesher.rotateJ(matrix, rx, ry, rz);
      matrix.translate(px, -py, -pz);
   }

   private static int which(String name) {
      return switch (name.toLowerCase(Locale.ROOT)) {
         case "rightitem" -> 1;
         case "rightarm", "rightsleeve", "rightarmarmor" -> 2;
         case "leftitem" -> 3;
         case "leftarm", "leftsleeve", "leftarmarmor" -> 4;
         default -> 0;
      };
   }

   @Override
   public Matrix4f bone(String name) {
      return switch (which(name)) {
         case 1 -> this.rightItem;
         case 2 -> this.rightArm;
         case 3 -> this.leftItem;
         case 4 -> this.leftArm;
         default -> this.camera;
      };
   }

   @Override
   public boolean has(String name) {
      return which(name) != 0 || name.equalsIgnoreCase("root");
   }

   @Override
   public float @Nullable [] pivot(String name) {
      return switch (which(name)) {
         case 1 -> RIGHT_ITEM_PIVOT;
         case 2 -> RIGHT_ARM_PIVOT;
         case 3 -> LEFT_ITEM_PIVOT;
         case 4 -> LEFT_ARM_PIVOT;
         default -> name.equalsIgnoreCase("root") ? ROOT_PIVOT : null;
      };
   }
}
