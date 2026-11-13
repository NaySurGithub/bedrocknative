package net.bedrocknative.client.command;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.bedrocknative.text.BedrockText;

final class Selectors {
   private static final List<String> VARIABLES = List.of("a", "e", "n", "p", "r", "s");
   private static final List<String> ARGUMENTS = List.of(
      "c",
      "dx",
      "dy",
      "dz",
      "family",
      "has_property",
      "hasitem",
      "haspermission",
      "l",
      "lm",
      "m",
      "name",
      "r",
      "rm",
      "rx",
      "rxm",
      "ry",
      "rym",
      "scores",
      "tag",
      "type",
      "x",
      "y",
      "z"
   );

   private Selectors() {
   }

   static void suggest(SuggestionsBuilder builder, Collection<String> players, boolean wildcard, CommandValues values) {
      String typed = builder.getRemaining();
      int open = typed.indexOf(91);
      if (typed.startsWith("@") && open >= 0) {
         SuggestionsBuilder inside = builder.createOffset(builder.getStart() + open + 1);
         suggestArgument(inside, typed.substring(open + 1), players, values);
         builder.add(inside);
      } else {
         String lower = typed.toLowerCase(Locale.ROOT);

         for (String variable : VARIABLES) {
            if (("@" + variable).startsWith(lower)) {
               builder.suggest("@" + variable, BedrockText.ui("commands.autocomplete." + variable));
            }
         }

         if (wildcard && "*".startsWith(lower)) {
            builder.suggest("*");
         }

         for (String name : players) {
            String quoted = Mentions.quoted(name);
            if (quoted.toLowerCase(Locale.ROOT).startsWith(lower) || name.toLowerCase(Locale.ROOT).startsWith(lower)) {
               builder.suggest(quoted);
            }
         }
      }
   }

   private static void suggestArgument(SuggestionsBuilder builder, String inside, Collection<String> players, CommandValues values) {
      int depth = 0;
      boolean quoted = false;
      int current = 0;

      for (int i = 0; i < inside.length(); i++) {
         char c = inside.charAt(i);
         if (c == '"') {
            quoted = !quoted;
         } else if (quoted || c != '{' && c != '[') {
            if (quoted || c != '}' && c != ']') {
               if (!quoted && depth == 0 && c == ',') {
                  current = i + 1;
               }
            } else if (--depth < 0) {
               return;
            }
         } else {
            depth++;
         }
      }

      if (depth <= 0 && !quoted) {
         String argument = inside.substring(current);
         int equals = argument.indexOf(61);
         SuggestionsBuilder here = builder.createOffset(builder.getStart() + current);
         if (equals < 0) {
            String typed = argument.stripLeading().toLowerCase(Locale.ROOT);

            for (String key : ARGUMENTS) {
               if (key.startsWith(typed)) {
                  here.suggest(key + "=");
               }
            }

            builder.add(here);
         } else {
            String key = argument.substring(0, equals).strip().toLowerCase(Locale.ROOT);
            int valueStart = equals + 1 + (argument.startsWith("!", equals + 1) ? 1 : 0);
            SuggestionsBuilder value = builder.createOffset(builder.getStart() + current + valueStart);
            switch (key) {
               case "type":
                  BedrockArgument.suggestValues(value, values.values("EntityType"));
                  break;
               case "m":
                  BedrockArgument.suggestValues(value, values.values("GameMode"));
                  break;
               case "tag":
                  BedrockArgument.suggestValues(value, values.values("TagValues"));
                  break;
               case "family":
                  BedrockArgument.suggestValues(value, List.of("monster", "mob", "player", "undead", "animal", "inanimate"));
                  break;
               case "name":
                  BedrockArgument.suggestValues(value, players);
            }

            builder.add(value);
         }
      }
   }
}
