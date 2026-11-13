package net.bedrocknative.net.nethernet;

import org.jspecify.annotations.Nullable;

public final class PromptText {
   public static final int MAX_DOMAIN = 64;
   public static final int MAX_TARGET = 80;

   private PromptText() {
   }

   public static String plain(@Nullable String text, int max) {
      if (text != null && max > 0) {
         StringBuilder out = new StringBuilder();
         int[] points = text.codePoints().toArray();
         boolean space = false;
         int kept = 0;
         boolean cut = false;

         for (int i = 0; i < points.length; i++) {
            int point = points[i];
            if (point == 167) {
               i++;
            } else if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
               space = kept > 0;
            } else if (printable(point)) {
               if (kept >= max) {
                  cut = true;
                  break;
               }

               if (space) {
                  out.append(' ');
                  space = false;
                  if (++kept >= max) {
                     cut = true;
                     break;
                  }
               }

               out.appendCodePoint(point);
               kept++;
            }
         }

         if (cut && out.length() > 0) {
            int end = out.offsetByCodePoints(out.length(), -1);
            out.setLength(end);
            out.append('…');
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private static boolean printable(int point) {
      if (Character.isISOControl(point)) {
         return false;
      }

      return switch (Character.getType(point)) {
         case 0, 13, 14, 15, 16, 18, 19 -> false;
         default -> true;
      };
   }
}
