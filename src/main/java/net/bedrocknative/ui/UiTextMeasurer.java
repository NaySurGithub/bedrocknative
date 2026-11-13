package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;

public interface UiTextMeasurer {
   UiTextMeasurer FIXED = new UiTextMeasurer() {
      @Override
      public float lineHeight(UiFont font) {
         return 10.0F * font.scale();
      }

      @Override
      public float width(String text, UiFont font) {
         int count = 0;
         int i = 0;

         while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == 167 && i < text.length()) {
               i += Character.charCount(text.codePointAt(i));
            } else {
               count++;
            }
         }

         return count * 5 * font.scale();
      }
   };

   float lineHeight(UiFont var1);

   float width(String var1, UiFont var2);

   default List<String> wrap(String text, float maxWidth, UiFont font) {
      List<String> lines = new ArrayList<>();
      StringBuilder carry = new StringBuilder();

      for (String paragraph : text.split("\n", -1)) {
         if (!Float.isNaN(maxWidth) && !(maxWidth <= 0.0F) && !(this.width(carry + paragraph, font) <= maxWidth)) {
            StringBuilder line = new StringBuilder();
            String lineCarry = carry.toString();

            for (String word : paragraph.split(" ", -1)) {
               String candidate = line.isEmpty() ? word : line + " " + word;
               if (this.width(lineCarry + candidate, font) <= maxWidth) {
                  line.setLength(0);
                  line.append(candidate);
               } else {
                  if (!line.isEmpty()) {
                     lines.add(line.toString());
                     carryFormats(carry, line.toString());
                     lineCarry = carry.toString();
                     line.setLength(0);
                  }

                  String rest = word;

                  while (!rest.isEmpty() && this.width(lineCarry + rest, font) > maxWidth) {
                     int cut = this.fitting(lineCarry, rest, maxWidth, font);
                     String piece = rest.substring(0, cut);
                     lines.add(piece);
                     carryFormats(carry, piece);
                     lineCarry = carry.toString();
                     rest = rest.substring(cut);
                  }

                  line.append(rest);
               }
            }

            lines.add(line.toString());
            carryFormats(carry, line.toString());
         } else {
            lines.add(paragraph);
            carryFormats(carry, paragraph);
         }
      }

      return lines;
   }

   default String ellipsize(String text, float maxWidth, UiFont font) {
      String line = text.replace('\n', ' ');
      if (!Float.isNaN(maxWidth) && !(this.width(line, font) <= maxWidth)) {
         String dots = "...";
         int best = 0;
         int i = 0;

         while (i < line.length()) {
            int next = i + Character.charCount(line.codePointAt(i));
            if (line.charAt(i) == 167 && next < line.length()) {
               next += Character.charCount(line.codePointAt(next));
            }

            if (this.width(line.substring(0, next) + dots, font) > maxWidth) {
               break;
            }

            best = next;
            i = next;
         }

         return line.substring(0, best).stripTrailing() + dots;
      } else {
         return line;
      }
   }

   static int linesThatFit(float height, float lineHeight, float linePadding) {
      float step = lineHeight + Math.max(0.0F, linePadding);
      return step > 0.0F && !Float.isNaN(height) ? Math.max(1, (int)Math.floor((height + Math.max(0.0F, linePadding) + 0.01F) / step)) : Integer.MAX_VALUE;
   }

   default List<String> cut(List<String> lines, int keep, float maxWidth, UiFont font) {
      if (keep <= 0 || lines.isEmpty()) {
         return List.of();
      } else if (keep >= lines.size()) {
         return lines;
      } else {
         List<String> kept = new ArrayList<>(lines.subList(0, keep));
         String last = kept.get(keep - 1);
         String dots = "...";
         StringBuilder carried = new StringBuilder();

         for (int line = 0; line < keep - 1; line++) {
            carryFormats(carried, kept.get(line));
         }

         String carry = carried.toString();
         if (!Float.isNaN(maxWidth) && !(this.width(carry + last.stripTrailing() + dots, font) <= maxWidth)) {
            int best = 0;
            int i = 0;

            while (i < last.length()) {
               int next = i + Character.charCount(last.codePointAt(i));
               if (last.charAt(i) == 167 && next < last.length()) {
                  next += Character.charCount(last.codePointAt(next));
               }

               if (this.width(carry + last.substring(0, next) + dots, font) > maxWidth) {
                  break;
               }

               best = next;
               i = next;
            }

            kept.set(keep - 1, last.substring(0, best).stripTrailing() + dots);
            return kept;
         } else {
            kept.set(keep - 1, last.stripTrailing() + dots);
            return kept;
         }
      }
   }

   private int fitting(String carry, String text, float maxWidth, UiFont font) {
      int best = 0;
      int i = 0;

      while (i < text.length()) {
         int next = i + Character.charCount(text.codePointAt(i));
         if (text.charAt(i) == 167 && next < text.length()) {
            next += Character.charCount(text.codePointAt(next));
         }

         if (best > 0 && this.width(carry + text.substring(0, next), font) > maxWidth) {
            break;
         }

         best = next;
         i = next;
      }

      return Math.max(best, Math.min(text.length(), 1));
   }

   static void carryFormats(StringBuilder carry, String text) {
      for (int i = 0; i + 1 < text.length(); i++) {
         if (text.charAt(i) == 167) {
            char code = text.charAt(i + 1);
            if (code == 'r') {
               carry.setLength(0);
            } else {
               carry.append('§').append(code);
            }

            i++;
         }
      }
   }
}
