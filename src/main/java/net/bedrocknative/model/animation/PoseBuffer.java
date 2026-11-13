package net.bedrocknative.model.animation;

import java.util.Arrays;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.molang.MolangEnv;

public final class PoseBuffer {
   public static final float ROOT_ORIGIN_Y = 24.0F;
   private final int slots;
   private final float[] position;
   private final float[] rotation;
   private final float[] scale;
   private final float[] restRotation;
   private final float[] restPosition;
   private final boolean[] relativeToEntity;

   public PoseBuffer(int slots) {
      this.slots = slots;
      this.position = new float[slots * 3];
      this.rotation = new float[slots * 3];
      this.scale = new float[slots * 3];
      this.restRotation = new float[slots * 3];
      this.restPosition = new float[slots * 3];
      this.relativeToEntity = new boolean[slots];
      this.reset();
   }

   public int slots() {
      return this.slots;
   }

   public void setRest(MeshedGeometry geometry, int[] boneToSlot) {
      for (int i = 0; i < boneToSlot.length; i++) {
         int slot = boneToSlot[i];
         if (slot >= 0 && slot < this.slots) {
            MeshedGeometry.Bone bone = geometry.bone(i);
            System.arraycopy(bone.restRotation(), 0, this.restRotation, slot * 3, 3);
            float[] pivot = bone.pivot();
            float[] parent = bone.parent() >= 0 ? geometry.bone(bone.parent()).pivot() : null;
            this.restPosition[slot * 3] = -pivot[0] + (parent != null ? parent[0] : 0.0F);
            this.restPosition[slot * 3 + 1] = pivot[1] - (parent != null ? parent[1] : 24.0F);
            this.restPosition[slot * 3 + 2] = pivot[2] - (parent != null ? parent[2] : 0.0F);
         }
      }
   }

   public void copyRest(PoseBuffer source) {
      int length = Math.min(this.restRotation.length, source.restRotation.length);
      System.arraycopy(source.restRotation, 0, this.restRotation, 0, length);
      System.arraycopy(source.restPosition, 0, this.restPosition, 0, length);
   }

   public float restPosition(int slot, int axis) {
      return this.restPosition[slot * 3 + axis];
   }

   public void reset() {
      Arrays.fill(this.position, 0.0F);
      Arrays.fill(this.scale, 1.0F);
      Arrays.fill(this.relativeToEntity, false);
      System.arraycopy(this.restRotation, 0, this.rotation, 0, this.rotation.length);
   }

   private void reset(int slot) {
      for (int axis = 0; axis < 3; axis++) {
         this.position[slot * 3 + axis] = 0.0F;
         this.scale[slot * 3 + axis] = 1.0F;
         this.rotation[slot * 3 + axis] = this.restRotation[slot * 3 + axis];
      }
   }

   public void apply(AnimationDef animation, int[] boneSlots, float time, float weight, MolangEnv env) {
      float savedAnimTime = env.animTime;
      env.animTime = time;

      for (int i = 0; i < boneSlots.length; i++) {
         int slot = boneSlots[i];
         if (slot >= 0 && slot < this.slots) {
            BoneTimeline bone = animation.bones().get(i);
            if (animation.overridePreviousAnimation()) {
               this.reset(slot);
            }

            int base = slot * 3;
            if (bone.rotation() != null && bone.rotation().length > 0) {
               for (int axis = 0; axis < 3; axis++) {
                  this.rotation[base + axis] = this.rotation[base + axis]
                     + weight * KeyframeSampler.sample(bone.rotation(), time, axis, this.rotation[base + axis], env);
               }
            }

            if (bone.position() != null && bone.position().length > 0) {
               for (int axis = 0; axis < 3; axis++) {
                  float place = this.restPosition[base + axis] + this.position[base + axis];
                  this.position[base + axis] = this.position[base + axis] + weight * KeyframeSampler.sample(bone.position(), time, axis, place, env);
               }
            }

            if (bone.scale() != null && bone.scale().length > 0) {
               for (int axis = 0; axis < 3; axis++) {
                  float sample = KeyframeSampler.sample(bone.scale(), time, axis, this.scale[base + axis], env);
                  this.scale[base + axis] = this.scale[base + axis] * (1.0F + (sample - 1.0F) * weight);
               }
            }

            if (bone.relativeToEntity()) {
               this.relativeToEntity[slot] = true;
            }
         }
      }

      env.animTime = savedAnimTime;
   }

   public void applyTo(Skeleton skeleton, int[] boneToSlot) {
      MeshedGeometry geometry = skeleton.geometry();

      for (int i = 0; i < boneToSlot.length; i++) {
         int slot = boneToSlot[i];
         if (slot >= 0 && slot < this.slots) {
            int base = slot * 3;
            float[] rest = geometry.bone(i).restRotation();
            skeleton.setRotation(
               i,
               rest[0] + this.rotation[base] - this.restRotation[base],
               rest[1] + this.rotation[base + 1] - this.restRotation[base + 1],
               rest[2] + this.rotation[base + 2] - this.restRotation[base + 2]
            );
            skeleton.setPosition(i, this.position[base], this.position[base + 1], this.position[base + 2]);
            skeleton.setScale(i, this.scale[base], this.scale[base + 1], this.scale[base + 2]);
            skeleton.setRelativeToEntity(i, this.relativeToEntity[slot]);
         }
      }

      skeleton.compute();
   }

   public float rotation(int slot, int axis) {
      return this.rotation[slot * 3 + axis];
   }

   public float position(int slot, int axis) {
      return this.position[slot * 3 + axis];
   }

   public float scale(int slot, int axis) {
      return this.scale[slot * 3 + axis];
   }

   public boolean relativeToEntity(int slot) {
      return this.relativeToEntity[slot];
   }

   public void blend(PoseBuffer target, float t, boolean shortestPath) {
      for (int i = 0; i < Math.min(this.rotation.length, target.rotation.length); i++) {
         float delta = target.rotation[i] - this.rotation[i];
         if (shortestPath) {
            delta -= 360.0F * (float)Math.floor((delta + 180.0F) / 360.0F);
         }

         this.rotation[i] = this.rotation[i] + delta * t;
         this.position[i] = this.position[i] + (target.position[i] - this.position[i]) * t;
         this.scale[i] = this.scale[i] + (target.scale[i] - this.scale[i]) * t;
      }
   }

   public PoseBuffer copyShape() {
      PoseBuffer copy = new PoseBuffer(this.slots);
      copy.copyRest(this);
      copy.reset();
      return copy;
   }

   public void addDelta(PoseBuffer delta, float weight) {
      for (int i = 0; i < Math.min(this.rotation.length, delta.rotation.length); i++) {
         this.rotation[i] = this.rotation[i] + weight * (delta.rotation[i] - delta.restRotation[i]);
         this.position[i] = this.position[i] + weight * delta.position[i];
         this.scale[i] = this.scale[i] * (1.0F + (delta.scale[i] - 1.0F) * weight);
      }

      for (int i = 0; i < Math.min(this.relativeToEntity.length, delta.relativeToEntity.length); i++) {
         this.relativeToEntity[i] = this.relativeToEntity[i] | delta.relativeToEntity[i];
      }
   }
}
