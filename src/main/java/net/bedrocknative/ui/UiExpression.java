package net.bedrocknative.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class UiExpression {
   public static final int MAX_SOURCE = 65536;
   public static final int MAX_STRING = 65536;
   private static final Pattern FORMAT = Pattern.compile("%(-)?(0)?(\\d+)?(?:\\.(\\d+))?s");
   private static final Pattern WORD_OPERATOR = Pattern.compile("(?:^|[\\s(])(and|or|not)(?:[\\s(]|$)");
   private final String source;
   private final UiExpression.Node root;
   private final @Nullable String error;
   private final int repairs;
   private boolean reported;

   private UiExpression(String source, UiExpression.Node root, @Nullable String error) {
      this(source, root, error, 0);
   }

   private UiExpression(String source, UiExpression.Node root, @Nullable String error, int repairs) {
      this.source = source;
      this.root = root;
      this.error = error;
      this.repairs = repairs;
   }

   public String source() {
      return this.source;
   }

   public boolean valid() {
      return this.error == null;
   }

   public @Nullable String error() {
      return this.error;
   }

   public int repairs() {
      return this.repairs;
   }

   public static UiExpression parse(String source) {
      if (source.length() > 65536) {
         return new UiExpression(source, new UiExpression.Literal(UiValue.NULL), "expression longer than 65536");
      }

      try {
         UiExpression.Parser parser = new UiExpression.Parser(tokenize(source));
         UiExpression.Node node = parser.expression();

         while (parser.position < parser.tokens.size()) {
            UiExpression.Token token = parser.tokens.get(parser.position);
            if (token.type != UiExpression.TokenType.CLOSE) {
               throw new IllegalArgumentException("unexpected " + token.text);
            }

            parser.position++;
            parser.repairs++;
            if (parser.position < parser.tokens.size()) {
               parser.pending = node;
               node = parser.expression();
            }
         }

         return new UiExpression(source, node, null, parser.repairs);
      } catch (IllegalArgumentException e) {
         return new UiExpression(source, new UiExpression.Literal(UiValue.NULL), e.getMessage());
      } catch (StackOverflowError e) {
         return new UiExpression(source, new UiExpression.Literal(UiValue.NULL), "expression nested too deeply");
      }
   }

   public boolean constant() {
      return this.error == null && this.hashNames().isEmpty() && this.dollarNames().isEmpty();
   }

   public static boolean looksLikeExpression(String text) {
      String trimmed = text.trim();
      if (trimmed.startsWith("(")) {
         return true;
      }

      if (trimmed.indexOf(35) < 0 && trimmed.indexOf(36) < 0) {
         return false;
      }

      if (!WORD_OPERATOR.matcher(stripQuoted(trimmed)).find()) {
         return false;
      }

      try {
         for (UiExpression.Token token : tokenize(trimmed)) {
            if (token.type == UiExpression.TokenType.WORD) {
               return false;
            }
         }

         return true;
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   private static String stripQuoted(String text) {
      return text.replaceAll("'[^']*'", "''");
   }

   public Set<String> hashNames() {
      Set<String> names = new LinkedHashSet<>();
      collect(this.root, names, true);
      return names;
   }

   public Set<String> dollarNames() {
      Set<String> names = new LinkedHashSet<>();
      collect(this.root, names, false);
      return names;
   }

   public boolean usesHash() {
      return !this.hashNames().isEmpty();
   }

   private static void collect(UiExpression.Node node, Set<String> names, boolean hash) {
      switch (node) {
         case UiExpression.Hash h:
            if (hash) {
               names.add(h.name);
            }
            break;
         case UiExpression.Dollar d:
            if (!hash) {
               names.add(d.name);
            }
            break;
         case UiExpression.Not n:
            collect(n.operand, names, hash);
            break;
         case UiExpression.Negate n:
            collect(n.operand, names, hash);
            break;
         case UiExpression.Binary b:
            collect(b.left, names, hash);
            collect(b.right, names, hash);
            break;
         case UiExpression.Literal var10:
            break;
         default:
            throw new MatchException(null, null);
      }
   }

   public UiExpression bind(Function<String, @Nullable UiValue> variables) {
      return this.error == null && !this.dollarNames().isEmpty() ? new UiExpression(this.source, fold(this.root, variables), null, this.repairs) : this;
   }

   private static UiExpression.Node fold(UiExpression.Node node, Function<String, @Nullable UiValue> variables) {
      return (UiExpression.Node)(switch (node) {
         case UiExpression.Dollar d -> {
            UiValue value = variables.apply(d.name);
            String text = value == null ? null : value.stringOrNull();
            yield text != null && text.length() > 1 && text.charAt(0) == '#' && UiControl.isName(text, 1)
               ? new UiExpression.Hash(text)
               : new UiExpression.Literal(value == null ? UiValue.NULL : value);
         }
         case UiExpression.Not n -> new UiExpression.Not(fold(n.operand, variables));
         case UiExpression.Negate n -> new UiExpression.Negate(fold(n.operand, variables));
         case UiExpression.Binary b -> new UiExpression.Binary(b.op, fold(b.left, variables), fold(b.right, variables));
         default -> node;
      });
   }

   public UiValue eval(Function<String, @Nullable UiValue> hash, Function<String, @Nullable UiValue> dollar, @Nullable UiProblems problems) {
      if (this.error != null) {
         this.report(problems, this.error);
         return UiValue.NULL;
      } else {
         UiExpression.Evaluation evaluation = new UiExpression.Evaluation(hash, dollar);
         UiValue value = evaluation.eval(this.root);
         if (evaluation.problem != null) {
            this.report(problems, evaluation.problem);
            return UiValue.NULL;
         } else {
            return value;
         }
      }
   }

   public UiValue eval(Function<String, @Nullable UiValue> hash, @Nullable UiProblems problems) {
      return this.eval(hash, name -> null, problems);
   }

   private void report(@Nullable UiProblems problems, String message) {
      if (!this.reported && problems != null) {
         this.reported = true;
         problems.add("expression", this.source + ": " + message);
      }
   }

   static String printf(Matcher format, String value) {
      boolean leftAlign = format.group(1) != null;
      boolean zero = format.group(2) != null;
      int width = format.group(3) != null ? Integer.parseInt(format.group(3)) : -1;
      int precision = format.group(4) != null ? Integer.parseInt(format.group(4)) : -1;
      String text = precision >= 0 ? firstBytes(value, precision) : value;
      int bytes = text.getBytes(StandardCharsets.UTF_8).length;
      if (width < 0 || bytes >= width) {
         return text;
      }

      if (zero) {
         return "0";
      }

      String padding = " ".repeat(Math.min(width - bytes, 65536));
      return leftAlign ? text + padding : padding + text;
   }

   static String firstBytes(String value, int limit) {
      int bytes = 0;
      int index = 0;

      while (index < value.length()) {
         int codePoint = value.codePointAt(index);
         int size = codePoint < 128 ? 1 : (codePoint < 2048 ? 2 : (codePoint < 65536 ? 3 : 4));
         if (bytes + size > limit) {
            break;
         }

         bytes += size;
         index += Character.charCount(codePoint);
      }

      return value.substring(0, index);
   }

   private static List<UiExpression.Token> tokenize(String source) {
      List<UiExpression.Token> tokens = new ArrayList<>();
      int i = 0;
      int length = source.length();

      while (i < length) {
         char c = source.charAt(i);
         if (Character.isWhitespace(c)) {
            i++;
         } else if (c == '(') {
            tokens.add(new UiExpression.Token(UiExpression.TokenType.OPEN, "("));
            i++;
         } else if (c == ')') {
            tokens.add(new UiExpression.Token(UiExpression.TokenType.CLOSE, ")"));
            i++;
         } else if (c == '\'') {
            int end = source.indexOf(39, i + 1);
            if (end < 0) {
               throw new IllegalArgumentException("unterminated string");
            }

            tokens.add(new UiExpression.Token(UiExpression.TokenType.STRING, source.substring(i + 1, end)));
            i = end + 1;
         } else if (c != '#' && c != '$') {
            if (Character.isDigit(c) || c == '.' && i + 1 < length && Character.isDigit(source.charAt(i + 1))) {
               int end = i;

               while (end < length && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '.')) {
                  end++;
               }

               tokens.add(new UiExpression.Token(UiExpression.TokenType.NUMBER, source.substring(i, end)));
               i = end;
            } else if (!Character.isLetter(c) && c != '_') {
               String two = i + 1 < length ? source.substring(i, i + 2) : "";
               switch (two) {
                  case ">=":
                  case "<=":
                  case "!=":
                  case "==":
                     tokens.add(new UiExpression.Token(UiExpression.TokenType.OPERATOR, two.equals("==") ? "=" : two));
                     i += 2;
                     break;
                  default:
                     if ("=><+-*/%".indexOf(c) < 0) {
                        throw new IllegalArgumentException("unexpected '" + c + "'");
                     }

                     tokens.add(new UiExpression.Token(UiExpression.TokenType.OPERATOR, String.valueOf(c)));
                     i++;
               }
            } else {
               int end = i;

               while (end < length && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) {
                  end++;
               }

               String word = source.substring(i, end);

               boolean keyword = switch (word) {
                  case "and", "or", "not", "true", "false" -> true;
                  default -> false;
               };
               tokens.add(new UiExpression.Token(keyword ? UiExpression.TokenType.KEYWORD : UiExpression.TokenType.WORD, word));
               i = end;
            }
         } else {
            int end = i + 1;

            while (end < length && isNameChar(source.charAt(end))) {
               end++;
            }

            if (end == i + 1) {
               throw new IllegalArgumentException("empty name at " + i);
            }

            tokens.add(new UiExpression.Token(c == '#' ? UiExpression.TokenType.HASH : UiExpression.TokenType.DOLLAR, source.substring(i, end)));
            i = end;
         }
      }

      return tokens;
   }

   private static boolean isNameChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '.';
   }

   @Override
   public String toString() {
      return this.source;
   }

   record Binary(UiExpression.Op op, UiExpression.Node left, UiExpression.Node right) implements UiExpression.Node {
   }

   record Dollar(String name) implements UiExpression.Node {
   }

   private static final class Evaluation {
      private final Function<String, @Nullable UiValue> hash;
      private final Function<String, @Nullable UiValue> dollar;
      private @Nullable String problem;

      Evaluation(Function<String, @Nullable UiValue> hash, Function<String, @Nullable UiValue> dollar) {
         this.hash = hash;
         this.dollar = dollar;
      }

      UiValue eval(UiExpression.Node node) {
         return switch (node) {
            case UiExpression.Literal l -> l.value;
            case UiExpression.Hash h -> {
               UiValue value = this.hash.apply(h.name);
               yield value == null ? UiValue.NULL : value;
            }
            case UiExpression.Dollar d -> {
               UiValue value = this.dollar.apply(d.name);
               yield value == null ? UiValue.NULL : value;
            }
            case UiExpression.Not n -> UiValue.of(!this.eval(n.operand).truthy());
            case UiExpression.Negate n -> {
               UiValue value = this.eval(n.operand);
               yield numeric(value) ? UiValue.of(-value.asNumber()) : this.mismatch("-", value);
            }
            case UiExpression.Binary b -> this.binary(b);
            default -> throw new MatchException(null, null);
         };
      }

      private UiValue binary(UiExpression.Binary b) {
         if (b.op == UiExpression.Op.OR) {
            return UiValue.of(this.eval(b.left).truthy() || this.eval(b.right).truthy());
         }

         if (b.op != UiExpression.Op.AND) {
            UiValue left = this.eval(b.left);
            UiValue right = this.eval(b.right);

            return switch (b.op) {
               case EQ -> UiValue.of(equal(left, right));
               case NE -> UiValue.of(!equal(left, right));
               case GT -> this.compare(left, right, c -> c > 0);
               case LT -> this.compare(left, right, c -> c < 0);
               case GE -> this.compare(left, right, c -> c >= 0);
               case LE -> this.compare(left, right, c -> c <= 0);
               case ADD -> this.add(left, right);
               case SUB -> this.subtract(left, right);
               case MUL -> this.multiply(left, right);
               case DIV, MOD -> this.divide(b.op, left, right);
               default -> throw new IllegalStateException();
            };
         } else {
            return UiValue.of(this.eval(b.left).truthy() && this.eval(b.right).truthy());
         }
      }

      private static boolean numeric(UiValue value) {
         return switch (value.kind()) {
            case NULL, BOOL, NUMBER, STRING -> true;
            default -> false;
         };
      }

      private UiValue mismatch(String op, UiValue... values) {
         this.fail("type mismatch for " + op + " on " + List.<UiValue>of(values));
         return UiValue.NULL;
      }

      private void fail(String message) {
         if (this.problem == null) {
            this.problem = message;
         }
      }

      private static boolean equal(UiValue left, UiValue right) {
         if (!left.isString() && !right.isString()) {
            return !left.isArray() && !left.isObject() && !right.isArray() && !right.isObject() ? left.asNumber() == right.asNumber() : left.equals(right);
         }

         if (left.isNull() || right.isNull()) {
            return left.asString().equals(right.asString());
         }

         if (left.isString() && right.isString()) {
            return left.asString().equals(right.asString());
         }

         UiValue number = left.isString() ? right : left;
         UiValue text = left.isString() ? left : right;
         return !number.isNumber() && !number.isBool()
            ? left.asString().equals(right.asString())
            : UiValue.format(number.asNumber()).equals(text.asString()) || isNumber(text.asString()) && number.asNumber() == text.asNumber();
      }

      private static boolean isNumber(String text) {
         try {
            Double.parseDouble(text.trim());
            return true;
         } catch (NumberFormatException e) {
            return false;
         }
      }

      private UiValue compare(UiValue left, UiValue right, IntPredicate test) {
         return numeric(left) && numeric(right)
            ? UiValue.of(test.test(Double.compare(left.asNumber(), right.asNumber())))
            : this.mismatch("comparison", left, right);
      }

      private UiValue add(UiValue left, UiValue right) {
         if (!left.isString() && !right.isString()) {
            return numeric(left) && numeric(right) ? UiValue.of(left.asNumber() + right.asNumber()) : this.mismatch("+", left, right);
         } else {
            return numeric(left) && numeric(right) ? this.limited(left.asString() + right.asString()) : this.mismatch("+", left, right);
         }
      }

      private UiValue subtract(UiValue left, UiValue right) {
         if (numeric(left) && numeric(right)) {
            if (left.isString() && (right.isString() || right.isNull()) || left.isNull() && right.isString()) {
               String text = left.asString();
               String remove = right.asString();
               return remove.isEmpty() ? UiValue.of(text) : UiValue.of(text.replace(remove, ""));
            } else {
               return UiValue.of(left.asNumber() - right.asNumber());
            }
         } else {
            return this.mismatch("-", left, right);
         }
      }

      private UiValue multiply(UiValue left, UiValue right) {
         if (numeric(left) && numeric(right)) {
            if (left.isString()) {
               Matcher format = UiExpression.FORMAT.matcher(left.asString());
               if (format.matches()) {
                  return this.limited(UiExpression.printf(format, right.asString()));
               }
            }

            return UiValue.of(left.asNumber() * right.asNumber());
         } else {
            return this.mismatch("*", left, right);
         }
      }

      private UiValue divide(UiExpression.Op op, UiValue left, UiValue right) {
         if (numeric(left) && numeric(right)) {
            double divisor = right.asNumber();
            if (divisor == 0.0) {
               this.fail("division by zero");
               return UiValue.NULL;
            } else {
               double dividend = left.asNumber();
               return UiValue.of(op == UiExpression.Op.DIV ? dividend / divisor : dividend % divisor);
            }
         } else {
            return this.mismatch(op == UiExpression.Op.DIV ? "/" : "%", left, right);
         }
      }

      private UiValue limited(String text) {
         if (text.length() > 65536) {
            this.fail("string longer than 65536");
            return UiValue.NULL;
         } else {
            return UiValue.of(text);
         }
      }
   }

   record Hash(String name) implements UiExpression.Node {
   }

   record Literal(UiValue value) implements UiExpression.Node {
   }

   record Negate(UiExpression.Node operand) implements UiExpression.Node {
   }

   sealed interface Node permits UiExpression.Literal, UiExpression.Hash, UiExpression.Dollar, UiExpression.Not, UiExpression.Negate, UiExpression.Binary {
   }

   record Not(UiExpression.Node operand) implements UiExpression.Node {
   }

   enum Op {
      OR,
      AND,
      EQ,
      NE,
      GT,
      LT,
      GE,
      LE,
      ADD,
      SUB,
      MUL,
      DIV,
      MOD;
   }

   private static final class Parser {
      private final List<UiExpression.Token> tokens;
      private int position;
      private static final int MAX_NESTING = 1024;
      private int depth;
      private int repairs;
      private UiExpression.@Nullable Node pending;

      Parser(List<UiExpression.Token> tokens) {
         this.tokens = tokens;
      }

      UiExpression.Node expression() {
         if (++this.depth > 1024) {
            throw new IllegalArgumentException("expression nested too deeply");
         }

         UiExpression.Node node = this.or();
         this.depth--;
         return node;
      }

      private UiExpression.Node or() {
         UiExpression.Node node = this.and();

         while (this.keyword("or")) {
            node = new UiExpression.Binary(UiExpression.Op.OR, node, this.and());
         }

         return node;
      }

      private UiExpression.Node and() {
         UiExpression.Node node = this.not();

         while (this.keyword("and")) {
            node = new UiExpression.Binary(UiExpression.Op.AND, node, this.not());
         }

         return node;
      }

      private UiExpression.Node not() {
         if (this.pending == null && this.keyword("not")) {
            if (++this.depth > 1024) {
               throw new IllegalArgumentException("expression nested too deeply");
            }

            UiExpression.Node operand = this.not();
            this.depth--;
            return new UiExpression.Not(operand);
         } else {
            return this.comparison();
         }
      }

      private UiExpression.Node comparison() {
         UiExpression.Node node = this.additive();

         while (true) {
            String op = this.operator("=", "!=", ">", "<", ">=", "<=");
            if (op == null) {
               return node;
            }
            UiExpression.Op kind = switch (op) {
               case "=" -> UiExpression.Op.EQ;
               case "!=" -> UiExpression.Op.NE;
               case ">" -> UiExpression.Op.GT;
               case "<" -> UiExpression.Op.LT;
               case ">=" -> UiExpression.Op.GE;
               default -> UiExpression.Op.LE;
            };
            node = new UiExpression.Binary(kind, node, this.additive());
         }
      }

      private UiExpression.Node additive() {
         UiExpression.Node node = this.multiplicative();

         while (true) {
            String op = this.operator("+", "-");
            if (op == null) {
               return node;
            }

            node = new UiExpression.Binary(op.equals("+") ? UiExpression.Op.ADD : UiExpression.Op.SUB, node, this.multiplicative());
         }
      }

      private UiExpression.Node multiplicative() {
         UiExpression.Node node = this.unary();

         while (true) {
            String op = this.operator("*", "/", "%");
            if (op == null) {
               return node;
            }

            node = new UiExpression.Binary(
               op.equals("*") ? UiExpression.Op.MUL : (op.equals("/") ? UiExpression.Op.DIV : UiExpression.Op.MOD), node, this.unary()
            );
         }
      }

      private UiExpression.Node unary() {
         if (this.pending != null || this.operator("-") == null) {
            return this.atom();
         }

         if (++this.depth > 1024) {
            throw new IllegalArgumentException("expression nested too deeply");
         }

         UiExpression.Node operand = this.unary();
         this.depth--;
         return new UiExpression.Negate(operand);
      }

      private UiExpression.Node atom() {
         if (this.pending != null) {
            UiExpression.Node operand = this.pending;
            this.pending = null;
            return operand;
         }

         if (this.position >= this.tokens.size()) {
            throw new IllegalArgumentException("unexpected end");
         }

         UiExpression.Token token = this.tokens.get(this.position++);

         return (UiExpression.Node)(switch (token.type) {
            case NUMBER -> {
               UiExpression.Literal var13;
               try {
                  var13 = new UiExpression.Literal(UiValue.of(Double.parseDouble(token.text)));
               } catch (NumberFormatException e) {
                  throw new IllegalArgumentException("bad number " + token.text);
               }

               yield var13;
            }
            case STRING -> {
               UiExpression.Literal var12 = new UiExpression.Literal(UiValue.of(token.text));
               yield var12;
            }
            case HASH -> {
               UiExpression.Hash var11 = new UiExpression.Hash(token.text);
               yield var11;
            }
            case DOLLAR -> {
               UiExpression.Dollar var10 = new UiExpression.Dollar(token.text);
               yield var10;
            }
            case WORD -> {
               UiExpression.Literal var9 = new UiExpression.Literal(UiValue.of(token.text));
               yield var9;
            }
            case KEYWORD -> {
               UiExpression.Literal var8 = switch (token.text) {
                  case "true" -> new UiExpression.Literal(UiValue.TRUE);
                  case "false" -> new UiExpression.Literal(UiValue.FALSE);
                  default -> throw new IllegalArgumentException("unexpected " + token.text);
               };
               yield var8;
            }
            default -> throw new IllegalArgumentException("unexpected " + token.text);
            case OPEN -> {
               UiExpression.Node inner = this.expression();
               if (this.position >= this.tokens.size()) {
                  this.repairs++;
                  UiExpression.Node var2 = inner;
                  yield var2;
               } else {
                  if (this.tokens.get(this.position).type != UiExpression.TokenType.CLOSE) {
                     throw new IllegalArgumentException("missing ) before " + this.tokens.get(this.position).text);
                  }

                  this.position++;
                  UiExpression.Node var7 = inner;
                  yield var7;
               }
            }
         });
      }

      private boolean keyword(String word) {
         if (this.position < this.tokens.size()
            && this.tokens.get(this.position).type == UiExpression.TokenType.KEYWORD
            && this.tokens.get(this.position).text.equals(word)) {
            this.position++;
            return true;
         } else {
            return false;
         }
      }

      private @Nullable String operator(String... options) {
         if (this.position < this.tokens.size() && this.tokens.get(this.position).type == UiExpression.TokenType.OPERATOR) {
            String text = this.tokens.get(this.position).text;

            for (String option : options) {
               if (option.equals(text)) {
                  this.position++;
                  return text;
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }

   private record Token(UiExpression.TokenType type, String text) {
   }

   private enum TokenType {
      NUMBER,
      STRING,
      HASH,
      DOLLAR,
      WORD,
      KEYWORD,
      OPERATOR,
      OPEN,
      CLOSE;
   }
}
