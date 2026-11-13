package net.bedrocknative.molang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CameraCalls {
   private static final Pattern CALL = Pattern.compile("\\b(?:query|q)\\s*\\.\\s*(camera_rotation|rotation_to_camera)\\s*\\(([^()]*)\\)", 2);
   private static final int EXAMPLES = 5;
   private static final int CALLS = 16;
   private static final int ARGUMENT = 40;

   private CameraCalls() {
   }

   public static List<String> in(String source) {
      if (!source.isEmpty() && source.toLowerCase(Locale.ROOT).indexOf("camera") >= 0) {
         List<String> calls = new ArrayList<>();
         Matcher matcher = CALL.matcher(source);

         while (matcher.find()) {
            String argument = matcher.group(2).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (argument.length() > 40) {
               argument = argument.substring(0, 40) + "...";
            }

            calls.add(matcher.group(1).toLowerCase(Locale.ROOT) + "(" + argument + ")");
         }

         return calls;
      } else {
         return List.of();
      }
   }

   public static boolean undocumentedAxis(String call) {
      return !call.endsWith("(0)") && !call.endsWith("(1)");
   }

   public static CameraCalls.Survey survey(Collection<MolangProgram> programs) {
      int count = 0;
      Set<String> calls = new TreeSet<>();
      List<String> examples = new ArrayList<>();

      for (MolangProgram program : programs) {
         boolean found = false;

         for (String call : in(program.source())) {
            if (undocumentedAxis(call)) {
               if (calls.size() < 16) {
                  calls.add(call);
               }

               found = true;
            }
         }

         if (found) {
            count++;
            if (examples.size() < 5) {
               String source = program.source().replaceAll("\\s+", " ");
               examples.add(source.length() > 120 ? source.substring(0, 120) + "..." : source);
            }
         }
      }

      return new CameraCalls.Survey(count, calls, List.copyOf(examples));
   }

   public record Survey(int programs, Set<String> calls, List<String> examples) {
   }
}
