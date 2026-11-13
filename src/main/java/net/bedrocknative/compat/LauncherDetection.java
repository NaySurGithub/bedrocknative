package net.bedrocknative.compat;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class LauncherDetection {
   public static final String PROPERTY = "bedrocknative.lunarCompat";
   static final List<String> LUNAR_PROPERTIES = List.of("ichor.fabric.localModPath", "ichor.usingIsolatedProfiles", "lunar.webosr.url");
   static final String LUNAR_MOD = "ichor";
   static final String LUNAR_BOOT_CLASS = "com.moonsworth.lunar.genesis.Genesis";

   private LauncherDetection() {
   }

   public static LauncherDetection.Result detect(UnaryOperator<String> properties, Predicate<String> modLoaded, Predicate<String> classPresent) {
      String forced = properties.apply("bedrocknative.lunarCompat");
      if (forced != null) {
         switch (forced.trim().toLowerCase(Locale.ROOT)) {
            case "on":
            case "true":
            case "yes":
            case "1":
               return new LauncherDetection.Result(true, "forced by -Dbedrocknative.lunarCompat");
            case "off":
            case "false":
            case "no":
            case "0":
               return new LauncherDetection.Result(false, "switched off by -Dbedrocknative.lunarCompat");
         }
      }

      for (String property : LUNAR_PROPERTIES) {
         if (properties.apply(property) != null) {
            return new LauncherDetection.Result(true, "system property " + property);
         }
      }

      if (modLoaded.test("ichor")) {
         return new LauncherDetection.Result(true, "Fabric mod ichor");
      } else {
         return classPresent.test("com.moonsworth.lunar.genesis.Genesis")
            ? new LauncherDetection.Result(true, "class com.moonsworth.lunar.genesis.Genesis")
            : new LauncherDetection.Result(false, "no Lunar property, mod or class");
      }
   }

   public record Result(boolean lunar, String reason) {
   }
}
