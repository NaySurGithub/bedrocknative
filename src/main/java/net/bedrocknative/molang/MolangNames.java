package net.bedrocknative.molang;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class MolangNames {
   private static final ConcurrentHashMap<String, Integer> IDS = new ConcurrentHashMap<>();
   private static String[] names = new String[256];
   private static int next = 1;

   private MolangNames() {
   }

   public static int id(String name) {
      String key = name.toLowerCase(Locale.ROOT);
      Integer id = IDS.get(key);
      return id != null ? id : register(key);
   }

   private static synchronized int register(String key) {
      Integer existing = IDS.get(key);
      if (existing != null) {
         return existing;
      }

      int id = next++;
      if (id >= names.length) {
         names = Arrays.copyOf(names, names.length * 2);
      }

      names[id] = key;
      IDS.put(key, id);
      return id;
   }

   public static synchronized String name(int id) {
      return id > 0 && id < next ? names[id] : "";
   }
}
