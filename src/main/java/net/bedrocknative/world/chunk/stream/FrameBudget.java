package net.bedrocknative.world.chunk.stream;

public final class FrameBudget {
   private final long fullNanos;
   private final long floorNanos;
   private final long roomyFrameNanos;
   private final long busyFrameNanos;
   private static final double WEIGHT = 0.3;
   private static final double PERIOD_WEIGHT = 0.1;
   private static final long PERIOD_MAX_NANOS = 50000000L;
   private static final int BACKLOG_FROM = 64;
   private static final int BACKLOG_FULL = 512;
   public static final long PACE_NANOS = 250000000L;
   private double otherNanos;
   private double periodNanos = 1.6666667E7;

   public FrameBudget(long fullNanos) {
      this(fullNanos, 1000000L, 8000000L, 15000000L);
   }

   public FrameBudget(long fullNanos, long floorNanos, long roomyFrameNanos, long busyFrameNanos) {
      this.fullNanos = fullNanos;
      this.floorNanos = Math.min(floorNanos, fullNanos);
      this.roomyFrameNanos = roomyFrameNanos;
      this.busyFrameNanos = Math.max(busyFrameNanos, roomyFrameNanos + 1L);
      this.otherNanos = 0.0;
   }

   public void frameEnded(long frameNanos, long chunkNanos) {
      this.frameEnded(frameNanos, 0L, chunkNanos);
   }

   public void frameEnded(long frameNanos, long idleNanos, long chunkNanos) {
      double other = Math.max(0L, frameNanos - Math.max(0L, idleNanos) - chunkNanos);
      double clamped = Math.min(other, this.otherNanos * 3.0 + 1000000.0);
      this.otherNanos = this.otherNanos + (clamped - this.otherNanos) * 0.3;
      if (frameNanos > 0L) {
         this.periodNanos = this.periodNanos + (Math.min(frameNanos, 50000000L) - this.periodNanos) * 0.1;
      }
   }

   public long nanos(int waiting) {
      long byRoom;
      if (this.otherNanos <= this.roomyFrameNanos) {
         byRoom = this.fullNanos;
      } else if (this.otherNanos >= this.busyFrameNanos) {
         byRoom = this.floorNanos;
      } else {
         double share = (this.busyFrameNanos - this.otherNanos) / (this.busyFrameNanos - this.roomyFrameNanos);
         byRoom = this.floorNanos + Math.round((this.fullNanos - this.floorNanos) * share);
      }

      long byBacklog;
      if (waiting <= 64) {
         byBacklog = this.floorNanos;
      } else if (waiting >= 512) {
         byBacklog = this.fullNanos;
      } else {
         byBacklog = this.floorNanos + (this.fullNanos - this.floorNanos) * (waiting - 64) / 448L;
      }

      return Math.max(byRoom, byBacklog);
   }

   public int columns(int waiting) {
      return waiting <= 0 ? 1 : (int)Math.max(1.0, Math.min(2.147483647E9, Math.ceil(waiting * this.periodNanos / 2.5E8)));
   }

   public double otherNanos() {
      return this.otherNanos;
   }

   public double periodNanos() {
      return this.periodNanos;
   }

   public long fullNanos() {
      return this.fullNanos;
   }
}
