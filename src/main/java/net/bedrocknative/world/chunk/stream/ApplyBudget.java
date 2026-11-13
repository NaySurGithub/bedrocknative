package net.bedrocknative.world.chunk.stream;

public final class ApplyBudget {
   private static final double WEIGHT_UP = 0.5;
   private static final double WEIGHT_DOWN = 0.2;
   private static final double CLAMP = 3.0;
   private static final double FLOOR_NANOS = 10000.0;
   private double applyNanos;
   private double finishNanos;
   private double downstreamNanos;

   public ApplyBudget() {
      this(300000.0, 200000.0, 200000.0);
   }

   public ApplyBudget(double applyNanos, double finishNanos, double downstreamNanos) {
      this.applyNanos = applyNanos;
      this.finishNanos = finishNanos;
      this.downstreamNanos = downstreamNanos;
   }

   public boolean allowsAnother(long spentNanos, int appliedSoFar, long budgetNanos) {
      return appliedSoFar == 0 ? true : spentNanos + this.applyNanos + (this.finishNanos + this.downstreamNanos) * (appliedSoFar + 1) <= budgetNanos;
   }

   public void recordApply(long nanos) {
      this.applyNanos = average(this.applyNanos, nanos);
   }

   public void recordFinish(long nanos, int columns) {
      if (columns > 0) {
         this.finishNanos = average(this.finishNanos, (double)nanos / columns);
      }
   }

   public void recordDownstream(long nanos, int columns) {
      if (columns > 0) {
         this.downstreamNanos = average(this.downstreamNanos, (double)nanos / columns);
      }
   }

   public double applyNanos() {
      return this.applyNanos;
   }

   public double finishNanos() {
      return this.finishNanos;
   }

   public double downstreamNanos() {
      return this.downstreamNanos;
   }

   private static double average(double current, double sample) {
      double clamped = Math.clamp(sample, current / 3.0, current * 3.0);
      return Math.max(10000.0, current + (clamped - current) * (clamped > current ? 0.5 : 0.2));
   }
}
