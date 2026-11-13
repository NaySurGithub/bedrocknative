package net.bedrocknative.model.animation;

import java.util.List;
import net.bedrocknative.molang.EffectSink;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;

public final class AnimationPlayer {
   private final AnimationDef animation;
   private float time;
   private float delay;
   private boolean started;
   private boolean finished;
   private boolean waitingToLoop;
   private int loops;

   public AnimationPlayer(AnimationDef animation) {
      this.animation = animation;
   }

   public AnimationDef animation() {
      return this.animation;
   }

   public void restart(MolangEnv env) {
      this.time = 0.0F;
      this.loops = 0;
      this.finished = false;
      this.waitingToLoop = false;
      this.started = false;
      env.animTime = 0.0F;
      this.delay = this.animation.startDelay().evaluate(env);
      if (this.delay <= 0.0F) {
         this.start(env);
      }
   }

   private void start(MolangEnv env) {
      this.started = true;
      this.delay = 0.0F;
      this.runTimeline(env, -1.0F, 0.0F);
   }

   public void update(MolangEnv env, float deltaTime) {
      if (!this.started) {
         this.delay -= deltaTime;
         if (this.delay <= 0.0F) {
            this.start(env);
         }
      } else if (!this.finished || this.animation.loop() == LoopMode.LOOP) {
         if (this.waitingToLoop) {
            this.delay -= deltaTime;
            if (this.delay <= 0.0F) {
               this.waitingToLoop = false;
               this.time = 0.0F;
               this.runTimeline(env, -1.0F, 0.0F);
            }
         } else {
            float previous = this.time;
            float length = this.animation.length();
            MolangProgram update = this.animation.animTimeUpdate();
            env.animTime = this.time;
            env.deltaTime = deltaTime;
            this.time = update != null ? update.evaluate(env) : this.time + deltaTime;
            switch (this.animation.loop()) {
               case LOOP:
                  if (length <= 0.0F) {
                     this.finished = true;
                  } else if (update != null) {
                     int wraps = (int)Math.floor(this.time / length);
                     if (wraps > this.loops) {
                        this.loops = wraps;
                        this.finished = true;
                     }

                     float wrappedPrevious = previous - (float)Math.floor(previous / length) * length;
                     float wrapped = this.sampleTime();
                     this.runTimeline(env, wrapped >= wrappedPrevious ? wrappedPrevious : -1.0F, wrapped);
                  } else if (this.time >= length) {
                     this.runTimeline(env, previous, length);
                     this.loops++;
                     this.finished = true;
                     env.animTime = length;
                     float loopDelay = this.animation.loopDelay().evaluate(env);
                     if (loopDelay > 0.0F) {
                        this.waitingToLoop = true;
                        this.delay = loopDelay;
                        this.time = length;
                     } else {
                        this.time %= length;
                        this.runTimeline(env, -1.0F, this.time);
                     }
                  } else {
                     this.runTimeline(env, previous, this.time);
                  }
                  break;
               case ONCE:
               case HOLD_ON_LAST_FRAME:
                  if (this.time >= length) {
                     this.runTimeline(env, previous, length);
                     this.time = length;
                     this.finished = true;
                  } else {
                     this.runTimeline(env, previous, this.time);
                  }
            }
         }
      }
   }

   private void runTimeline(MolangEnv env, float after, float until) {
      List<AnimationDef.TimedScript> timeline = this.animation.timeline();

      for (int i = 0; i < timeline.size(); i++) {
         AnimationDef.TimedScript script = timeline.get(i);
         if (script.time() > after && script.time() <= until) {
            env.animTime = script.time();
            List<MolangProgram> programs = script.programs();

            for (int p = 0; p < programs.size(); p++) {
               programs.get(p).evaluate(env);
            }
         }
      }

      EffectSink sink = env.effects;
      if (sink != null) {
         effects(this.animation.particleEffects(), after, until, sink, env, true);
         effects(this.animation.soundEffects(), after, until, sink, env, false);
      }
   }

   private static void effects(List<AnimationDef.Effect> effects, float after, float until, EffectSink sink, MolangEnv env, boolean particles) {
      for (int i = 0; i < effects.size(); i++) {
         AnimationDef.Effect effect = effects.get(i);
         if (effect.time() > after && effect.time() <= until) {
            if (particles) {
               sink.particle(effect.effect(), effect.locator(), effect.preEffectScript(), effect.bindToActor(), null, env);
            } else {
               sink.sound(effect.effect(), effect.locator(), env);
            }
         }
      }
   }

   public float sampleTime() {
      float length = this.animation.length();
      if (this.animation.loop() == LoopMode.LOOP && length > 0.0F && !this.waitingToLoop) {
         float wrapped = this.time % length;
         return wrapped < 0.0F ? wrapped + length : wrapped;
      } else {
         return this.animation.loop() == LoopMode.LOOP && length <= 0.0F
            ? Math.max(0.0F, this.time)
            : Math.min(Math.max(0.0F, this.time), Math.max(0.0F, length));
      }
   }

   public float time() {
      return this.animation.loop() == LoopMode.LOOP && this.animation.animTimeUpdate() == null ? this.sampleTime() : this.time;
   }

   public boolean started() {
      return this.started;
   }

   public boolean finished() {
      return this.finished;
   }

   public boolean contributes() {
      return this.started && (this.animation.loop() != LoopMode.ONCE || !this.finished || !(this.animation.length() > 0.0F));
   }

   public void apply(PoseBuffer pose, int[] boneSlots, float weight, MolangEnv env) {
      if (this.contributes()) {
         env.animTime = this.time();
         float blend = this.animation.blendWeight().evaluate(env);
         pose.apply(this.animation, boneSlots, this.sampleTime(), weight * blend, env);
      }
   }
}
