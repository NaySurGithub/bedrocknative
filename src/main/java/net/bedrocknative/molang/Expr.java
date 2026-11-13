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
   public record Arrow(Expr target, Expr body) implements Expr {
   }

   public record Assign(Expr target, Expr value, int position) implements Expr {
   }

   public record Binary(String op, Expr left, Expr right) implements Expr {
   }

   public record Block(List<Expr> statements) implements Expr {
   }

   public record Break() implements Expr {
   }

   public record Call(List<String> parts, List<Expr> args, int position) implements Expr {
   }

   public record Coalesce(Expr left, Expr right) implements Expr {
   }

   public record Continue() implements Expr {
   }

   public record ForEach(Expr variable, Expr array, Expr body) implements Expr {
   }

   public record Index(Expr target, Expr index) implements Expr {
   }

   public record Loop(Expr count, Expr body) implements Expr {
   }

   public record Member(Expr target, String name) implements Expr {
   }

   public record Name(List<String> parts, int position) implements Expr {
   }

   public record Num(float value) implements Expr {
   }

   public record Program(List<Expr> statements, boolean complex) implements Expr {
   }

   public record Return(Expr value) implements Expr {
   }

   public record Str(String value) implements Expr {
   }

   public record Ternary(Expr condition, Expr then, @Nullable Expr otherwise) implements Expr {
   }

   public record This() implements Expr {
   }

   public record Unary(String op, Expr operand) implements Expr {
   }
}
