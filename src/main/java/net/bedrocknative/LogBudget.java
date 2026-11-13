package net.bedrocknative;

public final class LogBudget {
   private final int burst;
   private final long refillNanos;
   private double tokens;
   private long last = Long.MIN_VALUE;
   private int held;

   public LogBudget(int burst, long refillMillis) {
      if (burst >= 1 && refillMillis >= 1L) {
         this.burst = burst;
         this.refillNanos = refillMillis * 1000000L;
         this.tokens = burst;
      } else {
         throw new IllegalArgumentException("burst " + burst + ", refill " + refillMillis + " ms");
      }
   }

   public boolean take(long nowNanos) {
      if (this.last != Long.MIN_VALUE && nowNanos > this.last) {
         this.tokens = Math.min(this.burst, this.tokens + (double)(nowNanos - this.last) / this.refillNanos);
      }

      this.last = nowNanos;
      if (this.tokens >= 1.0) {
         this.tokens--;
         return true;
      } else {
         this.held++;
         return false;
      }
   }

   public int takeHeld() {
      int value = this.held;
      this.held = 0;
      return value;
   }
}
