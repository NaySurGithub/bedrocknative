package net.bedrocknative.model.runtime;

import net.bedrocknative.model.animation.PoseBuffer;
import net.bedrocknative.molang.MolangControllerScope;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;

final class RuntimeController implements MolangControllerScope {
   final String key;
   private final AnimationNode node;
   private final MolangProgram stop;
   private final float blendOut;
   private float time;
   private float fade = -1.0F;
   private boolean done;

   RuntimeController(String key, AnimationNode node, MolangProgram stop, float blendOut) {
      this.key = key;
      this.node = node;
      this.stop = stop;
      this.blendOut = Math.max(0.0F, blendOut);
   }

   void start(MolangEnv env) {
      this.node.restart(env);
   }

   void update(MolangEnv env, float deltaTime) {
      this.time += deltaTime;
      this.node.update(env, deltaTime);
      if (this.fade < 0.0F) {
         MolangControllerScope saved = env.controller;
         env.controller = this;
         env.animTime = this.node.player.time();
         if (this.stop.test(env)) {
            this.fade = 0.0F;
         }

         env.controller = saved;
      } else {
         this.fade += deltaTime;
      }

      if (this.fade >= 0.0F && this.fade >= this.blendOut) {
         this.done = true;
      }
   }

   void apply(PoseBuffer pose, MolangEnv env) {
      if (!this.done) {
         float weight = this.fade < 0.0F ? 1.0F : 1.0F - this.fade / this.blendOut;
         MolangControllerScope saved = env.controller;
         env.controller = this;
         this.node.apply(pose, weight, env);
         env.controller = saved;
      }
   }

   boolean done() {
      return this.done;
   }

   String animation() {
      return this.node.player.animation().name();
   }

   @Override
   public boolean allAnimationsFinished() {
      return this.node.finished();
   }

   @Override
   public boolean anyAnimationFinished() {
      return this.node.finished();
   }

   @Override
   public float stateTime() {
      return this.time;
   }
}
