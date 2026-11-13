package net.bedrocknative.model.geometry;

import java.util.Arrays;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class Skeleton {
   private final MeshedGeometry geometry;
   private final float[] position;
   private final float[] rotation;
   private final float[] scale;
   private final boolean[] relativeToEntity;
   private final Matrix4f[] world;
   private final @Nullable Matrix4f[] boundTo;
   private final Matrix3f rotationScratch = new Matrix3f();
   private final Quaternionf quaternion = new Quaternionf();

   public Skeleton(MeshedGeometry geometry) {
      this.geometry = geometry;
      int bones = geometry.boneCount();
      this.position = new float[bones * 3];
      this.rotation = new float[bones * 3];
      this.scale = new float[bones * 3];
      this.relativeToEntity = new boolean[bones];
      this.world = new Matrix4f[bones];
      this.boundTo = new Matrix4f[bones];

      for (int i = 0; i < bones; i++) {
         this.world[i] = new Matrix4f();
      }

      this.resetToRest();
      this.compute();
   }

   public MeshedGeometry geometry() {
      return this.geometry;
   }

   public void resetToRest() {
      Arrays.fill(this.position, 0.0F);
      Arrays.fill(this.scale, 1.0F);
      Arrays.fill(this.relativeToEntity, false);

      for (int i = 0; i < this.geometry.boneCount(); i++) {
         System.arraycopy(this.geometry.bone(i).restRotation(), 0, this.rotation, i * 3, 3);
      }
   }

   public void setRotation(int bone, float x, float y, float z) {
      this.rotation[bone * 3] = x;
      this.rotation[bone * 3 + 1] = y;
      this.rotation[bone * 3 + 2] = z;
   }

   public void setPosition(int bone, float x, float y, float z) {
      this.position[bone * 3] = x;
      this.position[bone * 3 + 1] = y;
      this.position[bone * 3 + 2] = z;
   }

   public void setScale(int bone, float x, float y, float z) {
      this.scale[bone * 3] = x;
      this.scale[bone * 3 + 1] = y;
      this.scale[bone * 3 + 2] = z;
   }

   public void setRelativeToEntity(int bone, boolean relative) {
      this.relativeToEntity[bone] = relative;
   }

   public void bind(int bone, @Nullable Matrix4f parent) {
      this.boundTo[bone] = parent;
   }

   public float rotation(int bone, int axis) {
      return this.rotation[bone * 3 + axis];
   }

   public void compute() {
      for (int i = 0; i < this.geometry.boneCount(); i++) {
         MeshedGeometry.Bone bone = this.geometry.bone(i);
         Matrix4f matrix = this.world[i];
         if (this.boundTo[i] != null) {
            matrix.set(this.boundTo[i]);
         } else if (bone.parent() >= 0) {
            matrix.set(this.world[bone.parent()]);
         } else {
            matrix.identity();
         }

         float[] pivot = bone.pivot();
         int at = i * 3;
         if (this.position[at] != 0.0F || this.position[at + 1] != 0.0F || this.position[at + 2] != 0.0F) {
            matrix.translate(-this.position[at], this.position[at + 1], this.position[at + 2]);
         }

         matrix.translate(pivot[0], pivot[1], pivot[2]);
         if (this.relativeToEntity[i] && bone.parent() >= 0) {
            this.world[bone.parent()].get3x3(this.rotationScratch);
            this.rotationScratch.getNormalizedRotation(this.quaternion);
            matrix.rotate(this.quaternion.conjugate());
         }

         float rx = this.rotation[at];
         float ry = this.rotation[at + 1];
         float rz = this.rotation[at + 2];
         if (rz != 0.0F) {
            matrix.rotateZ((float)Math.toRadians(rz));
         }

         if (ry != 0.0F) {
            matrix.rotateY((float)Math.toRadians(-ry));
         }

         if (rx != 0.0F) {
            matrix.rotateX((float)Math.toRadians(-rx));
         }

         if (this.scale[at] != 1.0F || this.scale[at + 1] != 1.0F || this.scale[at + 2] != 1.0F) {
            matrix.scale(this.scale[at], this.scale[at + 1], this.scale[at + 2]);
         }

         matrix.translate(-pivot[0], -pivot[1], -pivot[2]);
      }
   }

   public Matrix4f world(int bone) {
      return this.world[bone];
   }

   public Vector3f transform(int bone, Vector3f point) {
      return this.world[bone].transformPosition(point);
   }

   public Vector3f transformRaw(int bone, float x, float y, float z) {
      Vector3f point = this.world[bone].transformPosition(new Vector3f(-x, y, z));
      point.x = -point.x;
      return point;
   }

   public @Nullable Vector3f locator(String name) {
      for (int i = 0; i < this.geometry.boneCount(); i++) {
         List<MeshedGeometry.Locator> locators = this.geometry.bone(i).locators();

         for (int l = 0; l < locators.size(); l++) {
            MeshedGeometry.Locator locator = locators.get(l);
            if (locator.name().equalsIgnoreCase(name)) {
               float[] offset = locator.offset();
               return this.world[i].transformPosition(new Vector3f(offset[0], offset[1], offset[2]));
            }
         }
      }

      return null;
   }

   public static Matrix4f entityFrame(float x, float y, float z, float bodyYaw, float scale) {
      return new Matrix4f().translate(x, y, z).rotateY((float)Math.toRadians(180.0F - bodyYaw)).scale(scale / 16.0F);
   }

   public static Vector3f toWorld(Vector3f jPoint, float bodyYaw, float scale) {
      return entityFrame(0.0F, 0.0F, 0.0F, bodyYaw, scale).transformPosition(new Vector3f(jPoint));
   }
}
