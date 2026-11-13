package net.bedrocknative.oreui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.bedrocknative.text.BedrockLanguage;
import org.jspecify.annotations.Nullable;

public final class OreLang {
   private final Map<String, String> entries;
   public static final OreLang EMPTY = new OreLang(Map.of());
   static volatile @Nullable Set<String> asked;

   private OreLang(Map<String, String> entries) {
      this.entries = Map.copyOf(entries);
   }

   public static OreLang load(Path oreTexts, Optional<Path> vanillaTexts, String javaCode) {
      Map<String, String> merged = new HashMap<>();
      vanillaTexts.filter(x$0 -> Files.isDirectory(x$0)).ifPresent(folder -> merged.putAll(BedrockLanguage.load(folder, javaCode)));
      if (Files.isDirectory(oreTexts)) {
         merged.putAll(BedrockLanguage.load(oreTexts, javaCode));
      }

      return new OreLang(merged);
   }

   public static OreLang of(Map<String, String> entries) {
      return new OreLang(entries);
   }

   public OreLang with(Map<String, String> extra) {
      Map<String, String> merged = new HashMap<>(extra);
      merged.putAll(this.entries);
      return new OreLang(merged);
   }

   public boolean has(String key) {
      record(key);
      return this.entries.containsKey(key);
   }

   public int size() {
      return this.entries.size();
   }

   private static void record(String key) {
      Set<String> recording = asked;
      if (recording != null) {
         recording.add(key);
      }
   }

   public String get(String key, Object... args) {
      record(key);
      String text = this.entries.get(key);
      return format(text == null ? key : text, args);
   }

   static String format(String text, Object... args) {
      if (text.indexOf(37) < 0) {
         return text;
      }

      StringBuilder out = new StringBuilder();
      int next = 0;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '%' && i + 1 < text.length()) {
            char d = text.charAt(i + 1);
            if (d == '%') {
               out.append('%');
               i++;
            } else {
               int j = i + 1;
               int number = 0;

               while (j < text.length() && Character.isDigit(text.charAt(j))) {
                  number = number * 10 + (text.charAt(j) - '0');
                  j++;
               }

               if (j <= i + 1 || j + 1 >= text.length() || text.charAt(j) != '$' || text.charAt(j + 1) != 's' && text.charAt(j + 1) != 'd') {
                  if (j == i + 1 && (d == 's' || d == 'd')) {
                     out.append(next < args.length ? String.valueOf(args[next]) : "");
                     next++;
                     i++;
                  } else {
                     out.append(c);
                  }
               } else {
                  out.append(number >= 1 && number <= args.length ? String.valueOf(args[number - 1]) : "");
                  i = j + 1;
               }
            }
         } else {
            out.append(c);
         }
      }

      return out.toString();
   }
}
