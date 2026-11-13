package net.bedrocknative.movement;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class BelowWorldWatch {
   public static final int REPORT_AFTER_TICKS = 100;
   static final int MAX_FALLS = 3;
   private int falls;
   private long belowSince = -1L;
   private int correctionsAtStart;
   private int teleportsAtStart;
   private boolean reported;

   public void reset() {
      this.falls = 0;
      this.belowSince = -1L;
      this.reported = false;
   }

   @Nullable
   public String tick(int minY, BelowWorldWatch.Sample sample) {
      boolean below = sample.y() < minY;
      if (!below) {
         if (this.belowSince >= 0L) {
            long ticks = sample.tick() - this.belowSince;
            this.belowSince = -1L;
            if (this.reported) {
               return String.format(
                  Locale.ROOT,
                  "Back above the world bottom after %.1f s (y %.2f): %d corrections and %d teleports from the server meanwhile",
                  ticks / 20.0,
                  sample.y(),
                  sample.corrections() - this.correctionsAtStart,
                  sample.teleports() - this.teleportsAtStart
               );
            }
         }

         return null;
      } else if (this.belowSince < 0L) {
         if (this.falls >= 3) {
            return null;
         } else {
            this.falls++;
            this.belowSince = sample.tick();
            this.correctionsAtStart = sample.corrections();
            this.teleportsAtStart = sample.teleports();
            this.reported = false;
            return String.format(
               Locale.ROOT,
               "Below the world bottom (y %d) at (%.2f, %.2f, %.2f), falling %.3f a tick, %s, health %.1f; so far %d corrections from the server%s and %d teleports; on Bedrock the server handles the void",
               minY,
               sample.x(),
               sample.y(),
               sample.z(),
               sample.dy(),
               sample.gameMode(),
               sample.health(),
               sample.corrections(),
               lastCorrected(sample),
               sample.teleports()
            );
         }
      } else if (!this.reported && sample.tick() - this.belowSince >= 100L) {
         this.reported = true;
         return String.format(
            Locale.ROOT,
            "Still below the world bottom after %.1f s at y %.2f (falling %.3f a tick, health %.1f): %d corrections%s and %d teleports from the server since the fall began",
            (sample.tick() - this.belowSince) / 20.0,
            sample.y(),
            sample.dy(),
            sample.health(),
            sample.corrections() - this.correctionsAtStart,
            lastCorrected(sample),
            sample.teleports() - this.teleportsAtStart
         );
      } else {
         return null;
      }
   }

   private static String lastCorrected(BelowWorldWatch.Sample sample) {
      return sample.lastCorrectedY() == null ? "" : String.format(Locale.ROOT, " (the last at y %.2f)", sample.lastCorrectedY());
   }

   public record Sample(
      long tick, double x, double y, double z, double dy, int corrections, int teleports, @Nullable Double lastCorrectedY, float health, String gameMode
   ) {
   }
}
