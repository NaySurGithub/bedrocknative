package net.bedrocknative.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.compat.LauncherDetection;
import net.fabricmc.loader.api.FabricLoader;

public final class LunarCompat {
   private static volatile LauncherDetection.Result result;
   private static final AtomicInteger DIAGNOSIS_LINES = new AtomicInteger(60);
   private static final Map<String, AtomicInteger> BUDGETS = new ConcurrentHashMap<>();

   private LunarCompat() {
   }

   public static boolean active() {
      LauncherDetection.Result current = result;
      if (current == null) {
         current = detect();
      }

      return current.lunar();
   }

   private static synchronized LauncherDetection.Result detect() {
      if (result == null) {
         LauncherDetection.Result found = LauncherDetection.detect(System::getProperty, LunarCompat::modLoaded, LunarCompat::classPresent);
         result = found;
         if (found.lunar()) {
            BedrockNative.LOGGER
               .info("Lunar Client detected ({}): the Lunar compatibility paths are on (glyph lookup, name tags, lobby, textures)", found.reason());
         }
      }

      return result;
   }

   private static boolean modLoaded(String id) {
      try {
         return FabricLoader.getInstance().isModLoaded(id);
      } catch (RuntimeException | LinkageError e) {
         return false;
      }
   }

   private static boolean classPresent(String name) {
      try {
         Class.forName(name, false, LunarCompat.class.getClassLoader());
         return true;
      } catch (ClassNotFoundException | LinkageError e) {
         return false;
      }
   }

   public static void diagnose(String topic, int max, String format, Object... arguments) {
      AtomicInteger left = BUDGETS.computeIfAbsent(topic, key -> new AtomicInteger(max));
      if (left.getAndDecrement() > 0 && DIAGNOSIS_LINES.getAndDecrement() > 0) {
         BedrockNative.LOGGER.info("Lunar [" + topic + "]: " + format, arguments);
      }
   }
}
