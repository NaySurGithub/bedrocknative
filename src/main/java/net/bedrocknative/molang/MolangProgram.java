package net.bedrocknative.molang;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class MolangProgram {
   static final String[] NO_NAMES = new String[0];
   private final String source;
   private final Node root;
   private final boolean complex;
   private final boolean constant;
   private final float constantValue;
   @Nullable
   private final Object constantObject;
   private final MolangDiagnostics diagnostics;
   private final String[] queries;
   private final String[] reads;
   private final String[] writes;
   private volatile boolean overBudget;

   MolangProgram(String source, Node root, boolean complex, MolangDiagnostics diagnostics, String[] queries, String[] reads, String[] writes) {
      this.source = source;
      this.root = root;
      this.complex = complex;
      this.diagnostics = diagnostics;
      this.queries = queries;
      this.reads = reads;
      this.writes = writes;
      this.constant = !complex && root.isConstant();
      if (this.constant) {
         MolangEnv env = new MolangEnv();
         this.constantValue = root.eval(env);
         this.constantObject = env.object;
      } else {
         this.constantValue = 0.0F;
         this.constantObject = null;
      }
   }

   static MolangProgram sequence(String source, List<MolangProgram> entries) {
      MolangDiagnostics diagnostics = new MolangDiagnostics(source);
      entries.forEach(entry -> diagnostics.absorb(entry.diagnostics));
      return new MolangProgram(
         source,
         new Nodes.Programs(entries.toArray(new MolangProgram[0])),
         false,
         diagnostics,
         union(entries, program -> program.queries),
         union(entries, program -> program.reads),
         union(entries, program -> program.writes)
      );
   }

   private static String[] union(List<MolangProgram> entries, Function<MolangProgram, String[]> names) {
      LinkedHashSet<String> all = new LinkedHashSet<>();
      entries.forEach(entry -> all.addAll(Arrays.asList(names.apply(entry))));
      return all.toArray(new String[0]);
   }

   public static MolangProgram constant(float value) {
      return new MolangProgram(Float.toString(value), new Nodes.Const(value), false, new MolangDiagnostics(""), NO_NAMES, NO_NAMES, NO_NAMES);
   }

   public List<String> queries() {
      return List.of(this.queries);
   }

   public List<String> variableReads() {
      return List.of(this.reads);
   }

   public List<String> variableWrites() {
      return List.of(this.writes);
   }

   public boolean overBudget() {
      return this.overBudget;
   }

   public float evaluate(MolangEnv env) {
      if (this.constant) {
         env.object = this.constantObject;
         return this.constantValue;
      } else if (this.overBudget) {
         env.object = null;
         return 0.0F;
      } else {
         int savedFlow = env.flow;
         float savedReturn = env.returnValue;
         Object savedReturnObject = env.returnObject;
         if (env.depth++ == 0) {
            env.temps.clear();
            env.steps = 100000L;
         }

         env.flow = 0;

         float var6;
         try {
            float value = this.root.eval(env);
            if (env.flow == 3) {
               value = env.returnValue;
               env.object = env.returnObject;
            } else if (this.complex) {
               value = 0.0F;
               env.object = null;
            }

            return Node.fix(value);
         } catch (MolangBudgetException var11) {
            this.overBudget = true;
            this.diagnostics.exception(var11);
            env.object = null;
            return 0.0F;
         } catch (StackOverflowError | RuntimeException var12) {
            this.diagnostics.exception(var12);
            env.object = null;
            var6 = 0.0F;
         } finally {
            env.depth--;
            env.flow = savedFlow;
            env.returnValue = savedReturn;
            env.returnObject = savedReturnObject;
         }

         return var6;
      }
   }

   public boolean test(MolangEnv env) {
      return Node.truthy(this.evaluate(env), env);
   }

   @Nullable
   public Object evaluateObject(MolangEnv env) {
      this.evaluate(env);
      return env.object;
   }

   public boolean isConstant() {
      return this.constant;
   }

   public float constantValue() {
      return this.constantValue;
   }

   public String source() {
      return this.source;
   }

   public MolangDiagnostics diagnostics() {
      return this.diagnostics;
   }

   @Override
   public String toString() {
      return this.source;
   }
}
