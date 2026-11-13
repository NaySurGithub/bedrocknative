package net.bedrocknative.content.own;

public final class OwnUi {
   public static final String SWITCH = "bedrocknative.ownUi";

   private OwnUi() {
   }

   public static boolean enabled() {
      String value = System.getProperty("bedrocknative.ownUi");
      return value == null || !value.trim().equalsIgnoreCase("off");
   }
}
