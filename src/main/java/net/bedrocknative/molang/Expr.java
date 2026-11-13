package net.bedrocknative.molang;

import java.util.List;
import org.jspecify.annotations.Nullable;

sealed interface Expr
   permits Expr.Num,
   Expr.Str,
   Expr.This,
   Expr.Name,
   Expr.Call,
   Expr.Index,
   Expr.Member,
   Expr.Unary,
   Expr.Binary,
   Expr.Ternary,
   Expr.Coalesce,
   Expr.Assign,
   Expr.Arrow,
   Expr.Block,
   Expr.Return,
   Expr.Break,
   Expr.Continue,
   Expr.Loop,
   Expr.ForEach,
   Expr.Program {
   record Arrow(Expr target, Expr body) implements Expr {
   }

   record Assign(Expr target, Expr value, int position) implements Expr {
   }

   record Binary(String op, Expr left, Expr right) implements Expr {
   }

   record Block(List<Expr> statements) implements Expr {
   }

   record Break() implements Expr {
   }

   record Call(List<String> parts, List<Expr> args, int position) implements Expr {
   }

   record Coalesce(Expr left, Expr right) implements Expr {
   }

   record Continue() implements Expr {
   }

   record ForEach(Expr variable, Expr array, Expr body) implements Expr {
   }

   record Index(Expr target, Expr index) implements Expr {
   }

   record Loop(Expr count, Expr body) implements Expr {
   }

   record Member(Expr target, String name) implements Expr {
   }

   record Name(List<String> parts, int position) implements Expr {
   }

   record Num(float value) implements Expr {
   }

   record Program(List<Expr> statements, boolean complex) implements Expr {
   }

   record Return(Expr value) implements Expr {
   }

   record Str(String value) implements Expr {
   }

   record Ternary(Expr condition, Expr then, @Nullable Expr otherwise) implements Expr {
   }

   record This() implements Expr {
   }

   record Unary(String op, Expr operand) implements Expr {
   }
}
