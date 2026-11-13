package net.bedrocknative.model.animation;

import java.util.List;
import java.util.Map;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public record ControllerDef(String name, String initialState, Map<String, ControllerDef.State> states, int formatVersion) {
   @Nullable
   public ControllerDef.State state(String name) {
      return this.states.get(name);
   }

   public record State(
      String name,
      List<ControllerDef.StateAnimation> animations,
      List<ControllerDef.Transition> transitions,
      float blendTransition,
      @Nullable float[] blendCurve,
      boolean blendViaShortestPath,
      List<MolangProgram> onEntry,
      List<MolangProgram> onExit,
      List<StateVariable> variables,
      List<AnimationDef.Effect> particleEffects,
      List<AnimationDef.Effect> soundEffects
   ) {
   }

   public record StateAnimation(String name, @Nullable MolangProgram weight) {
   }

   public record Transition(String target, MolangProgram condition) {
   }
}
