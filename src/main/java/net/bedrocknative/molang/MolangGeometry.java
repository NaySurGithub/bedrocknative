package net.bedrocknative.molang;

import org.jspecify.annotations.Nullable;

public interface MolangGeometry {
   @Nullable
   MolangStruct bone(String var1, String var2);

   float defaultBonePivot(String var1, int var2);

   float locatorOffset(String var1, int var2);

   float rootLocatorOffset(String var1, int var2);
}
