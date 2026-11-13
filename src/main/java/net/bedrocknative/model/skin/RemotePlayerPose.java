package net.bedrocknative.model.skin;

public final class RemotePlayerPose {
   public static final float SWIM_STEP = 0.09F;
   static final int MAX_CATCH_UP = 20;
   private float swimO;
   private float swim;
   private int glideTicks;
   private long lastTick = Long.MIN_VALUE;

   public void catchUp(long gameTime, boolean swimmingOrCrawling, boolean gliding) {
      if (this.lastTick != Long.MIN_VALUE && gameTime >= this.lastTick) {
         long run = Math.min(gameTime - this.lastTick, 20L);
         this.lastTick = gameTime;

         for (long i = 0L; i < run; i++) {
            this.swimO = this.swim;
            this.swim = swimmingOrCrawling ? Math.min(1.0F, this.swim + 0.09F) : Math.max(0.0F, this.swim - 0.09F);
            this.glideTicks = gliding ? this.glideTicks + 1 : 0;
         }
      } else {
         this.lastTick = gameTime;
      }
   }

   public float swimAmount(float partialTick) {
      return this.swimO + (this.swim - this.swimO) * partialTick;
   }

   public float glideScale(float partialTick) {
      if (this.glideTicks == 0) {
         return 0.0F;
      }

      float ticks = this.glideTicks + partialTick;
      return Math.clamp(ticks * ticks / 100.0F, 0.0F, 1.0F);
   }

   public float glidingSpeedValue(double speedSquared) {
      if (this.glideTicks == 0) {
         return 1.0F;
      }

      float value = (float)(speedSquared / 0.2);
      return Math.max(1.0F, value * value * value);
   }
}
