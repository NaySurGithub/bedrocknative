package net.bedrocknative.camera;

import org.cloudburstmc.protocol.bedrock.data.camera.CameraEase;

public final class CameraEasing {
   private static final double BACK = 1.70158;
   private static final double BACK_IN_OUT = 2.5949095;
   private static final double ELASTIC = Math.PI * 2.0 / 3.0;
   private static final double ELASTIC_IN_OUT = Math.PI * 4.0 / 9.0;

   private CameraEasing() {
   }

   public static double apply(CameraEase ease, double t) {
      if (t <= 0.0) {
         return 0.0;
      } else if (t >= 1.0) {
         return 1.0;
      } else {
         return switch (ease) {
            case LINEAR -> t;
            case SPRING -> 1.0 - Math.exp(-6.0 * t) * Math.cos((Math.PI * 3) * t) * (1.0 - t);
            case EASE_IN_SINE -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case EASE_OUT_SINE -> Math.sin(t * Math.PI / 2.0);
            case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case EASE_IN_QUAD -> in(t, 2);
            case EASE_OUT_QUAD -> out(t, 2);
            case EASE_IN_OUT_QUAD -> inOut(t, 2);
            case EASE_IN_CUBIC -> in(t, 3);
            case EASE_OUT_CUBIC -> out(t, 3);
            case EASE_IN_OUT_CUBIC -> inOut(t, 3);
            case EASE_IN_QUART -> in(t, 4);
            case EASE_OUT_QUART -> out(t, 4);
            case EASE_IN_OUT_QUART -> inOut(t, 4);
            case EASE_IN_QUINT -> in(t, 5);
            case EASE_OUT_QUINT -> out(t, 5);
            case EASE_IN_OUT_QUINT -> inOut(t, 5);
            case EASE_IN_EXPO -> Math.pow(2.0, 10.0 * t - 10.0);
            case EASE_OUT_EXPO -> 1.0 - Math.pow(2.0, -10.0 * t);
            case EASE_IN_OUT_EXPO -> t < 0.5 ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
            case EASE_IN_CIRC -> 1.0 - Math.sqrt(1.0 - t * t);
            case EASE_OUT_CIRC -> Math.sqrt(1.0 - (t - 1.0) * (t - 1.0));
            case EASE_IN_OUT_CIRC -> t < 0.5 ? (1.0 - Math.sqrt(1.0 - 4.0 * t * t)) / 2.0 : (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0;
            case EASE_IN_BACK -> 2.70158 * t * t * t - 1.70158 * t * t;
            case EASE_OUT_BACK -> 1.0 + 2.70158 * Math.pow(t - 1.0, 3.0) + 1.70158 * Math.pow(t - 1.0, 2.0);
            case EASE_IN_OUT_BACK -> t < 0.5
               ? Math.pow(2.0 * t, 2.0) * (7.189819 * t - 2.5949095) / 2.0
               : (Math.pow(2.0 * t - 2.0, 2.0) * (3.5949095 * (t * 2.0 - 2.0) + 2.5949095) + 2.0) / 2.0;
            case EASE_IN_ELASTIC -> -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * (Math.PI * 2.0 / 3.0));
            case EASE_OUT_ELASTIC -> Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0;
            case EASE_IN_OUT_ELASTIC -> t < 0.5
               ? -(Math.pow(2.0, 20.0 * t - 10.0) * Math.sin((20.0 * t - 11.125) * (Math.PI * 4.0 / 9.0))) / 2.0
               : Math.pow(2.0, -20.0 * t + 10.0) * Math.sin((20.0 * t - 11.125) * (Math.PI * 4.0 / 9.0)) / 2.0 + 1.0;
            case EASE_IN_BOUNCE -> 1.0 - bounce(1.0 - t);
            case EASE_OUT_BOUNCE -> bounce(t);
            case EASE_IN_OUT_BOUNCE -> t < 0.5 ? (1.0 - bounce(1.0 - 2.0 * t)) / 2.0 : (1.0 + bounce(2.0 * t - 1.0)) / 2.0;
         };
      }
   }

   private static double in(double t, int power) {
      return Math.pow(t, power);
   }

   private static double out(double t, int power) {
      return 1.0 - Math.pow(1.0 - t, power);
   }

   private static double inOut(double t, int power) {
      return t < 0.5 ? Math.pow(2.0, power - 1) * Math.pow(t, power) : 1.0 - Math.pow(-2.0 * t + 2.0, power) / 2.0;
   }

   private static double bounce(double t) {
      double n = 7.5625;
      double d = 2.75;
      if (t < 1.0 / d) {
         return n * t * t;
      } else if (t < 2.0 / d) {
         t -= 1.5 / d;
         return n * t * t + 0.75;
      } else if (t < 2.5 / d) {
         t -= 2.25 / d;
         return n * t * t + 0.9375;
      } else {
         t -= 2.625 / d;
         return n * t * t + 0.984375;
      }
   }
}
