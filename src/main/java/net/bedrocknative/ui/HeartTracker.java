package net.bedrocknative.ui;

public final class HeartTracker {
   private int lastHealth;
   private int displayHealth;
   private long lastHealthTime;
   private long blinkUntil;
   private boolean blink;

   public void update(int tick, long millis, int health, boolean damageCooldown) {
      this.blink = this.blinkUntil > tick && (this.blinkUntil - tick) / 3L % 2L == 1L;
      if (health < this.lastHealth && damageCooldown) {
         this.lastHealthTime = millis;
         this.blinkUntil = tick + 20;
      } else if (health > this.lastHealth && damageCooldown) {
         this.lastHealthTime = millis;
         this.blinkUntil = tick + 10;
      }

      if (millis - this.lastHealthTime > 1000L) {
         this.displayHealth = health;
         this.lastHealthTime = millis;
      }

      this.lastHealth = health;
   }

   public boolean blink() {
      return this.blink;
   }

   public int displayHealth() {
      return this.displayHealth;
   }

   public void reset() {
      this.lastHealth = 0;
      this.displayHealth = 0;
      this.lastHealthTime = 0L;
      this.blinkUntil = 0L;
      this.blink = false;
   }
}
