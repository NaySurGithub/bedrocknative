package net.bedrocknative.model.animation;

import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public final class Vec3Value {
   public static final Vec3Value ZERO = new Vec3Value(new float[3], null);
   public static final Vec3Value ONE = new Vec3Value(new float[]{1.0F, 1.0F, 1.0F}, null);
   private final float[] constants;
   private final @Nullable MolangProgram @Nullable [] programs;

   public Vec3Value(float[] constants, @Nullable MolangProgram @Nullable [] programs) {
      this.constants = constants;
      this.programs = programs;
   }

   public static Vec3Value of(float x, float y, float z) {
      return new Vec3Value(new float[]{x, y, z}, null);
   }

   public float get(int axis, MolangEnv env) {
      return this.programs != null && this.programs[axis] != null ? this.programs[axis].evaluate(env) : this.constants[axis];
   }

   public boolean isConstant() {
      return this.programs == null;
   }

   public float constant(int axis) {
      return this.constants[axis];
   }

   public @Nullable MolangProgram program(int axis) {
      return this.programs != null ? this.programs[axis] : null;
   }
}
