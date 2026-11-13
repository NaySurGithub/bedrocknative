package net.bedrocknative.molang;

import net.bedrocknative.model.entity.ClientEntityDef;
import org.jspecify.annotations.Nullable;

public interface EffectSink {
   void particle(String var1, @Nullable String var2, @Nullable MolangProgram var3, boolean var4, @Nullable Object var5, MolangEnv var6);

   void sound(String var1, @Nullable String var2, MolangEnv var3);

   void end(Object var1);

   default EffectSink forDefinition(ClientEntityDef definition) {
      return this;
   }
}
