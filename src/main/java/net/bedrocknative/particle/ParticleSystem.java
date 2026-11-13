package net.bedrocknative.particle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.VariableStore;
import org.jspecify.annotations.Nullable;

public final class ParticleSystem {
   public static final float TICK_SECONDS = 0.05F;
   private static final double FORGET_DISTANCE = 192.0;
   private static final int MAX_EMIT_DEPTH = 8;
   private final ParticleWorld world;
   private final int maxParticles;
   private final int maxEmitters;
   final MolangEnv env = new MolangEnv();
   final SplittableRandom random = new SplittableRandom();
   final double[] anchorScratch = new double[6];
   final double[] motion = new double[3];
   final double[] point = new double[3];
   final float[] shape = new float[6];
   private final List<ParticleEmitter> emitters = new ArrayList<>();
   private final List<ParticleEmitter> added = new ArrayList<>();
   private final Map<ParticleEffect, ParticleEmitter> manual = new IdentityHashMap<>();
   private final ArrayDeque<ParticleInstance> pool = new ArrayDeque<>();
   private int live;
   private int dropped;
   private int spawned;
   private boolean ticking;
   private int emitDepth;

   public ParticleSystem(ParticleWorld world, int maxParticles, int maxEmitters) {
      this.world = world;
      this.maxParticles = maxParticles;
      this.maxEmitters = maxEmitters;
   }

   ParticleWorld world() {
      return this.world;
   }

   @Nullable
   public ParticleEmitter spawn(
      ParticleEffect effect,
      double x,
      double y,
      double z,
      @Nullable ParticleAnchor anchor,
      @Nullable MolangProgram preEffect,
      @Nullable Consumer<VariableStore> setup
   ) {
      if (this.emitters.size() + this.added.size() >= this.maxEmitters) {
         this.dropped++;
         return null;
      } else {
         ParticleEmitter emitter = new ParticleEmitter(this, effect, x, y, z, anchor, false);
         if (preEffect != null) {
            this.env.variables = emitter.variables();
            this.env.actor = emitter.actor();
            preEffect.evaluate(this.env);
         }

         if (setup != null) {
            setup.accept(emitter.variables());
         }

         this.spawned++;
         if (this.ticking) {
            this.added.add(emitter);
         } else {
            this.emitters.add(emitter);
         }

         return emitter;
      }
   }

   public boolean emitManual(ParticleEffect effect, double x, double y, double z, float vx, float vy, float vz) {
      if (this.emitDepth >= 8) {
         this.dropped++;
         return false;
      } else {
         ParticleEmitter emitter = this.manual.get(effect);
         if (emitter == null) {
            if (this.emitters.size() + this.added.size() >= this.maxEmitters) {
               this.dropped++;
               return false;
            }

            emitter = new ParticleEmitter(this, effect, x, y, z, null, true);
            this.manual.put(effect, emitter);
            if (this.ticking) {
               this.added.add(emitter);
            } else {
               this.emitters.add(emitter);
            }
         }

         this.emitDepth++;

         boolean var12;
         try {
            var12 = emitter.emitManual(x, y, z, vx, vy, vz);
         } finally {
            this.emitDepth--;
         }

         return var12;
      }
   }

   public void tick(double viewerX, double viewerY, double viewerZ) {
      this.ticking = true;

      try {
         for (int i = 0; i < this.emitters.size(); i++) {
            ParticleEmitter emitter = this.emitters.get(i);
            boolean far = !emitter.anchored() && distanceSquared(emitter, viewerX, viewerY, viewerZ) > 36864.0;
            if (far || !emitter.tick(0.05F)) {
               if (far) {
                  emitter.discard();
               }

               this.manual.remove(emitter.effect(), emitter);
               int last = this.emitters.size() - 1;
               this.emitters.set(i, this.emitters.get(last));
               this.emitters.remove(last);
               i--;
            }
         }
      } finally {
         this.ticking = false;
      }

      if (!this.added.isEmpty()) {
         this.emitters.addAll(this.added);
         this.added.clear();
      }
   }

   private static double distanceSquared(ParticleEmitter emitter, double x, double y, double z) {
      double dx = emitter.x - x;
      double dy = emitter.y - y;
      double dz = emitter.z - z;
      return dx * dx + dy * dy + dz * dz;
   }

   public void render(ParticleCamera camera, float partialTick, QuadSink sink) {
      for (int e = 0; e < this.emitters.size(); e++) {
         ParticleEmitter emitter = this.emitters.get(e);
         if (!emitter.particles.isEmpty()) {
            ParticleEffect effect = emitter.effect();
            ParticleEffect.Billboard billboard = effect.particle().billboard();
            if (billboard != null && sink.begin(effect)) {
               this.env.actor = emitter.actor();
               this.env.variables = emitter.variables();
               emitter.emitterBuiltins();
               float yaw = emitter.yaw(partialTick);

               for (int i = 0; i < emitter.particles.size(); i++) {
                  ParticleInstance particle = emitter.particles.get(i);
                  double[] world = emitter.world(particle, partialTick);
                  double wx = world[0];
                  double wy = world[1];
                  double wz = world[2];
                  if (sink.visible(wx, wy, wz)) {
                     VariableStore scope = emitter.scopeFor(particle);
                     this.env.variables = scope;
                     float age = Math.max(0.0F, particle.age - (1.0F - partialTick) * 0.05F);
                     if (age > particle.lifetime) {
                        age = particle.lifetime;
                     }

                     ParticleVariables.particle(
                        scope, effect.builtins(), age, particle.lifetime, particle.random1, particle.random2, particle.random3, particle.random4
                     );
                     emitter.curves(this.env);
                     if (effect.particle().perRender() != null) {
                        effect.particle().perRender().evaluate(this.env);
                     }

                     ParticleBillboards.draw(effect, billboard, particle, this.env, camera, wx, wy, wz, age, yaw, partialTick, sink);
                  }
               }
            }
         }
      }
   }

   @Nullable
   ParticleInstance obtain() {
      if (this.live >= this.maxParticles) {
         this.dropped++;
         return null;
      } else {
         this.live++;
         ParticleInstance particle = this.pool.poll();
         if (particle == null) {
            particle = new ParticleInstance();
         }

         particle.reset();
         return particle;
      }
   }

   void release(ParticleInstance particle) {
      this.live--;
      this.pool.push(particle);
   }

   public void clear() {
      for (int i = 0; i < this.emitters.size(); i++) {
         this.emitters.get(i).discard();
      }

      for (int i = 0; i < this.added.size(); i++) {
         this.added.get(i).discard();
      }

      this.emitters.clear();
      this.added.clear();
      this.manual.clear();
   }

   public int particleCount() {
      return this.live;
   }

   public int emitterCount() {
      return this.emitters.size();
   }

   public int particleCount(String identifier) {
      int count = 0;

      for (int i = 0; i < this.emitters.size(); i++) {
         if (this.emitters.get(i).effect().identifier().equals(identifier)) {
            count += this.emitters.get(i).particleCount();
         }
      }

      return count;
   }

   public int droppedCount() {
      return this.dropped;
   }

   public int spawnedCount() {
      return this.spawned;
   }
}
