package net.bedrocknative.molang;

import org.jspecify.annotations.Nullable;

final class Nodes {
   static final int VARIABLE = 0;
   static final int TEMP = 1;
   static final int CONTEXT = 2;
   static final int MAX_LOOP = 1024;

   private Nodes() {
   }

   static VariableStore store(MolangEnv env, int scope) {
      return switch (scope) {
         case 1 -> env.temps;
         case 2 -> env.context;
         default -> env.variables;
      };
   }

   private static boolean continueLoop(MolangEnv env) {
      switch (env.flow) {
         case 1:
            env.flow = 0;
            return false;
         case 2:
            env.flow = 0;
            return true;
         case 3:
            return false;
         default:
            return true;
      }
   }

   static final class Add extends Nodes.Numeric {
      final Node left;
      final Node right;

      Add(Node left, Node right) {
         this.left = left;
         this.right = right;
      }

      @Override
      float eval(MolangEnv env) {
         float a = this.left.eval(env);
         float b = this.right.eval(env);
         env.object = null;
         return fix(a + b);
      }
   }

   static final class And extends Nodes.Numeric {
      final Node left;
      final Node right;

      And(Node left, Node right) {
         this.left = left;
         this.right = right;
      }

      @Override
      float eval(MolangEnv env) {
         boolean result = truthy(this.left.eval(env), env) && truthy(this.right.eval(env), env);
         env.object = null;
         return result ? 1.0F : 0.0F;
      }
   }

   static final class ArrayRef extends Node {
      final String name;

      ArrayRef(String name) {
         this.name = name;
      }

      @Override
      float eval(MolangEnv env) {
         env.object = env.resources != null ? env.resources.array(this.name) : null;
         return 0.0F;
      }
   }

   static final class Arrow extends Node {
      final Node target;
      final Node body;
      private static final ThreadLocal<VariableStore> EMPTY = ThreadLocal.withInitial(() -> new VariableStore(4));

      Arrow(Node target, Node body) {
         this.target = target;
         this.body = body;
      }

      @Override
      float eval(MolangEnv env) {
         this.target.eval(env);
         if (env.object instanceof MolangActor actor && actor.valid()) {
            MolangActor previousActor = env.actor;
            VariableStore previousVariables = env.variables;
            VariableStore shared = actor.publicVariables();
            env.actor = actor;
            env.variables = shared != null ? shared : EMPTY.get();

            try {
               return this.body.eval(env);
            } finally {
               env.actor = previousActor;
               env.variables = previousVariables;
            }
         } else {
            env.object = null;
            return 0.0F;
         }
      }
   }

   static final class Coalesce extends Node {
      final Node left;
      final Nodes.@Nullable VariableRead leftVariable;
      final Node right;

      Coalesce(Node left, Node right) {
         this.left = left;
         this.leftVariable = left instanceof Nodes.VariableRead read ? read : null;
         this.right = right;
      }

      @Override
      float eval(MolangEnv env) {
         if (this.leftVariable != null && !this.leftVariable.isSet(env)) {
            return this.right.eval(env);
         }

         float value = this.left.eval(env);
         return env.object instanceof MolangActor actor && !actor.valid() ? this.right.eval(env) : value;
      }
   }

   static final class Compare extends Nodes.Numeric {
      static final int LESS = 0;
      static final int LESS_EQUAL = 1;
      static final int GREATER = 2;
      static final int GREATER_EQUAL = 3;
      final Node left;
      final Node right;
      final int op;

      Compare(Node left, Node right, int op) {
         this.left = left;
         this.right = right;
         this.op = op;
      }

      @Override
      float eval(MolangEnv env) {
         float a = this.left.eval(env);
         float b = this.right.eval(env);
         env.object = null;

         boolean result = switch (this.op) {
            case 0 -> a < b;
            case 1 -> a <= b;
            case 2 -> a > b;
            default -> a >= b;
         };
         return result ? 1.0F : 0.0F;
      }
   }

   static final class Const extends Node {
      final float value;

      Const(float value) {
         this.value = value;
      }

      @Override
      float eval(MolangEnv env) {
         env.object = null;
         return this.value;
      }

      @Override
      boolean isConstant() {
         return true;
      }

      @Override
      boolean mayBeObject() {
         return false;
      }
   }

   static final class Divide extends Nodes.Numeric {
      final Node left;
      final Node right;
      final boolean absoluteDivisor;

      Divide(Node left, Node right, boolean absoluteDivisor) {
         this.left = left;
         this.right = right;
         this.absoluteDivisor = absoluteDivisor;
      }

      @Override
      float eval(MolangEnv env) {
         float a = this.left.eval(env);
         float b = this.right.eval(env);
         env.object = null;
         return b == 0.0F ? 0.0F : fix(a / (this.absoluteDivisor ? Math.abs(b) : b));
      }
   }

   static final class Equals extends Nodes.Numeric {
      final Node left;
      final Node right;
      final boolean negate;
      final MolangDiagnostics diagnostics;

      Equals(Node left, Node right, boolean negate, MolangDiagnostics diagnostics) {
         this.left = left;
         this.right = right;
         this.negate = negate;
         this.diagnostics = diagnostics;
      }

      @Override
      float eval(MolangEnv env) {
         float a = this.left.eval(env);
         Object leftObject = env.object;
         float b = this.right.eval(env);
         Object rightObject = env.object;
         env.object = null;
         boolean equal;
         if (leftObject == null && rightObject == null) {
            equal = a == b;
         } else if (leftObject != null && rightObject != null) {
            equal = leftObject == rightObject || leftObject.equals(rightObject);
         } else {
            this.diagnostics.runtime("Comparing a string or object with a number");
            equal = false;
         }

         return equal != this.negate ? 1.0F : 0.0F;
      }
   }

   static final class ForEach extends Nodes.Numeric {
      final int scope;
      final int[] path;
      final Node array;
      final Node body;

      ForEach(int scope, int[] path, Node array, Node body) {
         this.scope = scope;
         this.path = path;
         this.array = array;
         this.body = body;
      }

      @Override
      float eval(MolangEnv env) {
         this.array.eval(env);
         Object value = env.object;
         if (value instanceof MolangActor actor) {
            this.iterate(env, actor);
         } else if (value instanceof Object[] elements) {
            for (int i = 0; i < Math.min(1024, elements.length); i++) {
               env.charge(1L);
               if (!this.iterate(env, elements[i])) {
                  break;
               }
            }
         }

         env.object = null;
         return 0.0F;
      }

      private boolean iterate(MolangEnv env, @Nullable Object element) {
         Nodes.VariableWrite.assign(env, this.scope, this.path, 0.0F, element);
         this.body.eval(env);
         return Nodes.continueLoop(env);
      }
   }

   static final class Index extends Node {
      final Node target;
      final Node index;

      Index(Node target, Node index) {
         this.target = target;
         this.index = index;
      }

      @Override
      float eval(MolangEnv env) {
         this.target.eval(env);
         Object array = env.object;
         float position = this.index.eval(env);
         env.object = null;
         if (array instanceof Object[] elements && elements.length > 0) {
            Object element = elements[Math.max(0, (int)position) % elements.length];
            if (element instanceof Float number) {
               return number;
            }

            env.object = element;
         }

         return 0.0F;
      }
   }

   static final class Jump extends Nodes.Numeric {
      final int flow;

      Jump(int flow) {
         this.flow = flow;
      }

      @Override
      float eval(MolangEnv env) {
         env.object = null;
         if (env.flow == 0) {
            env.flow = this.flow;
         }

         return 0.0F;
      }
   }

   static final class Loop extends Nodes.Numeric {
      final Node count;
      final Node body;

      Loop(Node count, Node body) {
         this.count = count;
         this.body = body;
      }

      @Override
      float eval(MolangEnv env) {
         int times = (int)Math.min(1024.0F, this.count.eval(env));

         for (int i = 0; i < times; i++) {
            env.charge(1L);
            this.body.eval(env);
            if (!Nodes.continueLoop(env)) {
               break;
            }
         }

         env.object = null;
         return 0.0F;
      }
   }

   static final class MathCall extends Nodes.Numeric {
      final MathFunctions.Function function;
      final @Nullable Node a;
      final @Nullable Node b;
      final @Nullable Node c;

      MathCall(MathFunctions.Function function, @Nullable Node a, @Nullable Node b, @Nullable Node c) {
         this.function = function;
         this.a = a;
         this.b = b;
         this.c = c;
      }

      @Override
      float eval(MolangEnv env) {
         float x = this.a != null ? this.a.eval(env) : 0.0F;
         float y = this.b != null ? this.b.eval(env) : 0.0F;
         float z = this.c != null ? this.c.eval(env) : 0.0F;
         env.object = null;
         if (this.function.dice()) {
            env.charge((long)Math.max(0.0F, Math.min(1024.0F, x)));
         }

         return fix(this.function.random() != null ? this.function.random().apply(env.random, x, y, z) : this.function.pure().apply(x, y, z));
      }
   }

   static final class Member extends Node {
      final Node target;
      final int member;

      Member(Node target, int member) {
         this.target = target;
         this.member = member;
      }

      @Override
      float eval(MolangEnv env) {
         this.target.eval(env);
         if (env.object instanceof MolangStruct struct) {
            int index = struct.members.index(this.member);
            if (index >= 0) {
               env.object = struct.members.objectAt(index);
               return struct.members.valueAt(index);
            }
         }

         env.object = null;
         return 0.0F;
      }
   }

   static final class Multiply extends Nodes.Numeric {
      final Node left;
      final Node right;

      Multiply(Node left, Node right) {
         this.left = left;
         this.right = right;
      }

      @Override
      float eval(MolangEnv env) {
         float a = this.left.eval(env);
         float b = this.right.eval(env);
         env.object = null;
         return fix(a * b);
      }
   }

   static final class Negate extends Nodes.Numeric {
      final Node operand;

      Negate(Node operand) {
         this.operand = operand;
      }

      @Override
      float eval(MolangEnv env) {
         float value = this.operand.eval(env);
         env.object = null;
         return -value;
      }
   }

   static final class Not extends Nodes.Numeric {
      final Node operand;

      Not(Node operand) {
         this.operand = operand;
      }

      @Override
      float eval(MolangEnv env) {
         boolean value = truthy(this.operand.eval(env), env);
         env.object = null;
         return value ? 0.0F : 1.0F;
      }
   }

   abstract static class Numeric extends Node {
      @Override
      boolean mayBeObject() {
         return false;
      }
   }

   static final class ObjectConst extends Node {
      final Object value;

      ObjectConst(Object value) {
         this.value = value;
      }

      @Override
      float eval(MolangEnv env) {
         env.object = this.value;
         return 0.0F;
      }

      @Override
      boolean isConstant() {
         return true;
      }
   }

   static final class Or extends Nodes.Numeric {
      final Node left;
      final Node right;

      Or(Node left, Node right) {
         this.left = left;
         this.right = right;
      }

      @Override
      float eval(MolangEnv env) {
         boolean result = truthy(this.left.eval(env), env) || truthy(this.right.eval(env), env);
         env.object = null;
         return result ? 1.0F : 0.0F;
      }
   }

   static final class Programs extends Node {
      final MolangProgram[] parts;

      Programs(MolangProgram[] parts) {
         this.parts = parts;
      }

      @Override
      float eval(MolangEnv env) {
         float value = 0.0F;
         env.object = null;

         for (MolangProgram part : this.parts) {
            value = part.evaluate(env);
         }

         return value;
      }
   }

   static final class Query extends Node {
      final QueryFunction function;
      final QueryArgs args;

      Query(QueryFunction function, QueryArgs args) {
         this.function = function;
         this.args = args;
      }

      @Override
      float eval(MolangEnv env) {
         env.pending = null;
         float value = this.function.call(env, this.args);
         env.object = env.pending;
         env.pending = null;
         return fix(value);
      }
   }

   static final class Return extends Node {
      final Node value;

      Return(Node value) {
         this.value = value;
      }

      @Override
      float eval(MolangEnv env) {
         float result = this.value.eval(env);
         if (env.flow == 0) {
            env.returnValue = result;
            env.returnObject = env.object;
            env.flow = 3;
         }

         return result;
      }
   }

   static final class Sequence extends Node {
      final Node[] statements;

      Sequence(Node[] statements) {
         this.statements = statements;
      }

      @Override
      float eval(MolangEnv env) {
         for (int i = 0; i < this.statements.length; i++) {
            this.statements[i].eval(env);
            if (env.flow != 0) {
               break;
            }
         }

         env.object = null;
         return 0.0F;
      }

      @Override
      boolean mayBeObject() {
         return false;
      }
   }

   static final class Subtract extends Nodes.Numeric {
      final Node left;
      final Node right;

      Subtract(Node left, Node right) {
         this.left = left;
         this.right = right;
      }

      @Override
      float eval(MolangEnv env) {
         float a = this.left.eval(env);
         float b = this.right.eval(env);
         env.object = null;
         return fix(a - b);
      }
   }

   static final class Ternary extends Node {
      final Node condition;
      final Node then;
      final @Nullable Node otherwise;

      Ternary(Node condition, Node then, @Nullable Node otherwise) {
         this.condition = condition;
         this.then = then;
         this.otherwise = otherwise;
      }

      @Override
      float eval(MolangEnv env) {
         if (truthy(this.condition.eval(env), env)) {
            return this.then.eval(env);
         } else if (this.otherwise == null) {
            env.object = null;
            return 0.0F;
         } else {
            return this.otherwise.eval(env);
         }
      }

      @Override
      boolean mayBeObject() {
         return this.then.mayBeObject() || this.otherwise != null && this.otherwise.mayBeObject();
      }
   }

   static final class This extends Node {
      @Override
      float eval(MolangEnv env) {
         env.object = null;
         return env.thisValue;
      }

      @Override
      boolean mayBeObject() {
         return false;
      }
   }

   static final class VariableRead extends Node {
      final int scope;
      final int[] path;
      final boolean quiet;
      final String text;
      final MolangDiagnostics diagnostics;

      VariableRead(int scope, int[] path, boolean quiet, String text, MolangDiagnostics diagnostics) {
         this.scope = scope;
         this.path = path;
         this.quiet = quiet;
         this.text = text;
         this.diagnostics = diagnostics;
      }

      Nodes.VariableRead quiet() {
         return new Nodes.VariableRead(this.scope, this.path, true, this.text, this.diagnostics);
      }

      @Override
      float eval(MolangEnv env) {
         VariableStore store = Nodes.store(env, this.scope);

         int index;
         for (index = store.index(this.path[0]); index < 0 && store.parent() != null; index = store.index(this.path[0])) {
            store = store.parent();
         }

         if (index < 0) {
            return this.missing(env);
         }

         float value = store.valueAt(index);
         Object object = store.objectAt(index);

         for (int i = 1; i < this.path.length; i++) {
            if (!(object instanceof MolangStruct struct)) {
               return this.missing(env);
            }

            int member = struct.members.index(this.path[i]);
            if (member < 0) {
               return this.missing(env);
            }

            value = struct.members.valueAt(member);
            object = struct.members.objectAt(member);
         }

         env.object = object;
         return value;
      }

      boolean isSet(MolangEnv env) {
         VariableStore store = Nodes.store(env, this.scope);

         int index;
         for (index = store.index(this.path[0]); index < 0 && store.parent() != null; index = store.index(this.path[0])) {
            store = store.parent();
         }

         if (index < 0) {
            return false;
         }

         Object object = store.objectAt(index);

         for (int i = 1; i < this.path.length; i++) {
            if (!(object instanceof MolangStruct struct)) {
               return false;
            }

            int member = struct.members.index(this.path[i]);
            if (member < 0) {
               return false;
            }

            object = struct.members.objectAt(member);
         }

         return true;
      }

      private float missing(MolangEnv env) {
         if (!this.quiet) {
            this.diagnostics.runtime(this.text);
         }

         env.object = null;
         return 0.0F;
      }
   }

   static final class VariableWrite extends Node {
      final int scope;
      final int[] path;
      final Node value;

      VariableWrite(int scope, int[] path, Node value) {
         this.scope = scope;
         this.path = path;
         this.value = value;
      }

      @Override
      float eval(MolangEnv env) {
         float number = this.value.eval(env);
         Object object = env.object;
         if (env.flow != 0) {
            return number;
         }

         if (object instanceof MolangStruct struct) {
            object = struct.copy();
         }

         assign(env, this.scope, this.path, number, object);
         env.object = object;
         return number;
      }

      static void assign(MolangEnv env, int scope, int[] path, float number, @Nullable Object object) {
         VariableStore target = Nodes.store(env, scope);

         for (int i = 0; i < path.length - 1; i++) {
            MolangStruct struct;
            if (target.object(path[i]) instanceof MolangStruct found) {
               struct = found;
            } else {
               struct = new MolangStruct();
               target.set(path[i], 0.0F, struct);
            }

            target = struct.members;
         }

         target.set(path[path.length - 1], number, object);
      }
   }
}
