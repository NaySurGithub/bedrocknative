package net.bedrocknative.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MolangLexer {
   private static final String[] OPERATORS = new String[]{
      "->", "??", "==", "!=", "<=", ">=", "&&", "||", "+", "-", "*", "/", "!", "<", ">", "?", ":", "=", "(", ")", "{", "}", "[", "]", ",", ";", "."
   };

   private MolangLexer() {
   }

   static List<MolangLexer.Token> lex(String source, boolean lowerCaseStrings) {
      List<MolangLexer.Token> tokens = new ArrayList<>();
      int length = source.length();
      int i = 0;
      int identEnd = -1;

      while (i < length) {
         char c = source.charAt(i);
         if (Character.isWhitespace(c)) {
            i++;
         } else {
            int start = i;
            if (isDigit(c) && !tokens.isEmpty() && tokens.getLast().is(".") && tokens.getLast().position() == i - 1 && identEnd == i - 1) {
               while (i < length && isNameChar(source.charAt(i))) {
                  i++;
               }

               tokens.add(new MolangLexer.Token(MolangLexer.Kind.IDENT, source.substring(start, i).toLowerCase(Locale.ROOT), 0.0F, start));
               identEnd = i;
            } else if (isDigit(c)
               || c == '.' && i + 1 < length && isDigit(source.charAt(i + 1)) && (tokens.isEmpty() || tokens.getLast().kind != MolangLexer.Kind.IDENT)) {
               i = number(source, i);
               String text = source.substring(start, i);

               float value;
               try {
                  value = Float.parseFloat(!text.endsWith("f") && !text.endsWith("F") ? text : text.substring(0, text.length() - 1));
               } catch (NumberFormatException e) {
                  throw new MolangException("Bad number '" + text + "'", start);
               }

               tokens.add(new MolangLexer.Token(MolangLexer.Kind.NUMBER, text, value, start));
            } else if (c == '\'') {
               int end = source.indexOf(39, i + 1);
               if (end < 0) {
                  throw new MolangException("Unterminated string", start);
               }

               String text = source.substring(i + 1, end);
               tokens.add(new MolangLexer.Token(MolangLexer.Kind.STRING, lowerCaseStrings ? text.toLowerCase(Locale.ROOT) : text, 0.0F, start));
               i = end + 1;
            } else if (!Character.isLetter(c) && c != '_') {
               String operator = null;
               String[] text = OPERATORS;
               int e = text.length;
               int var11 = 0;

               while (true) {
                  if (var11 < e) {
                     String candidate = text[var11];
                     if (!source.startsWith(candidate, i)) {
                        var11++;
                        continue;
                     }

                     operator = candidate;
                  }

                  if (operator == null) {
                     throw new MolangException("Unexpected character '" + c + "'", start);
                  }

                  tokens.add(new MolangLexer.Token(MolangLexer.Kind.OP, operator, 0.0F, start));
                  i += operator.length();
                  break;
               }
            } else {
               while (i < length && isNameChar(source.charAt(i))) {
                  i++;
               }

               tokens.add(new MolangLexer.Token(MolangLexer.Kind.IDENT, alias(source.substring(start, i).toLowerCase(Locale.ROOT), tokens), 0.0F, start));
               identEnd = i;
            }
         }
      }

      tokens.add(new MolangLexer.Token(MolangLexer.Kind.EOF, "", 0.0F, length));
      return tokens;
   }

   private static String alias(String name, List<MolangLexer.Token> tokens) {
      if (!tokens.isEmpty() && tokens.getLast().is(".")) {
         return name;
      }

      return switch (name) {
         case "q" -> "query";
         case "v" -> "variable";
         case "t" -> "temp";
         case "c" -> "context";
         default -> name;
      };
   }

   private static int number(String source, int i) {
      int length = source.length();

      while (i < length && isDigit(source.charAt(i))) {
         i++;
      }

      if (i < length && source.charAt(i) == '.' && (i + 1 >= length || !Character.isLetter(source.charAt(i + 1)) || isExponent(source, i + 1))) {
         i++;

         while (i < length && isDigit(source.charAt(i))) {
            i++;
         }
      }

      if (isExponent(source, i)) {
         i++;
         if (source.charAt(i) == '+' || source.charAt(i) == '-') {
            i++;
         }

         while (i < length && isDigit(source.charAt(i))) {
            i++;
         }
      }

      if (i < length
         && (source.charAt(i) == 'f' || source.charAt(i) == 'F')
         && (i + 1 >= length || !Character.isLetterOrDigit(source.charAt(i + 1)) && source.charAt(i + 1) != '_')) {
         i++;
      }

      return i;
   }

   private static boolean isExponent(String source, int i) {
      if (i < source.length() && (source.charAt(i) == 'e' || source.charAt(i) == 'E')) {
         int next = i + 1;
         if (next < source.length() && (source.charAt(next) == '+' || source.charAt(next) == '-')) {
            next++;
         }

         return next < source.length() && isDigit(source.charAt(next));
      } else {
         return false;
      }
   }

   private static boolean isDigit(char c) {
      return c >= '0' && c <= '9';
   }

   private static boolean isNameChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
   }

   enum Kind {
      NUMBER,
      STRING,
      IDENT,
      OP,
      EOF;
   }

   record Token(MolangLexer.Kind kind, String text, float number, int position) {
      boolean is(String op) {
         return this.kind == MolangLexer.Kind.OP && this.text.equals(op);
      }
   }
}
