package net.bedrocknative.ui;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum UiEasing {
   LINEAR,
   SPRING,
   IN_QUAD,
   OUT_QUAD,
   IN_OUT_QUAD,
   IN_CUBIC,
   OUT_CUBIC,
   IN_OUT_CUBIC,
   IN_QUART,
   OUT_QUART,
   IN_OUT_QUART,
   IN_QUINT,
   OUT_QUINT,
   IN_OUT_QUINT,
   IN_SINE,
   OUT_SINE,
   IN_OUT_SINE,
   IN_EXPO,
   OUT_EXPO,
   IN_OUT_EXPO,
   IN_CIRC,
   OUT_CIRC,
   IN_OUT_CIRC,
   IN_BOUNCE,
   OUT_BOUNCE,
   IN_OUT_BOUNCE,
   IN_BACK,
   OUT_BACK,
   IN_OUT_BACK,
   IN_ELASTIC,
   OUT_ELASTIC,
   IN_OUT_ELASTIC;

   public static UiEasing of(@Nullable String name) {
      if (name == null) {
         return LINEAR;
      } else {
         try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
            return LINEAR;
         }
      }
   }

   public double apply(double t) {
      if (t <= 0.0) {
         return 0.0;
      } else if (t >= 1.0) {
         return 1.0;
      } else {
         return switch (this) {
            case LINEAR -> t;
            case SPRING -> 1.0 - Math.cos(t * Math.PI * 4.5) * Math.exp(-6.0 * t);
            case IN_QUAD -> t * t;
            case OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t);
            case IN_OUT_QUAD -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case IN_CUBIC -> t * t * t;
            case OUT_CUBIC -> 1.0 - Math.pow(1.0 - t, 3.0);
            case IN_OUT_CUBIC -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            case IN_QUART -> Math.pow(t, 4.0);
            case OUT_QUART -> 1.0 - Math.pow(1.0 - t, 4.0);
            case IN_OUT_QUART -> t < 0.5 ? 8.0 * Math.pow(t, 4.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 4.0) / 2.0;
            case IN_QUINT -> Math.pow(t, 5.0);
            case OUT_QUINT -> 1.0 - Math.pow(1.0 - t, 5.0);
            case IN_OUT_QUINT -> t < 0.5 ? 16.0 * Math.pow(t, 5.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 5.0) / 2.0;
            case IN_SINE -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case OUT_SINE -> Math.sin(t * Math.PI / 2.0);
            case IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case IN_EXPO -> Math.pow(2.0, 10.0 * t - 10.0);
            case OUT_EXPO -> 1.0 - Math.pow(2.0, -10.0 * t);
            case IN_OUT_EXPO -> t < 0.5 ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
            case IN_CIRC -> 1.0 - Math.sqrt(1.0 - t * t);
            case OUT_CIRC -> Math.sqrt(1.0 - Math.pow(t - 1.0, 2.0));
            case IN_OUT_CIRC -> t < 0.5 ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * t, 2.0))) / 2.0 : (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0;
            case IN_BOUNCE -> 1.0 - bounce(1.0 - t);
            case OUT_BOUNCE -> bounce(t);
            case IN_OUT_BOUNCE -> t < 0.5 ? (1.0 - bounce(1.0 - 2.0 * t)) / 2.0 : (1.0 + bounce(2.0 * t - 1.0)) / 2.0;
            case IN_BACK -> 2.70158 * t * t * t - 1.70158 * t * t;
            case OUT_BACK -> 1.0 + 2.70158 * Math.pow(t - 1.0, 3.0) + 1.70158 * Math.pow(t - 1.0, 2.0);
            case IN_OUT_BACK -> {
               double c = 2.5949095;
               yield t < 0.5
                  ? Math.pow(2.0 * t, 2.0) * ((c + 1.0) * 2.0 * t - c) / 2.0
                  : (Math.pow(2.0 * t - 2.0, 2.0) * ((c + 1.0) * (t * 2.0 - 2.0) + c) + 2.0) / 2.0;
            }
            case IN_ELASTIC -> -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * (Math.PI * 2.0 / 3.0));
            case OUT_ELASTIC -> Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0;
            case IN_OUT_ELASTIC -> {
               double c = Math.PI * 4.0 / 9.0;
               yield t < 0.5
                  ? -(Math.pow(2.0, 20.0 * t - 10.0) * Math.sin((20.0 * t - 11.125) * c)) / 2.0
                  : Math.pow(2.0, -20.0 * t + 10.0) * Math.sin((20.0 * t - 11.125) * c) / 2.0 + 1.0;
            }
         };
      }
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
