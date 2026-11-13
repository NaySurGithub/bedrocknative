package net.bedrocknative.world.block.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import net.bedrocknative.BedrockNative;

public final class StateCondition {
   private static final Map<String, StateCondition.Node> PARSED = new ConcurrentHashMap<>();
   private static final StateCondition.Node ZERO = states -> 0.0;

   private StateCondition() {
   }

   public static boolean test(String expression, Map<String, ?> states) {
      return truthy(evaluate(expression, states));
   }

   public static Object evaluate(String expression, Map<String, ?> states) {
      return PARSED.computeIfAbsent(expression, StateCondition::parse).eval(states);
   }

   private static StateCondition.Node parse(String expression) {
      try {
         StateCondition.Parser parser = new StateCondition.Parser(expression);
         StateCondition.Node node = parser.statements();
         if (!parser.atEnd()) {
            throw new IllegalArgumentException("unexpected '" + parser.rest() + "'");
         } else {
            return node;
         }
      } catch (RuntimeException e) {
         BedrockNative.LOGGER.warn("Unsupported block condition '{}': {}", expression, e.getMessage());
         return ZERO;
      }
   }

   static boolean truthy(Object value) {
      return value instanceof Double number ? number != 0.0 : value instanceof String text && !text.isEmpty();
   }

   private static double number(Object value) {
      return value instanceof Double number ? number : 0.0;
   }

   private static Object stateValue(Map<String, ?> states, String name) {
      Object value = states.get(name);

      return switch (value) {
         case null -> 0.0;
         case Number number -> number.doubleValue();
         case Boolean bool -> bool ? 1.0 : 0.0;
         default -> value.toString();
      };
   }

   private static boolean equal(Object left, Object right) {
      return left instanceof String a && right instanceof String b
         ? a.equals(b)
         : left instanceof Double da && right instanceof Double db && Math.abs(da - db) < 1.0E-6;
   }

   @FunctionalInterface
   private interface Node {
      Object eval(Map<String, ?> var1);
   }

   private static final class Parser {
      private final String text;
      private int pos;

      Parser(String text) {
         this.text = text;
      }

      boolean atEnd() {
         this.skipSpace();
         return this.pos >= this.text.length();
      }

      String rest() {
         return this.text.substring(this.pos);
      }

      StateCondition.Node statements() {
         StateCondition.Node result = StateCondition.ZERO;

         do {
            this.skipSpace();
            if (this.atEnd()) {
               break;
            }

            this.keyword("return");
            result = this.ternary();
         } while (this.eat(";"));

         return result;
      }

      StateCondition.Node ternary() {
         StateCondition.Node condition = this.coalesce();
         if (this.eat("?")) {
            StateCondition.Node then = this.ternary();
            StateCondition.Node otherwise = this.eat(":") ? this.ternary() : StateCondition.ZERO;
            return states -> StateCondition.truthy(condition.eval(states)) ? then.eval(states) : otherwise.eval(states);
         } else {
            return condition;
         }
      }

      StateCondition.Node coalesce() {
         StateCondition.Node left = this.or();

         while (this.eat("??")) {
            StateCondition.Node first = left;
            StateCondition.Node right = this.or();
            left = states -> {
               Object value = first.eval(states);
               return value != null ? value : right.eval(states);
            };
         }

         return left;
      }

      StateCondition.Node or() {
         StateCondition.Node left = this.and();

         while (this.eat("||")) {
            StateCondition.Node first = left;
            StateCondition.Node right = this.and();
            left = states -> !StateCondition.truthy(first.eval(states)) && !StateCondition.truthy(right.eval(states)) ? 0.0 : 1.0;
         }

         return left;
      }

      StateCondition.Node and() {
         StateCondition.Node left = this.equality();

         while (this.eat("&&")) {
            StateCondition.Node first = left;
            StateCondition.Node right = this.equality();
            left = states -> StateCondition.truthy(first.eval(states)) && StateCondition.truthy(right.eval(states)) ? 1.0 : 0.0;
         }

         return left;
      }

      StateCondition.Node equality() {
         StateCondition.Node left = this.relational();

         while (true) {
            StateCondition.Node first = left;
            if (this.eat("==")) {
               StateCondition.Node right = this.relational();
               left = states -> StateCondition.equal(first.eval(states), right.eval(states)) ? 1.0 : 0.0;
            } else {
               if (!this.eat("!=")) {
                  return left;
               }

               StateCondition.Node right = this.relational();
               left = states -> StateCondition.equal(first.eval(states), right.eval(states)) ? 0.0 : 1.0;
            }
         }
      }

      StateCondition.Node relational() {
         StateCondition.Node left = this.additive();

         while (true) {
            StateCondition.Node first = left;
            String op = this.eat("<=") ? "<=" : (this.eat(">=") ? ">=" : (this.eat("<") ? "<" : (this.eat(">") ? ">" : null)));
            if (op == null) {
               return left;
            }

            StateCondition.Node right = this.additive();
            left = states -> {
               double a = StateCondition.number(first.eval(states));
               double b = StateCondition.number(right.eval(states));

               boolean result = switch (op) {
                  case "<=" -> a <= b;
                  case ">=" -> a >= b;
                  case "<" -> a < b;
                  default -> a > b;
               };
               return result ? 1.0 : 0.0;
            };
         }
      }

      StateCondition.Node additive() {
         StateCondition.Node left = this.multiplicative();

         while (true) {
            StateCondition.Node first = left;
            if (this.eat("+")) {
               StateCondition.Node right = this.multiplicative();
               left = states -> StateCondition.number(first.eval(states)) + StateCondition.number(right.eval(states));
            } else {
               if (!this.peekMinus()) {
                  return left;
               }

               this.pos++;
               StateCondition.Node right = this.multiplicative();
               left = states -> StateCondition.number(first.eval(states)) - StateCondition.number(right.eval(states));
            }
         }
      }

      StateCondition.Node multiplicative() {
         StateCondition.Node left = this.unary();

         while (true) {
            StateCondition.Node first = left;
            if (this.eat("*")) {
               StateCondition.Node right = this.unary();
               left = states -> StateCondition.number(first.eval(states)) * StateCondition.number(right.eval(states));
            } else {
               if (!this.eat("/")) {
                  return left;
               }

               StateCondition.Node right = this.unary();
               left = states -> {
                  double divisor = StateCondition.number(right.eval(states));
                  return divisor == 0.0 ? 0.0 : StateCondition.number(first.eval(states)) / divisor;
               };
            }
         }
      }

      StateCondition.Node unary() {
         if (this.eat("!")) {
            StateCondition.Node operand = this.unary();
            return states -> StateCondition.truthy(operand.eval(states)) ? 0.0 : 1.0;
         } else if (this.eat("-")) {
            StateCondition.Node operand = this.unary();
            return states -> -StateCondition.number(operand.eval(states));
         } else {
            return this.primary();
         }
      }

      StateCondition.Node primary() {
         this.skipSpace();
         if (this.pos >= this.text.length()) {
            throw new IllegalArgumentException("unexpected end");
         }

         char c = this.text.charAt(this.pos);
         if (c == '(') {
            this.pos++;
            StateCondition.Node inner = this.ternary();
            this.expect(")");
            return inner;
         }

         if (c == '\'' || c == '"') {
            String value = this.string();
            return states -> value;
         }

         if (Character.isDigit(c) || c == '.') {
            int start = this.pos;

            while (this.pos < this.text.length() && (Character.isDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '.')) {
               this.pos++;
            }

            double value = Double.parseDouble(this.text.substring(start, this.pos));
            if (this.pos < this.text.length() && (this.text.charAt(this.pos) == 'f' || this.text.charAt(this.pos) == 'F')) {
               this.pos++;
            }

            return states -> value;
         } else {
            if (!Character.isLetter(c) && c != '_') {
               throw new IllegalArgumentException("unexpected '" + c + "'");
            }

            String name = this.identifier().toLowerCase(Locale.ROOT);
            List<StateCondition.Node> arguments = new ArrayList<>();
            if (this.eat("(") && !this.eat(")")) {
               do {
                  arguments.add(this.ternary());
               } while (this.eat(","));

               this.expect(")");
            }

            return this.call(name, arguments);
         }
      }

      private StateCondition.Node call(String name, List<StateCondition.Node> arguments) {
         return switch (name) {
            case "true" -> states -> 1.0;
            case "false" -> states -> 0.0;
            case "query.block_state", "q.block_state", "query.block_property", "q.block_property" -> {
               StateCondition.Node argument = arguments.isEmpty() ? StateCondition.ZERO : arguments.getFirst();
               yield states -> StateCondition.stateValue(states, String.valueOf(argument.eval(states)));
            }
            case "query.has_block_state", "q.has_block_state", "query.has_block_property", "q.has_block_property" -> {
               StateCondition.Node argument = arguments.isEmpty() ? StateCondition.ZERO : arguments.getFirst();
               yield states -> states.containsKey(String.valueOf(argument.eval(states))) ? 1.0 : 0.0;
            }
            case "math.abs" -> unaryMath(arguments, Math::abs);
            case "math.floor" -> unaryMath(arguments, Math::floor);
            case "math.ceil" -> unaryMath(arguments, Math::ceil);
            case "math.round" -> unaryMath(arguments, value -> Math.round(value));
            case "math.trunc" -> unaryMath(arguments, value -> value < 0.0 ? Math.ceil(value) : Math.floor(value));
            case "math.mod" -> binaryMath(arguments, (a, b) -> b == 0.0 ? 0.0 : a % b);
            case "math.min" -> binaryMath(arguments, Math::min);
            case "math.max" -> binaryMath(arguments, Math::max);
            default -> StateCondition.ZERO;
         };
      }

      private static StateCondition.Node unaryMath(List<StateCondition.Node> arguments, DoubleUnaryOperator operator) {
         StateCondition.Node argument = arguments.isEmpty() ? StateCondition.ZERO : arguments.getFirst();
         return states -> operator.applyAsDouble(StateCondition.number(argument.eval(states)));
      }

      private static StateCondition.Node binaryMath(List<StateCondition.Node> arguments, DoubleBinaryOperator operator) {
         StateCondition.Node a = arguments.isEmpty() ? StateCondition.ZERO : arguments.get(0);
         StateCondition.Node b = arguments.size() < 2 ? StateCondition.ZERO : arguments.get(1);
         return states -> operator.applyAsDouble(StateCondition.number(a.eval(states)), StateCondition.number(b.eval(states)));
      }

      private String identifier() {
         int start = this.pos;

         while (
            this.pos < this.text.length()
               && (Character.isLetterOrDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '_' || this.text.charAt(this.pos) == '.')
         ) {
            this.pos++;
         }

         return this.text.substring(start, this.pos);
      }

      private String string() {
         char quote = this.text.charAt(this.pos++);
         int end = this.text.indexOf(quote, this.pos);
         if (end < 0) {
            throw new IllegalArgumentException("unterminated string");
         }

         String value = this.text.substring(this.pos, end);
         this.pos = end + 1;
         return value;
      }

      private boolean keyword(String word) {
         this.skipSpace();
         if (!this.text.regionMatches(true, this.pos, word, 0, word.length())
            || this.pos + word.length() < this.text.length() && Character.isLetterOrDigit(this.text.charAt(this.pos + word.length()))) {
            return false;
         }

         this.pos = this.pos + word.length();
         return true;
      }

      private boolean peekMinus() {
         this.skipSpace();
         return this.pos < this.text.length() && this.text.charAt(this.pos) == '-';
      }

      private boolean eat(String token) {
         this.skipSpace();
         if (!this.text.startsWith(token, this.pos)) {
            return false;
         }

         int after = this.pos + token.length();
         if (token.length() == 1 && after < this.text.length()) {
            char next = this.text.charAt(after);
            if ((token.equals("<") || token.equals(">") || token.equals("!")) && next == '=') {
               return false;
            }

            if (token.equals("?") && next == '?') {
               return false;
            }
         }

         this.pos = after;
         return true;
      }

      private void expect(String token) {
         if (!this.eat(token)) {
            throw new IllegalArgumentException("expected '" + token + "' at " + this.pos);
         }
      }

      private void skipSpace() {
         while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
            this.pos++;
         }
      }
   }
}
