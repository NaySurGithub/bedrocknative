package net.bedrocknative.model.runtime;

import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.AnimationPlayer;
import net.bedrocknative.model.animation.LoopMode;
import net.bedrocknative.model.animation.PoseBuffer;
import net.bedrocknative.molang.MolangEnv;

final class AnimationNode implements Playable {
   final AnimationPlayer player;
   final AnimationDef animation;
   private final int[] slots;

   AnimationNode(AnimationDef animation, int[] slots) {
      this.animation = animation;
      this.player = new AnimationPlayer(animation);
      this.slots = slots;
   }

   @Override
   public void restart(MolangEnv env) {
      this.player.restart(env);
   }

   @Override
   public void update(MolangEnv env, float deltaTime) {
      this.player.update(env, deltaTime);
   }

   @Override
   public void apply(PoseBuffer pose, float weight, MolangEnv env) {
      this.player.apply(pose, this.slots, weight, env);
   }

   void applyOnly(PoseBuffer pose, float weight, MolangEnv env, int[] only) {
      this.player.apply(pose, only, weight, env);
   }

   int[] slots() {
      return this.slots;
   }

   boolean looping() {
      return this.player.animation().loop() == LoopMode.LOOP;
   }

   boolean heldPose() {
      return this.player.animation().loop() == LoopMode.HOLD_ON_LAST_FRAME && this.player.started() && this.player.finished();
   }

   @Override
   public boolean finished() {
      return this.player.finished();
   }
}
