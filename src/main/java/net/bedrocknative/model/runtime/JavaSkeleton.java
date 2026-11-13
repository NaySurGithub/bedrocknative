package net.bedrocknative.model.runtime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class JavaSkeleton implements WearerBones {
   private static final JavaSkeleton.Part[] PARTS = JavaSkeleton.Part.values();
   private static final Map<String, JavaSkeleton.Bone> BONES = Map.ofEntries(
      Map.entry("root", new JavaSkeleton.Bone(JavaSkeleton.Part.ROOT, 0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F)),
      Map.entry("waist", new JavaSkeleton.Bone(JavaSkeleton.Part.ROOT, 0.0F, 24.0F, 0.0F, 0.0F, 12.0F, 0.0F)),
      bone("head", JavaSkeleton.Part.HEAD, 0.0F, 24.0F, 0.0F),
      bone("helmet", JavaSkeleton.Part.HEAD, 0.0F, 24.0F, 0.0F),
      bone("hat", JavaSkeleton.Part.HAT, 0.0F, 24.0F, 0.0F),
      bone("body", JavaSkeleton.Part.BODY, 0.0F, 24.0F, 0.0F),
      bone("bodyarmor", JavaSkeleton.Part.BODY, 0.0F, 24.0F, 0.0F),
      bone("belt", JavaSkeleton.Part.BODY, 0.0F, 24.0F, 0.0F),
      bone("cape", JavaSkeleton.Part.BODY, 0.0F, 24.0F, 0.0F),
      bone("jacket", JavaSkeleton.Part.JACKET, 0.0F, 24.0F, 0.0F),
      bone("rightarm", JavaSkeleton.Part.RIGHT_ARM, -5.0F, 22.0F, 0.0F),
      bone("rightarmarmor", JavaSkeleton.Part.RIGHT_ARM, -5.0F, 22.0F, 0.0F),
      bone("rightsleeve", JavaSkeleton.Part.RIGHT_SLEEVE, -5.0F, 22.0F, 0.0F),
      Map.entry("rightitem", new JavaSkeleton.Bone(JavaSkeleton.Part.RIGHT_ARM, -5.0F, 22.0F, 0.0F, -6.0F, 15.0F, 1.0F)),
      bone("leftarm", JavaSkeleton.Part.LEFT_ARM, 5.0F, 22.0F, 0.0F),
      bone("leftarmarmor", JavaSkeleton.Part.LEFT_ARM, 5.0F, 22.0F, 0.0F),
      bone("leftsleeve", JavaSkeleton.Part.LEFT_SLEEVE, 5.0F, 22.0F, 0.0F),
      Map.entry("leftitem", new JavaSkeleton.Bone(JavaSkeleton.Part.LEFT_ARM, 5.0F, 22.0F, 0.0F, 6.0F, 15.0F, 1.0F)),
      bone("rightleg", JavaSkeleton.Part.RIGHT_LEG, -1.9F, 12.0F, 0.0F),
      bone("rightlegging", JavaSkeleton.Part.RIGHT_LEG, -1.9F, 12.0F, 0.0F),
      bone("rightboot", JavaSkeleton.Part.RIGHT_LEG, -1.9F, 12.0F, 0.0F),
      bone("rightsock", JavaSkeleton.Part.RIGHT_LEG, -1.9F, 12.0F, 0.0F),
      bone("rightpants", JavaSkeleton.Part.RIGHT_PANTS, -1.9F, 12.0F, 0.0F),
      bone("leftleg", JavaSkeleton.Part.LEFT_LEG, 1.9F, 12.0F, 0.0F),
      bone("leftlegging", JavaSkeleton.Part.LEFT_LEG, 1.9F, 12.0F, 0.0F),
      bone("leftboot", JavaSkeleton.Part.LEFT_LEG, 1.9F, 12.0F, 0.0F),
      bone("leftsock", JavaSkeleton.Part.LEFT_LEG, 1.9F, 12.0F, 0.0F),
      bone("leftpants", JavaSkeleton.Part.LEFT_PANTS, 1.9F, 12.0F, 0.0F)
   );
   private final Matrix4f model = new Matrix4f();
   private final float[] poses = new float[PARTS.length * 9];
   private final boolean[] present = new boolean[PARTS.length];
   private final Matrix4f[] parts = new Matrix4f[PARTS.length];
   private final Map<String, JavaSkeleton.Answer> answers = new HashMap<>();
   private final Quaternionf rotation = new Quaternionf();
   private long generation;

   public JavaSkeleton() {
      for (int i = 0; i < this.parts.length; i++) {
         this.parts[i] = new Matrix4f();
      }

      this.clear();
   }

   private static Entry<String, JavaSkeleton.Bone> bone(String name, JavaSkeleton.Part part, float x, float y, float z) {
      return Map.entry(name, new JavaSkeleton.Bone(part, x, y, z));
   }

   public static JavaSkeleton.@Nullable Bone humanoidBone(String name) {
      return BONES.get(name.toLowerCase(Locale.ROOT));
   }

   public void clear() {
      this.model.identity();
      Arrays.fill(this.present, false);

      for (int i = 0; i < PARTS.length; i++) {
         int base = i * 9;
         Arrays.fill(this.poses, base, base + 6, 0.0F);
         this.poses[base + 6] = this.poses[base + 7] = this.poses[base + 8] = 1.0F;
      }
   }

   public void setModelFrame(Matrix4fc frame) {
      this.model.set(frame);
   }

   public void setPart(JavaSkeleton.Part part, float x, float y, float z, float xRot, float yRot, float zRot, float xScale, float yScale, float zScale) {
      int base = part.ordinal() * 9;
      this.poses[base] = x;
      this.poses[base + 1] = y;
      this.poses[base + 2] = z;
      this.poses[base + 3] = xRot;
      this.poses[base + 4] = yRot;
      this.poses[base + 5] = zRot;
      this.poses[base + 6] = xScale;
      this.poses[base + 7] = yScale;
      this.poses[base + 8] = zScale;
      this.present[part.ordinal()] = true;
   }

   public void compute() {
      for (JavaSkeleton.Part part : PARTS) {
         Matrix4f matrix = this.parts[part.ordinal()];
         matrix.set(part.parent() == null ? this.model : this.parts[part.parent().ordinal()]);
         if (this.present[part.ordinal()]) {
            apply(matrix, this.poses, part.ordinal() * 9, this.rotation);
         }
      }

      this.generation++;
   }

   public static Matrix4f translateAndRotate(
      Matrix4f matrix, float x, float y, float z, float xRot, float yRot, float zRot, float xScale, float yScale, float zScale
   ) {
      float[] pose = new float[]{x, y, z, xRot, yRot, zRot, xScale, yScale, zScale};
      return apply(matrix, pose, 0, new Quaternionf());
   }

   private static Matrix4f apply(Matrix4f matrix, float[] pose, int base, Quaternionf scratch) {
      matrix.translate(pose[base] / 16.0F, pose[base + 1] / 16.0F, pose[base + 2] / 16.0F);
      if (pose[base + 3] != 0.0F || pose[base + 4] != 0.0F || pose[base + 5] != 0.0F) {
         matrix.rotate(scratch.rotationZYX(pose[base + 5], pose[base + 4], pose[base + 3]));
      }

      if (pose[base + 6] != 1.0F || pose[base + 7] != 1.0F || pose[base + 8] != 1.0F) {
         matrix.scale(pose[base + 6], pose[base + 7], pose[base + 8]);
      }

      return matrix;
   }

   public Matrix4f part(JavaSkeleton.Part part) {
      return this.parts[part.ordinal()];
   }

   @Override
   public Matrix4f bone(String name) {
      JavaSkeleton.Answer answer = this.answer(name);
      if (answer.generation != this.generation) {
         answer.generation = this.generation;
         wearerMatrix(this.parts[answer.frame.part().ordinal()], answer.frame, answer.matrix);
      }

      return answer.matrix;
   }

   @Override
   public boolean has(String name) {
      return this.answer(name).bone != null;
   }

   @Override
   public float @Nullable [] pivot(String name) {
      return this.answer(name).pivot;
   }

   private JavaSkeleton.Answer answer(String name) {
      JavaSkeleton.Answer answer = this.answers.get(name);
      if (answer == null) {
         answer = new JavaSkeleton.Answer(humanoidBone(name));
         this.answers.put(name, answer);
      }

      return answer;
   }

   public static Matrix4f wearerMatrix(Matrix4fc part, JavaSkeleton.Bone bone, Matrix4f out) {
      return out.set(part).scale(0.0625F).scale(-1.0F, -1.0F, 1.0F).translate(bone.originX(), -bone.originY(), -bone.originZ());
   }

   public static Matrix4f livingModelFrame(float bodyYaw, float scale) {
      return new Matrix4f().scale(scale).rotateY((float)Math.toRadians(180.0F - bodyYaw)).scale(-1.0F, -1.0F, 1.0F).translate(0.0F, -1.501F, 0.0F);
   }

   private static final class Answer {
      final JavaSkeleton.@Nullable Bone bone;
      final JavaSkeleton.Bone frame;
      final float @Nullable [] pivot;
      final Matrix4f matrix = new Matrix4f();
      long generation = -1L;

      Answer(JavaSkeleton.@Nullable Bone bone) {
         this.bone = bone;
         this.frame = bone != null ? bone : JavaSkeleton.BONES.get("root");
         this.pivot = bone != null ? bone.pivotJ() : null;
      }
   }

   public record Bone(JavaSkeleton.Part part, float originX, float originY, float originZ, float pivotX, float pivotY, float pivotZ) {
      Bone(JavaSkeleton.Part part, float x, float y, float z) {
         this(part, x, y, z, x, y, z);
      }

      public float[] pivotJ() {
         return new float[]{-this.pivotX, this.pivotY, this.pivotZ};
      }
   }

   public enum Part {
      ROOT(null),
      HEAD(ROOT),
      HAT(HEAD),
      BODY(ROOT),
      JACKET(BODY),
      RIGHT_ARM(ROOT),
      RIGHT_SLEEVE(RIGHT_ARM),
      LEFT_ARM(ROOT),
      LEFT_SLEEVE(LEFT_ARM),
      RIGHT_LEG(ROOT),
      RIGHT_PANTS(RIGHT_LEG),
      LEFT_LEG(ROOT),
      LEFT_PANTS(LEFT_LEG);

      private final JavaSkeleton.@Nullable Part parent;

      Part(JavaSkeleton.@Nullable Part parent) {
         this.parent = parent;
      }

      public JavaSkeleton.@Nullable Part parent() {
         return this.parent;
      }
   }
}
