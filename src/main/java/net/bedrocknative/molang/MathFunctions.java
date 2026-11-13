package net.bedrocknative.molang;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;

public final class MathFunctions {
   public static final float PI = (float) Math.PI;
   private static final double BACK_C1 = 1.70158;
   private static final double BACK_C2 = 2.5949095;
   private static final double BACK_C3 = 2.70158;
   private static final Map<String, MathFunctions.Function> FUNCTIONS = new HashMap<>();

   private MathFunctions() {
   }

   @Nullable
   static MathFunctions.Function get(String name) {
      return FUNCTIONS.get(name);
   }

   public static int count() {
      return FUNCTIONS.size() + 1;
   }

   static float round(float value) {
      return Math.signum(value) * (float)Math.floor(Math.abs(value) + 0.5F);
   }

   static float minAngle(float degrees) {
      return degrees - 360.0F * (float)Math.floor((degrees + 180.0F) / 360.0F);
   }

   private static float uniform(SplittableRandom random, float a, float b) {
      float low = Math.min(a, b);
      float high = Math.max(a, b);
      return low == high ? low : low + (float)random.nextDouble() * (high - low);
   }

   private static float uniformInteger(SplittableRandom random, float a, float b) {
      int low = (int)Math.min(a, b);
      int high = (int)Math.max(a, b);
      return low == high ? low : random.nextInt(low, high + 1);
   }

   private static void pure(String name, int arity, MathFunctions.Pure function) {
      FUNCTIONS.put(name, new MathFunctions.Function(name, arity, function, null));
   }

   private static void random(String name, int arity, MathFunctions.Random function) {
      FUNCTIONS.put(name, new MathFunctions.Function(name, arity, null, function));
   }

   private static void ease(String name, MathFunctions.Curve in, MathFunctions.Curve out, MathFunctions.Curve inOut) {
      pure("ease_in_" + name, 3, (a, b, c) -> a + (b - a) * (float)in.at(c));
      pure("ease_out_" + name, 3, (a, b, c) -> a + (b - a) * (float)out.at(c));
      pure("ease_in_out_" + name, 3, (a, b, c) -> a + (b - a) * (float)inOut.at(c));
   }

   private static double backIn(double t) {
      return 2.70158 * t * t * t - 1.70158 * t * t;
   }

   private static double backInOut(double t) {
      return t < 0.5
         ? Math.pow(2.0 * t, 2.0) * (7.189819 * t - 2.5949095) / 2.0
         : (Math.pow(2.0 * t - 2.0, 2.0) * (3.5949095 * (t * 2.0 - 2.0) + 2.5949095) + 2.0) / 2.0;
   }

   private static double elasticIn(double t) {
      return t == 0.0 ? 0.0 : (t == 1.0 ? 1.0 : -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * (Math.PI * 2.0 / 3.0)));
   }

   private static double elasticOut(double t) {
      return t == 0.0 ? 0.0 : (t == 1.0 ? 1.0 : Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0);
   }

   private static double elasticInOut(double t) {
      double c5 = Math.PI * 4.0 / 9.0;
      if (t != 0.0 && t != 1.0) {
         return t < 0.5
            ? -(Math.pow(2.0, 20.0 * t - 10.0) * Math.sin((20.0 * t - 11.125) * c5)) / 2.0
            : Math.pow(2.0, -20.0 * t + 10.0) * Math.sin((20.0 * t - 11.125) * c5) / 2.0 + 1.0;
      } else {
         return t;
      }
   }

   private static double bounceOut(double t) {
      double n1 = 7.5625;
      double d1 = 2.75;
      if (t < 1.0 / d1) {
         return n1 * t * t;
      } else if (t < 2.0 / d1) {
         t -= 1.5 / d1;
         return n1 * t * t + 0.75;
      } else if (t < 2.5 / d1) {
         t -= 2.25 / d1;
         return n1 * t * t + 0.9375;
      } else {
         t -= 2.625 / d1;
         return n1 * t * t + 0.984375;
      }
   }

   static {
      pure("abs", 1, (a, b, c) -> Math.abs(a));
      pure("ceil", 1, (a, b, c) -> (float)Math.ceil(a));
      pure("floor", 1, (a, b, c) -> (float)Math.floor(a));
      pure("trunc", 1, (a, b, c) -> a < 0.0F ? (float)Math.ceil(a) : (float)Math.floor(a));
      pure("round", 1, (a, b, c) -> round(a));
      pure("sqrt", 1, (a, b, c) -> (float)Math.sqrt(a));
      pure("exp", 1, (a, b, c) -> (float)Math.exp(a));
      pure("ln", 1, (a, b, c) -> (float)Math.log(a));
      pure("pow", 2, (a, b, c) -> (float)Math.pow(a, b));
      pure("sin", 1, (a, b, c) -> (float)Math.sin(Math.toRadians(a)));
      pure("cos", 1, (a, b, c) -> (float)Math.cos(Math.toRadians(a)));
      pure("asin", 1, (a, b, c) -> (float)Math.toDegrees(Math.asin(a)));
      pure("acos", 1, (a, b, c) -> (float)Math.toDegrees(Math.acos(a)));
      pure("atan", 1, (a, b, c) -> (float)Math.toDegrees(Math.atan(a)));
      pure("atan2", 2, (a, b, c) -> (float)Math.toDegrees(Math.atan2(a, b)));
      pure("clamp", 3, (a, b, c) -> Math.min(Math.max(a, b), c));
      pure("min", 2, (a, b, c) -> Math.min(a, b));
      pure("max", 2, (a, b, c) -> Math.max(a, b));
      pure("mod", 2, (a, b, c) -> b == 0.0F ? 0.0F : a % b);
      pure("copy_sign", 2, (a, b, c) -> Math.copySign(a, b));
      pure("sign", 1, (a, b, c) -> a > 0.0F ? 1.0F : -1.0F);
      pure("lerp", 3, (a, b, c) -> a + (b - a) * c);
      pure("inverse_lerp", 3, (a, b, c) -> a == b ? 0.0F : (c - a) / (b - a));
      pure("lerprotate", 3, (a, b, c) -> a + minAngle(b - a) * c);
      pure("min_angle", 1, (a, b, c) -> minAngle(a));
      pure("hermite_blend", 1, (a, b, c) -> 3.0F * a * a - 2.0F * a * a * a);
      random("random", 2, (random, a, b, c) -> uniform(random, a, b));
      random("random_integer", 2, (random, a, b, c) -> uniformInteger(random, a, b));
      random("die_roll", 3, (random, a, b, c) -> {
         float sum = 0.0F;
         int i = 0;

         for (int n = (int)Math.min(1024.0F, a); i < n; i++) {
            sum += uniform(random, b, c);
         }

         return sum;
      });
      random("die_roll_integer", 3, (random, a, b, c) -> {
         float sum = 0.0F;
         int i = 0;

         for (int n = (int)Math.min(1024.0F, a); i < n; i++) {
            sum += uniformInteger(random, b, c);
         }

         return sum;
      });
      ease("quad", t -> t * t, t -> 1.0 - (1.0 - t) * (1.0 - t), t -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0);
      ease("cubic", t -> t * t * t, t -> 1.0 - Math.pow(1.0 - t, 3.0), t -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0);
      ease("quart", t -> Math.pow(t, 4.0), t -> 1.0 - Math.pow(1.0 - t, 4.0), t -> t < 0.5 ? 8.0 * Math.pow(t, 4.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 4.0) / 2.0);
      ease(
         "quint", t -> Math.pow(t, 5.0), t -> 1.0 - Math.pow(1.0 - t, 5.0), t -> t < 0.5 ? 16.0 * Math.pow(t, 5.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 5.0) / 2.0
      );
      ease("sine", t -> 1.0 - Math.cos(t * Math.PI / 2.0), t -> Math.sin(t * Math.PI / 2.0), t -> -(Math.cos(Math.PI * t) - 1.0) / 2.0);
      ease(
         "expo",
         t -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0),
         t -> t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t),
         t -> t == 0.0 ? 0.0 : (t == 1.0 ? 1.0 : (t < 0.5 ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0))
      );
      ease(
         "circ",
         t -> 1.0 - Math.sqrt(1.0 - t * t),
         t -> Math.sqrt(1.0 - (t - 1.0) * (t - 1.0)),
         t -> t < 0.5 ? (1.0 - Math.sqrt(1.0 - 4.0 * t * t)) / 2.0 : (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0
      );
      ease("back", MathFunctions::backIn, t -> 1.0 + 2.70158 * Math.pow(t - 1.0, 3.0) + 1.70158 * Math.pow(t - 1.0, 2.0), MathFunctions::backInOut);
      ease("elastic", MathFunctions::elasticIn, MathFunctions::elasticOut, MathFunctions::elasticInOut);
      ease(
         "bounce",
         t -> 1.0 - bounceOut(1.0 - t),
         MathFunctions::bounceOut,
         t -> t < 0.5 ? (1.0 - bounceOut(1.0 - 2.0 * t)) / 2.0 : (1.0 + bounceOut(2.0 * t - 1.0)) / 2.0
      );
   }

   @FunctionalInterface
   private interface Curve {
      double at(double var1);
   }

   record Function(String name, int arity, @Nullable MathFunctions.Pure pure, @Nullable MathFunctions.Random random) {
      boolean dice() {
         return this.random != null && this.name.startsWith("die_roll");
      }
   }

   @FunctionalInterface
   interface Pure {
      float apply(float var1, float var2, float var3);
   }

   @FunctionalInterface
   interface Random {
      float apply(SplittableRandom var1, float var2, float var3, float var4);
   }
}
