package net.bedrocknative.molang;

@FunctionalInterface
public interface QueryFunction {
   float call(MolangEnv var1, QueryArgs var2);
}
