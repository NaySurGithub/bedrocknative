package net.bedrocknative.model.animation;

import java.util.List;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public record AnimationDef(
   String name,
   LoopMode loop,
   float length,
   @Nullable MolangProgram animTimeUpdate,
   MolangProgram blendWeight,
   MolangProgram startDelay,
   MolangProgram loopDelay,
   boolean overridePreviousAnimation,
   List<BoneTimeline> bones,
   List<AnimationDef.TimedScript> timeline,
   List<AnimationDef.Effect> soundEffects,
   List<AnimationDef.Effect> particleEffects
) {
   public record Effect(float time, String effect, @Nullable String locator, @Nullable MolangProgram preEffectScript, boolean bindToActor) {
   }

   public record TimedScript(float time, List<MolangProgram> programs) {
   }
}
