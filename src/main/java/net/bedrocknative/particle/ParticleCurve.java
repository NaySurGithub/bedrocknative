package net.bedrocknative.particle;

import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;

public final class ParticleCurve {
   private final String variable;
   private final int variableId;
   private final ParticleCurve.Type type;
   private final MolangProgram input;
   private final MolangProgram range;
   private final MolangProgram[] nodes;
   private final ParticleCurve.ChainNode[] chain;
   private final float[] scratch;

   public ParticleCurve(
      String variable,
      int variableId,
      ParticleCurve.Type type,
      MolangProgram input,
      MolangProgram range,
      MolangProgram[] nodes,
      ParticleCurve.ChainNode[] chain
   ) {
      this.variable = variable;
      this.variableId = variableId;
      this.type = type;
      this.input = input;
      this.range = range;
      this.nodes = nodes;
      this.chain = chain;
      this.scratch = new float[Math.max(4, nodes.length)];
   }

   public String variable() {
      return this.variable;
   }

   public int variableId() {
      return this.variableId;
   }

   public ParticleCurve.Type type() {
      return this.type;
   }

   public void apply(MolangEnv env) {
      float x = this.input.evaluate(env);
      float result;
      if (this.type == ParticleCurve.Type.BEZIER_CHAIN) {
         result = this.chain(x, env);
      } else {
         float width = this.range.evaluate(env);
         float t = width != 0.0F ? x / width : x;

         for (int i = 0; i < this.nodes.length; i++) {
            this.scratch[i] = this.nodes[i].evaluate(env);
         }
         result = switch (this.type) {
            case LINEAR -> linear(this.scratch, this.nodes.length, t);
            case BEZIER -> bezier(this.scratch, this.nodes.length, t);
            default -> catmullRom(this.scratch, this.nodes.length, t);
         };
      }

      env.variables.set(this.variableId, result, null);
   }

   static float linear(float[] values, int count, float t) {
      if (count == 0) {
         return 0.0F;
      } else if (count == 1) {
         return values[0];
      } else {
         float clamped = Math.max(0.0F, Math.min(1.0F, t));
         float position = clamped * (count - 1);
         int index = Math.min(count - 2, (int)position);
         float local = position - index;
         return values[index] + (values[index + 1] - values[index]) * local;
      }
   }

   static float bezier(float[] values, int count, float t) {
      if (count < 4) {
         return linear(values, count, t);
      } else {
         float u = Math.max(0.0F, Math.min(1.0F, t));
         float v = 1.0F - u;
         return v * v * v * values[0] + 3.0F * v * v * u * values[1] + 3.0F * v * u * u * values[2] + u * u * u * values[3];
      }
   }

   static float catmullRom(float[] values, int count, float t) {
      if (count < 4) {
         return linear(values, count, t);
      } else {
         int segments = count - 3;
         float clamped = Math.max(0.0F, Math.min(1.0F, t));
         float position = clamped * segments;
         int segment = Math.min(segments - 1, (int)position);
         float u = position - segment;
         float p0 = values[segment];
         float p1 = values[segment + 1];
         float p2 = values[segment + 2];
         float p3 = values[segment + 3];
         float u2 = u * u;
         float u3 = u2 * u;
         return 0.5F * (2.0F * p1 + (-p0 + p2) * u + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * u2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * u3);
      }
   }

   private float chain(float x, MolangEnv env) {
      if (this.chain.length == 0) {
         return 0.0F;
      } else if (this.chain.length != 1 && !(x <= this.chain[0].key())) {
         ParticleCurve.ChainNode last = this.chain[this.chain.length - 1];
         if (x >= last.key()) {
            return last.rightValue().evaluate(env);
         } else {
            int index = 0;

            while (index < this.chain.length - 2 && x >= this.chain[index + 1].key()) {
               index++;
            }

            ParticleCurve.ChainNode from = this.chain[index];
            ParticleCurve.ChainNode to = this.chain[index + 1];
            float width = to.key() - from.key();
            float t = width > 0.0F ? (x - from.key()) / width : 0.0F;
            float p0 = from.rightValue().evaluate(env);
            float p3 = to.leftValue().evaluate(env);
            float p1 = p0 + from.rightSlope().evaluate(env) * width / 3.0F;
            float p2 = p3 - to.leftSlope().evaluate(env) * width / 3.0F;
            float v = 1.0F - t;
            return v * v * v * p0 + 3.0F * v * v * t * p1 + 3.0F * v * t * t * p2 + t * t * t * p3;
         }
      } else {
         return (x <= this.chain[0].key() ? this.chain[0].leftValue() : this.chain[0].rightValue()).evaluate(env);
      }
   }

   public record ChainNode(float key, MolangProgram leftValue, MolangProgram rightValue, MolangProgram leftSlope, MolangProgram rightSlope) {
   }

   public static enum Type {
      LINEAR,
      BEZIER,
      CATMULL_ROM,
      BEZIER_CHAIN;
   }
}
