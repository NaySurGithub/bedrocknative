package net.bedrocknative.perf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RenderSwitches {
   public static volatile boolean guiBatch = on("guiBatch");
   public static final boolean fontPreload = on("fontPreload");
   public static volatile boolean inputOrderCache = on("inputOrderCache");
   public static volatile boolean animationChanges = on("animationChanges");

   private RenderSwitches() {
   }

   private static boolean on(String name) {
      return !"off".equalsIgnoreCase(System.getProperty("bedrocknative." + name, "on"));
   }

   public static Map<String, RenderSwitches.Switch> all() {
      Map<String, RenderSwitches.Switch> all = new LinkedHashMap<>();
      all.put("guiBatch", new RenderSwitches.Switch(() -> guiBatch, on -> guiBatch = on));
      all.put("inputOrderCache", new RenderSwitches.Switch(() -> inputOrderCache, on -> inputOrderCache = on));
      all.put("animationChanges", new RenderSwitches.Switch(() -> animationChanges, on -> animationChanges = on));
      return all;
   }

   public static List<String> known(List<String> names) {
      Map<String, RenderSwitches.Switch> all = all();
      return names.stream().map(String::trim).filter(all::containsKey).toList();
   }

   public record Switch(Supplier<Boolean> get, Consumer<Boolean> set) {
   }
}
