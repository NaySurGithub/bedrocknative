package net.bedrocknative.molang;

public final class Molang {
   private Molang() {
   }

   public static MolangProgram compile(String source, MolangVersion version) {
      return MolangCompiler.compile(source, version, false);
   }

   public static MolangProgram compile(String source, MolangVersion version, boolean lowerCaseStrings) {
      return MolangCompiler.compile(source, version, lowerCaseStrings);
   }

   public static float evaluate(String source, MolangEnv env) {
      return compile(source, MolangVersion.LATEST).evaluate(env);
   }
}
