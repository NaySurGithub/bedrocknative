package net.bedrocknative.molang;

import org.jspecify.annotations.Nullable;

public interface MolangResources {
   @Nullable
   Object[] array(String var1);

   public record Ref(String kind, String name) {
   }
}
