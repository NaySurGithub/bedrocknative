package net.bedrocknative.movement;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class EntityPick {
   public static final double MARGIN = 0.1;
   public static final double CREATIVE_REACH = 5.0;

   private EntityPick() {
   }

   public static double reach(double javaRange, boolean creative) {
      return creative ? Math.max(javaRange, 5.0) : javaRange;
   }

   public static double margin(double javaPickRadius) {
      return Math.max(0.1, javaPickRadius);
   }

   public static @Nullable Vec3 hit(AABB box, Vec3 eye, Vec3 look, double reach) {
      EntityPick.Choice choice = choose(List.of(new EntityPick.Candidate(box, 0.0, false, false)), eye, eye.add(look.scale(reach)), reach * reach);
      return choice == null ? null : choice.point();
   }

   public static EntityPick.@Nullable Choice choose(List<EntityPick.Candidate> candidates, Vec3 from, Vec3 to, double maxDistanceSqr) {
      int best = -1;
      Vec3 bestPoint = null;
      double bestDistance = maxDistanceSqr;
      int band = -1;
      double bandCentre = Double.MAX_VALUE;
      int inside = -1;
      double insideCentre = Double.MAX_VALUE;

      for (int i = 0; i < candidates.size(); i++) {
         EntityPick.Candidate c = candidates.get(i);
         if (!c.sameVehicle()) {
            AABB own = c.box().inflate(c.pickRadius());
            if (own.contains(from)) {
               double centre = c.box().getCenter().distanceToSqr(from);
               if (c.canBePickedFromInside() && centre < insideCentre) {
                  inside = i;
                  insideCentre = centre;
               }
            } else {
               AABB grown = c.box().inflate(margin(c.pickRadius()));
               if (grown.contains(from)) {
                  double centre = c.box().getCenter().distanceToSqr(from);
                  if (centre < bandCentre) {
                     band = i;
                     bandCentre = centre;
                  }
               } else {
                  Optional<Vec3> point = grown.clip(from, to);
                  if (point.isPresent()) {
                     double distance = from.distanceToSqr(point.get());
                     if (distance < bestDistance) {
                        best = i;
                        bestPoint = point.get();
                        bestDistance = distance;
                     }
                  }
               }
            }
         }
      }

      if (best >= 0) {
         return new EntityPick.Choice(best, bestPoint);
      } else if (band >= 0 && maxDistanceSqr > 0.0) {
         return new EntityPick.Choice(band, from);
      } else {
         return inside >= 0 && maxDistanceSqr > 0.0 ? new EntityPick.Choice(inside, from) : null;
      }
   }

   public record Candidate(AABB box, double pickRadius, boolean canBePickedFromInside, boolean sameVehicle) {
   }

   public record Choice(int index, Vec3 point) {
   }
}
