package net.bedrocknative.molang;

import java.util.ArrayList;
import java.util.List;

final class MolangParser {
   private static final int MAX_DEPTH = 256;
   private final List<MolangLexer.Token> tokens;
   private final boolean rightAssociativeTernary;
   private final boolean modernLogic;
   private int index;
   private int depth;

   private MolangParser(List<MolangLexer.Token> tokens, MolangVersion version) {
      this.tokens = tokens;
      this.rightAssociativeTernary = version.atLeast(MolangVersion.CONDITIONAL_OPERATOR_ASSOCIATIVITY);
      this.modernLogic = version.atLeast(MolangVersion.COMPARISON_AND_LOGICAL_OPERATOR_PRECEDENCE);
   }

   static Expr.Program parse(String source, MolangVersion version, boolean lowerCaseStrings) {
      return new MolangParser(MolangLexer.lex(source, lowerCaseStrings), version).program();
   }

   private Expr.Program program() {
      List<Expr> statements = new ArrayList<>();
      boolean complex = false;

      while (this.peek().kind() != MolangLexer.Kind.EOF) {
         if (this.match(";")) {
            complex = true;
         } else {
            statements.add(this.expression());
            if (this.match(";")) {
               complex = true;
            } else if (this.peek().kind() != MolangLexer.Kind.EOF) {
               throw this.error("Expected ';'");
            }
         }
      }

      return new Expr.Program(statements, complex);
   }

   private Expr expression() {
      if (++this.depth > 256) {
         throw this.error("Expression nested too deeply");
      }

      try {
         return this.assignment();
      } finally {
         this.depth--;
      }
   }

   private Expr assignment() {
      Expr left = this.coalesce();
      if (this.peek().is("=")) {
         int position = this.next().position();
         if (!(left instanceof Expr.Name)) {
            throw new MolangException("Cannot assign to this", position);
         } else {
            return new Expr.Assign(left, this.assignment(), position);
         }
      } else {
         return left;
      }
   }

   private Expr coalesce() {
      Expr left = this.conditional();

      while (this.match("??")) {
         left = new Expr.Coalesce(left, this.conditional());
      }

      return left;
   }

   private Expr conditional() {
      Expr condition = this.logical();
      if (this.rightAssociativeTernary) {
         if (this.match("?")) {
            Expr then = this.expression();
            Expr otherwise = this.match(":") ? this.conditional() : null;
            return new Expr.Ternary(condition, then, otherwise);
         } else {
            return condition;
         }
      } else {
         while (this.match("?")) {
            Expr then = this.logical();
            Expr otherwise = this.match(":") ? this.logical() : null;
            condition = new Expr.Ternary(condition, then, otherwise);
         }

         return condition;
      }
   }

   private Expr logical() {
      return this.modernLogic ? this.or() : this.legacyAnd();
   }

   private Expr or() {
      Expr left = this.and();

      while (this.match("||")) {
         left = new Expr.Binary("||", left, this.and());
      }

      return left;
   }

   private Expr and() {
      Expr left = this.equality();

      while (this.match("&&")) {
         left = new Expr.Binary("&&", left, this.equality());
      }

      return left;
   }

   private Expr equality() {
      Expr left = this.relational();

      while (this.peek().is("==") || this.peek().is("!=")) {
         String op = this.next().text();
         left = new Expr.Binary(op, left, this.relational());
      }

      return left;
   }

   private Expr relational() {
      Expr left = this.additive();

      while (this.peek().is("<") || this.peek().is("<=") || this.peek().is(">") || this.peek().is(">=")) {
         String op = this.next().text();
         left = new Expr.Binary(op, left, this.additive());
      }

      return left;
   }

   private Expr legacyAnd() {
      Expr left = this.legacyOr();

      while (this.match("&&")) {
         left = new Expr.Binary("&&", left, this.legacyOr());
      }

      return left;
   }

   private Expr legacyOr() {
      Expr left = this.legacyComparison();

      while (this.match("||")) {
         left = new Expr.Binary("||", left, this.legacyComparison());
      }

      return left;
   }

   private Expr legacyComparison() {
      Expr left = this.additive();

      while (this.peek().is("<") || this.peek().is("<=") || this.peek().is(">") || this.peek().is(">=") || this.peek().is("==") || this.peek().is("!=")) {
         String op = this.next().text();
         left = new Expr.Binary(op, left, this.additive());
      }

      return left;
   }

   private Expr additive() {
      Expr left = this.multiplicative();

      while (this.peek().is("+") || this.peek().is("-")) {
         String op = this.next().text();
         left = new Expr.Binary(op, left, this.multiplicative());
      }

      return left;
   }

   private Expr multiplicative() {
      Expr left = this.unary();

      while (this.peek().is("*") || this.peek().is("/")) {
         String op = this.next().text();
         left = new Expr.Binary(op, left, this.unary());
      }

      return left;
   }

   private Expr unary() {
      if (this.peek().is("!") || this.peek().is("-")) {
         String op = this.next().text();
         if (++this.depth > 256) {
            throw this.error("Expression nested too deeply");
         }

         try {
            return new Expr.Unary(op, this.unary());
         } finally {
            this.depth--;
         }
      } else {
         return this.match("+") ? this.unary() : this.arrow();
      }
   }

   private Expr arrow() {
      Expr left = this.postfix();
      return this.match("->") ? new Expr.Arrow(left, this.postfix()) : left;
   }

   private Expr postfix() {
      Expr expr = this.primary();

      while (true) {
         while (this.match("[")) {
            Expr index = this.expression();
            this.expect("]");
            expr = new Expr.Index(expr, index);
         }

         if (!this.peek().is(".") || this.peekAt(1).kind() != MolangLexer.Kind.IDENT) {
            return expr;
         }

         this.next();
         expr = new Expr.Member(expr, this.next().text());
      }
   }

   private Expr primary() {
      MolangLexer.Token token = this.next();
      switch (token.kind()) {
         case NUMBER:
            return new Expr.Num(token.number());
         case STRING:
            return new Expr.Str(token.text());
         case OP:
            if (token.is("(")) {
               Expr inner = this.expression();
               this.expect(")");
               return inner;
            } else {
               if (token.is("{")) {
                  return this.block();
               }

               throw new MolangException("Unexpected '" + token.text() + "'", token.position());
            }
         case IDENT:
            return this.identifier(token);
         default:
            throw new MolangException("Unexpected end of expression", token.position());
      }
   }

   private Expr identifier(MolangLexer.Token token) {
      switch (token.text()) {
         case "true":
            return new Expr.Num(1.0F);
         case "false":
            return new Expr.Num(0.0F);
         case "this":
            return new Expr.This();
         case "return":
            return new Expr.Return(
               !this.peek().is(";") && !this.peek().is("}") && this.peek().kind() != MolangLexer.Kind.EOF ? this.expression() : new Expr.Num(0.0F)
            );
         case "break":
            return new Expr.Break();
         case "continue":
            return new Expr.Continue();
         case "loop":
            if (this.peek().is("(")) {
               this.next();
               Expr count = this.expression();
               this.expect(",");
               Expr body = this.expression();
               this.expect(")");
               return new Expr.Loop(count, body);
            }
            break;
         case "for_each":
            if (this.peek().is("(")) {
               this.next();
               Expr variable = this.expression();
               this.expect(",");
               Expr array = this.expression();
               this.expect(",");
               Expr body = this.expression();
               this.expect(")");
               return new Expr.ForEach(variable, array, body);
            }
      }

      List<String> parts = new ArrayList<>();
      parts.add(token.text());

      while (this.peek().is(".") && this.peekAt(1).kind() == MolangLexer.Kind.IDENT) {
         if (parts.size() >= 2 && isCallNamespace(parts.getFirst())) {
            if (!isResourceNamespace(parts.getFirst())) {
               break;
            }

            this.next();
            parts.set(1, parts.get(1) + "." + this.next().text());
         } else {
            this.next();
            parts.add(this.next().text());
         }
      }

      if (!this.match("(")) {
         return new Expr.Name(List.copyOf(parts), token.position());
      }

      List<Expr> args = new ArrayList<>();
      if (!this.match(")")) {
         do {
            args.add(this.expression());
         } while (this.match(","));

         this.expect(")");
      }

      return new Expr.Call(List.copyOf(parts), args, token.position());
   }

   private static boolean isCallNamespace(String namespace) {
      return namespace.equals("query") || namespace.equals("math") || isResourceNamespace(namespace);
   }

   private static boolean isResourceNamespace(String namespace) {
      return namespace.equals("geometry") || namespace.equals("material") || namespace.equals("texture") || namespace.equals("array");
   }

   private Expr.Block block() {
      List<Expr> statements = new ArrayList<>();

      while (!this.match("}")) {
         if (this.peek().kind() == MolangLexer.Kind.EOF) {
            throw this.error("Expected '}'");
         }

         if (!this.match(";")) {
            statements.add(this.expression());
            if (!this.match(";") && !this.peek().is("}")) {
               throw this.error("Expected ';'");
            }
         }
      }

      return new Expr.Block(statements);
   }

   private MolangLexer.Token peek() {
      return this.tokens.get(this.index);
   }

   private MolangLexer.Token peekAt(int offset) {
      return this.tokens.get(Math.min(this.tokens.size() - 1, this.index + offset));
   }

   private MolangLexer.Token next() {
      MolangLexer.Token token = this.tokens.get(this.index);
      if (token.kind() != MolangLexer.Kind.EOF) {
         this.index++;
      }

      return token;
   }

   private boolean match(String op) {
      if (this.peek().is(op)) {
         this.index++;
         return true;
      } else {
         return false;
      }
   }

   private void expect(String op) {
      if (!this.match(op)) {
         throw this.error("Expected '" + op + "'");
      }
   }

   private MolangException error(String message) {
      MolangLexer.Token token = this.peek();
      return new MolangException(message + (token.kind() == MolangLexer.Kind.EOF ? " at the end" : " before '" + token.text() + "'"), token.position());
   }
}
