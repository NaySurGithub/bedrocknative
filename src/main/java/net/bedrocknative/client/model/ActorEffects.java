package net.bedrocknative.client.model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.bedrocknative.client.particle.EntityParticleAnchor;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.molang.EffectSink;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.particle.ParticleEffect;
import net.bedrocknative.particle.ParticleEffects;
import net.bedrocknative.particle.ParticleEmitter;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundPlanner;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class ActorEffects implements EffectSink {
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
   private final ClientEntityDef definition;
   private final Entity entity;
   private final Map<Object, List<ParticleEmitter>> byOwner = new IdentityHashMap<>();
   private final Map<ClientEntityDef, EffectSink> scoped = new IdentityHashMap<>();
   private int particles;
   private int sounds;

   ActorEffects(ClientEntityDef definition, Entity entity) {
      this.definition = definition;
      this.entity = entity;
   }

   ClientEntityDef definition() {
      return this.definition;
   }

   Entity entity() {
      return this.entity;
   }

   @Override
   public EffectSink forDefinition(ClientEntityDef other) {
      return other == this.definition ? this : this.scoped.computeIfAbsent(other, key -> new ActorEffects.Scoped(this, key));
   }

   @Override
   public void particle(String effect, @Nullable String locator, @Nullable MolangProgram preEffect, boolean bindToActor, @Nullable Object owner, MolangEnv env) {
      this.particle(this.definition, effect, locator, preEffect, bindToActor, owner);
   }

   private void particle(
      ClientEntityDef definition, String effect, @Nullable String locator, @Nullable MolangProgram preEffect, boolean bindToActor, @Nullable Object owner
   ) {
      ParticleEffects effects = BedrockParticleEngine.effects();
      String identifier = definition.particleEffect(effect);
      ParticleEffect particle = effects != null && identifier != null ? effects.get(identifier) : null;
      if (particle == null) {
         if (identifier != null && effects != null && LOGGED.add(definition.identifier() + " " + identifier)) {
            BedrockNative.LOGGER.info("Bedrock actor {}: no particle effect {} for {}", new Object[]{definition.identifier(), identifier, effect});
         }
      } else if (definition.serverPack() || particle.serverPack() || BedrockParticleEngine.vanilla()) {
         EntityParticleAnchor anchor = new EntityParticleAnchor(this.entity, locator, 0.0, 0.0, 0.0);
         ParticleEmitter emitter;
         if (!bindToActor && owner == null) {
            double[] at = new double[3];
            anchor.position(at);
            emitter = BedrockParticleEngine.spawn(particle, at[0], at[1], at[2], null, preEffect, null);
         } else {
            emitter = BedrockParticleEngine.spawn(particle, this.entity.getX(), this.entity.getY(), this.entity.getZ(), anchor, preEffect, null);
         }

         if (emitter != null) {
            this.particles++;
            if (owner != null) {
               this.byOwner.computeIfAbsent(owner, key -> new ArrayList<>()).add(emitter);
            }
         }
      }
   }

   @Override
   public void sound(String effect, @Nullable String locator, MolangEnv env) {
      this.sound(this.definition, effect, locator);
   }

   private void sound(ClientEntityDef definition, String effect, @Nullable String locator) {
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      String name = definition.soundEffect(effect);
      if (catalog != null && name != null) {
         SoundPlanner.Play play = SoundPlanner.definition(catalog.definitions(), name, 1.0F, 1.0F, definition.serverPack(), BedrockSoundEngine.random());
         if (play != null && (play.serverContent() || BedrockSoundEngine.vanilla())) {
            Vec3 position = this.entity.position();
            if (locator != null) {
               ActorState state = BedrockActors.state(this.entity);
               Vector3f offset = state != null ? state.locatorOffset(locator) : null;
               if (offset != null) {
                  position = position.add(offset.x, offset.y, offset.z);
               }
            }

            if (BedrockSoundEngine.play(play, position, BedrockSoundEngine.Options.DEFAULT) == BedrockSoundEngine.Result.PLAYED) {
               this.sounds++;
            }
         }
      }
   }

   @Override
   public void end(Object owner) {
      List<ParticleEmitter> emitters = this.byOwner.remove(owner);
      if (emitters != null) {
         emitters.forEach(emitter -> emitter.stop(true));
      }
   }

   void close() {
      this.byOwner.values().forEach(emitters -> emitters.forEach(emitter -> emitter.stop(true)));
      this.byOwner.clear();
   }

   int particlesStarted() {
      return this.particles;
   }

   int soundsPlayed() {
      return this.sounds;
   }

   private record Scoped(ActorEffects base, ClientEntityDef definition) implements EffectSink {
      @Override
      public void particle(
         String effect, @Nullable String locator, @Nullable MolangProgram preEffect, boolean bindToActor, @Nullable Object owner, MolangEnv env
      ) {
         this.base.particle(this.definition, effect, locator, preEffect, bindToActor, owner);
      }

      @Override
      public void sound(String effect, @Nullable String locator, MolangEnv env) {
         this.base.sound(this.definition, effect, locator);
      }

      @Override
      public void end(Object owner) {
         this.base.end(owner);
      }

      @Override
      public EffectSink forDefinition(ClientEntityDef other) {
         return this.base.forDefinition(other);
      }
   }
}
