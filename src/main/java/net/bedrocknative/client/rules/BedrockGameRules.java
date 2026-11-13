package net.bedrocknative.client.rules;

public final class BedrockGameRules {
   private static volatile boolean immediateRespawn;
   private static volatile boolean locatorBar = true;

   private BedrockGameRules() {
   }

   public static void onGameRule(String name, Object value) {
      if (value instanceof Boolean on) {
         if (name.equalsIgnoreCase("doimmediaterespawn")) {
            immediateRespawn = on;
         } else if (name.equalsIgnoreCase("locatorbar")) {
            locatorBar = on;
         }
      }
   }

   public static void reset() {
      immediateRespawn = false;
      locatorBar = true;
   }

   public static boolean immediateRespawn() {
      return immediateRespawn;
   }

   public static boolean locatorBar() {
      return locatorBar;
   }
}
