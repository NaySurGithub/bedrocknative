package net.bedrocknative.particle;

import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.VariableStore;

final class ParticleVariables {
   private static final int PARTICLE_AGE = MolangNames.id("particle_age");
   private static final int PARTICLE_AGE_OLD = MolangNames.id("particleage");
   private static final int PARTICLE_LIFETIME = MolangNames.id("particle_lifetime");
   private static final int PARTICLE_LIFETIME_OLD = MolangNames.id("particlelifetime");
   private static final int[] PARTICLE_RANDOM = new int[]{
      MolangNames.id("particle_random_1"), MolangNames.id("particle_random_2"), MolangNames.id("particle_random_3"), MolangNames.id("particle_random_4")
   };
   private static final int[] PARTICLE_RANDOM_OLD = new int[]{
      MolangNames.id("particlerandom1"), MolangNames.id("particlerandom2"), MolangNames.id("particlerandom3"), MolangNames.id("particlerandom4")
   };
   private static final int EMITTER_AGE = MolangNames.id("emitter_age");
   private static final int EMITTER_AGE_OLD = MolangNames.id("emitterage");
   private static final int EMITTER_LIFETIME = MolangNames.id("emitter_lifetime");
   private static final int EMITTER_LIFETIME_OLD = MolangNames.id("emitterlifetime");
   private static final int[] EMITTER_RANDOM = new int[]{
      MolangNames.id("emitter_random_1"), MolangNames.id("emitter_random_2"), MolangNames.id("emitter_random_3"), MolangNames.id("emitter_random_4")
   };
   private static final int[] EMITTER_RANDOM_OLD = new int[]{
      MolangNames.id("emitterrandom1"), MolangNames.id("emitterrandom2"), MolangNames.id("emitterrandom3"), MolangNames.id("emitterrandom4")
   };
   private static final int ENTITY_SCALE = MolangNames.id("entity_scale");

   private ParticleVariables() {
   }

   static void particle(VariableStore store, ParticleEffect.Builtins builtins, float age, float lifetime, float r1, float r2, float r3, float r4) {
      if (builtins.particleAge()) {
         store.set(PARTICLE_AGE, age, null);
         store.set(PARTICLE_AGE_OLD, age, null);
      }

      if (builtins.particleLifetime()) {
         float finite = Float.isInfinite(lifetime) ? 0.0F : lifetime;
         store.set(PARTICLE_LIFETIME, finite, null);
         store.set(PARTICLE_LIFETIME_OLD, finite, null);
      }

      if (builtins.particleRandom()) {
         set(store, PARTICLE_RANDOM, PARTICLE_RANDOM_OLD, r1, r2, r3, r4);
      }
   }

   static void emitter(VariableStore store, ParticleEffect.Builtins builtins, float age, float lifetime, float r1, float r2, float r3, float r4, float scale) {
      if (builtins.emitterAge()) {
         store.set(EMITTER_AGE, age, null);
         store.set(EMITTER_AGE_OLD, age, null);
      }

      if (builtins.emitterLifetime()) {
         store.set(EMITTER_LIFETIME, lifetime, null);
         store.set(EMITTER_LIFETIME_OLD, lifetime, null);
      }

      if (builtins.emitterRandom()) {
         set(store, EMITTER_RANDOM, EMITTER_RANDOM_OLD, r1, r2, r3, r4);
      }

      if (builtins.entityScale()) {
         store.set(ENTITY_SCALE, scale, null);
      }
   }

   private static void set(VariableStore store, int[] ids, int[] old, float r1, float r2, float r3, float r4) {
      store.set(ids[0], r1, null);
      store.set(ids[1], r2, null);
      store.set(ids[2], r3, null);
      store.set(ids[3], r4, null);
      store.set(old[0], r1, null);
      store.set(old[1], r2, null);
      store.set(old[2], r3, null);
      store.set(old[3], r4, null);
   }
}
