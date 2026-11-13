package net.bedrocknative.model.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.ControllerDef;
import net.bedrocknative.model.animation.PoseBuffer;
import net.bedrocknative.model.animation.StateVariable;
import net.bedrocknative.molang.EffectSink;
import net.bedrocknative.molang.MolangControllerScope;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

final class ControllerInstance implements Playable, MolangControllerScope {
   private final ControllerDef definition;
   private final Function<String, @Nullable Playable> factory;
   private final boolean vanilla;
   private final Map<ControllerDef.State, List<ControllerInstance.Entry>> stateEntries = new IdentityHashMap<>();
   private final Map<ControllerDef.State, int[]> stateVariableIds = new IdentityHashMap<>();
   private ControllerDef.@Nullable State current;
   private List<ControllerInstance.Entry> entries = List.of();
   private ControllerDef.@Nullable State previous;
   private List<ControllerInstance.Entry> previousEntries = List.of();
   private float stateTime;
   private float blendElapsed;
   private float blendDuration;
   private boolean blendShortestPath;
   private boolean entered;
   private int[] variableIds = new int[0];
   private @Nullable PoseBuffer scratchFrom;
   private @Nullable PoseBuffer scratchTo;

   ControllerInstance(ControllerDef definition, Function<String, @Nullable Playable> factory) {
      this(definition, factory, false);
   }

   ControllerInstance(ControllerDef definition, Function<String, @Nullable Playable> factory, boolean vanilla) {
      this.definition = definition;
      this.factory = factory;
      this.vanilla = vanilla;
   }

   ControllerDef definition() {
      return this.definition;
   }

   @Nullable String state() {
      return this.current != null ? this.current.name() : null;
   }

   boolean blending() {
      return this.previous != null;
   }

   @Override
   public void restart(MolangEnv env) {
      if (env.effects != null && this.current != null && !this.current.particleEffects().isEmpty()) {
         env.effects.end(this);
      }

      this.previous = null;
      this.previousEntries = List.of();
      this.current = this.definition.state(this.definition.initialState());
      this.entries = this.entries(this.current);
      this.variableIds = this.variableIds(this.current);
      this.stateTime = 0.0F;
      this.entered = true;
      MolangControllerScope saved = env.controller;
      env.controller = this;
      if (this.current != null) {
         run(this.current.onEntry(), env);
         this.startEffects(this.current, env);
      }

      restartAll(this.entries, env);
      env.controller = saved;
   }

   private void startEffects(ControllerDef.State state, MolangEnv env) {
      EffectSink sink = env.effects;
      if (sink != null) {
         List<AnimationDef.Effect> particles = state.particleEffects();

         for (int i = 0; i < particles.size(); i++) {
            AnimationDef.Effect effect = particles.get(i);
            sink.particle(effect.effect(), effect.locator(), effect.preEffectScript(), effect.bindToActor(), this, env);
         }

         List<AnimationDef.Effect> sounds = state.soundEffects();

         for (int i = 0; i < sounds.size(); i++) {
            sink.sound(sounds.get(i).effect(), sounds.get(i).locator(), env);
         }
      }
   }

   private static void restartAll(List<ControllerInstance.Entry> list, MolangEnv env) {
      for (int i = 0; i < list.size(); i++) {
         ControllerInstance.Entry entry = list.get(i);
         entry.playable.restart(env);
         entry.active = true;
      }
   }

   @Override
   public void update(MolangEnv env, float deltaTime) {
      if (!this.entered) {
         this.restart(env);
      }

      if (this.current != null) {
         MolangControllerScope saved = env.controller;
         env.controller = this;
         List<ControllerDef.Transition> transitions = this.current.transitions();

         for (int i = 0; i < transitions.size(); i++) {
            ControllerDef.Transition transition = transitions.get(i);
            ControllerDef.State target = this.definition.state(transition.target());
            if (target != null && transition.condition().test(env)) {
               this.switchTo(target, env);
               break;
            }
         }

         this.stateTime += deltaTime;
         this.updateEntries(this.entries, env, deltaTime);
         if (this.previous != null) {
            this.blendElapsed += deltaTime;
            if (this.blendElapsed >= this.blendDuration) {
               this.previous = null;
               this.previousEntries = List.of();
            } else {
               this.updateEntries(this.previousEntries, env, deltaTime);
            }
         }

         env.controller = saved;
      }
   }

   private void updateEntries(List<ControllerInstance.Entry> list, MolangEnv env, float deltaTime) {
      for (int i = 0; i < list.size(); i++) {
         ControllerInstance.Entry entry = list.get(i);
         if (this.vanilla && entry.weight != null) {
            if (entry.weight.evaluate(env) == 0.0F) {
               entry.active = false;
               continue;
            }

            if (!entry.active) {
               entry.playable.restart(env);
               entry.active = true;
            }
         }

         entry.playable.update(env, deltaTime);
      }
   }

   private void switchTo(ControllerDef.State target, MolangEnv env) {
      ControllerDef.State leaving = this.current;
      run(leaving.onExit(), env);
      if (env.effects != null && !leaving.particleEffects().isEmpty()) {
         env.effects.end(this);
      }

      if (leaving.blendTransition() > 0.0F) {
         this.previous = leaving;
         this.previousEntries = this.entries;
         this.blendDuration = leaving.blendTransition();
         this.blendElapsed = 0.0F;
         this.blendShortestPath = leaving.blendViaShortestPath();
      } else {
         this.previous = null;
         this.previousEntries = List.of();
      }

      this.current = target;
      this.entries = this.previous == target ? this.createEntries(target) : this.entries(target);
      this.variableIds = this.variableIds(target);
      this.stateTime = 0.0F;
      run(target.onEntry(), env);
      this.startEffects(target, env);
      restartAll(this.entries, env);
   }

   @Override
   public void apply(PoseBuffer pose, float weight, MolangEnv env) {
      if (this.current != null) {
         MolangControllerScope saved = env.controller;
         env.controller = this;
         List<StateVariable> variables = this.current.variables();

         for (int i = 0; i < variables.size(); i++) {
            env.variables.set(this.variableIds[i], variables.get(i).evaluate(env), null);
         }

         if (this.previous != null && this.blendDuration > 0.0F) {
            float t = Math.min(1.0F, this.blendElapsed / this.blendDuration);
            if (this.blendShortestPath) {
               if (this.scratchFrom == null || this.scratchFrom.slots() != pose.slots()) {
                  this.scratchFrom = pose.copyShape();
                  this.scratchTo = pose.copyShape();
               }

               this.scratchFrom.copyRest(pose);
               this.scratchTo.copyRest(pose);
               this.scratchFrom.reset();
               this.scratchTo.reset();
               applyEntries(this.previousEntries, this.scratchFrom, 1.0F, env);
               applyEntries(this.entries, this.scratchTo, 1.0F, env);
               this.scratchFrom.blend(this.scratchTo, t, true);
               pose.addDelta(this.scratchFrom, weight);
            } else {
               applyEntries(this.previousEntries, pose, weight * (1.0F - t), env);
               applyEntries(this.entries, pose, weight * t, env);
            }
         } else {
            applyEntries(this.entries, pose, weight, env);
         }

         env.controller = saved;
      }
   }

   private static void applyEntries(List<ControllerInstance.Entry> list, PoseBuffer pose, float weight, MolangEnv env) {
      for (int i = 0; i < list.size(); i++) {
         ControllerInstance.Entry entry = list.get(i);
         if (entry.active) {
            float entryWeight = entry.weight != null ? entry.weight.evaluate(env) : 1.0F;
            if (entryWeight != 0.0F && weight != 0.0F) {
               entry.playable.apply(pose, weight * entryWeight, env);
            }
         }
      }
   }

   private List<ControllerInstance.Entry> entries(ControllerDef.@Nullable State state) {
      if (state == null) {
         return List.of();
      }

      List<ControllerInstance.Entry> cached = this.stateEntries.get(state);
      return cached != null ? cached : this.createEntries(state);
   }

   private List<ControllerInstance.Entry> createEntries(ControllerDef.State state) {
      List<ControllerInstance.Entry> list = new ArrayList<>();

      for (ControllerDef.StateAnimation animation : state.animations()) {
         Playable playable = this.factory.apply(animation.name());
         if (playable != null) {
            list.add(new ControllerInstance.Entry(playable, animation.weight()));
         }
      }

      List<ControllerInstance.Entry> created = List.copyOf(list);
      this.stateEntries.put(state, created);
      return created;
   }

   private int[] variableIds(ControllerDef.@Nullable State state) {
      if (state == null) {
         return new int[0];
      }

      int[] cached = this.stateVariableIds.get(state);
      if (cached != null) {
         return cached;
      }

      int[] ids = new int[state.variables().size()];

      for (int i = 0; i < ids.length; i++) {
         ids[i] = MolangNames.id(state.variables().get(i).name());
      }

      this.stateVariableIds.put(state, ids);
      return ids;
   }

   private static void run(List<MolangProgram> programs, MolangEnv env) {
      for (int i = 0; i < programs.size(); i++) {
         programs.get(i).evaluate(env);
      }
   }

   @Override
   public boolean finished() {
      return this.allAnimationsFinished();
   }

   @Override
   public boolean allAnimationsFinished() {
      for (int i = 0; i < this.entries.size(); i++) {
         ControllerInstance.Entry entry = this.entries.get(i);
         if (entry.active && !entry.playable.finished()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public boolean anyAnimationFinished() {
      for (int i = 0; i < this.entries.size(); i++) {
         ControllerInstance.Entry entry = this.entries.get(i);
         if (entry.active && entry.playable.finished()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public float stateTime() {
      return this.stateTime;
   }

   private static final class Entry {
      final Playable playable;
      final @Nullable MolangProgram weight;
      boolean active = true;

      Entry(Playable playable, @Nullable MolangProgram weight) {
         this.playable = playable;
         this.weight = weight;
      }
   }
}
