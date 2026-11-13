package net.bedrocknative.ui;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record UiAnimation(
   String id,
   UiAnimation.Type type,
   double duration,
   UiValue from,
   UiValue to,
   UiEasing easing,
   @Nullable String next,
   @Nullable String destroyAtEnd,
   @Nullable String playEvent,
   @Nullable String endEvent,
   @Nullable String resetEvent,
   boolean looping,
   double fps,
   int frameCount,
   float frameStep,
   float @Nullable [] initialUv,
   boolean reversible,
   boolean scaleFromStartingAlpha
) {
   public double length() {
      if (this.type != UiAnimation.Type.FLIP_BOOK && this.type != UiAnimation.Type.ASEPRITE_FLIP_BOOK) {
         return Math.max(0.0, this.duration);
      } else if (this.looping) {
         return Double.POSITIVE_INFINITY;
      } else {
         return this.fps > 0.0 && this.frameCount > 0 ? (this.reversible ? 2 * this.frameCount - 1 : this.frameCount) / this.fps : 0.0;
      }
   }

   public interface Events {
      void event(String var1);

      void destroy(UiControl var1, String var2);
   }

   public interface Resolver {
      @Nullable UiAnimation resolve(String var1, UiControl var2);
   }

   public static final class Track {
      private final UiControl control;
      private final @Nullable String property;
      private final UiAnimation first;
      private UiAnimation current;
      private double start;
      private boolean waiting;
      private boolean done;
      private @Nullable UiValue value;
      private UiLength @Nullable [] lengths;
      private double activatedAt = Double.NEGATIVE_INFINITY;

      public Track(UiControl control, @Nullable String property, UiAnimation first, double now) {
         this.control = control;
         this.property = property;
         this.first = first;
         this.restart(now);
      }

      private void restart(double now) {
         this.current = this.first;
         this.start = now;
         this.done = false;
         this.waiting = this.first.playEvent != null;
         this.value = null;
         this.lengths = null;
         this.activatedAt = this.waiting ? Double.NEGATIVE_INFINITY : now;
         this.hold(this.first, 0.0);
      }

      public UiControl control() {
         return this.control;
      }

      public @Nullable String property() {
         return this.property != null ? this.property : this.current.type.property();
      }

      public UiAnimation current() {
         return this.current;
      }

      public boolean done() {
         return this.done;
      }

      public boolean waiting() {
         return this.waiting;
      }

      public @Nullable UiValue value() {
         return this.value;
      }

      public UiLength @Nullable [] lengths() {
         return this.lengths;
      }

      public boolean event(String name, double now) {
         if (name.equals(this.first.resetEvent) || name.equals(this.current.resetEvent)) {
            this.restart(now);
            if (this.first.playEvent != null && name.equals(this.first.playEvent)) {
               this.waiting = false;
               this.start = now;
               this.activatedAt = now;
            }

            return true;
         } else if (this.waiting && name.equals(this.current.playEvent)) {
            this.waiting = false;
            this.start = now;
            this.activatedAt = now;
            return true;
         } else {
            return false;
         }
      }

      public boolean playing() {
         return !this.done && !this.waiting;
      }

      public double activatedAt() {
         return this.activatedAt;
      }

      public boolean advance(double now, UiAnimation.Events events, UiAnimation.Resolver resolver) {
         UiValue valueBefore = this.value;
         UiLength[] lengthsBefore = this.lengths;
         UiAnimation stepBefore = this.current;
         boolean waitingBefore = this.waiting;
         boolean doneBefore = this.done;
         double activatedBefore = this.activatedAt;
         this.step(now, events, resolver);
         return !Objects.equals(this.value, valueBefore)
            || !sameLengths(this.lengths, lengthsBefore)
            || this.current != stepBefore
            || this.waiting != waitingBefore
            || this.done != doneBefore
            || this.activatedAt != activatedBefore;
      }

      private static boolean sameLengths(UiLength @Nullable [] a, UiLength @Nullable [] b) {
         if (a == b) {
            return true;
         }

         if (a != null && b != null && a.length == b.length) {
            for (int i = 0; i < a.length; i++) {
               UiLength x = a[i];
               UiLength y = b[i];
               if (x.kind != y.kind
                  || x.px != y.px
                  || x.parent != y.parent
                  || x.children != y.children
                  || x.largestChild != y.largestChild
                  || x.largestSibling != y.largestSibling
                  || x.width != y.width
                  || x.height != y.height) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }

      private void step(double now, UiAnimation.Events events, UiAnimation.Resolver resolver) {
         for (int guard = 0; guard < 64 && !this.done; guard++) {
            if (this.waiting) {
               return;
            }

            double length = this.current.length();
            double elapsed = now - this.start;
            if (Double.isInfinite(length)) {
               this.value = flipBookAt(this.current, elapsed);
               return;
            }

            if (elapsed < length) {
               this.sample(this.current, length > 0.0 ? elapsed / length : 1.0);
               return;
            }

            this.sample(this.current, 1.0);
            UiAnimation ended = this.current;
            if (ended.endEvent != null) {
               events.event(ended.endEvent);
            }

            if (ended.destroyAtEnd != null) {
               events.destroy(this.control, ended.destroyAtEnd);
            }

            UiAnimation next = ended.next == null ? null : resolver.resolve(ended.next, this.control);
            if (next == null) {
               this.done = true;
               return;
            }

            this.current = next;
            this.start += length;
            if (next.playEvent != null) {
               this.waiting = true;
            }
         }
      }

      private void hold(UiAnimation step, double t) {
         if (step.type != UiAnimation.Type.WAIT) {
            this.sample(step, t);
         }
      }

      private void sample(UiAnimation step, double t) {
         double eased = step.easing.apply(t);
         switch (step.type) {
            case ALPHA:
            case CLIP:
               this.value = UiValue.of(mix(step.from.asNumber(), step.to.asNumber(), eased));
               break;
            case OFFSET:
            case SIZE:
               UiLength[] from = UiLength.pair(step.from);
               UiLength[] to = UiLength.pair(step.to);
               if (from != null && to != null) {
                  this.lengths = new UiLength[]{UiLength.lerp(from[0], to[0], (float)eased), UiLength.lerp(from[1], to[1], (float)eased)};
               }
               break;
            case COLOR:
            case UV:
               this.value = mixArrays(step.from, step.to, eased);
               break;
            case FLIP_BOOK:
            case ASEPRITE_FLIP_BOOK:
               this.value = flipBookAt(step, Math.min(t * step.length(), Math.max(0.0, step.length() - 1.0E-9)));
            case WAIT:
         }
      }

      private static UiValue flipBookAt(UiAnimation step, double elapsed) {
         float[] initial = step.initialUv == null ? new float[]{0.0F, 0.0F} : step.initialUv;
         if (step.frameCount > 0 && !(step.fps <= 0.0)) {
            int frame = (int)Math.floor(elapsed * step.fps);
            int cycle = step.reversible ? Math.max(1, 2 * step.frameCount - 2) : step.frameCount;
            frame = Math.floorMod(frame, cycle);
            if (step.reversible && frame >= step.frameCount) {
               frame = cycle - frame;
            }

            return UiValue.array(initial[0], initial[1] + frame * step.frameStep);
         } else {
            return UiValue.array(initial[0], initial[1]);
         }
      }

      private static double mix(double a, double b, double t) {
         return a + (b - a) * t;
      }

      private static UiValue mixArrays(UiValue from, UiValue to, double t) {
         float[] a = from.floats();
         float[] b = to.floats();
         if (a != null && b != null) {
            int length = Math.max(a.length, b.length);
            double[] mixed = new double[length];

            for (int i = 0; i < length; i++) {
               float x = i < a.length ? a[i] : 1.0F;
               float y = i < b.length ? b[i] : 1.0F;
               mixed[i] = mix(x, y, t);
            }

            return UiValue.array(mixed);
         } else {
            return t < 1.0 ? from : to;
         }
      }
   }

   public enum Type {
      ALPHA,
      OFFSET,
      SIZE,
      COLOR,
      UV,
      FLIP_BOOK,
      ASEPRITE_FLIP_BOOK,
      WAIT,
      CLIP;

      public static UiAnimation.@Nullable Type of(@Nullable String name) {
         if (name == null) {
            return null;
         }

         try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException e) {
            return null;
         }
      }

      public @Nullable String property() {
         return switch (this) {
            case ALPHA -> "alpha";
            case OFFSET -> "offset";
            case SIZE -> "size";
            case COLOR -> "color";
            case UV, FLIP_BOOK, ASEPRITE_FLIP_BOOK -> "uv";
            case WAIT -> null;
            case CLIP -> "clip_ratio";
         };
      }
   }
}
