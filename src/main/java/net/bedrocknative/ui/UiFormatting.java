package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class UiFormatting {
   private UiFormatting() {
   }

   public static int color(char code) {
      return switch (Character.toLowerCase(code)) {
         case '0' -> 0;
         case '1' -> 170;
         case '2' -> 43520;
         case '3' -> 43690;
         case '4' -> 11141120;
         case '5' -> 11141290;
         case '6' -> 16755200;
         case '7' -> 13027014;
         case '8' -> 5592405;
         case '9' -> 5592575;
         default -> -1;
         case 'a' -> 5635925;
         case 'b' -> 5636095;
         case 'c' -> 16733525;
         case 'd' -> 16733695;
         case 'e' -> 16777045;
         case 'f' -> 16777215;
         case 'g' -> 14538245;
         case 'h' -> 14931153;
         case 'i' -> 13552330;
         case 'j' -> 4471355;
         case 'm' -> 9901575;
         case 'n' -> 11823181;
         case 'p' -> 14594349;
         case 'q' -> 4694070;
         case 's' -> 2931368;
         case 't' -> 2181499;
         case 'u' -> 10116294;
         case 'v' -> 15429908;
      };
   }

   public static List<UiFormatting.Run> runs(String line, UiFormatting.State state, @Nullable UiFormatting.State[] end) {
      List<UiFormatting.Run> runs = new ArrayList<>();
      int rgb = state.rgb();
      boolean bold = state.bold();
      boolean italic = state.italic();
      boolean obfuscated = state.obfuscated();
      StringBuilder text = new StringBuilder();

      for (int i = 0; i < line.length(); i++) {
         char c = line.charAt(i);
         if (c == 167 && i + 1 < line.length()) {
            char code = Character.toLowerCase(line.charAt(++i));
            if (!text.isEmpty()) {
               runs.add(new UiFormatting.Run(text.toString(), rgb, bold, italic, obfuscated));
               text.setLength(0);
            }

            int colour = color(code);
            if (colour >= 0) {
               rgb = colour;
            } else {
               switch (code) {
                  case 'k':
                     obfuscated = true;
                     break;
                  case 'l':
                     bold = true;
                  case 'm':
                  case 'n':
                  case 'p':
                  case 'q':
                  default:
                     break;
                  case 'o':
                     italic = true;
                     break;
                  case 'r':
                     rgb = -1;
                     bold = false;
                     italic = false;
                     obfuscated = false;
               }
            }
         } else {
            text.append(c);
         }
      }

      if (!text.isEmpty()) {
         runs.add(new UiFormatting.Run(text.toString(), rgb, bold, italic, obfuscated));
      }

      if (end != null && end.length > 0) {
         end[0] = new UiFormatting.State(rgb, bold, italic, obfuscated);
      }

      return runs;
   }

   public static String strip(String text) {
      StringBuilder plain = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167 && i + 1 < text.length()) {
            i++;
         } else {
            plain.append(c);
         }
      }

      return plain.toString();
   }

   public record Run(String text, int rgb, boolean bold, boolean italic, boolean obfuscated) {
   }

   public record State(int rgb, boolean bold, boolean italic, boolean obfuscated) {
      public static final UiFormatting.State INITIAL = new UiFormatting.State(-1, false, false, false);
   }
}
