package net.bedrocknative.model.runtime;

import net.bedrocknative.model.animation.PoseBuffer;
import net.bedrocknative.molang.MolangEnv;

interface Playable {
   void restart(MolangEnv var1);

   void update(MolangEnv var1, float var2);

   void apply(PoseBuffer var1, float var2, MolangEnv var3);

   boolean finished();
}
