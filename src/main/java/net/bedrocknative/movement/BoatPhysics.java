package net.bedrocknative.movement;

public final class BoatPhysics {
   public static final float GRAVITY = 0.04F;
   public static final float VERTICAL_DRAG = 0.98F;
   public static final float STROKE_START = 0.04125F;
   public static final float STROKE = 0.039875F;
   public static final int STROKE_TICKS = 10;
   public static final float WATER_FRICTION = 0.9F;
   public static final float UNDER_WATER_FRICTION = 0.45F;
   public static final float AIR_FRICTION = 1.0F;
   public static final float TORQUE = 48.0F;
   public static final float FAST_TORQUE = 30.0F;
   public static final float FAST_SPEED = 0.1F;
   public static final float STROKE_BONUS = 0.001375F;
   public static final float REVERSE_STROKE = -0.0048125F;
   public static final float TURNING_INNER = 0.01342219F;
   public static final float TURNING_OUTER = 0.04914573F;
   public static final float REVERSE_TURNING_INNER = -4.37275E-4F;
   public static final float FLOAT_DEPTH = 0.4138F;
   public static final float WATER_DAMPING = 0.75F;
   public static final float FLOAT_BAND_MIN = 0.3F;
   public static final float FLOAT_BAND_MAX = 0.46F;
   public static final double FLOAT_DRAG = 0.981644;
   public static final double FLOAT_FILTER = 0.882239;
   public static final double FLOAT_STIFFNESS = 3.36137E-4;
   private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

   private BoatPhysics() {
   }

   public static void beforeMove(BoatPhysics.State state, BoatPhysics.Controls controls, BoatPhysics.Surroundings around) {
      float friction = friction(around);
      state.vx *= friction;
      state.vz *= friction;
      boolean fast = Math.hypot(state.vx, state.vz) >= 0.1F;
      state.angularVelocity *= friction;
      float lastVy = state.vy;
      switch (around.medium()) {
         case WATER:
            float depth = around.depth();
            boolean afloat = depth >= 0.3F && depth <= 0.46F;
            if (afloat && state.floating) {
               state.vy = (float)(1.863883 * state.vy - 0.866044620916 * state.vyPrev + 3.36137E-4 * (depth - 0.4138F));
            } else {
               state.vy = (state.vy - 0.04F + 0.04F * (depth / 0.4138F)) * 0.75F;
            }

            state.floating = afloat;
            break;
         case UNDER_WATER:
            state.vy = (state.vy - 0.04F + 0.04F * (around.depth() / 0.4138F)) * 0.75F;
            state.floating = false;
            break;
         default:
            state.vy -= 0.04F;
            state.floating = false;
      }

      state.vyPrev = lastVy;
      boolean turnLeft = controls.left() && !controls.right();
      boolean turnRight = controls.right() && !controls.left();
      boolean turning = turnLeft || turnRight;
      boolean forward = controls.forward() && !controls.backward();
      boolean backward = controls.backward() && !controls.forward();
      float leftBase = 0.0F;
      float rightBase = 0.0F;
      if (forward) {
         leftBase = turnLeft ? 0.01342219F : (turnRight ? 0.04914573F : 0.039875F);
         rightBase = turnLeft ? 0.04914573F : (turnRight ? 0.01342219F : 0.039875F);
      } else if (backward) {
         leftBase = turnLeft ? -0.0048125F : (turnRight ? -4.37275E-4F : -0.0048125F);
         rightBase = turnLeft ? -4.37275E-4F : (turnRight ? -0.0048125F : -0.0048125F);
      } else {
         leftBase = turnRight ? 0.039875F : 0.0F;
         rightBase = turnLeft ? 0.039875F : 0.0F;
      }

      float left = row(state, true, leftBase);
      float right = row(state, false, rightBase);
      state.angularVelocity -= (fast ? 30.0F : 48.0F) * (right - left);
      state.angularVelocity *= friction;
      state.yaw = state.yaw + state.angularVelocity;
      float push = left + right;
      if (turning && !fast) {
         push *= friction;
      }

      if (push != 0.0F) {
         float angle = state.yaw * (float) (Math.PI / 180.0);
         state.vx = state.vx + (float)Math.sin(-angle) * push;
         state.vz = state.vz + (float)Math.cos(angle) * push;
      }

      state.vx *= friction;
      state.vz *= friction;
      if (around.medium() == BoatPhysics.Medium.LAND || around.medium() == BoatPhysics.Medium.AIR) {
         state.vy *= 0.98F;
      }
   }

   public static boolean isBlocked(double from, double intended, double after) {
      float expected = (float)(from + intended);
      double tolerance = Math.max(1.0E-5 * Math.max(1.0, Math.abs(intended)), Math.ulp(Math.max(Math.abs(expected), Math.abs((float)after))));
      return Math.abs(after - expected) > tolerance;
   }

   public static void afterMove(BoatPhysics.State state, boolean xBlocked, boolean yBlocked, boolean zBlocked) {
      if (xBlocked) {
         state.vx = 0.0F;
      }

      if (yBlocked) {
         state.vy = 0.0F;
      }

      if (zBlocked) {
         state.vz = 0.0F;
      }
   }

   public static float friction(BoatPhysics.Surroundings around) {
      return switch (around.medium()) {
         case LAND -> around.friction();
         case WATER -> 0.9F;
         case UNDER_WATER -> 0.45F;
         case AIR -> 1.0F;
      };
   }

   private static float row(BoatPhysics.State state, boolean leftSide, float base) {
      if (base == 0.0F) {
         if (leftSide) {
            state.leftRowed = 0;
            state.leftRowTime = 0.0F;
         } else {
            state.rightRowed = 0;
            state.rightRowTime = 0.0F;
         }

         return 0.0F;
      } else {
         int rowed = leftSide ? state.leftRowed : state.rightRowed;
         boolean start = rowed % 10 == 0;
         float stroke = base == 0.039875F ? (start ? 0.04125F : 0.039875F) : (start ? base + Math.copySign(0.001375F, base) : base);
         if (leftSide) {
            state.leftRowed++;
            state.leftRowTime += stroke;
         } else {
            state.rightRowed++;
            state.rightRowTime += stroke;
         }

         return stroke;
      }
   }

   public record Controls(boolean left, boolean right, boolean forward, boolean backward) {
      public static final BoatPhysics.Controls NONE = new BoatPhysics.Controls(false, false, false, false);

      public boolean paddling() {
         return this.left != this.right || this.forward;
      }
   }

   public enum Medium {
      LAND,
      WATER,
      UNDER_WATER,
      AIR;
   }

   public static final class State {
      public float vx;
      public float vy;
      public float vz;
      public float yaw;
      public float angularVelocity;
      public float vyPrev;
      public boolean floating = true;
      public int leftRowed;
      public int rightRowed;
      public float leftRowTime;
      public float rightRowTime;

      public BoatPhysics.State copy() {
         BoatPhysics.State copy = new BoatPhysics.State();
         copy.set(this);
         return copy;
      }

      public void set(BoatPhysics.State other) {
         this.vx = other.vx;
         this.vy = other.vy;
         this.vz = other.vz;
         this.yaw = other.yaw;
         this.angularVelocity = other.angularVelocity;
         this.vyPrev = other.vyPrev;
         this.floating = other.floating;
         this.leftRowed = other.leftRowed;
         this.rightRowed = other.rightRowed;
         this.leftRowTime = other.leftRowTime;
         this.rightRowTime = other.rightRowTime;
      }

      public boolean leftPaddling(BoatPhysics.Controls controls) {
         return controls.right() && !controls.left() || controls.forward();
      }

      public boolean rightPaddling(BoatPhysics.Controls controls) {
         return controls.left() && !controls.right() || controls.forward();
      }
   }

   public record Surroundings(BoatPhysics.Medium medium, float friction, float depth) {
      public static BoatPhysics.Surroundings land(float friction) {
         return new BoatPhysics.Surroundings(BoatPhysics.Medium.LAND, friction, 0.0F);
      }

      public static BoatPhysics.Surroundings water(BoatPhysics.Medium medium, float depth) {
         return new BoatPhysics.Surroundings(medium, 0.0F, depth);
      }

      public static BoatPhysics.Surroundings air() {
         return new BoatPhysics.Surroundings(BoatPhysics.Medium.AIR, 0.0F, 0.0F);
      }
   }
}
