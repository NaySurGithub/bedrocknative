package net.bedrocknative.molang;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MolangCompiler {
   private final boolean absoluteDivisor;
   private final MolangDiagnostics diagnostics;
   private final MolangEnv foldEnv = new MolangEnv();
   private final Set<String> queries = new LinkedHashSet<>();
   private final Set<String> reads = new LinkedHashSet<>();
   private final Set<String> writes = new LinkedHashSet<>();

   private MolangCompiler(MolangVersion version, MolangDiagnostics diagnostics) {
      this.absoluteDivisor = !version.atLeast(MolangVersion.DIVIDE_BY_NEGATIVE_VALUE);
      this.diagnostics = diagnostics;
   }

   static MolangProgram compile(String source, MolangVersion version, boolean lowerCaseStrings) {
      MolangDiagnostics diagnostics = new MolangDiagnostics(source);

      try {
         Expr.Program program = MolangParser.parse(source, version, lowerCaseStrings);
         MolangCompiler compiler = new MolangCompiler(version, diagnostics);
         Node root = (Node)(program.statements().isEmpty()
            ? new Nodes.Const(0.0F)
            : (
               program.statements().size() == 1 && !program.complex()
                  ? compiler.node(program.statements().getFirst())
                  : new Nodes.Sequence(compiler.nodes(program.statements()))
            ));
         return new MolangProgram(source, root, program.complex(), diagnostics, names(compiler.queries), names(compiler.reads), names(compiler.writes));
      } catch (MolangException var7) {
         diagnostics.aborted(var7.getMessage(), var7.position);
      } catch (StackOverflowError | RuntimeException var8) {
         diagnostics.aborted(var8.toString(), 0);
      }

      return new MolangProgram(source, new Nodes.Const(0.0F), false, diagnostics, MolangProgram.NO_NAMES, MolangProgram.NO_NAMES, MolangProgram.NO_NAMES);
   }

   private static String[] names(Set<String> names) {
      return names.isEmpty() ? MolangProgram.NO_NAMES : names.toArray(new String[0]);
   }

   private Node[] nodes(List<Expr> expressions) {
      Node[] nodes = new Node[expressions.size()];

      for (int i = 0; i < nodes.length; i++) {
         nodes[i] = this.node(expressions.get(i));
      }

      return nodes;
   }

   private Node node(Expr expr) {
      return (Node)(switch (expr) {
         case Expr.Num num -> new Nodes.Const(num.value());
         case Expr.Str str -> new Nodes.ObjectConst(str.value().intern());
         case Expr.This ignored -> new Nodes.This();
         case Expr.Name name -> this.name(name, false);
         case Expr.Call call -> this.call(call);
         case Expr.Index index -> new Nodes.Index(this.node(index.target()), this.node(index.index()));
         case Expr.Member member -> new Nodes.Member(this.node(member.target()), MolangNames.id(member.name()));
         case Expr.Unary unary -> this.fold(
            (Node)(unary.op().equals("-") ? new Nodes.Negate(this.node(unary.operand())) : new Nodes.Not(this.node(unary.operand())))
         );
         case Expr.Binary binary -> this.binary(binary);
         case Expr.Ternary ternary -> this.ternary(ternary);
         case Expr.Coalesce coalesce -> new Nodes.Coalesce(
            coalesce.left() instanceof Expr.Name name ? this.name(name, true) : this.node(coalesce.left()), this.node(coalesce.right())
         );
         case Expr.Assign assign -> this.assign(assign);
         case Expr.Arrow arrow -> new Nodes.Arrow(this.node(arrow.target()), this.node(arrow.body()));
         case Expr.Block block -> new Nodes.Sequence(this.nodes(block.statements()));
         case Expr.Return ret -> new Nodes.Return(this.node(ret.value()));
         case Expr.Break ignoredx -> new Nodes.Jump(1);
         case Expr.Continue ignoredxx -> new Nodes.Jump(2);
         case Expr.Loop loop -> new Nodes.Loop(this.node(loop.count()), this.node(loop.body()));
         case Expr.ForEach forEach -> this.forEach(forEach);
         case Expr.Program program -> new Nodes.Sequence(this.nodes(program.statements()));
         default -> throw new MatchException(null, null);
      });
   }

   private Node name(Expr.Name name, boolean quiet) {
      List<String> parts = name.parts();
      String namespace = parts.getFirst();
      int scope = scope(namespace);
      if (scope >= 0 && parts.size() >= 2) {
         if (scope == 0 && !quiet) {
            this.reads.add(parts.get(1));
         }

         Nodes.VariableRead read = new Nodes.VariableRead(scope, ids(parts), false, String.join(".", parts), this.diagnostics);
         return quiet ? read.quiet() : read;
      } else {
         if (parts.size() == 2) {
            switch (namespace) {
               case "query":
                  return this.query(parts.get(1), List.of(), name.position());
               case "math":
                  if (parts.get(1).equals("pi")) {
                     return new Nodes.Const((float) Math.PI);
                  }

                  return this.error("math." + parts.get(1) + " needs arguments", name.position());
               case "geometry":
               case "material":
               case "texture":
                  return new Nodes.ObjectConst(new MolangResources.Ref(namespace, parts.get(1)));
               case "array":
                  return new Nodes.ArrayRef(parts.get(1));
            }
         }

         return this.error("Unknown name " + String.join(".", parts), name.position());
      }
   }

   private Node call(Expr.Call call) {
      List<String> parts = call.parts();
      if (parts.size() == 2 && parts.getFirst().equals("query")) {
         return this.query(parts.get(1), call.args(), call.position());
      } else if (parts.size() == 2 && parts.getFirst().equals("math")) {
         MathFunctions.Function function = MathFunctions.get(parts.get(1));
         if (function == null) {
            return this.error("Unknown function math." + parts.get(1), call.position());
         } else if (call.args().size() != function.arity()) {
            return this.error("math." + parts.get(1) + " takes " + function.arity() + " arguments", call.position());
         } else {
            Node[] args = this.nodes(call.args());
            Node node = new Nodes.MathCall(function, args.length > 0 ? args[0] : null, args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);
            return function.random() == null ? this.fold(node) : node;
         }
      } else {
         return this.error("Unknown function " + String.join(".", parts), call.position());
      }
   }

   private Node query(String name, List<Expr> args, int position) {
      this.queries.add(name);
      QueryRegistry.Entry entry = QueryRegistry.get(name);
      if (entry == null) {
         return this.error("Unknown query." + name, position);
      } else {
         return (Node)(!entry.accepts(args.size())
            ? this.error("query." + name + " doesn't take " + args.size() + " arguments", position)
            : new Nodes.Query(entry.function(), args.isEmpty() ? QueryArgs.NONE : new QueryArgs(this.nodes(args))));
      }
   }

   private Node binary(Expr.Binary binary) {
      Node left = this.node(binary.left());
      Node right = this.node(binary.right());
      String var5 = binary.op();

      Node node = (Node)(switch (var5) {
         case "+" -> new Nodes.Add(left, right);
         case "-" -> new Nodes.Subtract(left, right);
         case "*" -> new Nodes.Multiply(left, right);
         case "/" -> new Nodes.Divide(left, right, this.absoluteDivisor && !right.isConstant());
         case "<" -> new Nodes.Compare(left, right, 0);
         case "<=" -> new Nodes.Compare(left, right, 1);
         case ">" -> new Nodes.Compare(left, right, 2);
         case ">=" -> new Nodes.Compare(left, right, 3);
         case "==" -> new Nodes.Equals(left, right, false, this.diagnostics);
         case "!=" -> new Nodes.Equals(left, right, true, this.diagnostics);
         case "&&" -> new Nodes.And(left, right);
         default -> new Nodes.Or(left, right);
      });
      if (!binary.op().equals("&&") && !binary.op().equals("||")) {
         return this.fold(node);
      } else {
         return left.isConstant() && right.isConstant() ? this.fold(node) : node;
      }
   }

   private Node ternary(Expr.Ternary ternary) {
      Node condition = this.node(ternary.condition());
      Node then = this.node(ternary.then());
      Node otherwise = ternary.otherwise() != null ? this.node(ternary.otherwise()) : null;
      if (condition.isConstant()) {
         boolean truth = Node.truthy(condition.eval(this.foldEnv), this.foldEnv);
         return (Node)(truth ? then : (otherwise != null ? otherwise : new Nodes.Const(0.0F)));
      } else {
         return new Nodes.Ternary(condition, then, otherwise);
      }
   }

   private Node assign(Expr.Assign assign) {
      Expr.Name target = (Expr.Name)assign.target();
      List<String> parts = target.parts();
      int scope = scope(parts.getFirst());
      if (scope < 0 || parts.size() < 2) {
         return this.error("Cannot assign to " + String.join(".", parts), assign.position());
      } else if (scope == 2) {
         this.diagnostics.error("context." + parts.get(1) + " is read-only", assign.position());
         return this.node(assign.value());
      } else {
         if (scope == 0) {
            this.writes.add(parts.get(1));
         }

         return new Nodes.VariableWrite(scope, ids(parts), this.node(assign.value()));
      }
   }

   private Node forEach(Expr.ForEach forEach) {
      if (forEach.variable() instanceof Expr.Name name
         && scope(name.parts().getFirst()) >= 0
         && name.parts().size() >= 2
         && scope(name.parts().getFirst()) != 2) {
         if (scope(name.parts().getFirst()) == 0) {
            this.writes.add(name.parts().get(1));
         }

         return new Nodes.ForEach(scope(name.parts().getFirst()), ids(name.parts()), this.node(forEach.array()), this.node(forEach.body()));
      } else {
         return this.error("for_each needs a variable.* or temp.* name", 0);
      }
   }

   private Node fold(Node node) {
      boolean constant = switch (node) {
         case Nodes.Negate negate -> negate.operand.isConstant();
         case Nodes.Not not -> not.operand.isConstant();
         case Nodes.Add add -> add.left.isConstant() && add.right.isConstant();
         case Nodes.Subtract subtract -> subtract.left.isConstant() && subtract.right.isConstant();
         case Nodes.Multiply multiply -> multiply.left.isConstant() && multiply.right.isConstant();
         case Nodes.Divide divide -> divide.left.isConstant() && divide.right.isConstant();
         case Nodes.Compare compare -> compare.left.isConstant() && compare.right.isConstant();
         case Nodes.Equals equals -> equals.left.isConstant() && equals.right.isConstant();
         case Nodes.And and -> and.left.isConstant() && and.right.isConstant();
         case Nodes.Or or -> or.left.isConstant() && or.right.isConstant();
         case Nodes.MathCall math -> (math.a == null || math.a.isConstant())
            && (math.b == null || math.b.isConstant())
            && (math.c == null || math.c.isConstant());
         default -> false;
      };
      if (!constant) {
         return node;
      } else {
         float value = node.eval(this.foldEnv);
         return new Nodes.Const(value);
      }
   }

   private Node error(String message, int position) {
      this.diagnostics.error(message, position);
      return new Nodes.Const(0.0F);
   }

   private static int scope(String namespace) {
      return switch (namespace) {
         case "variable" -> 0;
         case "temp" -> 1;
         case "context" -> 2;
         default -> -1;
      };
   }

   private static int[] ids(List<String> parts) {
      int[] ids = new int[parts.size() - 1];

      for (int i = 1; i < parts.size(); i++) {
         ids[i - 1] = MolangNames.id(parts.get(i));
      }

      return ids;
   }
}
