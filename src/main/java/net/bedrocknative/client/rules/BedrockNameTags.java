package net.bedrocknative.client.rules;

import java.util.concurrent.atomic.AtomicInteger;

public final class BedrockNameTags {
   private static volatile boolean hidden;
   private static final AtomicInteger HIDDEN_TAGS = new AtomicInteger();

   private BedrockNameTags() {
   }

   public static void onGameRule(String name, Object value) {
      if (name.equalsIgnoreCase("showtags") && value instanceof Boolean show) {
         hidden = !show;
      }
   }

   public static void reset() {
      hidden = false;
   }

   public static boolean hides() {
      if (hidden) {
         HIDDEN_TAGS.incrementAndGet();
      }

      return hidden;
   }

   public static boolean hidden() {
      return hidden;
   }

   public static int hiddenTags() {
      return HIDDEN_TAGS.get();
   }
}
