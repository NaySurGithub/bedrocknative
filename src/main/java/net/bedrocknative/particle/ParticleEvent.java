package net.bedrocknative.particle;

import java.util.List;
import java.util.random.RandomGenerator;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public record ParticleEvent(
   List<ParticleEvent> sequence,
   List<ParticleEvent.Weighted> randomize,
   ParticleEvent.@Nullable Effect effect,
   @Nullable String sound,
   @Nullable MolangProgram expression,
   @Nullable String log
) {
   public static final ParticleEvent EMPTY = new ParticleEvent(List.of(), List.of(), null, null, null, null);

   public void collect(RandomGenerator random, List<ParticleEvent> out) {
      if (this.effect != null || this.sound != null || this.expression != null || this.log != null) {
         out.add(this);
      }

      for (int i = 0; i < this.sequence.size(); i++) {
         this.sequence.get(i).collect(random, out);
      }

      if (!this.randomize.isEmpty()) {
         float total = 0.0F;

         for (int i = 0; i < this.randomize.size(); i++) {
            total += Math.max(0.0F, this.randomize.get(i).weight());
         }

         float roll = total > 0.0F ? random.nextFloat() * total : 0.0F;

         for (int i = 0; i < this.randomize.size(); i++) {
            ParticleEvent.Weighted option = this.randomize.get(i);
            roll -= Math.max(0.0F, option.weight());
            if (roll < 0.0F || i == this.randomize.size() - 1) {
               option.event().collect(random, out);
               break;
            }
         }
      }
   }

   public record Effect(String identifier, ParticleEvent.EffectType type, @Nullable MolangProgram preEffectExpression) {
   }

   public enum EffectType {
      EMITTER,
      EMITTER_BOUND,
      PARTICLE,
      PARTICLE_WITH_VELOCITY;
   }

   public record Weighted(float weight, ParticleEvent event) {
   }
}
