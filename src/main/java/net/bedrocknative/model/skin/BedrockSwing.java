package net.bedrocknative.model.skin;

public final class BedrockSwing {
   public static final int DURATION = 6;
   static final int MAX_CATCH_UP = 8;
   private boolean swinging;
   private int swingTime;
   private float attackO;
   private float attack;
   private long lastTick = Long.MIN_VALUE;

   public boolean swing(long gameTime) {
      this.catchUp(gameTime);
      if (this.swinging && this.swingTime >= 0 && this.swingTime < 3) {
         return false;
      } else {
         this.swingTime = -1;
         this.swinging = true;
         return true;
      }
   }

   public void catchUp(long gameTime) {
      if (this.lastTick != Long.MIN_VALUE && gameTime >= this.lastTick) {
         long run = Math.min(gameTime - this.lastTick, 8L);
         this.lastTick = gameTime;

         for (long i = 0L; i < run; i++) {
            this.tick();
         }
      } else {
         this.lastTick = gameTime;
      }
   }

   private void tick() {
      this.attackO = this.attack;
      if (this.swinging) {
         this.swingTime++;
         if (this.swingTime >= 6) {
            this.swingTime = 0;
            this.swinging = false;
         }
      } else {
         this.swingTime = 0;
      }

      this.attack = this.swingTime / 6.0F;
   }

   public float attackTime(float partialTick) {
      float delta = this.attack - this.attackO;
      if (delta < 0.0F) {
         delta++;
      }

      return this.attackO + delta * partialTick;
   }

   public boolean swinging() {
      return this.swinging || this.attack > 0.0F;
   }
}
