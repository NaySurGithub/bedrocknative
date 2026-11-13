package net.bedrocknative.world.biome;

import java.util.List;

public final class BiomeClimates {
   private BiomeClimates() {
   }

   public static double distance(BiomeClimates.Climate a, BiomeClimates.Climate b) {
      return Math.hypot(a.x() - b.x(), a.y() - b.y());
   }

   public static <K> K closest(BiomeClimates.Climate wanted, List<BiomeClimates.Candidate<K>> candidates) {
      K best = null;
      double bestDistance = Double.MAX_VALUE;

      for (BiomeClimates.Candidate<K> candidate : candidates) {
         double distance = distance(wanted, candidate.climate());
         if (distance < bestDistance - 1.0E-9) {
            best = candidate.key();
            bestDistance = distance;
         }
      }

      return best;
   }

   public record Candidate<K>(K key, BiomeClimates.Climate climate) {
   }

   public record Climate(float temperature, float downfall) {
      double x() {
         return clamp(this.temperature);
      }

      double y() {
         return clamp(this.downfall) * clamp(this.temperature);
      }

      private static double clamp(float value) {
         return Math.max(0.0F, Math.min(1.0F, value));
      }
   }
}
