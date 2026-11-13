package net.bedrocknative.molang;

import org.jspecify.annotations.Nullable;

public interface MolangResources {
   @Nullable Object @Nullable [] array(String var1);

   record Ref(String kind, String name) {
   }
}
