package net.bedrocknative.content;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class LoadQueue {
   private final int capacity;
   private final ArrayDeque<LoadQueue.Entry> waiting = new ArrayDeque<>();
   private boolean loading;

   public LoadQueue(int capacity) {
      this.capacity = Math.max(1, capacity);
   }

   public void begin() {
      this.loading = true;
   }

   public boolean loading() {
      return this.loading;
   }

   public boolean isEmpty() {
      return this.waiting.isEmpty();
   }

   public int size() {
      return this.waiting.size();
   }

   public boolean offer(String key, Runnable task) {
      if (!this.loading) {
         return false;
      } else {
         if (this.waiting.size() >= this.capacity) {
            this.waiting.pollFirst();
         }

         this.waiting.addLast(new LoadQueue.Entry(key, task));
         return true;
      }
   }

   public void remove(String key) {
      this.waiting.removeIf(entry -> entry.key().equals(key));
   }

   public void clear() {
      this.waiting.clear();
   }

   public void finish(Consumer<RuntimeException> onError) {
      this.loading = false;
      List<LoadQueue.Entry> tasks = new ArrayList<>(this.waiting);
      this.waiting.clear();

      for (LoadQueue.Entry entry : tasks) {
         try {
            entry.task().run();
         } catch (RuntimeException var6) {
            onError.accept(var6);
         }
      }
   }

   public void abandon() {
      this.loading = false;
      this.waiting.clear();
   }

   private record Entry(String key, Runnable task) {
   }
}
