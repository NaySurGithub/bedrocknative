package net.bedrocknative.client.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.text.BedrockText;

public final class Mentions {
   private Mentions() {
   }

   static int lastWord(String text) {
      return text.lastIndexOf(32) + 1;
   }

   public static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> players) {
      String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
      if (!typed.startsWith("@")) {
         return builder.buildFuture();
      } else {
         if ("@a".startsWith(typed)) {
            builder.suggest("@a", BedrockText.ui("chat.mentions.autocomplete.allPlayers"));
         }

         for (String name : players) {
            String mention = "@" + quoted(name);
            if (mention.toLowerCase(Locale.ROOT).startsWith(typed) || ("@" + name).toLowerCase(Locale.ROOT).startsWith(typed)) {
               builder.suggest(mention);
            }
         }

         return builder.buildFuture();
      }
   }

   static String quoted(String name) {
      return name.indexOf(32) >= 0 ? "\"" + name + "\"" : name;
   }
}
