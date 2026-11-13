package net.bedrocknative.compat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class DeferredReleases<K> {
   private final Map<K, Integer> left = new LinkedHashMap<>();

   public void add(K key, int ticks) {
      this.left.put(key, Math.max(1, ticks));
   }

   public void cancel(K key) {
      this.left.remove(key);
   }

   public boolean isEmpty() {
      return this.left.isEmpty();
   }

   public int size() {
      return this.left.size();
   }

   public List<K> tick() {
      if (this.left.isEmpty()) {
         return List.of();
      } else {
         List<K> due = new ArrayList<>();
         Iterator<Entry<K, Integer>> entries = this.left.entrySet().iterator();

         while (entries.hasNext()) {
            Entry<K, Integer> entry = entries.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
               due.add(entry.getKey());
               entries.remove();
            } else {
               entry.setValue(remaining);
            }
         }

         return due;
      }
   }

   public List<K> drain() {
      List<K> all = new ArrayList<>(this.left.keySet());
      this.left.clear();
      return all;
   }
}
