package net.bedrocknative.particle;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.VariableStore;
import org.jspecify.annotations.Nullable;

public final class ParticleEmitter {
   private final ParticleSystem system;
   private final ParticleEffect effect;
   private final VariableStore variables = new VariableStore();
   final List<ParticleInstance> particles = new ArrayList<>();
   private final boolean manual;
   @Nullable
   private final ParticleAnchor anchor;
   double x;
   double y;
   double z;
   double prevX;
   double prevY;
   double prevZ;
   private float vx;
   private float vy;
   private float vz;
   private float yaw;
   private float prevYaw;
   private float scale = 1.0F;
   private float age;
   private float loopLifetime;
   private float sleepTime;
   private float sleepLeft;
   private float random1;
   private float random2;
   private float random3;
   private float random4;
   private boolean started;
   private boolean sleeping;
   private boolean expired;
   private boolean instantPending;
   private boolean emittedInstant;
   private float spawnAccumulator;
   private int maxParticles = 50;
   private int timelineIndex;
   private double travelled;
   private final float[] nextDistance;
   static final double MAX_SWEEP = 64.0;
   static final double MAX_SWEEP_RADIUS = 4.0;

   ParticleEmitter(ParticleSystem system, ParticleEffect effect, double x, double y, double z, @Nullable ParticleAnchor anchor, boolean manual) {
      this.system = system;
      this.effect = effect;
      this.anchor = anchor;
      this.manual = manual;
      this.x = this.prevX = x;
      this.y = this.prevY = y;
      this.z = this.prevZ = z;
      List<ParticleEffect.DistanceEvent> distances = effect.emitter().distanceEvents();
      this.nextDistance = new float[distances.size()];

      for (int i = 0; i < this.nextDistance.length; i++) {
         this.nextDistance[i] = distances.get(i).distance();
      }

      if (anchor != null && anchor.valid()) {
         this.follow();
         this.prevX = this.x;
         this.prevY = this.y;
         this.prevZ = this.z;
         this.prevYaw = this.yaw;
      }
   }

   public ParticleEffect effect() {
      return this.effect;
   }

   public VariableStore variables() {
      return this.variables;
   }

   public boolean expired() {
      return this.expired;
   }

   public int particleCount() {
      return this.particles.size();
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   boolean anchored() {
      return this.anchor != null || this.manual;
   }

   public void stop(boolean kill) {
      this.expireNow();
      if (kill) {
         this.killAll();
      }
   }

   MolangActor actor() {
      return this.anchor != null ? this.anchor.actor() : MolangActor.NONE;
   }

   private void follow() {
      ParticleAnchor current = this.anchor;
      if (current != null) {
         double[] position = this.system.anchorScratch;
         current.position(position);
         this.x = position[0];
         this.y = position[1];
         this.z = position[2];
         this.yaw = current.yaw();
         this.scale = current.scale();
      }
   }

   private boolean localPosition() {
      return this.effect.emitter().localPosition() && this.anchor != null;
   }

   private boolean localRotation() {
      return this.effect.emitter().localRotation() && this.anchor != null;
   }

   boolean tick(float dt) {
      this.prevX = this.x;
      this.prevY = this.y;
      this.prevZ = this.z;
      this.prevYaw = this.yaw;
      if (this.anchor != null) {
         if (!this.anchor.valid()) {
            this.killAll();
            return false;
         }

         this.follow();
      }

      this.vx = (float)((this.x - this.prevX) / dt);
      this.vy = (float)((this.y - this.prevY) / dt);
      this.vz = (float)((this.z - this.prevZ) / dt);
      MolangEnv env = this.system.env;
      env.actor = this.actor();
      env.variables = this.variables;
      if (!this.started) {
         this.start(env);
      }

      ParticleEffect.Emitter components = this.effect.emitter();
      this.builtins();
      if (components.perUpdate() != null) {
         components.perUpdate().evaluate(env);
      }

      boolean expireAfter = false;
      boolean active = false;
      if (!this.expired && !this.manual) {
         switch (components.lifetime()) {
            case EXPRESSION:
               active = components.activation().evaluate(env) != 0.0F;
               expireAfter = components.expiration().evaluate(env) != 0.0F;
               break;
            case ONCE:
               if (this.age >= this.loopLifetime && !this.instantPending) {
                  this.expireNow();
               } else {
                  active = true;
               }
               break;
            case LOOPING:
               if (this.sleeping) {
                  this.sleepLeft -= dt;
                  if (this.sleepLeft <= 0.0F) {
                     this.startLoop(env);
                  }
               } else if (this.age >= this.loopLifetime && !this.instantPending) {
                  if (this.sleepTime > 0.0F) {
                     this.sleeping = true;
                     this.sleepLeft = this.sleepTime;
                  } else {
                     this.startLoop(env);
                  }
               }

               active = !this.sleeping;
         }
      }

      if (active && !this.expired) {
         this.emit(env, dt);
      }

      this.updateParticles(env, dt);
      env.variables = this.variables;
      env.actor = this.actor();
      if (!this.expired) {
         this.timeline();
         this.travel();
      }

      if (expireAfter) {
         this.expireNow();
      }

      if (!this.sleeping) {
         this.age += dt;
      }

      if (this.expired) {
         return !this.particles.isEmpty();
      } else {
         if (this.anchor == null && this.particles.isEmpty() && !this.manual) {
            ParticleEffect.Rate rate = components.rate();
            if (rate == ParticleEffect.Rate.NONE
               || rate == ParticleEffect.Rate.MANUAL
               || rate == ParticleEffect.Rate.INSTANT && this.emittedInstant && components.lifetime() != ParticleEffect.Lifetime.LOOPING) {
               return false;
            }
         }

         return true;
      }
   }

   private void start(MolangEnv env) {
      this.started = true;
      ParticleEffect.Emitter components = this.effect.emitter();
      this.rollRandoms();
      this.builtins();
      if (components.creation() != null) {
         components.creation().evaluate(env);
      }

      this.loop(env);
      this.fire(components.creationEvents(), this.x, this.y, this.z, null);
   }

   private void startLoop(MolangEnv env) {
      this.sleeping = false;
      this.age = 0.0F;
      this.timelineIndex = 0;
      this.rollRandoms();
      this.builtins();
      this.loop(env);
   }

   private void loop(MolangEnv env) {
      ParticleEffect.Emitter components = this.effect.emitter();
      this.loopLifetime = components.activeTime().evaluate(env);
      this.sleepTime = components.sleepTime().evaluate(env);
      this.maxParticles = Math.max(0, Math.round(components.maxParticles().evaluate(env)));
      this.instantPending = components.rate() == ParticleEffect.Rate.INSTANT;
      this.spawnAccumulator = 0.0F;
   }

   private void rollRandoms() {
      this.random1 = this.system.random.nextFloat();
      this.random2 = this.system.random.nextFloat();
      this.random3 = this.system.random.nextFloat();
      this.random4 = this.system.random.nextFloat();
   }

   private void builtins() {
      ParticleVariables.emitter(
         this.variables, this.effect.builtins(), this.age, this.loopLifetime, this.random1, this.random2, this.random3, this.random4, this.scale
      );
   }

   private void expireNow() {
      if (!this.expired) {
         this.expired = true;
         this.fire(this.effect.emitter().expirationEvents(), this.x, this.y, this.z, null);
      }
   }

   private void emit(MolangEnv env, float dt) {
      ParticleEffect.Emitter components = this.effect.emitter();
      switch (components.rate()) {
         case INSTANT:
            if (this.instantPending) {
               this.instantPending = false;
               this.emittedInstant = true;
               int count = Math.round(components.rateAmount().evaluate(env));
               int i = 0;

               while (i < count && this.emitParticle(env, this.x, this.y, this.z, 0.0F, 0.0F, 0.0F)) {
                  i++;
               }
            }
            break;
         case STEADY:
            this.spawnAccumulator = this.spawnAccumulator + Math.max(0.0F, components.rateAmount().evaluate(env)) * dt;
            this.spawnAccumulator = Math.min(this.spawnAccumulator, (float)Math.max(1, this.maxParticles));

            while (this.spawnAccumulator >= 1.0F) {
               this.spawnAccumulator--;
               if (this.particles.size() >= this.maxParticles || !this.emitParticle(env, this.x, this.y, this.z, 0.0F, 0.0F, 0.0F)) {
                  break;
               }
            }
      }
   }

   boolean emitManual(double px, double py, double pz, float pvx, float pvy, float pvz) {
      MolangEnv env = this.system.env;
      env.actor = this.actor();
      env.variables = this.variables;
      if (!this.started) {
         this.start(env);
      }

      if (this.particles.size() >= Math.max(1, this.maxParticles)) {
         return false;
      } else {
         this.builtins();
         return this.emitParticle(env, px, py, pz, pvx, pvy, pvz);
      }
   }

   private boolean emitParticle(MolangEnv env, double originX, double originY, double originZ, float extraX, float extraY, float extraZ) {
      ParticleInstance particle = this.system.obtain();
      if (particle == null) {
         return false;
      } else {
         ParticleEffect.Particle components = this.effect.particle();
         ParticleEffect.Emitter emitter = this.effect.emitter();
         particle.random1 = this.system.random.nextFloat();
         particle.random2 = this.system.random.nextFloat();
         particle.random3 = this.system.random.nextFloat();
         particle.random4 = this.system.random.nextFloat();
         VariableStore scope = this.scope(particle);
         env.variables = scope;
         ParticleVariables.particle(scope, this.effect.builtins(), 0.0F, 0.0F, particle.random1, particle.random2, particle.random3, particle.random4);
         particle.lifetime = components.maxLifetime() != null ? components.maxLifetime().evaluate(env) : Float.POSITIVE_INFINITY;
         ParticleVariables.particle(
            scope, this.effect.builtins(), 0.0F, particle.lifetime, particle.random1, particle.random2, particle.random3, particle.random4
         );
         float[] shape = this.system.shape;
         ParticleShapes.sample(this.effect, env, this.system.random, this.anchor, shape, this.system.anchorScratch);
         float ox = shape[0];
         float oy = shape[1];
         float oz = shape[2];
         float dx = shape[3];
         float dy = shape[4];
         float dz = shape[5];
         boolean turn = this.anchor != null && this.yaw != 0.0F && !this.localPosition();
         if (turn) {
            float radians = (float)Math.toRadians(this.yaw);
            float cos = (float)Math.cos(radians);
            float sin = (float)Math.sin(radians);
            float rx = ox * cos - oz * sin;
            oz = ox * sin + oz * cos;
            ox = rx;
            float rdx = dx * cos - dz * sin;
            dz = dx * sin + dz * cos;
            dx = rdx;
         }

         if (this.localPosition()) {
            particle.x = ox;
            particle.y = oy;
            particle.z = oz;
         } else {
            particle.x = originX + ox;
            particle.y = originY + oy;
            particle.z = originZ + oz;
         }

         particle.prevX = particle.x;
         particle.prevY = particle.y;
         particle.prevZ = particle.z;
         if (components.speedVector() != null) {
            particle.vx = components.speedVector()[0].evaluate(env);
            particle.vy = components.speedVector()[1].evaluate(env);
            particle.vz = components.speedVector()[2].evaluate(env);
         } else if (components.speed() != null) {
            float speed = components.speed().evaluate(env);
            particle.vx = dx * speed;
            particle.vy = dy * speed;
            particle.vz = dz * speed;
         }

         if (emitter.localVelocity()) {
            particle.vx = particle.vx + this.vx;
            particle.vy = particle.vy + this.vy;
            particle.vz = particle.vz + this.vz;
         }

         particle.vx += extraX;
         particle.vy += extraY;
         particle.vz += extraZ;
         particle.dirX = dx;
         particle.dirY = dy;
         particle.dirZ = dz;
         float speed = (float)Math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy + particle.vz * particle.vz);
         if (speed > 1.0E-6F) {
            particle.dirX = particle.vx / speed;
            particle.dirY = particle.vy / speed;
            particle.dirZ = particle.vz / speed;
         }

         particle.rotation = particle.prevRotation = components.spinRotation().evaluate(env);
         particle.rotationRate = components.spinRate().evaluate(env);
         if (components.creation() != null) {
            components.creation().evaluate(env);
         }

         if (components.killPlane() != null) {
            particle.killSide = Math.signum(this.killPlane(particle, components.killPlane()));
         }

         this.particles.add(particle);
         env.variables = this.variables;
         if (components.creationEvents().length > 0) {
            double[] point = this.world(particle, 1.0F);
            this.fire(components.creationEvents(), point[0], point[1], point[2], particle);
         }

         return true;
      }
   }

   private VariableStore scope(ParticleInstance particle) {
      if (!this.effect.builtins().particleScope()) {
         return this.variables;
      } else {
         if (particle.variables == null) {
            particle.variables = new VariableStore(8);
         }

         particle.variables.setParent(this.variables);
         return particle.variables;
      }
   }

   private void updateParticles(MolangEnv env, float dt) {
      for (int i = 0; i < this.particles.size(); i++) {
         ParticleInstance particle = this.particles.get(i);
         if (!this.updateParticle(env, particle, dt)) {
            int last = this.particles.size() - 1;
            this.particles.set(i, this.particles.get(last));
            this.particles.remove(last);
            this.system.release(particle);
            i--;
         }
      }
   }

   private boolean updateParticle(MolangEnv env, ParticleInstance particle, float dt) {
      ParticleEffect.Particle components = this.effect.particle();
      particle.prevX = particle.x;
      particle.prevY = particle.y;
      particle.prevZ = particle.z;
      particle.prevRotation = particle.rotation;
      particle.age += dt;
      VariableStore scope = this.scope(particle);
      env.variables = scope;
      ParticleVariables.particle(
         scope, this.effect.builtins(), particle.age, particle.lifetime, particle.random1, particle.random2, particle.random3, particle.random4
      );
      if (particle.age >= particle.lifetime) {
         return this.die(particle);
      } else {
         this.curves(env);
         if (components.perUpdate() != null) {
            components.perUpdate().evaluate(env);
         }

         if (components.expiration() != null && components.expiration().evaluate(env) != 0.0F) {
            return this.die(particle);
         } else {
            if (components.parametricPosition() != null) {
               MolangProgram[] position = components.parametricPosition();
               float px = position[0].evaluate(env);
               float py = position[1].evaluate(env);
               float pz = position[2].evaluate(env);
               if (this.localPosition()) {
                  particle.x = px;
                  particle.y = py;
                  particle.z = pz;
               } else {
                  particle.x = this.x + px;
                  particle.y = this.y + py;
                  particle.z = this.z + pz;
               }
            } else {
               MolangProgram[] acceleration = components.acceleration();
               float drag = components.drag().evaluate(env);
               particle.vx = particle.vx + (acceleration[0].evaluate(env) - drag * particle.vx) * dt;
               particle.vy = particle.vy + (acceleration[1].evaluate(env) - drag * particle.vy) * dt;
               particle.vz = particle.vz + (acceleration[2].evaluate(env) - drag * particle.vz) * dt;
               double[] motion = this.system.motion;
               motion[0] = particle.vx * dt;
               motion[1] = particle.vy * dt;
               motion[2] = particle.vz * dt;
               ParticleEffect.Collision collision = components.collision();
               if (collision != null && collision.enabled().evaluate(env) != 0.0F && !this.collide(particle, collision, motion, dt)) {
                  return this.die(particle);
               }

               particle.x = particle.x + motion[0];
               particle.y = particle.y + motion[1];
               particle.z = particle.z + motion[2];
            }

            float rotationDrag = components.rotationDrag().evaluate(env);
            particle.rotationRate = particle.rotationRate + (components.rotationAcceleration().evaluate(env) - rotationDrag * particle.rotationRate) * dt;
            particle.rotation = particle.rotation + particle.rotationRate * dt;
            if (components.parametricRotation() != null) {
               particle.rotation = components.parametricRotation().evaluate(env);
            }

            if (components.parametricDirection() != null) {
               particle.dirX = components.parametricDirection()[0].evaluate(env);
               particle.dirY = components.parametricDirection()[1].evaluate(env);
               particle.dirZ = components.parametricDirection()[2].evaluate(env);
            } else {
               ParticleEffect.Billboard billboard = components.billboard();
               float threshold = billboard != null ? billboard.minSpeed() : 0.01F;
               float speed = (float)Math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy + particle.vz * particle.vz);
               if (speed > threshold && speed > 1.0E-6F) {
                  particle.dirX = particle.vx / speed;
                  particle.dirY = particle.vy / speed;
                  particle.dirZ = particle.vz / speed;
               }
            }

            if (components.killPlane() != null) {
               float side = Math.signum(this.killPlane(particle, components.killPlane()));
               if (particle.killSide != 0.0F && side != 0.0F && side != particle.killSide) {
                  return this.die(particle);
               }
            }

            if (!components.expireInBlocks().isEmpty() || !components.expireNotInBlocks().isEmpty()) {
               double[] point = this.world(particle, 1.0F);
               String block = this.system.world().blockName(point[0], point[1], point[2]);
               if (components.expireInBlocks().contains(block)) {
                  return this.die(particle);
               }

               if (!components.expireNotInBlocks().isEmpty() && !components.expireNotInBlocks().contains(block)) {
                  return this.die(particle);
               }
            }

            for (ParticleEffect.Timeline timeline = components.timeline();
               particle.timelineIndex < timeline.times().length && timeline.times()[particle.timelineIndex] <= particle.age;
               env.variables = this.scope(particle)
            ) {
               double[] pointx = this.world(particle, 1.0F);
               this.fire(timeline.names()[particle.timelineIndex++], pointx[0], pointx[1], pointx[2], particle);
            }

            return true;
         }
      }
   }

   private boolean collide(ParticleInstance particle, ParticleEffect.Collision collision, double[] motion, float dt) {
      double before = Math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy + particle.vz * particle.vz);
      double[] point = this.world(particle, 1.0F);

      for (int axis = 0; axis < 3; axis++) {
         double wanted = motion[axis];
         motion[axis] = wanted != wanted ? 0.0 : Math.max(-64.0, Math.min(64.0, wanted));
      }

      double radius = Math.min(4.0, (double)Math.max(0.005F, collision.radius()));
      int hit = this.system.world().collide(point[0], point[1], point[2], radius, motion);
      if (hit == 0) {
         return true;
      } else if (collision.expireOnContact()) {
         this.fireCollision(particle, collision, before);
         return false;
      } else {
         float restitution = collision.restitution();
         if ((hit & 1) != 0) {
            particle.vx = -particle.vx * restitution;
         }

         if ((hit & 2) != 0) {
            particle.vy = -particle.vy * restitution;
         }

         if ((hit & 4) != 0) {
            particle.vz = -particle.vz * restitution;
         }

         if (collision.drag() > 0.0F) {
            float speed = (float)Math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy + particle.vz * particle.vz);
            if (speed > 0.0F) {
               float slowed = Math.max(0.0F, speed - collision.drag() * dt) / speed;
               particle.vx *= slowed;
               particle.vy *= slowed;
               particle.vz *= slowed;
            }
         }

         this.fireCollision(particle, collision, before);
         return true;
      }
   }

   private void fireCollision(ParticleInstance particle, ParticleEffect.Collision collision, double speed) {
      List<ParticleEffect.CollisionEvent> events = collision.events();

      for (int i = 0; i < events.size(); i++) {
         ParticleEffect.CollisionEvent event = events.get(i);
         if (speed >= event.minSpeed()) {
            double[] point = this.world(particle, 1.0F);
            this.fire(event.event(), point[0], point[1], point[2], particle);
         }
      }
   }

   private boolean die(ParticleInstance particle) {
      String[] expiration = this.effect.particle().expirationEvents();
      if (expiration.length > 0) {
         double[] point = this.world(particle, 1.0F);
         this.fire(expiration, point[0], point[1], point[2], particle);
      }

      return false;
   }

   private float killPlane(ParticleInstance particle, float[] plane) {
      double px = this.localPosition() ? particle.x : particle.x - this.x;
      double py = this.localPosition() ? particle.y : particle.y - this.y;
      double pz = this.localPosition() ? particle.z : particle.z - this.z;
      return (float)(plane[0] * px + plane[1] * py + plane[2] * pz + plane[3]);
   }

   void curves(MolangEnv env) {
      List<ParticleCurve> curves = this.effect.curves();

      for (int i = 0; i < curves.size(); i++) {
         curves.get(i).apply(env);
      }
   }

   private void timeline() {
      ParticleEffect.Timeline timeline = this.effect.emitter().timeline();

      while (this.timelineIndex < timeline.times().length && timeline.times()[this.timelineIndex] <= this.age) {
         this.fire(timeline.names()[this.timelineIndex++], this.x, this.y, this.z, null);
      }
   }

   private void travel() {
      List<ParticleEffect.DistanceEvent> events = this.effect.emitter().distanceEvents();
      if (!events.isEmpty()) {
         double dx = this.x - this.prevX;
         double dy = this.y - this.prevY;
         double dz = this.z - this.prevZ;
         this.travelled = this.travelled + Math.sqrt(dx * dx + dy * dy + dz * dz);

         for (int i = 0; i < events.size(); i++) {
            for (ParticleEffect.DistanceEvent event = events.get(i);
               this.nextDistance[i] >= 0.0F && this.travelled >= this.nextDistance[i];
               this.nextDistance[i] = event.looping() ? this.nextDistance[i] + event.distance() : -1.0F
            ) {
               this.fire(event.names(), this.x, this.y, this.z, null);
            }
         }
      }
   }

   private void fire(String[] names, double px, double py, double pz, @Nullable ParticleInstance from) {
      for (String name : names) {
         this.fire(name, px, py, pz, from);
      }
   }

   private void fire(String name, double px, double py, double pz, @Nullable ParticleInstance from) {
      ParticleEvent event = this.effect.events().get(name);
      if (event != null) {
         this.run(event, px, py, pz, from, 0);
      }
   }

   private void run(ParticleEvent event, double px, double py, double pz, @Nullable ParticleInstance from, int depth) {
      if (depth <= 16) {
         ParticleEvent.Effect spawn = event.effect();
         if (spawn != null) {
            this.spawnEffect(spawn, px, py, pz, from);
         }

         if (event.sound() != null) {
            this.system.world().playSound(event.sound(), px, py, pz, this.effect);
         }

         if (event.expression() != null) {
            MolangEnv env = this.system.env;
            VariableStore saved = env.variables;
            MolangActor savedActor = env.actor;
            env.variables = this.variables;
            env.actor = this.actor();
            event.expression().evaluate(env);
            env.variables = saved;
            env.actor = savedActor;
         }

         if (event.log() != null) {
            this.system.world().log(this.effect, event.log(), px, py, pz);
         }

         List<ParticleEvent> sequence = event.sequence();

         for (int i = 0; i < sequence.size(); i++) {
            this.run(sequence.get(i), px, py, pz, from, depth + 1);
         }

         List<ParticleEvent.Weighted> randomize = event.randomize();
         if (!randomize.isEmpty()) {
            float total = 0.0F;

            for (int i = 0; i < randomize.size(); i++) {
               total += Math.max(0.0F, randomize.get(i).weight());
            }

            float roll = this.system.random.nextFloat() * total;

            for (int i = 0; i < randomize.size(); i++) {
               roll -= Math.max(0.0F, randomize.get(i).weight());
               if (roll < 0.0F || i == randomize.size() - 1) {
                  this.run(randomize.get(i).event(), px, py, pz, from, depth + 1);
                  break;
               }
            }
         }
      }
   }

   private void spawnEffect(ParticleEvent.Effect spawn, double px, double py, double pz, @Nullable ParticleInstance from) {
      ParticleEffect target = this.system.world().effect(spawn.identifier());
      if (target != null) {
         MolangEnv env = this.system.env;
         VariableStore savedVariables = env.variables;
         MolangActor savedActor = env.actor;

         try {
            switch (spawn.type()) {
               case EMITTER:
                  this.system.spawn(target, px, py, pz, null, spawn.preEffectExpression(), null);
                  break;
               case EMITTER_BOUND:
                  this.system.spawn(target, px, py, pz, this.anchor, spawn.preEffectExpression(), null);
                  break;
               case PARTICLE:
                  this.system.emitManual(target, px, py, pz, 0.0F, 0.0F, 0.0F);
                  break;
               case PARTICLE_WITH_VELOCITY:
                  this.system.emitManual(target, px, py, pz, from != null ? from.vx : 0.0F, from != null ? from.vy : 0.0F, from != null ? from.vz : 0.0F);
            }
         } finally {
            env.variables = savedVariables;
            env.actor = savedActor;
         }
      }
   }

   void discard() {
      this.killAll();
   }

   private void killAll() {
      for (int i = 0; i < this.particles.size(); i++) {
         this.system.release(this.particles.get(i));
      }

      this.particles.clear();
      this.expired = true;
   }

   double[] world(ParticleInstance particle, float partialTick) {
      double[] point = this.system.point;
      double lx = particle.prevX + (particle.x - particle.prevX) * partialTick;
      double ly = particle.prevY + (particle.y - particle.prevY) * partialTick;
      double lz = particle.prevZ + (particle.z - particle.prevZ) * partialTick;
      if (!this.localPosition()) {
         point[0] = lx;
         point[1] = ly;
         point[2] = lz;
         return point;
      } else {
         if (this.localRotation()) {
            float turn = (float)Math.toRadians(this.prevYaw + (this.yaw - this.prevYaw) * partialTick);
            float cos = (float)Math.cos(turn);
            float sin = (float)Math.sin(turn);
            double rx = lx * cos - lz * sin;
            lz = lx * sin + lz * cos;
            lx = rx;
         }

         point[0] = this.prevX + (this.x - this.prevX) * partialTick + lx;
         point[1] = this.prevY + (this.y - this.prevY) * partialTick + ly;
         point[2] = this.prevZ + (this.z - this.prevZ) * partialTick + lz;
         return point;
      }
   }

   float yaw(float partialTick) {
      return this.prevYaw + (this.yaw - this.prevYaw) * partialTick;
   }

   VariableStore scopeFor(ParticleInstance particle) {
      return this.scope(particle);
   }

   void emitterBuiltins() {
      this.builtins();
   }
}
