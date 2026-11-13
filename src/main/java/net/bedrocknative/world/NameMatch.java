package net.bedrocknative.world;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class NameMatch {
   private NameMatch() {
   }

   public static String best(String bedrock, Collection<String> candidates) {
      Set<String> words = words(bedrock);
      return candidates.stream()
         .min(Comparator.<String>comparingInt(java -> -shared(words, java)).thenComparingInt(String::length).thenComparing(Comparator.naturalOrder()))
         .orElseThrow();
   }

   private static Set<String> words(String name) {
      return new HashSet<>(Arrays.asList(name.toLowerCase(Locale.ROOT).split("[._:]")));
   }

   private static int shared(Set<String> words, String java) {
      int shared = 0;

      for (String word : java.toLowerCase(Locale.ROOT).split("[._:]")) {
         if (words.contains(word)) {
            shared++;
         }
      }

      return shared;
   }
}
